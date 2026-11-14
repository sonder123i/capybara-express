package dev.showcode.ui.parcels

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.showcode.R
import dev.showcode.core.CodeKind
import dev.showcode.core.LaunchOutcome
import dev.showcode.core.Parcel
import dev.showcode.core.Stage
import dev.showcode.core.account.StageMachine
import dev.showcode.core.account.TraceRow
import dev.showcode.core.account.parseTraceTimeline
import dev.showcode.ui.copyCode
import dev.showcode.ui.navBarClearance
import dev.showcode.ui.performTrackingLookup
import dev.showcode.ui.relativeTimeText
import dev.showcode.ui.onsite.trackingLookupText
import dev.showcode.ui.inbox.ScreenTopBar
import dev.showcode.ui.stageLabel
import dev.showcode.ui.theme.AccentInk
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.sharedParcelThumb
import dev.showcode.ui.theme.InkFaint
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.SurfaceInset
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.withTabular
import dev.showcode.ui.theme.environmentBackground

/**
 * 一件的详情页：运单号、驿站、阶段、完整轨迹原文。
 *
 * 空码件在这里不显示码，只显示「暂无取件码」和去官方的出口；
 * 运单号可以露出来，因为它不是取件码，不能冒充码。
 */
@Composable
fun ParcelDetailScreen(
    parcel: Parcel?,
    onBack: () -> Unit,
    onOpenTaobao: (Parcel) -> LaunchOutcome,
    /** 演示态：交接动作不写剪贴板、不拉起外部 App，避免任何系统浮层。 */
    isDemo: Boolean = false,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .environmentBackground()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        ScreenTopBar(title = stringResource(R.string.detail_title), onBack = onBack)
        if (parcel == null) {
            Text(
                text = stringResource(R.string.parcel_detail_gone),
                modifier = Modifier.padding(20.dp),
                style = ShowCodeText.Body,
                color = InkMuted,
            )
            return@Column
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = navBarClearance()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = parcel.pointName.ifBlank { stringResource(R.string.parcel_no_point) },
                style = ShowCodeText.Section,
                color = Ink,
            )
            /* 「已签收」不一定代表件被取走了：极兔等承运商在代收点入库后就会写
               「包裹已签收（凭取件码）」，件其实还在驿站。这时状态必须两件事一起说，
               否则用户会以为 App 漏看了平台的签收。 */
            val agentSigned = parcel.stage == Stage.AT_STATION &&
                StageMachine.isAgentSigned(parcel.raw)
            val stageText = if (agentSigned) {
                stringResource(R.string.stage_at_station_signed)
            } else {
                stageLabel(parcel.stage)
            }
            Text(
                text = listOf(
                    parcel.carrier,
                    stageText,
                ).joinToString(" · "),
                style = ShowCodeText.Meta,
                color = InkMuted,
            )
            if (agentSigned) {
                Text(
                    text = stringResource(R.string.detail_agent_signed_hint),
                    style = ShowCodeText.Caption,
                    color = InkMuted,
                )
            }

            if (parcel.code.isBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.no_pickup_code),
                        style = ShowCodeText.EmptyState,
                        color = Ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (trackingLookupText(parcel) != null) {
                            stringResource(R.string.onsite_no_code_taobao_hint)
                        } else {
                            stringResource(R.string.onsite_no_code_body)
                        },
                        style = ShowCodeText.Meta,
                        color = InkMuted,
                    )
                    if (trackingLookupText(parcel) != null) {
                        Spacer(Modifier.height(4.dp))
                        TrackingLookupBlock(
                            parcel = parcel, onOpenTaobao = onOpenTaobao,
                            context = context, isDemo = isDemo,
                        )
                    }
                }
            } else {
                Column {
                    Text(
                        text = stringResource(
                            if (parcel.codeKind == CodeKind.SUFFIX) R.string.detail_suffix
                            else R.string.detail_pickup_code,
                        ),
                        style = ShowCodeText.Caption,
                        color = InkMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = parcel.code,
                        style = ShowCodeText.CodeLarge,
                        color = Ink,
                    )
                    if (parcel.lockerNo != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${stringResource(R.string.detail_locker)} ${parcel.lockerNo}",
                            style = ShowCodeText.Meta,
                            color = InkMuted,
                        )
                    }
                    /* 后五位同样要交给淘宝；真取件码（PICKUP）不出现这两个入口。 */
                    if (parcel.codeKind == CodeKind.SUFFIX && trackingLookupText(parcel) != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.onsite_no_code_taobao_hint),
                            style = ShowCodeText.Meta,
                            color = InkMuted,
                        )
                        Spacer(Modifier.height(4.dp))
                        TrackingLookupBlock(
                            parcel = parcel, onOpenTaobao = onOpenTaobao,
                            context = context, isDemo = isDemo,
                        )
                    }
                }
            }

            if (showsGoodsCard(parcel)) {
                GoodsCard(parcel)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, ShowCodeShapes.Md)
                    .border(1.dp, Hairline, ShowCodeShapes.Md)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailRow(stringResource(R.string.detail_tracking), parcel.trackingNo ?: "—")
                DetailRow(stringResource(R.string.detail_stage), stageText)
                DetailRow(
                    stringResource(R.string.detail_point),
                    parcel.pointName.ifBlank { stringResource(R.string.parcel_no_point) },
                )
                DetailRow(stringResource(R.string.detail_carrier), parcel.carrier)
                DetailRow(stringResource(R.string.detail_order), parcel.orderSn ?: "—")
            }

            Text(
                text = stringResource(R.string.detail_trace),
                style = ShowCodeText.Action,
                color = InkMuted,
            )
            /* 账号件的 raw 是整页物流 innerText，混着订单头 / 商品卡 / 推荐位；
               先抽成「带时间的轨迹」，能抽出来就画时间线，抽不出来才回退原文。 */
            val traceRows = remember(parcel.raw) { parseTraceTimeline(parcel.raw) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, ShowCodeShapes.Md)
                    .border(1.dp, Hairline, ShowCodeShapes.Md)
                    .padding(14.dp),
            ) {
                when {
                    traceRows.isNotEmpty() -> TraceTimeline(traceRows)
                    parcel.raw.isBlank() -> Text(
                        text = stringResource(R.string.detail_no_trace),
                        style = ShowCodeText.Meta,
                        color = InkMuted,
                    )
                    else -> SelectionContainer {
                        Text(
                            text = parcel.raw,
                            style = ShowCodeText.Body,
                            color = Ink,
                        )
                    }
                }
                /* 轨迹来自「最近一次同步」，不是打开这一刻的快递公司实时页；
                   如实标一下时间，免得把「我们手里最新」误读成「现在最新」。 */
                parcel.lastSyncedAt?.let { syncedAt ->
                    Spacer(Modifier.height(if (traceRows.isNotEmpty()) 12.dp else 8.dp))
                    Text(
                        text = "轨迹来自最近一次同步 · " +
                            relativeTimeText(syncedAt, System.currentTimeMillis()),
                        style = ShowCodeText.Caption,
                        color = InkFaint,
                    )
                }
            }
        }
    }
}

/**
 * 商品卡。位置在元数据卡上方，视觉沿用家族的 SurfaceCard + Hairline + 14dp。
 * 图片只在确实有 URL 时才占那 60dp；加载失败画中性占位，不伪造图。
 */
@Composable
private fun GoodsCard(parcel: Parcel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val image = goodsThumbnailUrl(parcel)
        if (image != null) {
            /* 另一端：从首页卡片飞过来的那张图在这里落位。
               key 必须和卡片那边完全一致（`parcelThumbKey(id)`）。 */
            dev.showcode.ui.GoodsThumbnail(
                url = image,
                size = 76.dp,
                modifier = Modifier.sharedParcelThumb(parcel.id),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            parcel.goodsTitle?.takeIf { it.isNotBlank() }?.let { title ->
                Text(
                    text = title,
                    style = ShowCodeText.Item,
                    color = Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            parcel.goodsPrice?.takeIf { it.isNotBlank() }?.let { price ->
                Text(text = price, style = ShowCodeText.Action, color = Ink)
            }
            if (image == null && !parcel.goodsTitle.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.detail_goods_pending),
                    style = ShowCodeText.Caption,
                    color = InkMuted,
                )
            }
            val shop = parcel.shopName?.takeIf { it.isNotBlank() }
            val ordered = parcel.orderedAt?.let {
                stringResource(R.string.detail_ordered_at, orderedAtText(it))
            }
            val meta = listOfNotNull(shop, ordered)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    style = ShowCodeText.Caption,
                    color = InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}



/**
 * 详情页的两个淘宝交接动作。和现场模式同一套文案、同一个黑色主按钮/次级蓝，
 * 只是这里左对齐、随滚动布局，不占满屏。
 */
@Composable
private fun TrackingLookupBlock(
    parcel: Parcel,
    onOpenTaobao: (Parcel) -> LaunchOutcome,
    context: android.content.Context,
    isDemo: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .background(Ink, ShowCodeShapes.Control)
                .clickable { if (!isDemo) performTrackingLookup(context, parcel, onOpenTaobao) }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.onsite_query_taobao),
                color = SurfaceCard,
                style = ShowCodeText.Button,
            )
        }
        Text(
            text = stringResource(R.string.onsite_copy_tracking),
            modifier = Modifier
                .clickable {
                    if (!isDemo) trackingLookupText(parcel)?.let {
                        copyCode(context, it, R.string.onsite_tracking_copied)
                    }
                }
                .padding(vertical = 12.dp),
            style = ShowCodeText.Action,
            color = AccentInk,
        )
    }
}

/**
 * 物流时间线：新的在上，最新一条实心高亮，历史条目空心点 + 竖线连起来。
 * 数据来自 [parseTraceTimeline]，只含带时间的真实轨迹，不含商品/推荐文案。
 */
@Composable
private fun TraceTimeline(rows: List<TraceRow>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            TraceRowItem(row = row, isLatest = index == 0, isLast = index == rows.lastIndex)
        }
    }
}

@Composable
private fun TraceRowItem(row: TraceRow, isLatest: Boolean, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        /* 左轨：圆点 + 连接到下一条的竖线。最新一条实心，其余空心。 */
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(9.dp)
                    .then(
                        if (isLatest) {
                            Modifier.background(AccentInk, CircleShape)
                        } else {
                            Modifier.border(1.5.dp, InkFaint, CircleShape)
                        },
                    ),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .width(1.5.dp)
                        .weight(1f)
                        .background(Hairline),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, bottom = if (isLast) 0.dp else 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.stage.isNotBlank()) {
                    Text(
                        text = row.stage,
                        style = ShowCodeText.Meta,
                        fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Medium,
                        color = if (isLatest) AccentInk else Ink,
                    )
                }
                if (row.stage.isNotBlank() && row.time.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                }
                if (row.time.isNotBlank()) {
                    Text(
                        text = row.time,
                        style = ShowCodeText.Caption.withTabular(),
                        color = if (isLatest) AccentInk else InkMuted,
                    )
                }
            }
            if (row.detail.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = highlightPhones(row.detail),
                    style = ShowCodeText.Body,
                    color = if (isLatest) Ink else InkSecondary,
                )
            }
        }
    }
}

/** 明细里的手机号 / 座机号高亮成强调色（对齐参照图里被点亮的号码）。 */
@Composable
private fun highlightPhones(text: String): AnnotatedString {
    val color = UrgencyDueSoon
    return remember(text, color) {
        buildAnnotatedString {
            var last = 0
            for (m in PHONE.findAll(text)) {
                if (m.range.first > last) append(text.substring(last, m.range.first))
                withStyle(SpanStyle(color = color)) { append(m.value) }
                last = m.range.last + 1
            }
            if (last < text.length) append(text.substring(last))
        }
    }
}

private val PHONE = Regex("1[3-9]\\d{9}|0\\d{2,3}-\\d{7,8}")

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = ShowCodeText.Caption,
            color = InkMuted,
        )
        Text(
            text = value.ifBlank { "—" },
            style = ShowCodeText.Meta,
            color = Ink,
            fontFamily = FontFamily.Monospace,
            maxLines = 3,
        )
    }
}
