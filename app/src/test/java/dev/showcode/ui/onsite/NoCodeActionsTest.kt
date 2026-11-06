package dev.showcode.ui.onsite

import dev.showcode.R
import dev.showcode.core.CodeKind
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoCodeActionsTest {

    private fun parcel(
        code: String,
        kind: CodeKind = CodeKind.PICKUP,
        tracking: String? = "79140000040095",
    ) = Parcel(
        id = 1,
        code = code,
        lockerNo = null,
        carrier = "中通快递",
        pointKey = "cndm",
        pointName = "菜鸟驿站·东门",
        pointKind = PointKind.STATION,
        owner = Owner.SELF,
        source = Source.ACCOUNT,
        receivedAt = 1_000,
        freeUntilAt = null,
        closesAtMinuteOfDay = null,
        picked = false,
        pickedAt = null,
        raw = "",
        trackingNo = tracking,
        codeKind = kind,
    )

    @Test
    fun `后五位的件也出现这两个入口，复制的是完整运单号`() {
        val text = trackingLookupText(parcel(code = "40095", kind = CodeKind.SUFFIX))
        assertEquals("79140000040095", text)
    }

    @Test
    fun `完全没有取件码的件也出现，复制的是完整运单号`() {
        assertEquals(
            "79140000040095",
            trackingLookupText(parcel(code = "", kind = CodeKind.NONE)),
        )
    }

    @Test
    fun `真取件码的件保持干净`() {
        assertNull(trackingLookupText(parcel(code = "6-5-15031", kind = CodeKind.PICKUP)))
    }

    @Test
    fun `没有运单号时两个入口都不出现`() {
        assertNull(trackingLookupText(parcel(code = "", kind = CodeKind.NONE, tracking = null)))
        assertNull(trackingLookupText(parcel(code = "", kind = CodeKind.NONE, tracking = "  ")))
    }

    @Test
    fun `这一屏用到的文案 key 都存在`() {
        val keys = listOf(
            R.string.onsite_no_code_taobao_hint,
            R.string.onsite_query_taobao,
            R.string.onsite_copy_tracking,
            R.string.onsite_tracking_copied,
            R.string.onsite_query_taobao_fallback,
        )
        assertTrue(keys.all { it != 0 })
    }
}
