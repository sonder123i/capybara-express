package dev.showcode.ui.parcels

import dev.showcode.core.CodeKind
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoodsCardTest {

    private fun parcel(
        source: Source = Source.ACCOUNT,
        accountId: String? = "pdd",
        title: String? = "示例竹浆手帕纸",
        image: String? = "https://img.alicdn.com/a.jpg",
        price: String? = "¥9.90",
        shop: String? = "拼多多官方旗舰店",
        orderedAt: Long? = 1_763_000_000_000L,
    ) = Parcel(
        id = 1,
        code = "",
        lockerNo = null,
        carrier = "中通快递",
        pointKey = "cndm",
        pointName = "菜鸟驿站·东门",
        pointKind = PointKind.STATION,
        owner = Owner.SELF,
        source = source,
        receivedAt = 1_000,
        freeUntilAt = null,
        closesAtMinuteOfDay = null,
        picked = false,
        pickedAt = null,
        raw = "",
        trackingNo = "79030000003029",
        accountId = accountId,
        stage = dev.showcode.core.Stage.AT_STATION,
        codeKind = CodeKind.NONE,
        goodsTitle = title,
        goodsImage = image,
        goodsPrice = price,
        shopName = shop,
        orderedAt = orderedAt,
    )

    @Test
    fun `账号件有商品字段 - 显示商品卡`() {
        assertTrue(showsGoodsCard(parcel()))
    }

    @Test
    fun `短信与手动件 - 不显示`() {
        assertFalse(showsGoodsCard(parcel(source = Source.SMS, accountId = null)))
        assertFalse(showsGoodsCard(parcel(source = Source.MANUAL, accountId = null)))
    }

    @Test
    fun `账号件没有账号归属 - 也不显示`() {
        assertFalse(showsGoodsCard(parcel(accountId = null)))
        assertFalse(showsGoodsCard(parcel(accountId = " ")))
    }

    @Test
    fun `账号件但商品字段全缺 - 不占位`() {
        assertFalse(
            showsGoodsCard(
                parcel(title = null, image = null, price = null, shop = null, orderedAt = null),
            ),
        )
    }

    @Test
    fun `只有标题或只有图片也显示`() {
        assertTrue(showsGoodsCard(parcel(image = null, price = null, shop = null, orderedAt = null)))
        assertTrue(showsGoodsCard(parcel(title = null, price = null, shop = null, orderedAt = null)))
    }

    @Test
    fun `缩略图地址只在账号件且有图时给出`() {
        assertEquals("https://img.alicdn.com/a.jpg", goodsThumbnailUrl(parcel()))
        assertNull(goodsThumbnailUrl(parcel(image = null)))
        assertNull(goodsThumbnailUrl(parcel(source = Source.SMS, accountId = null)))
    }
}
