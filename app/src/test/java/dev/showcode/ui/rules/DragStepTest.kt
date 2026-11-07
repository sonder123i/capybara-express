package dev.showcode.ui.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拖拽换位的判据。
 *
 * 为什么要单测而不是只靠手点：手势在模拟器上注入不出来——`adb shell input motionevent`
 * 给不出真实的按住时长，Compose 的长按检测直接不触发。所以「拖多远换一格」这件事
 * 必须能脱离 UI 验证，否则它就是个永远没跑过的分支。
 */
class DragStepTest {

    private val row = 100f

    @Test
    fun noMoveBeforeHalfARow() {
        val step = resolveDragStep(dragOffsetY = 40f, dragIndex = 0, rowHeightPx = row, itemCount = 3)
        assertFalse("不到半张卡不该换位", step.moved)
        assertEquals(0, step.index)
        assertEquals(40f, step.offsetY, 0.01f)
    }

    @Test
    fun movesOncePastHalfARow() {
        val step = resolveDragStep(dragOffsetY = 60f, dragIndex = 0, rowHeightPx = row, itemCount = 3)
        assertTrue(step.moved)
        assertEquals(0, step.from)
        assertEquals(1, step.to)
        /* 换完要扣掉一整行高，剩下的位移继续参与下一次判定——
           不扣的话手指还在原位，下一帧会立刻再换一次，一路滑到底。 */
        assertEquals(-40f, step.offsetY, 0.01f)
    }

    @Test
    fun fastFlingCanCrossSeveralRows() {
        /* 260px / 100px 行高：越过 50 → 换到 1（剩 160），
           越过 50 → 换到 2（剩 60），60 仍 > 50 → 换到 3（剩 -40）。
           所以是「跨过三格、停在最后一行」。测试值第一次写成 2，是算错了不是代码错了。 */
        val step = resolveDragStep(dragOffsetY = 260f, dragIndex = 0, rowHeightPx = row, itemCount = 4)
        assertEquals(3, step.index)
        assertEquals(-40f, step.offsetY, 0.01f)
    }

    @Test
    fun cannotMoveAboveTheFirstRow() {
        val step = resolveDragStep(dragOffsetY = -400f, dragIndex = 0, rowHeightPx = row, itemCount = 3)
        assertFalse("已经在第一行，往上拖不该换位", step.moved)
        assertEquals(0, step.index)
    }

    @Test
    fun cannotMoveBelowTheLastRow() {
        val step = resolveDragStep(dragOffsetY = 400f, dragIndex = 2, rowHeightPx = row, itemCount = 3)
        assertFalse("已经在最后一行，往下拖不该换位", step.moved)
        assertEquals(2, step.index)
    }

    @Test
    fun singleItemNeverMoves() {
        val step = resolveDragStep(dragOffsetY = 500f, dragIndex = 0, rowHeightPx = row, itemCount = 1)
        assertFalse(step.moved)
    }

    @Test
    fun unmeasuredRowHeightIsSafe() {
        /* 第一次布局之前 rowHeightPx 是 0。这里必须安全返回，
           否则除零会得到 NaN，换位循环永远不结束。 */
        val step = resolveDragStep(dragOffsetY = 100f, dragIndex = 0, rowHeightPx = 0f, itemCount = 3)
        assertFalse(step.moved)
        assertEquals(0, step.index)
    }
}
