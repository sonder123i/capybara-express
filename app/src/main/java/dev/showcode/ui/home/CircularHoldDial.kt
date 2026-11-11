package dev.showcode.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.showcode.core.HOUR_MS
import dev.showcode.core.LOCKER_FREE_HOURS_DEFAULT
import dev.showcode.core.Parcel
import dev.showcode.core.PointKind
import dev.showcode.core.deadlineOf
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.UrgencyDueSoon

/*
 * 18h 保管环（react-bits CircularText / CometDial 范式）。
 *
 * 官网 Calm.jsx 用 CometDial 做 18h 表盘并与时钟双向联动；App 侧此前只有
 * countdownText 一行字，临期档在首页卡上只有一枚告警图标。这里补的是
 * 「还剩多少」的比例本身，不是又一个装饰环：
 *
 * - 只对柜件画（18 小时免费保管本来就是快递柜规则，见 Urgency.kt）；
 * - fraction 由 deadlineOf(parcel, now) 与 LOCKER_FREE_HOURS_DEFAULT 算出，
 *   算不出来就不画（null 不伪造时效，对齐 README「不编数字」）；
 * - 静止图形，无动画：现场屏的码已在动，首页这颗环再动会抢读码的注意力。
 */
private val DIAL_SIZE = 18.dp
private val DIAL_STROKE = 2.dp

@Composable
fun CircularHoldDial(
    parcel: Parcel,
    now: Long,
    modifier: Modifier = Modifier,
) {
    if (parcel.pointKind != PointKind.LOCKER) return
    val deadline = deadlineOf(parcel, now) ?: return
    val window = LOCKER_FREE_HOURS_DEFAULT * HOUR_MS
    val fraction = ((deadline - now).toFloat() / window).coerceIn(0f, 1f)

    Canvas(modifier = modifier.size(DIAL_SIZE)) {
        val stroke = DIAL_STROKE.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = Hairline,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = UrgencyDueSoon,
            startAngle = -90f,
            sweepAngle = 360f * fraction,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
