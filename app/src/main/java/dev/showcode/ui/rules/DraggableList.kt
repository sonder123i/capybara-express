package dev.showcode.ui.rules

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp

/**
 * 长按拖拽排序的一层壳。
 *
 * 为什么手写而不引 `Calvin-LL/Reorderable`（1.4k star）：
 * 这一屏最多 20 条、结构固定（等高的卡），一个 60 行的实现就够，
 * 而引一个库要为它承担 Compose 版本兼容（我们已经在 1.12.1，多数排序库还停在 1.7）。
 * 逻辑也不难：**长按 → 跟随手指 → 越过半张卡就换位 → 松手落库**。
 *
 * 三件事必须做对，否则手感会「飘」：
 *
 * 1. **拖起来的那张浮在上面**（`zIndex`），否则它会被后面的卡盖住一半；
 * 2. **换位发生在越过中线时**，不是越过整张卡——后者要拖很远才动，像卡住了；
 * 3. **松手才落库**，拖动过程中只改内存顺序。每换一次位就写一次库，
 *    一次拖动会写十几次，而且中途卡顿会让画面和库错位。
 *
 * @param itemCount 条目数（只有规则本身，不含列表头尾的说明项）
 * @param onMove 内存里的顺序变了：从 [from] 挪到 [to]
 * @param onSettle 松手了：把当前顺序落库
 */
@Composable
fun DraggableList(
    itemCount: Int,
    onMove: (from: Int, to: Int) -> Unit,
    onSettle: () -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int, dragging: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    /* 行高不写死：字号放大后卡片会变高，写死 72dp 会让换位阈值错位。
       由调用方在布局完成后告诉这里。 */
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnSettle by rememberUpdatedState(onSettle)
    val currentCount by rememberUpdatedState(itemCount)

    Box(modifier = modifier.fillMaxWidth()) {
        /* 必须用 Column 依次排下去：Box 会把所有行叠在同一个原点，
           看起来就是「只渲染了一条、还被裁断」。 */
        androidx.compose.foundation.layout.Column {
        for (index in 0 until itemCount) {
            val dragging = index == dragIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    /* 拖动中的那一张要浮在别人上面，否则会被相邻的卡盖掉一半。 */
                    .zIndex(if (dragging) 1f else 0f)
                    /* 手势挂在**每一行自己**身上，不是外层容器：
                       外层挂的话，`offset.y` 是全列表坐标，得再换算成行号；
                       而且长按会被卡内「删除」「开关」的 clickable 先消费掉。 */
                    .pointerInput(index, rowHeightPx, currentCount) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                if (rowHeightPx <= 0f) return@detectDragGesturesAfterLongPress
                                dragIndex = index
                                dragOffsetY = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                if (dragIndex < 0 || rowHeightPx <= 0f) {
                                    return@detectDragGesturesAfterLongPress
                                }
                                val step = resolveDragStep(
                                    dragOffsetY = dragOffsetY + amount.y,
                                    dragIndex = dragIndex,
                                    rowHeightPx = rowHeightPx,
                                    itemCount = currentCount,
                                )
                                if (step.moved) {
                                    currentOnMove(step.from, step.to)
                                }
                                dragIndex = step.index
                                dragOffsetY = step.offsetY
                            },
                            onDragEnd = {
                                dragIndex = -1
                                dragOffsetY = 0f
                                currentOnSettle()
                            },
                            onDragCancel = {
                                dragIndex = -1
                                dragOffsetY = 0f
                                currentOnSettle()
                            },
                        )
                    }
                    .onGloballyPositioned { coords ->
                        /* 第一次布局时把行高量下来（含卡间距）。
                           不能在 graphicsLayer 里读 height——那个作用域拿不到。 */
                        if (index == 0 && rowHeightPx <= 0f && coords.size.height > 0) {
                            rowHeightPx = coords.size.height + with(density) { 10.dp.toPx() }
                        }
                    }
                    .graphicsLayer {
                        if (dragging) {
                            translationY = dragOffsetY
                            /* 抬起来要有一点点放大和阴影感：只改缩放，
                               不引入 elevation（那会破坏玻璃底图的取样）。 */
                            scaleX = 1.02f
                            scaleY = 1.02f
                            alpha = 0.96f
                        }
                    },
            ) {
                itemContent(index, dragging)
            }
        }
        }
    }
}

/** 一次换位判定的结果。 */
data class DragStep(
    /** 从哪一格挪到哪一格。没换位时两者相等。 */
    val from: Int,
    val to: Int,
    /** 换完之后手指还压在哪个格上。 */
    val index: Int,
    /** 换完之后剩下的位移（已经扣掉整行高的整数倍）。 */
    val offsetY: Float,
    val moved: Boolean,
)

/**
 * 拖拽时「要不要换位、换到哪」的判据。
 *
 * 抽成纯函数是为了能测：手势本身在模拟器上用 adb 注入不出来
 * （长按检测依赖事件时间戳，`input motionevent` 给不出真实的 hold 段），
 * 所以换位逻辑必须能脱离 UI 单独验证。
 *
 * 规则：越过**半张卡**就换位。越过整张卡要拖很远才动（像卡住），
 * 越过 1/4 又太灵敏——半张是「跟手」和「不抖」的平衡点。
 */
fun resolveDragStep(
    dragOffsetY: Float,
    dragIndex: Int,
    rowHeightPx: Float,
    itemCount: Int,
): DragStep {
    if (rowHeightPx <= 0f || itemCount <= 1 || dragIndex !in 0 until itemCount) {
        return DragStep(dragIndex, dragIndex, dragIndex, dragOffsetY, moved = false)
    }
    val threshold = rowHeightPx / 2f
    var index = dragIndex
    var offset = dragOffsetY
    var moved = false
    /* 一次 onDrag 可能拖过好几张卡（快速甩动），所以用 while 而不是 if。 */
    while (offset > threshold && index < itemCount - 1) {
        index += 1
        offset -= rowHeightPx
        moved = true
    }
    while (offset < -threshold && index > 0) {
        index -= 1
        offset += rowHeightPx
        moved = true
    }
    return DragStep(
        from = dragIndex,
        to = index,
        index = index,
        offsetY = offset,
        moved = moved,
    )
}
