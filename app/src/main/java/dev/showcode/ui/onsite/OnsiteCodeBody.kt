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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import dev.showcode.ui.theme.TabularNumbers
import dev.showcode.ui.urgencyColor

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
    /** 静默态的控件透明度。只作用于「长按可复制」这一行——码、格口、倒计时永远不许淡。 */
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // vw 是视口宽度而不是内容宽度，所以必须在 padding 之前量：
        // 140 这个常数已经把两侧各 20 的内边距算进去了，再量一次内容宽就等于扣两遍边距。
        val viewportWidthDp = maxWidth.value
        val codeSize = codeFontSizeSp(parcel.code, hasLocker, viewportWidthDp, fontScale).sp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            /* 上区：商品信息。和下面的码区用一条 hairline + 16dp 呼吸隔开，
               不再把商品文字塞进码块。 */
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

            // 中区：承运商 + 码/后五位。快递公司只是让人确认「是这个包裹」
            Text(text = parcel.carrier, style = ShowCodeText.Caption, color = InkMuted)

            Spacer(Modifier.height(16.dp))

            /* 单号后五位不是取件码：先给一行说明，再让数字自己说话。
               字的几何契约一点不改——码仍然是纯黑、等宽、按 len 防溢出。 */
            if (parcel.codeKind == CodeKind.SUFFIX) {
                Text(
                    text = stringResource(R.string.suffix_label),
                    fontSize = 12.sp,
                    color = InkMuted,
                )
                Spacer(Modifier.height(8.dp))
            }

            // 取件码：等宽 + 超大 + 纯黑，连字符降墨。长按复制（在位者 issue #52 的诉求）。
            Text(
                text = dimmedSeparators(parcel.code),
                modifier = Modifier.pointerInput(parcel.code) {
                    detectTapGestures(onLongPress = { onCopyCode() })
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

            /* 后五位也只是一串数字；真正能查到取件码的地方在淘宝。
               大字仍然是主角，这一组动作放在码下方、字阶降一档，横排压住高度。 */
            val tracking = trackingLookupText(parcel)
            val hasLookupActions = parcel.codeKind == CodeKind.SUFFIX && tracking != null
            if (hasLookupActions) {
                /* 后五位这一屏天然比普通码多三行（说明、两个动作、详情）。
                   间距收紧一档，保证格口与倒计时都在首屏内，不需要滚动。 */
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.onsite_no_code_taobao_hint),
                    modifier = Modifier.graphicsLayer { alpha = chromeAlpha },
                    fontSize = 13.sp,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                )
                if (onCopyTracking != null && onQueryTaobao != null) {
                    Spacer(Modifier.height(12.dp))
                    /* 收纳成一行图标 + 短词，不再和码抢版面；码仍是全屏最重的一块。 */
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
                if (onOpenDetail != null && !parcel.accountId.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.detail_view),
                        modifier = Modifier
                            .graphicsLayer { alpha = chromeAlpha }
                            .clickable(onClick = onOpenDetail)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        style = ShowCodeText.Action,
                        color = InkMuted,
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onsite_copy_hint),
                    modifier = Modifier.graphicsLayer { alpha = chromeAlpha },
                    fontSize = 12.sp,
                    color = InkFaint,
                )
            }

            /* 格口紧贴码下方。缺失时整块消失、码变大——不留空位、不显示 "--"。
               后五位这一屏已经有动作区和详情入口，格口改用一行紧凑文字，
               避免整块大胶囊把倒计时挤出首屏（录制时会出现半截白条）。 */
            if (hasLocker) {
                if (hasLookupActions) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = lockerLabel, fontSize = 13.sp, color = InkMuted)
                        Text(
                            text = parcel.lockerNo.orEmpty(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            style = TabularNumbers,
                            color = OnsiteInk,
                        )
                    }
                } else {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier
                            .background(SurfaceCard, RoundedCornerShape(14.dp))
                            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = lockerLabel,
                            modifier = Modifier.alignByBaseline(),
                            fontSize = 14.sp,
                            color = InkMuted,
                        )
                        Text(
                            text = parcel.lockerNo.orEmpty(),
                            modifier = Modifier.alignByBaseline(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 34.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Bold,
                            style = TabularNumbers,
                            color = OnsiteInk,
                        )
                    }
                }
            }

            /* 账号件才有详情页入口：详情里的商品卡只属于账号渠道，
               短信/手动件给同一颗按钮只会通向一张空商品区。
               后五位的详情入口挪进上面那组动作里，避免一屏出现两个「查看详情」。 */
            if (!hasLookupActions && onOpenDetail != null && !parcel.accountId.isNullOrBlank()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.detail_view),
                    modifier = Modifier
                        .graphicsLayer { alpha = chromeAlpha }
                        .clickable(onClick = onOpenDetail)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    style = ShowCodeText.Action,
                    color = AccentInk,
                )
            }

            // 时效：三态下唯一的颜色。小字、低位，不抢码的注意力
            if (deadlineText != null) {
                val tint = if (urgency == Urgency.SAFE) InkMuted else urgencyColor(urgency)
                Spacer(Modifier.height(24.dp))
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
