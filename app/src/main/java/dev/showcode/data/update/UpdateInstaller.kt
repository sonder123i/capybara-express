package dev.showcode.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/**
 * 下载并安装更新。三条红线：
 *
 * 1. 下载交给系统 DownloadManager：切走、锁屏、App 被杀都还能继续，通知栏能看见进度。
 * 2. **校验和不对就不装**。下载完成后算一遍 SHA-256，和发布页的 SHA256SUMS.txt 对不上
 *    就把文件删掉——一个「能装但内容不对」的包比不更新危险得多。
 * 3. 安装必须由用户再点一下。不在下载完成回调里自动拉起安装器（Android 8+ 的
 *    「安装未知应用」要求这是用户发起的动作），也不替用户绕过那道系统开关。
 */
object UpdateInstaller {

    private const val MIME = "application/vnd.android.package-archive"

    fun fileFor(context: Context, versionName: String): File =
        File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "capybara-express-$versionName.apk",
        )

    fun enqueue(context: Context, info: UpdateInfo): Long? {
        val target = fileFor(context, info.versionName)
        runCatching { if (target.exists()) target.delete() }
        val request = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
            setTitle("卡皮巴拉快递助手 ${info.versionName}")
            setDescription("正在下载更新")
            setMimeType(MIME)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, target.name)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }
        val manager = context.getSystemService(DownloadManager::class.java) ?: return null
        return runCatching { manager.enqueue(request) }.getOrNull()
    }

    /** 某一笔下载的当前状态。没有这笔（被系统清掉、被用户删了）返回 null。 */
    data class Progress(val status: Int, val bytes: Long, val total: Long, val reason: Int) {
        val running get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING
        val paused get() = status == DownloadManager.STATUS_PAUSED
        val successful get() = status == DownloadManager.STATUS_SUCCESSFUL
    }

    fun progress(context: Context, id: Long): Progress? {
        val manager = context.getSystemService(DownloadManager::class.java) ?: return null
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                fun column(name: String) = cursor.getColumnIndexOrThrow(name)
                Progress(
                    status = cursor.getInt(column(DownloadManager.COLUMN_STATUS)),
                    bytes = cursor.getLong(column(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        .coerceAtLeast(0),
                    total = cursor.getLong(column(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        .coerceAtLeast(0),
                    reason = cursor.getInt(column(DownloadManager.COLUMN_REASON)),
                )
            }
        }.getOrNull()
    }

    /**
     * 取消会把系统里的记录和文件一起删掉；调用方负责把本地状态也退回「未下载」。
     *
     * 没有暂停/继续：`DownloadManager.pauseDownload` 是系统内部接口，公开 SDK 里不存在。
     * 想要可暂停就只能自建下载器——那会把「切走、锁屏、App 被杀仍能继续」一起丢掉，
     * 对一次十几 MB 的更新不值得。下载在通知栏里看得见，也随时可以取消。
     */
    fun cancel(context: Context, id: Long) {
        runCatching { context.getSystemService(DownloadManager::class.java)?.remove(id) }
    }

    /** 算文件自己的 SHA-256，和发布页给的对比。expected 为空一律算失败。 */
    fun verify(file: File, expected: String?): Boolean {
        if (expected.isNullOrBlank() || !file.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expected, ignoreCase = true)
    }

    /**
     * 装之前再确认一次「这确实是本应用的新版本」，返回 null 表示通过。
     *
     * SHA-256 只能证明「文件没被换过」——它证明不了这个包属于本应用。一个被换了内容的
     * Release 资产只要同时改掉 SHA256SUMS.txt 就能过第一道关；包名与签名这两条是
     * 换不掉的，所以必须在拉起安装器之前再拦一次。
     *
     * 不比对 `versionName`：发布标签可能带 `-beta` 之类的后缀，而 APK 里的 versionName
     * 是干净的三段式；拿它们比会把合法更新挡在门外。版本前进看 versionCode 就够。
     */
    fun identityProblem(context: Context, file: File): String? {
        if (!file.exists()) return "安装包不在了，重新下载一次"
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: return "无法识别这个安装包"
        if (archive.packageName != context.packageName) return "安装包不是本应用，已删除"
        val installed = runCatching { pm.getPackageInfo(context.packageName, flags) }.getOrNull()
            ?: return "读不到本机应用信息"
        if (versionCodeOf(archive) <= versionCodeOf(installed)) return "这个安装包不比当前版本新"
        val incoming = signaturesOf(archive)
        val current = signaturesOf(installed)
        if (incoming.isEmpty() || current.isEmpty() || incoming != current) {
            return "安装包签名与当前应用不一致，已删除"
        }
        return null
    }

    private fun versionCodeOf(info: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun signaturesOf(info: android.content.pm.PackageInfo): Set<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        }

    sealed interface Install {
        data object Started : Install
        data object NeedsPermission : Install
        data class Failed(val reason: String) : Install
    }

    fun install(context: Context, file: File): Install {
        if (!file.exists()) return Install.Failed("安装包不在了，重新下载一次")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return Install.NeedsPermission
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Install.Started
        }.getOrElse { Install.Failed("打不开安装器，去发布页手动下载") }
    }

    fun permissionSettings(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
