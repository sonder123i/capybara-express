package dev.showcode.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.showcode.R
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.MotionDuration
import dev.showcode.ui.theme.MotionEasing
import dev.showcode.ui.theme.ShowCodeText

/**
 * 按需说明：一行问句 + 展开后的那段话。
 *
 * 给「用户可能想知道，但不该每天读一遍」的内容一个位置。默认收起时它只是一行
 * 灰字，和列表里的次要文字同一个重量；展开才露出完整解释。
 *
 * 能用一句话说清、且会改变下一步动作的，别用它——那种应该是常驻的状态行。
 * 这里放的是「为什么」和「怎么回事」。
 */
@Composable
fun Explain(
    label: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable(label) { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = if (open) stringResource(R.string.explain_collapse) else label,
            modifier = Modifier
                .clickable { open = !open }
                .padding(vertical = 6.dp),
            style = ShowCodeText.Caption,
            color = InkMuted,
        )
        AnimatedVisibility(
            visible = open,
            enter = androidx.compose.animation.expandVertically(
                animationSpec = tween(MotionDuration.Medium, easing = MotionEasing.FastOutSlowIn),
            ),
            exit = androidx.compose.animation.shrinkVertically(
                animationSpec = tween(MotionDuration.Fast, easing = MotionEasing.FastOutSlowIn),
            ),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(bottom = 6.dp),
                style = ShowCodeText.Caption,
                color = InkMuted,
            )
        }
    }
}
