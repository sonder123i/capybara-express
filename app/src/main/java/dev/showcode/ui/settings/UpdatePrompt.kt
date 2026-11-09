package dev.showcode.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.showcode.BuildConfig
import dev.showcode.ui.glass.GlassTier
import dev.showcode.R
import dev.showcode.data.update.DownloadPhase
import dev.showcode.data.update.UpdateCenter
import dev.showcode.data.update.UpdateInfo
import dev.showcode.data.update.UpdateState
import com.kyant.backdrop.Backdrop
import dev.showcode.ui.GhostButton
import dev.showcode.ui.GlassModal
import dev.showcode.ui.SolidButton
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceSunken
import dev.showcode.ui.theme.UrgencyDueSoon
import java.util.Locale

/**
 * 发现新版本时的全局弹窗。
 *
 * 与设置页那张「关于」卡看同一份 `UpdateCenter` 状态：弹窗关掉不打断下载，
 * 回到设置页仍然看得到进度。所有按钮都只调用 `UpdateCenter`，这里不碰网络也不碰文件。
 */
@Composable
fun UpdatePrompt(
    center: UpdateCenter = UpdateCenter.get(LocalContext.current),
    tier: GlassTier = GlassTier.OPAQUE,
    backdrop: Backdrop? = null,
) {
    val state by center.state.collectAsState()
    val available = state as? UpdateState.Available ?: return
    val info = available.info
    val context = LocalContext.current
    val noHash = info.sha256.isNullOrBlank()
    val busy = available.phase in setOf(DownloadPhase.Downloading, DownloadPhase.Verifying)

    GlassModal(
        visible = true,
        onDismissRequest = { center.dismissPrompt() },
        tier = tier,
        backdrop = backdrop,
    ) {
            Text(
                text = stringResource(R.string.update_dialog_title, info.versionName),
                style = ShowCodeText.Headline,
                color = Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    stringResource(R.string.update_dialog_current, BuildConfig.VERSION_NAME),
                    info.source.label,
                    updateSizeText(info.sizeBytes),
                ).joinToString(" · "),
                style = ShowCodeText.Caption,
                color = InkMuted,
            )

            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = info.notes,
                    style = ShowCodeText.Body,
                    color = InkSecondary,
                )
            }

            when (available.phase) {
                DownloadPhase.Downloading, DownloadPhase.Paused, DownloadPhase.Verifying -> {
                    Spacer(Modifier.height(16.dp))
                    val fraction = available.progressFraction()
                    UpdateProgressBar(fraction = fraction)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (available.phase == DownloadPhase.Verifying) {
                            stringResource(R.string.update_dialog_verifying)
                        } else {
                            available.progressText()
                        },
                        style = ShowCodeText.Caption,
                        color = InkMuted,
                    )
                }
                DownloadPhase.Ready -> {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.update_dialog_ready),
                        style = ShowCodeText.Meta,
                        color = InkSecondary,
                    )
                }
                DownloadPhase.Idle -> Unit
            }

            available.notice?.let { notice ->
                Spacer(Modifier.height(10.dp))
                Text(text = notice, style = ShowCodeText.Meta, color = UrgencyDueSoon)
            }

            Spacer(Modifier.height(18.dp))
            when {
                noHash -> SolidButton(
                    text = stringResource(R.string.update_dialog_open_page),
                    onClick = {
                        openUrl(context, info.pageUrl)
                        center.dismissPrompt()
                    },
                    tone = dev.showcode.ui.SolidTone.Accent,
                )
                available.phase == DownloadPhase.Ready -> SolidButton(
                    text = stringResource(R.string.update_dialog_install),
                    onClick = { center.install() },
                    tone = dev.showcode.ui.SolidTone.Accent,
                )
                available.phase == DownloadPhase.Downloading -> SolidButton(
                    text = stringResource(R.string.update_dialog_downloading),
                    onClick = {},
                    enabled = false,
                )
                available.phase == DownloadPhase.Paused -> SolidButton(
                    text = stringResource(R.string.update_dialog_redownload),
                    onClick = { center.download() },
                    tone = dev.showcode.ui.SolidTone.Accent,
                )
                available.phase == DownloadPhase.Verifying -> SolidButton(
                    text = stringResource(R.string.update_dialog_verifying_short),
                    onClick = {},
                    enabled = false,
                )
                else -> SolidButton(
                    text = stringResource(R.string.update_dialog_download),
                    onClick = { center.download() },
                    tone = dev.showcode.ui.SolidTone.Accent,
                )
            }
            Spacer(Modifier.height(4.dp))
            GhostButton(
                text = if (busy) {
                    stringResource(R.string.update_dialog_background)
                } else {
                    stringResource(R.string.update_dialog_later)
                },
                onClick = { center.dismissPrompt() },
            )
            if (available.phase in setOf(DownloadPhase.Downloading, DownloadPhase.Paused)) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.update_dialog_cancel),
                        modifier = Modifier
                            .clickable { center.cancelDownload() }
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        style = ShowCodeText.Caption,
                        color = InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
}

/** 设置页与弹窗共用的一行体积文案。 */
internal fun updateSizeText(bytes: Long?): String? = bytes?.takeIf { it > 0 }?.let {
    if (it >= 1024 * 1024) {
        String.format(Locale.US, "%.1f MB", it / 1024f / 1024f)
    } else {
        String.format(Locale.US, "%d KB", (it / 1024).coerceAtLeast(1))
    }
}

internal fun UpdateState.Available.progressFraction(): Float {
    val total = total ?: return 0f
    if (total <= 0) return 0f
    return (bytes.toFloat() / total).coerceIn(0f, 1f)
}

/** 进度行：`45% · 5.4 MB / 12.1 MB`。总大小未知时就只报已下多少，不留一条空斜杠。 */
internal fun UpdateState.Available.progressText(): String {
    val percent = (progressFraction() * 100).toInt()
    val got = updateSizeText(bytes) ?: "0 KB"
    val all = updateSizeText(total)
    return if (all != null) "$percent% · $got / $all" else "$percent% · $got"
}

@Composable
internal fun UpdateProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(SurfaceSunken, ShowCodeShapes.Pill),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .background(Accent, ShowCodeShapes.Pill),
            )
        }
    }
}

internal fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
