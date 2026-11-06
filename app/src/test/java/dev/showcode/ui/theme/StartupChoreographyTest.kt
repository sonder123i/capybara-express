package dev.showcode.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupChoreographyTest {
    @Test
    fun durationAndRevealWindowMatchTheReference() {
        assertEquals(1100L, StartupChoreography.DurationMillis)
        assertEquals(850f, StartupChoreography.RevealStartMillis, 0f)
        assertEquals(250f, StartupChoreography.RevealMillis, 0f)
    }

    @Test
    fun contentStaysHiddenUntilRevealThenRisesToFull() {
        assertEquals(0f, StartupChoreography.content(-100f), 1e-6f)
        assertEquals(0f, StartupChoreography.content(0f), 1e-6f)
        assertEquals(0f, StartupChoreography.content(850f), 1e-6f)
        assertEquals(0.5f, StartupChoreography.content(975f), 1e-6f)
        assertEquals(1f, StartupChoreography.content(1100f), 1e-6f)
        assertEquals(1f, StartupChoreography.content(2000f), 1e-6f)
    }

    @Test
    fun contentIsMonotonicallyNonDecreasing() {
        var previous = StartupChoreography.content(-1000f)
        var ms = -1000f
        while (ms <= 1500f) {
            val current = StartupChoreography.content(ms)
            assertTrue("content regressed at $ms", current >= previous)
            previous = current
            ms += 10f
        }
    }

    @Test
    fun settleScaleIsOneOutsideTheRevealAndPeaksAtHalf() {
        assertEquals(1f, StartupChoreography.settleScale(0f), 1e-6f)
        assertEquals(1f, StartupChoreography.settleScale(850f), 1e-6f)
        assertEquals(1.0225f, StartupChoreography.settleScale(975f), 1e-6f)
        assertEquals(1f, StartupChoreography.settleScale(1100f), 1e-6f)
    }

    @Test
    fun settleScaleNeverExceedsItsDocumentedPeak() {
        var ms = 850f
        while (ms <= 1100f) {
            val scale = StartupChoreography.settleScale(ms)
            assertTrue("scale below 1 at $ms", scale >= 1f - 1e-6f)
            assertTrue("scale above peak at $ms", scale <= 1.0261f + 1e-6f)
            ms += 5f
        }
    }

    @Test
    fun navEntryDelaysTheBarByFifteenPercent() {
        assertEquals(0f, StartupChoreography.navEntry(0f), 1e-6f)
        assertEquals(0f, StartupChoreography.navEntry(0.15f), 1e-6f)
        assertEquals(0.41176471f, StartupChoreography.navEntry(0.5f), 1e-5f)
        assertEquals(1f, StartupChoreography.navEntry(1f), 1e-6f)
        assertEquals(0f, StartupChoreography.navEntry(-1f), 1e-6f)
        assertEquals(1f, StartupChoreography.navEntry(2f), 1e-6f)
    }

    @Test
    fun layerTableIsInDrawOrderAndCarriesTheStoryboardValues() {
        assertEquals(
            listOf("bg", "map", "bed", "speed", "truck", "capybara", "pin"),
            StartupChoreography.Layers.map { it.name },
        )
        StartupChoreography.Layers.forEachIndexed { index, layer -> assertEquals(index, layer.index) }

        assertEquals(1.100f, StartupChoreography.Layers.single { it.name == "pin" }.maxScale, 1e-6f)
        assertEquals(1.025f, StartupChoreography.Layers.single { it.name == "truck" }.maxScale, 1e-6f)
        assertEquals(1.004f, StartupChoreography.Layers.single { it.name == "bed" }.maxScale, 1e-6f)
        assertEquals(1.5f, StartupChoreography.Layers.single { it.name == "map" }.dyDp, 1e-6f)
        assertEquals(5f, StartupChoreography.Layers.single { it.name == "speed" }.dxDp, 1e-6f)
        assertEquals(4f, StartupChoreography.Layers.single { it.name == "truck" }.dxDp, 1e-6f)

        // 缩放轴心必须是画布内的归一化坐标，否则 tile 会被画到卡片外。
        StartupChoreography.Layers.forEach { layer ->
            assertTrue("pivot x out of range: ${layer.name}", layer.pivotX in 0f..1f)
            assertTrue("pivot y out of range: ${layer.name}", layer.pivotY in 0f..1f)
        }
    }

    @Test
    fun spreadExpandsUntil180ThenCollapsesByIndex() {
        assertEquals(0f, StartupChoreography.spread(0f, 0), 1e-6f)
        assertEquals(0.5f, StartupChoreography.spread(90f, 3), 1e-6f)
        StartupChoreography.Layers.forEach {
            assertEquals("expand not full at 180 for ${it.name}", 1f, StartupChoreography.spread(180f, it.index), 1e-6f)
            assertEquals("not settled at 650 for ${it.name}", 0f, StartupChoreography.spread(650f, it.index), 1e-6f)
            assertEquals("not settled at 1100 for ${it.name}", 0f, StartupChoreography.spread(1100f, it.index), 1e-6f)
        }
    }

    @Test
    fun staggerMakesTheLastLayerCollapseLater() {
        // index 6 的收拢起点是 180 + 72 = 252ms，240ms 时它还没开始动。
        assertEquals(1f, StartupChoreography.spread(240f, 6), 1e-6f)
        val firstAt240 = StartupChoreography.spread(240f, 0)
        assertTrue("index 0 should have started collapsing by 240ms", firstAt240 < 0.96f)
    }

    @Test
    fun spreadNeverReboundsAfterTheCollapseStarts() {
        for (index in 0..6) {
            var previous = 1f
            var ms = 180f
            while (ms <= 650f) {
                val current = StartupChoreography.spread(ms, index)
                assertTrue("spread regressed at $ms / $index", current <= previous + 1e-6f)
                previous = current
                ms += 2f
            }
            assertEquals("index $index did not reach zero", 0f, previous, 1e-6f)
        }
    }

    @Test
    fun speedAlphaFadesOutThenDrawsBack() {
        assertEquals(1f, StartupChoreography.speedAlpha(0f), 1e-6f)
        assertEquals(0.5f, StartupChoreography.speedAlpha(75f), 1e-6f)
        assertEquals(0f, StartupChoreography.speedAlpha(150f), 1e-6f)
        assertEquals(0f, StartupChoreography.speedAlpha(400f), 1e-6f)
        assertEquals(0f, StartupChoreography.speedAlpha(650f), 1e-6f)
        assertEquals(0.5f, StartupChoreography.speedAlpha(750f), 1e-6f)
        assertEquals(1f, StartupChoreography.speedAlpha(850f), 1e-6f)
        assertEquals(1f, StartupChoreography.speedAlpha(1100f), 1e-6f)
    }

    @Test
    fun speedAlphaNeverDipsBelowZero() {
        var ms = 0f
        while (ms <= 1100f) {
            val alpha = StartupChoreography.speedAlpha(ms)
            assertTrue("speed alpha out of range at $ms", alpha in 0f..1f)
            ms += 5f
        }
    }

    @Test
    fun pinBouncePeaksInTheMiddleOfTheDrawBackAndIsZeroElsewhere() {
        assertEquals(0f, StartupChoreography.pinBounce(650f), 1e-6f)
        assertEquals(0.04f, StartupChoreography.pinBounce(750f), 1e-4f)
        assertEquals(0f, StartupChoreography.pinBounce(850f), 1e-6f)
        assertEquals(0f, StartupChoreography.pinBounce(1100f), 1e-6f)
        var ms = 650f
        while (ms <= 850f) {
            assertTrue("bounce out of range at $ms", StartupChoreography.pinBounce(ms) in 0f..0.04f)
            ms += 5f
        }
    }

    /** t=0 与 t≥650ms 必须逐层回到母版几何：无位移、无额外缩放。 */
    @Test
    fun everyLayerIsAtItsMasterGeometryAtZeroAndAfterCollapse() {
        for (ms in listOf(0f, 650f, 850f, 1000f, 1100f)) {
            for (layer in StartupChoreography.Layers) {
                assertEquals("scale at $ms / ${layer.name}", 1f, StartupChoreography.layerScale(ms, layer), 1e-6f)
                assertEquals("dx at $ms / ${layer.name}", 0f, StartupChoreography.layerOffsetXDp(ms, layer), 1e-6f)
                assertEquals("dy at $ms / ${layer.name}", 0f, StartupChoreography.layerOffsetYDp(ms, layer), 1e-6f)
            }
        }
    }

    @Test
    fun anchorLayersStayStillAndTheSpeedLineOnlyFades() {
        // 锚点层（bg / capybara）任何时刻都不产生几何变化；速度线不缩放，只在 0–150ms 右移淡出。
        for (name in listOf("bg", "capybara")) {
            val layer = StartupChoreography.Layers.single { it.name == name }
            assertEquals("$name should not scale", 1f, layer.maxScale, 1e-6f)
            assertEquals("$name should not move x", 0f, layer.dxDp, 1e-6f)
            assertEquals("$name should not move y", 0f, layer.dyDp, 1e-6f)
        }
        val speed = StartupChoreography.Layers.single { it.name == "speed" }
        assertEquals(1f, speed.maxScale, 1e-6f)
        assertEquals(5f, speed.dxDp, 1e-6f)
        assertEquals(0f, speed.dyDp, 1e-6f)
        assertTrue(speed.speedLine)
    }
}
