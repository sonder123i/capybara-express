package dev.showcode.data.update

import android.content.Context
import dev.showcode.BuildConfig
import dev.showcode.data.Prefs
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 下载在四种状态之间走：没开始 / 在下 / 停了 / 校验完。 */
enum class DownloadPhase { Idle, Downloading, Paused, Verifying, Ready }

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object NoRelease : UpdateState
    data class Failed(val message: String) : UpdateState
    data class Available(
        val info: UpdateInfo,
        val phase: DownloadPhase = DownloadPhase.Idle,
        val bytes: Long = 0L,
        val total: Long? = info.sizeBytes,
        /** 一次性提示（校验失败、需要安装权限…），不改变「有更新可装」这件事本身。 */
        val notice: String? = null,
        val file: File? = null,
    ) : UpdateState
}

/**
 * 更新状态的家。原来这些状态住在 `SettingsScreen` 的局部 `remember` 里，
 * 于是「启动时查一次」「离开设置页后下载完没人校验」「重新进入设置页要再查一遍」
 * 都做不了——状态跟着屏幕生灭，下载却活在系统里。
 *
 * 这里的取舍与 `UpdateChecker` 一致：唯一的网络请求是 Release API，且自动检查
 * 6 小时最多一次、可在设置里关掉。下载仍交给系统 DownloadManager（切走也不断），
 * 本类只负责观察进度与把关校验，不自己造一套下载器。
 */
class UpdateCenter private constructor(context: Context) {

    private val app: Context = context.applicationContext
    private val prefs = app.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val settings = Prefs(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** 要不要把更新弹窗给用户看。「稍后」只关这个，不丢下载。 */
    private val _prompt = MutableStateFlow(false)
    val prompt: StateFlow<Boolean> = _prompt.asStateFlow()

    private var checkJob: Job? = null
    private var pollJob: Job? = null
    private var downloadId: Long = -1L

    init {
        scope.launch { restore() }
    }

    /**
     * 查更新。
     *
     * - `manual = true`：设置页那颗按钮，永远真的去问一次。
     * - `manual = false`：启动时调用，受「自动检查」开关与 6 小时节流约束。
     *
     * 正在展示新版本（含下载中/已就绪）时直接返回：重复查只会把用户已经看到的
     * 进度条换掉，换不来任何新信息。
     */
    fun check(manual: Boolean) {
        if (checkJob?.isActive == true) return
        if (!manual) {
            if (!settings.autoCheckUpdate) return
            val since = System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0L)
            if (since in 0 until AUTO_INTERVAL_MS) return
        }
        if (_state.value is UpdateState.Available) {
            /* 已经查到的那条不重新查；手动按「检查更新」就把弹窗再拿回来，
               否则用户点了一颗按钮，屏幕上一点反应都没有。 */
            if (manual) _prompt.value = true
            return
        }
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        if (manual) _prompt.value = false
        _state.value = UpdateState.Checking
        checkJob = scope.launch {
            val result = runCatching { UpdateChecker.check(BuildConfig.VERSION_NAME) }
            if (!isActive) return@launch
            when (val value = result.getOrNull()) {
                null -> _state.value = UpdateState.Failed("检查更新时出了点问题，稍后再试")
                UpdateResult.UpToDate -> _state.value = UpdateState.UpToDate
                UpdateResult.NoRelease -> _state.value = UpdateState.NoRelease
                is UpdateResult.Failed -> _state.value = UpdateState.Failed(value.reason)
                is UpdateResult.Available -> {
                    _state.value = UpdateState.Available(value.info)
                    if (manual || shouldPrompt(value.info)) _prompt.value = true
                }
            }
        }
    }

    /** 「稍后」：同一版本 24 小时内不再自己弹；手动查仍然会弹。 */
    fun dismissPrompt() {
        (_state.value as? UpdateState.Available)?.info?.let { info ->
            prefs.edit().putLong(deferKey(info), System.currentTimeMillis() + DEFER_MS).apply()
        }
        _prompt.value = false
    }

    fun download() {
        val current = _state.value as? UpdateState.Available ?: return
        when (current.phase) {
            DownloadPhase.Ready -> install()
            DownloadPhase.Downloading, DownloadPhase.Verifying -> Unit
            /* 系统自己把下载挂起（等网络、等 WiFi）时公开接口没有「继续」可调；
               再 enqueue 一次会覆盖目标文件、从头开始——这是唯一能保证动作发生的一条。 */
            DownloadPhase.Paused, DownloadPhase.Idle ->
                enqueue(current.copy(phase = DownloadPhase.Idle))
        }
    }

    fun cancelDownload() {
        val current = _state.value as? UpdateState.Available ?: return
        pollJob?.cancel()
        if (downloadId > 0) UpdateInstaller.cancel(app, downloadId)
        downloadId = -1
        runCatching { UpdateInstaller.fileFor(app, current.info.versionName).delete() }
        clearPersisted()
        _state.value = current.copy(
            phase = DownloadPhase.Idle,
            bytes = 0L,
            file = null,
            notice = null,
        )
    }

    fun install() {
        val current = _state.value as? UpdateState.Available ?: return
        val file = current.file ?: UpdateInstaller.fileFor(app, current.info.versionName)
        scope.launch {
            /* 下载完已经算过一次；装之前再算一次。文件从校验到安装之间还可能被换掉，
               而这中间隔着用户的一段时间（去设置权限、切回桌面）。 */
            val intact = withContext(Dispatchers.IO) {
                UpdateInstaller.verify(file, current.info.sha256)
            }
            if (!intact) {
                runCatching { file.delete() }
                downloadId = -1
                clearPersisted()
                _state.value = current.copy(
                    phase = DownloadPhase.Idle,
                    bytes = 0L,
                    file = null,
                    notice = "安装包校验失败，已删除，请重新下载",
                )
                return@launch
            }
            val problem = withContext(Dispatchers.IO) { UpdateInstaller.identityProblem(app, file) }
            if (problem != null) {
                runCatching { file.delete() }
                downloadId = -1
                clearPersisted()
                _state.value = current.copy(
                    phase = DownloadPhase.Idle,
                    bytes = 0L,
                    file = null,
                    notice = problem,
                )
                return@launch
            }
            when (val result = withContext(Dispatchers.Main) { UpdateInstaller.install(app, file) }) {
                UpdateInstaller.Install.Started -> {
                    _prompt.value = false
                    _state.value = current.copy(notice = "安装器已打开，按系统提示完成安装")
                }
                UpdateInstaller.Install.NeedsPermission -> {
                    withContext(Dispatchers.Main) {
                        runCatching { app.startActivity(UpdateInstaller.permissionSettings(app)) }
                    }
                    _state.value = current.copy(notice = "允许「安装未知应用」后再点一次安装")
                }
                is UpdateInstaller.Install.Failed ->
                    _state.value = current.copy(notice = result.reason)
            }
        }
    }

    /* ── 下载 ───────────────────────────────────────────────────────── */

    private fun enqueue(
        current: UpdateState.Available,
        usedFallback: Boolean = false,
        keepNotice: Boolean = false,
    ) {
        val info = current.info
        if (info.sha256.isNullOrBlank()) {
            _state.value = current.copy(notice = "这个版本没有校验和，请点「用浏览器下载」手动安装")
            return
        }
        /* 重新下载（含系统挂起后重来）时先把上一笔记录撤掉：两条记录指向同一个目标文件，
           旧的那条晚点恢复会把新下载覆盖成一个半截文件。 */
        if (downloadId > 0) {
            UpdateInstaller.cancel(app, downloadId)
            downloadId = -1
        }
        _state.value = current.copy(
            phase = DownloadPhase.Downloading,
            bytes = 0L,
            file = null,
            notice = if (keepNotice) current.notice else null,
        )
        scope.launch {
            val id = UpdateInstaller.enqueue(app, info)
            if (!isActive) return@launch
            if (id == null) {
                failDownload(info, "下载没起来，稍后再试")
                return@launch
            }
            downloadId = id
            persist(info, id, DownloadPhase.Downloading)
            startPolling(id, info, usedFallback)
        }
    }

    private fun startPolling(id: Long, info: UpdateInfo, usedFallback: Boolean = false) {
        pollJob?.cancel()
        pollJob = scope.launch {
            /* DownloadManager 卡在 PENDING 时会一直返回 0 字节、也不报错。
               记录「0 字节持续了多久」，超时就换源或让用户走浏览器，不再无限转圈。 */
            var stallSince: Long? = null
            while (isActive) {
                val progress = UpdateInstaller.progress(app, id)
                if (progress == null) {
                    failDownload(info, "下载记录不见了，重新下载一次")
                    return@launch
                }
                val total = progress.total.takeIf { it > 0 } ?: info.sizeBytes
                when {
                    progress.successful -> {
                        _state.value = UpdateState.Available(
                            info = info,
                            phase = DownloadPhase.Verifying,
                            bytes = total ?: progress.bytes,
                            total = total,
                        )
                        val file = UpdateInstaller.fileFor(app, info.versionName)
                        val ok = withContext(Dispatchers.IO) {
                            UpdateInstaller.verify(file, info.sha256)
                        }
                        if (!isActive) return@launch
                        if (ok) {
                            downloadId = -1
                            persist(info, -1, DownloadPhase.Ready)
                            _state.value = UpdateState.Available(
                                info = info,
                                phase = DownloadPhase.Ready,
                                bytes = file.length(),
                                total = file.length(),
                                file = file,
                            )
                        } else {
                            runCatching { file.delete() }
                            failDownload(info, "校验失败，安装包已删除")
                        }
                        return@launch
                    }
                    progress.paused -> {
                        /* 系统挂起（常见于流量策略/等 WiFi）。继续轮询：网络回来后它会自己走，
                           界面不需要用户先点一下才知道。 */
                        _state.value = UpdateState.Available(
                            info = info,
                            phase = DownloadPhase.Paused,
                            bytes = progress.bytes,
                            total = total,
                        )
                    }
                    progress.status == android.app.DownloadManager.STATUS_FAILED -> {
                        failDownload(info, failureText(progress.reason))
                        return@launch
                    }
                    else -> {
                        _state.value = UpdateState.Available(
                            info = info,
                            phase = DownloadPhase.Downloading,
                            bytes = progress.bytes,
                            total = total,
                        )
                        val now = System.currentTimeMillis()
                        if (progress.bytes > 0) {
                            stallSince = null
                        } else if (stallSince == null) {
                            stallSince = now
                        } else if (now - stallSince!! > STALL_TIMEOUT_MS) {
                            switchDownloadSource(id, info, usedFallback)
                            return@launch
                        }
                    }
                }
                delay(POLL_MS)
            }
        }
    }

    /**
     * 下载长时间 0 字节：Gitee 的下载链要两次 302、第一跳还会 Set-Cookie，
     * 系统 DownloadManager 在国内 ROM 上常卡在这一步，既不报错也不推进。
     * 先换另一家的单跳直链重试；换过还不动就停下来，把「用浏览器下载」
     * 这条一定能走通的路交给用户。
     */
    private fun switchDownloadSource(id: Long, info: UpdateInfo, usedFallback: Boolean) {
        UpdateInstaller.cancel(app, id)
        downloadId = -1
        pollJob?.cancel()
        runCatching { UpdateInstaller.fileFor(app, info.versionName).delete() }
        clearPersisted()
        val fallback = info.fallbackApkUrl?.takeIf { it.isNotBlank() && it != info.apkUrl }
        if (!usedFallback && fallback != null) {
            val switched = info.copy(apkUrl = fallback, fallbackApkUrl = null)
            _state.value = UpdateState.Available(
                info = switched,
                phase = DownloadPhase.Idle,
                notice = "国内源没动，已自动切到备用源重新下载",
            )
            enqueue(_state.value as UpdateState.Available, usedFallback = true, keepNotice = true)
        } else {
            _state.value = UpdateState.Available(
                info = info,
                phase = DownloadPhase.Idle,
                notice = "下载一直没动，请点「用浏览器下载」手动安装",
            )
        }
    }

    /** 下载失败原因只说人话。未知原因也不报数字——用户拿那个数字做不了任何事。 */
    private fun failureText(reason: Int): String = when (reason) {
        android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足，腾出空间后再试"
        android.app.DownloadManager.ERROR_FILE_ERROR -> "安装包写不进存储，换个网络或稍后再试"
        android.app.DownloadManager.ERROR_UNHANDLED_HTTP_CODE,
        android.app.DownloadManager.ERROR_HTTP_DATA_ERROR -> "下载中断了，检查网络后再试"
        else -> "下载没完成，稍后再试"
    }

    private fun failDownload(info: UpdateInfo, message: String) {        pollJob?.cancel()
        downloadId = -1
        clearPersisted()
        runCatching { UpdateInstaller.fileFor(app, info.versionName).delete() }
        _state.value = UpdateState.Available(
            info = info,
            phase = DownloadPhase.Idle,
            notice = message,
        )
    }

    /* ── 进程重启后把上次那笔下载接回来 ─────────────────────────────── */

    private suspend fun restore() {
        val raw = prefs.getString(KEY_TASK, null) ?: return
        val saved = runCatching { JSONObject(raw) }.getOrNull() ?: run { clearPersisted(); return }
        val info = infoOf(saved) ?: run { clearPersisted(); return }
        if (!dev.showcode.core.update.isNewerVersion(info.versionName, BuildConfig.VERSION_NAME)) {
            runCatching { UpdateInstaller.fileFor(app, info.versionName).delete() }
            clearPersisted()
            return
        }
        val phase = runCatching { DownloadPhase.valueOf(saved.optString("phase")) }
            .getOrDefault(DownloadPhase.Idle)
        val file = UpdateInstaller.fileFor(app, info.versionName)

        if (phase == DownloadPhase.Ready && file.exists()) {
            val ok = withContext(Dispatchers.IO) { UpdateInstaller.verify(file, info.sha256) }
            if (ok) {
                _state.value = UpdateState.Available(
                    info = info,
                    phase = DownloadPhase.Ready,
                    bytes = file.length(),
                    total = file.length(),
                    file = file,
                )
                return
            }
            clearPersisted()
            return
        }

        val id = saved.optLong("downloadId", -1L)
        if (id <= 0) return
        val progress = UpdateInstaller.progress(app, id)
        if (progress == null) {
            runCatching { file.delete() }
            clearPersisted()
            return
        }
        downloadId = id
        val total = progress.total.takeIf { it > 0 } ?: info.sizeBytes
        when {
            progress.successful -> {
                val ok = withContext(Dispatchers.IO) { UpdateInstaller.verify(file, info.sha256) }
                if (ok) {
                    downloadId = -1
                    persist(info, -1, DownloadPhase.Ready)
                    _state.value = UpdateState.Available(
                        info = info,
                        phase = DownloadPhase.Ready,
                        bytes = file.length(),
                        total = file.length(),
                        file = file,
                    )
                } else {
                    runCatching { file.delete() }
                    failDownload(info, "校验失败，安装包已删除")
                }
            }
            progress.paused -> {
                _state.value = UpdateState.Available(
                    info = info,
                    phase = DownloadPhase.Paused,
                    bytes = progress.bytes,
                    total = total,
                )
                startPolling(id, info)
            }
            else -> {
                _state.value = UpdateState.Available(
                    info = info,
                    phase = DownloadPhase.Downloading,
                    bytes = progress.bytes,
                    total = total,
                )
                startPolling(id, info)
            }
        }
    }

    /* ── 落盘 ───────────────────────────────────────────────────────── */

    private fun persist(info: UpdateInfo, id: Long, phase: DownloadPhase) {
        val json = JSONObject()
            .put("versionName", info.versionName)
            .put("notes", info.notes)
            .put("apkUrl", info.apkUrl)
            .put("sha256", info.sha256 ?: "")
            .put("sizeBytes", info.sizeBytes ?: -1L)
            .put("source", info.source.name)
            .put("pageUrl", info.pageUrl)
            .put("fallbackApkUrl", info.fallbackApkUrl ?: "")
            .put("downloadId", id)
            .put("phase", phase.name)
        prefs.edit().putString(KEY_TASK, json.toString()).apply()
    }

    private fun infoOf(json: JSONObject): UpdateInfo? {
        val version = json.optString("versionName").takeIf { it.isNotBlank() } ?: return null
        val url = json.optString("apkUrl").takeIf { it.isNotBlank() } ?: return null
        return UpdateInfo(
            versionName = version,
            notes = json.optString("notes"),
            apkUrl = url,
            sha256 = json.optString("sha256").takeIf { it.isNotBlank() },
            sizeBytes = json.optLong("sizeBytes", -1L).takeIf { it > 0 },
            source = runCatching { UpdateSource.valueOf(json.optString("source")) }
                .getOrDefault(UpdateSource.GITHUB),
            pageUrl = json.optString("pageUrl"),
            fallbackApkUrl = json.optString("fallbackApkUrl").takeIf { it.isNotBlank() },
        )
    }

    private fun clearPersisted() = prefs.edit().remove(KEY_TASK).apply()

    private fun shouldPrompt(info: UpdateInfo): Boolean =
        System.currentTimeMillis() >= prefs.getLong(deferKey(info), 0L)

    private fun deferKey(info: UpdateInfo) = "defer:${info.versionName}"

    companion object {
        private const val NAME = "showcode_update"
        private const val KEY_LAST_CHECK = "last_check_at"
        private const val KEY_TASK = "task"
        private const val AUTO_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val DEFER_MS = 24 * 60 * 60 * 1000L
        private const val POLL_MS = 600L
        /** 0 字节持续这么久就判定卡住：换源，别让用户对着「正在下载…」干等。 */
        private const val STALL_TIMEOUT_MS = 20_000L

        @Volatile
        private var instance: UpdateCenter? = null

        fun get(context: Context): UpdateCenter =
            instance ?: synchronized(this) {
                instance ?: UpdateCenter(context).also { instance = it }
            }
    }
}
