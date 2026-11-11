package dev.showcode.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 商品图只能按同一件商品的标题绑定。详情页下方是推荐流，
 * 「最大一张」一定会抓到别人的图——这几个用例就是那次线上问题的回归。
 */
class GoodsImagePickTest {

    private fun candidate(src: String, area: Float, ctx: String, w: Float = 0f, h: Float = 0f) =
        GoodsImageCandidate(src = src, area = area, ctx = ctx, width = w, height = h)

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

    /* 线上问题回归：详情页顶部那条「抢，到手价」整宽促销横幅，容器正文也以商品标题开头、
       且面积更大，靠 maxBy(area) 抢走缩略图 —— 所有件因此显示成同一张横幅。
       现在按宽高比把宽扁横幅排掉，只留接近方形的商品主图。 */
    @Test
    fun `整宽促销横幅再大也不当商品图 - 选方形的商品主图`() {
        val title = "COMGM半高领德绒打底衫男女秋冬保暖双面德绒中领内搭长袖t恤上衣"
        val banner = candidate(
            "https://img.pddpic.com/promo_banner.jpg", 1080f * 160f, title + " 抢，到手价 ¥29.9", 1080f, 160f,
        )
        val product = candidate(
            "https://img.pddpic.com/goods.jpg", 360f * 360f, title + " ￥59.9", 360f, 360f,
        )
        assertEquals("https://img.pddpic.com/goods.jpg", pickGoodsImage(listOf(banner, product), title))
    }

    @Test
    fun `只剩宽扁横幅时宁可为空 - 不显示到手横幅`() {
        val title = "齐心双鱼座a4打印纸70g复印纸草稿纸"
        val banner = candidate(
            "https://img.pddpic.com/promo.jpg", 1080f * 160f, title + " 抢，到手价", 1080f, 160f,
        )
        assertNull(pickGoodsImage(listOf(banner), title))
    }
}
