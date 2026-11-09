package dev.showcode.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.showcode.R
import dev.showcode.ui.glass.GlassTier
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.ShowCodeText

/** 项目仓库。设置页那一行与这里用的是同一个地址。 */
const val PROJECT_STAR_URL = "https://github.com/sonder123i/capybara-express"

/**
 * 点 Star 的邀请。第 3 次启动起才出现，最多两次；点过之后永久不再打扰。
 *
 * 不放在启动第一屏：第一次打开就被要 Star，用户还没从「这玩意儿有没有用」里走出来。
 * 第二次的文案直接说「最后一次」——说清楚截止时间，比无限次温柔地重复更体面。
 */
@Composable
fun StarPrompt(
    dismissals: Int,
    onDismiss: () -> Unit,
    onStar: () -> Unit,
    tier: GlassTier = GlassTier.OPAQUE,
    backdrop: Backdrop? = null,
) {
    val context = LocalContext.current
    val last = dismissals >= 1
    GlassModal(
        visible = true,
        onDismissRequest = onDismiss,
        tier = tier,
        backdrop = backdrop,
    ) {
        Text(
            text = stringResource(
                if (last) R.string.star_prompt_title_last else R.string.star_prompt_title
            ),
            style = ShowCodeText.Headline,
            color = Ink,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(
                if (last) R.string.star_prompt_body_last else R.string.star_prompt_body
            ),
            style = ShowCodeText.Body,
            color = InkSecondary,
        )
        Spacer(Modifier.height(18.dp))
        SolidButton(
            text = stringResource(R.string.star_prompt_go),
            onClick = {
                if (openStarPage(context)) {
                    onStar()
                } else {
                    Toast.makeText(context, R.string.star_prompt_no_browser, Toast.LENGTH_SHORT)
                        .show()
                }
            },
            tone = SolidTone.Accent,
        )
        Spacer(Modifier.height(4.dp))
        GhostButton(text = stringResource(R.string.star_prompt_later), onClick = onDismiss)
    }
}

private fun openStarPage(context: Context): Boolean = runCatching {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_STAR_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}.isSuccess
