package dev.showcode.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 商品图只能按同一件商品的标题绑定。详情页下方是推荐流，
 * 「最大一张」一定会抓到别人的图——这几个用例就是那次线上问题的回归。
 */
class GoodsImagePickTest {

    private fun candidate(src: String, area: Float, ctx: String) =
        GoodsImageCandidate(src = src, area = area, ctx = ctx)

    @Test
    fun `标题与图同源 - 选到自己的商品图`() {
        val panpan = "盼盼梅尼耶饼干蛋糕混合口味抹茶味点心整箱早餐面包干休闲零食品"
        val picked = pickGoodsImage(
            listOf(
                candidate("https://cdn/other.jpg", 400_000f, "和丝露苹果醋 整箱 ￥39.9"),
                candidate("https://cdn/panpan.jpg", 10_000f, "盼盼梅尼耶饼干蛋糕混合口味 规格 22包 ￥25.49"),
            ),
            panpan,
        )
        assertEquals("https://cdn/panpan.jpg", picked)
    }

    @Test
    fun `推荐位图再大也不借用 - 宁可为空`() {
        val title = "胡婆婆麻酱金钱肚魔芋素毛肚休闲零食"
        val picked = pickGoodsImage(
            listOf(
                candidate("https://cdn/stone.jpg", 900_000f, "景区讲解石文创挂件 ￥116.1"),
                candidate("https://cdn/vinegar.jpg", 500_000f, "和丝露苹果醋 ￥39.9"),
            ),
            title,
        )
        assertNull(picked)
    }

    @Test
    fun `缺标题或缺候选 - 不走首图兜底`() {
        assertNull(pickGoodsImage(emptyList(), "盼盼梅尼耶饼干"))
        assertNull(pickGoodsImage(listOf(candidate("https://cdn/a.jpg", 10_000f, "盼盼梅尼耶")), null))
        assertNull(pickGoodsImage(listOf(candidate("https://cdn/a.jpg", 10_000f, "")), "盼盼梅尼耶饼干"))
    }

    @Test
    fun `小于 40dp 的图标不当作商品图`() {
        val picked = pickGoodsImage(
            listOf(candidate("https://cdn/icon.jpg", 100f, "盼盼梅尼耶饼干 ￥25.49")),
            "盼盼梅尼耶饼干蛋糕混合口味",
        )
        assertNull(picked)
    }
}
