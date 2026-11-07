package dev.showcode.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
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
