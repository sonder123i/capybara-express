package dev.showcode.data.update

import dev.showcode.core.update.compareVersions
import dev.showcode.core.update.isNewerVersion
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 一个更新的来源。国内先问 Gitee，拿不到再问 GitHub。 */
enum class UpdateSource(val label: String) {
    GITEE("Gitee"),
    GITHUB("GitHub"),
}

data class UpdateInfo(
    /** 去掉前缀 v 的版本号，例如 `0.3.0`。 */
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    /** 发布页 SHA256SUMS.txt 里的那串；没有就不让 App 内安装（只给发布页）。 */
    val sha256: String?,
    val sizeBytes: Long?,
    val source: UpdateSource,
    val pageUrl: String,
)

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data object NoRelease : UpdateResult
    data class Available(val info: UpdateInfo) : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

/**
 * 检查更新。
 *
 * 由 `UpdateCenter` 在两种时机调用：启动后（6 小时最多一次，可在设置里关掉）与用户
 * 手动按「检查更新」。没有后台轮询，也不带账号信息——只问公开的 Release API。
 *
 * 两个源都走公开的 Release API，JSON 形状基本一致（Gitee 的 `assets[]` 与 GitHub 同构）。
 * Gitee 默认按旧到新排序，所以显式带 `direction=desc`；两边的结果取版本更高的那个，
 * 同版本时用 Gitee（国内下载更快）。
 */
object UpdateChecker {

    private const val OWNER = "sonder123i"
    private const val REPO = "capybara-express"

    private const val GITEE_API = "https://gitee.com/api/v5/repos/$OWNER/$REPO"
    private const val GITHUB_API = "https://api.github.com/repos/$OWNER/$REPO"

    const val GITEE_PAGE = "https://gitee.com/$OWNER/$REPO/releases"
    const val GITHUB_PAGE = "https://github.com/$OWNER/$REPO/releases"

    private const val GITEE_LIST = "$GITEE_API/releases?per_page=5&direction=desc"
    private const val GITHUB_LIST = "$GITHUB_API/releases?per_page=5"

    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        val giteeAttempt = runCatching { fetchReleases(GITEE_LIST, github = false) }
        val githubAttempt = runCatching { fetchReleases(GITHUB_LIST, github = true) }

        val gitee = giteeAttempt.getOrNull()?.let { pickRelease(it, UpdateSource.GITEE) }
        val github = githubAttempt.getOrNull()?.let { pickRelease(it, UpdateSource.GITHUB) }

        val best = when {
            gitee == null -> github
            github == null -> gitee
            compareVersions(gitee.release.tag, github.release.tag) >= 0 -> gitee
            else -> github
        } ?: run {
            /* 两个源都通、但都没有带 APK 的发行版：这是「还没发布」，不是网络错。 */
            val bothReached = giteeAttempt.isSuccess && githubAttempt.isSuccess
            return@withContext if (bothReached) {
                UpdateResult.NoRelease
            } else {
                UpdateResult.Failed("这次没连上更新源，稍后再试")
            }
        }

        if (!isNewerVersion(best.release.tag, currentVersion)) {
            return@withContext UpdateResult.UpToDate
        }

        val apk = best.release.apkAsset() ?: return@withContext UpdateResult.NoRelease
        val sha = resolveSha256(best.release, apk, github = best.source == UpdateSource.GITHUB)
        UpdateResult.Available(
            UpdateInfo(
                versionName = displayVersion(best.release.tag),
                notes = notesOf(best.release),
                apkUrl = apk.url,
                sha256 = sha,
                sizeBytes = apk.size?.takeIf { it > 0 },
                source = best.source,
                pageUrl = if (best.source == UpdateSource.GITEE) GITEE_PAGE else GITHUB_PAGE,
            )
        )
    }

    /* ── 解析（internal 是为了单测能直接喂 JSON） ─────────────────────── */

    internal data class Asset(
        val name: String,
        val url: String,
        val size: Long?,
        val digest: String?,
    )

    internal data class Release(
        val tag: String,
        val name: String,
        val body: String,
        val prerelease: Boolean,
        val assets: List<Asset>,
    ) {
        fun apkAsset(): Asset? = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
    }

    internal data class Picked(val release: Release, val source: UpdateSource)

    internal fun parseReleases(raw: String): List<Release> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()
        val array = if (text.startsWith("[")) {
            JSONArray(text)
        } else {
            JSONArray().put(JSONObject(text))
        }
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val tag = o.optString("tag_name").takeIf { it.isNotBlank() } ?: continue
                val assetsJson = o.optJSONArray("assets") ?: JSONArray()
                val assets = buildList {
                    for (j in 0 until assetsJson.length()) {
                        val a = assetsJson.optJSONObject(j) ?: continue
                        val name = a.optString("name")
                        val url = a.optString("browser_download_url")
                        if (name.isBlank() || url.isBlank()) continue
                        add(
                            Asset(
                                name = name,
                                url = url,
                                size = a.optLong("size", -1L).takeIf { it >= 0 },
                                digest = a.optString("digest").takeIf { it.isNotBlank() },
                            )
                        )
                    }
                }
                add(
                    Release(
                        tag = tag,
                        name = o.optString("name"),
                        body = o.optString("body"),
                        prerelease = o.optBoolean("prerelease", false),
                        assets = assets,
                    )
                )
            }
        }
    }

    /** 只挑带 APK 的发行版；有正式版就不拿预发布版。列表已经是新到旧。 */
    internal fun pickRelease(releases: List<Release>, source: UpdateSource): Picked? {
        val withApk = releases.filter { it.apkAsset() != null }
        val stable = withApk.firstOrNull { !it.prerelease }
        val chosen = stable ?: withApk.firstOrNull() ?: return null
        return Picked(chosen, source)
    }

    internal fun parseSha256Sums(text: String, apkName: String): String? {
        val line = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.endsWith(apkName) }
            ?: return null
        val hash = line.substringBefore(' ').trim()
        return hash.takeIf { it.length == 64 && it.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } }
    }

    private fun displayVersion(tag: String): String =
        tag.trim().removePrefix("v").removePrefix("V")

    private fun notesOf(release: Release): String {
        val body = release.body.trim()
        if (body.isNotEmpty()) return body.take(2000)
        val name = release.name.trim()
        return if (name.isNotEmpty()) name else "新版本 ${displayVersion(release.tag)}"
    }

    private fun resolveSha256(release: Release, apk: Asset, github: Boolean): String? {
        /* GitHub 新上传的 asset 自带 digest；Gitee 靠同目录的 SHA256SUMS.txt。 */
        apk.digest?.removePrefix("sha256:")?.takeIf { it.length == 64 }?.let { return it }
        val sums = release.assets.firstOrNull { it.name.equals("SHA256SUMS.txt", ignoreCase = true) }
            ?: return null
        val text = runCatching { httpGet(sums.url, github = false) }.getOrNull() ?: return null
        return parseSha256Sums(text, apk.name)
    }

    private fun fetchReleases(url: String, github: Boolean): List<Release> =
        parseReleases(httpGet(url, github))

    private fun httpGet(url: String, github: Boolean): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "capybara-express-update")
            setRequestProperty(
                "Accept",
                if (github) "application/vnd.github+json" else "application/json",
            )
        }
        return try {
            val code = conn.responseCode
            if (code == 404) return "[]"
            if (code !in 200..299) throw IOException("HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
