package dev.showcode.ui.onsite

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.showcode.R
import dev.showcode.core.CodeKind
import dev.showcode.core.Parcel
import dev.showcode.core.Urgency
import dev.showcode.ui.GoodsThumbnail
import dev.showcode.ui.parcels.showsGoodsCard
import dev.showcode.ui.theme.AccentInk
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.InkFaint
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.OnsiteInk
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.MotionProfile
import dev.showcode.ui.theme.TabularNumbers
import dev.showcode.ui.theme.UrgencySafe
import dev.showcode.ui.theme.rememberReducedMotion
import dev.showcode.ui.urgencyColor
import kotlinx.coroutines.delay

/**
 * 现场模式的核心渲染体。整个产品的灵魂就是这一块。
 *
 * 2026-10-19 起并进家族（环境渐变 + 家族墨阶 + 卡片语言），但四条几何契约一条不动：
 *
 * 1. **字号防溢出**：`min(base, 140/len vw)`，见 [codeFontSizeSp]。
 * 2. **有/无格口的排版分叉**：柜号缺失时整块消失、取件码放大 15%——
 *    不是留白，是重排。绝不显示 null / "--" / 空位。
 * 3. **三态配色 + 倒计时**：安全态用墨色 50% 透明、临期与过期风险用各自的语义色；
 *    临期态的文案由调用方每秒重算，数字连续变化本身就是紧迫度的表达。
 * 4. **静默时只收控件**：只有「长按可复制」这一行会淡；码、格口、倒计时任何时候都必须全墨。
 *
 * 并进家族后的两条表现层契约：
 *
 * 5. **连字符降墨**（[InkFaint]，和 ✕、示例水印同一档）：分隔符不承载信息，
 *    三米外先跳出来的应该是数字。数字本身保持纯黑——屏内对比度最高的元素必须是码。
 * 6. **码不在卡片里**：环境渐变直接铺到码底下。卡片会吃掉约 7% 的码宽，
 *    而扫码机要的恰好是那块最大最亮的自发光区域；家族感由渐变、墨阶与顶栏承担。
 */
@Composable
fun OnsiteCodeBody(
    parcel: Parcel,
    urgency: Urgency,
    lockerLabel: String,
    deadlineText: String?,
    /** 静默态的控件透明度。只作用于复制提示与次级动作——码、格口、倒计时永远不许淡。 */
    chromeAlpha: Float,
    onCopyCode: () -> Unit,
    /** 空码件的「复制快递单号」。为空时那一行不出现。 */
    onCopyTracking: (() -> Unit)? = null,
    /** 空码件的「去淘宝查询」。为空时那一行不出现。 */
    onQueryTaobao: (() -> Unit)? = null,
    /** 空码件的详情页入口。 */
    onOpenDetail: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    val hasLocker = parcel.lockerNo != null

    /* 没有码就不亮码：不画空框、不显示占位数字，直接换成一句人话加一个出口。
       档口的屏幕就是这个 App 最不能骗人的地方。 */
    if (parcel.code.isBlank()) {
        NoCodeBody(
            parcel = parcel,
            onCopyTracking = onCopyTracking,
            onQueryTaobao = onQueryTaobao,
            onOpenDetail = onOpenDetail,
            modifier = modifier,
        )
        return
    }

    val tracking = trackingLookupText(parcel)
    val hasLookupActions = parcel.codeKind == CodeKind.SUFFIX && tracking != null

    /* 复制成功的那 1.2 秒：提示变成「已复制 ✓」，并给一次轻震。 */
    var copied by remember(parcel.code) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    fun copy() {
        onCopyCode()
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        copied = true
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_200)
            copied = false
        }
    }

    /* 对焦：进屏（或换件）时码从 0.94 放大到 1.0 并上移 6dp。
       它必须**在 180ms 内走完**——比这更长就会和「举起手机等扫码」抢时间；
       减少动画时直接落在终态，不播。 */
    val reducedMotion = rememberReducedMotion()
    val focus = remember(parcel.code) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(parcel.code) {
        if (!reducedMotion) {
            focus.animateTo(1f, MotionProfile.iconSpring())
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // vw 是视口宽度而不是内容宽度，所以必须在 padding 之前量。
        val viewportWidthDp = maxWidth.value
        val codeSize = codeFontSizeSp(parcel.code, hasLocker, viewportWidthDp, fontScale).sp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            /* 顶部只回答「这是哪个包裹」。整行可点进详情——省掉一颗漂在码下面的「查看详情」，
               也把商品信息从散落的注脚变成这一屏的身份抬头。 */
            ParcelIdentityHeader(parcel = parcel, onOpenDetail = onOpenDetail)

            /* 码在中间，上下两段空白对称：举起来时视线落在屏幕中线，不用低头找码。 */
            Spacer(Modifier.weight(1f))

            if (parcel.codeKind == CodeKind.SUFFIX) {
                Text(
                    text = stringResource(R.string.suffix_label),
                    fontSize = 12.sp,
                    color = InkMuted,
                )
                Spacer(Modifier.height(8.dp))
            }

            /* 取件码：等宽 + 超大 + 纯黑，连字符降墨。点一下复制，长按仍然可以。 */
            Text(
                text = dimmedSeparators(parcel.code),
                modifier = Modifier
                    .graphicsLayer {
                        val p = focus.value
                        val s = 0.94f + 0.06f * p
                        scaleX = s
                        scaleY = s
                        translationY = (1f - p) * 6.dp.toPx()
                    }
                    .pointerInput(parcel.code) {
                    detectTapGestures(
                        onTap = { copy() },
                        onLongPress = { copy() },
                    )
                },
                fontSize = codeSize,
                lineHeight = codeSize * 1.05f,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.02.em,
                color = OnsiteInk,
                fontFamily = FontFamily.Monospace,
                style = TabularNumbers,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))
            CopyHint(chromeAlpha = chromeAlpha, copied = copied, onCopy = ::copy)

            if (hasLocker) {
                Spacer(Modifier.height(if (hasLookupActions) 12.dp else 18.dp))
                LockerPill(
                    label = lockerLabel,
                    value = parcel.lockerNo.orEmpty(),
                    compact = hasLookupActions,
                )
            }

            if (deadlineText != null) {
                val tint = if (urgency == Urgency.SAFE) InkMuted else urgencyColor(urgency)
                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ClockIcon(color = tint, size = 16.dp)
                    Text(
                        text = deadlineText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tint,
                        style = TabularNumbers,
                    )
                }
            }

            /* 单号后五位只是数字；真正能查到取件码的地方在淘宝。
               大字仍然是主角，这一组动作放在码下方、字阶降一档。 */
            if (hasLookupActions) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.onsite_no_code_taobao_hint),
                    modifier = Modifier.graphicsLayer { alpha = chromeAlpha },
                    fontSize = 13.sp,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                )
                if (onCopyTracking != null && onQueryTaobao != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.graphicsLayer { alpha = chromeAlpha },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        CompactAction(
                            label = stringResource(R.string.onsite_query_short),
                            onClick = onQueryTaobao,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AccentInk,
                            )
                        }
                        CompactAction(
                            label = stringResource(R.string.onsite_copy_short),
                            onClick = onCopyTracking,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_copy),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AccentInk,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * 这一屏的身份抬头：商品图 + 标题 + 「店铺 · 承运商 · 价格」。
 * 有详情时整行可点，右侧给一个箭头——这是「看一眼它是哪件」的入口，不是第二颗按钮。
 */
@Composable
private fun ParcelIdentityHeader(parcel: Parcel, onOpenDetail: (() -> Unit)?) {
    val canOpen = onOpenDetail != null && !parcel.accountId.isNullOrBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canOpen) Modifier.clickable(onClick = onOpenDetail) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dev.showcode.ui.parcels.goodsThumbnailUrl(parcel)?.let {
            GoodsThumbnail(url = it, size = 48.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            val title = parcel.goodsTitle?.takeIf { it.isNotBlank() }
                ?: parcel.shopName?.takeIf { it.isNotBlank() }
                ?: parcel.carrier
            Text(
                text = title,
                style = ShowCodeText.Meta,
                color = OnsiteInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val brief = listOfNotNull(
                parcel.shopName?.takeIf { it.isNotBlank() && it != title },
                parcel.carrier.takeIf { it.isNotBlank() && it != title },
                parcel.goodsPrice?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (brief.isNotEmpty()) {
                Text(
                    text = brief,
                    style = ShowCodeText.Caption,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (canOpen) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = InkMuted,
            )
        }
    }
}

/** 复制不是长按里的秘密：码下面一行明说「轻触复制」，点提示也能复制。 */
@Composable
private fun CopyHint(chromeAlpha: Float, copied: Boolean, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .graphicsLayer { alpha = chromeAlpha }
            .clickable(onClick = onCopy)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (copied) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = UrgencySafe,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_action_copy),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = InkFaint,
            )
        }
        Text(
            text = if (copied) "已复制" else stringResource(R.string.onsite_copy_hint),
            fontSize = 12.sp,
            color = if (copied) UrgencySafe else InkFaint,
        )
    }
}

/** 格口：有柜号时是一块和码同一视觉层级的读数，不显示 "--" 或空位。 */
@Composable
private fun LockerPill(label: String, value: String, compact: Boolean) {
    if (compact) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, fontSize = 13.sp, color = InkMuted)
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                style = TabularNumbers,
                color = OnsiteInk,
            )
        }
    } else {
        Row(
            modifier = Modifier
                .background(SurfaceCard, RoundedCornerShape(14.dp))
                .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.alignByBaseline(),
                fontSize = 13.sp,
                color = InkMuted,
            )
            Text(
                text = value,
                modifier = Modifier.alignByBaseline(),
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                style = TabularNumbers,
                color = OnsiteInk,
            )
        }
    }
}

/**
 * 分隔符降墨：`-` / `－` / 空格 降到 [InkFaint]，数字保持纯黑。
 *
 * 码的字符数一个都不变，所以字号防溢出公式照常工作；变的只是分隔符的墨色。
 */
/**
 * 收纳后的次级操作：图标 + 短词，一行放下两个。
 * 仍是蓝色可点文字（不是藏起来的长按），点击区至少 40dp 高，戴着薄手套也点得到。
 */
@Composable
private fun CompactAction(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .sizeIn(minHeight = 40.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon()
        Text(text = label, style = ShowCodeText.Action, color = AccentInk)
    }
}

/**
 * 现场屏顶部的商品行：44dp 图 + 标题单行 + 店铺/价格小字。
 * 它是「一眼确认这是哪个快递」的注脚，不能抢走码的主角位置。
 */
@Composable
private fun GoodsHeader(parcel: Parcel) {
    Row(
        modifier = Modifier.fillMaxWidth(0.9f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        dev.showcode.ui.parcels.goodsThumbnailUrl(parcel)?.let { GoodsThumbnail(url = it, size = 44.dp) }
        Column(modifier = Modifier.weight(1f)) {
            parcel.goodsTitle?.takeIf { it.isNotBlank() }?.let { title ->
                Text(
                    text = title,
                    style = ShowCodeText.Meta,
                    color = OnsiteInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val brief = listOfNotNull(
                parcel.shopName?.takeIf { it.isNotBlank() },
                parcel.goodsPrice?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (brief.isNotEmpty()) {
                Text(
                    text = brief,
                    style = ShowCodeText.Caption,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 空码件的现场体。它不是「码框留白」，而是重排成：单号 → 怎么用 → 两个动作 → 详情。
 * 运单号可以露出来，因为它不是码，不能冒充码。
 *
 * 两个动作只在真有运单号时出现；有码件走不到这里（[OnsiteCodeBody] 已经在上游分叉）。
 */
@Composable
private fun NoCodeBody(
    parcel: Parcel,
    onCopyTracking: (() -> Unit)?,
    onQueryTaobao: (() -> Unit)?,
    onOpenDetail: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showsGoodsCard(parcel)) {
            GoodsHeader(parcel)
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(1.dp)
                    .background(Hairline),
            )
            Spacer(Modifier.height(16.dp))
        }
        Text(text = parcel.carrier, style = ShowCodeText.Caption, color = InkMuted)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onsite_no_code_lead),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = OnsiteInk,
            textAlign = TextAlign.Center,
        )
        trackingLookupText(parcel)?.let { tracking ->
            Spacer(Modifier.height(14.dp))
            Text(
                text = "${stringResource(R.string.detail_tracking)} $tracking",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = OnsiteInk,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onsite_no_code_taobao_hint),
                fontSize = 13.sp,
                color = InkMuted,
                textAlign = TextAlign.Center,
            )
            if (onCopyTracking != null && onQueryTaobao != null) {
                Spacer(Modifier.height(16.dp))
                /* 与后五位那屏同一套收纳：一行图标 + 短词，主区只留单号和提示。 */
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    CompactAction(
                        label = stringResource(R.string.onsite_query_short),
                        onClick = onQueryTaobao,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = AccentInk,
                        )
                    }
                    CompactAction(
                        label = stringResource(R.string.onsite_copy_short),
                        onClick = onCopyTracking,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_copy),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = AccentInk,
                        )
                    }
                }
            }
        }
        if (onOpenDetail != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.detail_view),
                modifier = Modifier
                    .clickable(onClick = onOpenDetail)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                style = ShowCodeText.Action,
                color = InkMuted,
            )
        }
    }
}

/**
 * 分隔符降墨：`-` / `－` / 空格 降到 [InkFaint]，数字保持纯黑。
 *
 * 码的字符数一个都不变，所以字号防溢出公式照常工作；变的只是分隔符的墨色。
 */
private fun dimmedSeparators(code: String): AnnotatedString = buildAnnotatedString {
    code.forEach { c ->
        if (c == '-' || c == '－' || c == '—' || c == ' ') {
            withStyle(SpanStyle(color = InkFaint)) { append(c) }
        } else {
            append(c)
        }
    }
}

/**
 * 时钟图标。material-icons-core 里没有 Schedule（那在 extended 包里），
 * 为一个 16dp 的装饰图标拖进整个 extended 依赖不值当，所以直接画。
 */
@Composable
private fun ClockIcon(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension / 12f
        val radius = this.size.minDimension / 2f - stroke / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(color = color, radius = radius, style = Stroke(width = stroke))
        drawLine(color, center, Offset(center.x, center.y - radius * 0.6f), stroke, StrokeCap.Round)
        drawLine(color, center, Offset(center.x + radius * 0.45f, center.y + radius * 0.2f), stroke, StrokeCap.Round)
    }
}
