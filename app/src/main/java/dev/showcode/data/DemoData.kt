package dev.showcode.data

import dev.showcode.core.DAY_MS
import dev.showcode.core.HOUR_MS
import dev.showcode.core.MINUTE_MS
import dev.showcode.core.CodeKind
import dev.showcode.core.IdentityCode
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import dev.showcode.core.Stage
import dev.showcode.core.account.AccountState
import dev.showcode.core.account.BoundAccount
import dev.showcode.core.account.Platform
import dev.showcode.core.pointKey

/**
 * 演示态数据（宣传片录制专用）。
 *
 * 录制标准比普通 demo 严：**确定性、不联网、不读真实账号、每帧都有图**。
 *
 * 1. **纯内存、永不落库**。id 一律取负数；退出演示后真实数据一个字都不受影响。
 * 2. **时间冻结**。所有时效都相对传入的 [now] 生成，渲染时也不再走真实时钟，
 *    所以同一场演示里倒计时和紧急度不会自己跳；每次进入重新生成，相对时长完全一致。
 * 3. **素材本地**。商品图用 `demo:` 前缀指向打包进 APK 的 drawable，不走网络、没有加载态。
 * 4. 覆盖全部录制场景：多驿站多件、PICKUP 大字码、SUFFIX 后五位、NONE 无码交接、
 *    以及已取/全部取完的收尾。
 */
object DemoData {

    private const val FENGCHAO = "丰巢快递柜(3号楼下)"
    private const val CAINIAO = "菜鸟驿站(阳光花园小区东门店)"
    private const val JD = "京东自提点(好邻居便利店)"

    /**
     * 固定排序的演示件。三条点按「先急后缓、同档件数多者优先」排：
     * 丰巢（有超时件）→ 菜鸟（3 件）→ 京东（2 件）。
     *
     * 驿站/自提点的关门时刻留空（不编造营业时间），因此它们的紧急度恒为安全，
     * 不会因为录制时间在 21:00 前后而改变分组顺序。紧急度只由丰巢的 freeUntilAt 提供。
     */
    fun parcels(now: Long): List<Parcel> = listOf(
        // ── 丰巢柜：一件已超时、一件临期；两件都是 PICKUP 大字码。
        demo(
            id = -1, code = "8-3-1024", lockerNo = "A12", carrier = "顺丰速运",
            pointName = FENGCHAO, kind = PointKind.LOCKER,
            receivedAt = now - 19 * HOUR_MS, freeUntilAt = now - HOUR_MS,
            raw = "【丰巢】您的顺丰速运快递已放入丰巢快递柜(3号楼下)，格口A12，取件码：8-3-1024。" +
                "18小时内免费保管，超时按0.5元/12小时计费，上限3元。",
            goodsTitle = "日式豚骨叉烧拉面 3袋装", goodsImage = "demo:ramen",
            goodsPrice = "¥28.4", shopName = "面说", orderedAt = now - 2 * DAY_MS,
            accountId = "demo-pdd",
        ),
        demo(
            id = -2, code = "6-2-0588", lockerNo = "B07", carrier = "京东物流",
            pointName = FENGCHAO, kind = PointKind.LOCKER,
            receivedAt = now - 14 * HOUR_MS, freeUntilAt = now + 4 * HOUR_MS + 12 * MINUTE_MS,
            raw = "【丰巢】您的京东物流快递已放入丰巢快递柜(3号楼下)，格口B07，取件码：6-2-0588。",
            goodsTitle = "氨基酸控油洗发水 500ml", goodsImage = "demo:shampoo",
            goodsPrice = "¥59.9", shopName = "未可", orderedAt = now - 5 * DAY_MS,
            accountId = "demo-tb",
        ),

        // ── 菜鸟驿站：3 件。PICKUP → SUFFIX → PICKUP，正好演完三种码形态里的前两种。
        demo(
            id = -3, code = "5-2-3391", lockerNo = "3排12号", carrier = "中通快递",
            pointName = CAINIAO, kind = PointKind.STATION,
            receivedAt = now - 5 * HOUR_MS,
            raw = "【菜鸟驿站】您的中通快递包裹已到达菜鸟驿站(阳光花园小区东门店)，取件码5-2-3391，货架3排12号。",
            goodsTitle = "意式拼配咖啡豆 500g 中深烘焙", goodsImage = "demo:coffee",
            goodsPrice = "¥68.0", shopName = "星巴克", orderedAt = now - 4 * DAY_MS,
            accountId = "demo-tb",
        ),
        demo(
            id = -4, code = "76416", lockerNo = "2排03号", carrier = "圆通速递",
            pointName = CAINIAO, kind = PointKind.STATION,
            receivedAt = now - 3 * HOUR_MS,
            raw = "【菜鸟驿站】您的圆通速递包裹已到达，运单号773800000023381，请出示单号后五位领取。",
            goodsTitle = "清风原木纯品抽纸 24包", goodsImage = "demo:tissues",
            goodsPrice = "¥39.9", shopName = "清风", orderedAt = now - 3 * DAY_MS,
            accountId = "demo-pdd", codeKind = CodeKind.SUFFIX,
            trackingNo = "YT78230000023381",
        ),
        demo(
            id = -5, code = "5-2-3408", lockerNo = "1排06号", carrier = "韵达快递",
            pointName = CAINIAO, kind = PointKind.STATION,
            receivedAt = now - HOUR_MS,
            raw = "【菜鸟驿站】您的韵达快递包裹已到达菜鸟驿站(阳光花园小区东门店)，取件码5-2-3408，货架1排06号。",
            goodsTitle = "每日坚果混合果仁 30袋装", goodsImage = "demo:snack",
            goodsPrice = "¥49.9", shopName = "三只松鼠", orderedAt = now - 2 * DAY_MS,
            accountId = "demo-tb",
        ),

        // ── 京东自提点：先演「没有取件码」的交接页，再演普通取件码。
        demo(
            id = -6, code = "", lockerNo = null, carrier = "京东物流",
            pointName = JD, kind = PointKind.SELFPICK,
            receivedAt = now - 6 * HOUR_MS,
            raw = "【京东物流】您的包裹已到达京东自提点(好邻居便利店)，请出示运单号JDVA00290081234领取。",
            goodsTitle = "高清透明手机壳 防摔软壳", goodsImage = "demo:phone_case",
            goodsPrice = "¥19.9", shopName = "闪魔", orderedAt = now - DAY_MS,
            accountId = "demo-pdd", codeKind = CodeKind.NONE,
            trackingNo = "JDVA00290081234",
        ),
    )

    /** 演示用的「已绑定」账号。和真实加密表无关，退出演示不留痕。 */
    fun accounts(now: Long): List<BoundAccount> = listOf(
        BoundAccount(
            id = "demo-pdd", platform = Platform.PDD, label = "拼多多 · 小满",
            secret = "", enabled = true, state = AccountState.ACTIVE,
            createdAt = now - 20 * DAY_MS, lastSyncAt = now - 2 * MINUTE_MS,
            lastSyncResult = "2 件待取",
        ),
        BoundAccount(
            id = "demo-tb", platform = Platform.TB, label = "淘宝 · 小满",
            secret = "", enabled = true, state = AccountState.ACTIVE,
            createdAt = now - 15 * DAY_MS, lastSyncAt = now - 5 * MINUTE_MS,
            lastSyncResult = "3 件待取",
        ),
    )

    /**
     * 演示用的身份码。和真实身份码表无关：id 取负数，退出演示即丢弃。
     * 一静一动两条，覆盖「静态码」与「会话级动态码」两种出示状态。
     */
    fun identityCodes(now: Long): List<IdentityCode> = listOf(
        IdentityCode(
            id = -101,
            label = "小区门禁码",
            value = "8809 1122",
            dynamic = false,
            note = "演示用码，不是任何真实门禁",
            createdAt = now - 30 * DAY_MS,
        ),
        IdentityCode(
            id = -102,
            label = "菜鸟会员码",
            value = "CN 8890 2211",
            dynamic = true,
            note = "会话级动态码，演示中可能显示过期提示",
            createdAt = now - 7 * DAY_MS,
        ),
    )

    private fun demo(
        id: Long,
        code: String,
        lockerNo: String?,
        carrier: String,
        pointName: String,
        kind: PointKind,
        receivedAt: Long,
        freeUntilAt: Long? = null,
        owner: Owner = Owner.SELF,
        raw: String,
        goodsTitle: String,
        goodsImage: String,
        goodsPrice: String,
        shopName: String,
        orderedAt: Long,
        accountId: String,
        codeKind: CodeKind = CodeKind.PICKUP,
        trackingNo: String? = null,
    ) = Parcel(
        id = id,
        code = code,
        lockerNo = lockerNo,
        carrier = carrier,
        pointKey = pointKey(pointName),
        pointName = pointName,
        pointKind = kind,
        owner = owner,
        source = Source.ACCOUNT,
        receivedAt = receivedAt,
        freeUntilAt = freeUntilAt,
        closesAtMinuteOfDay = null,
        picked = false,
        pickedAt = null,
        raw = raw,
        trackingNo = trackingNo,
        orderSn = "DEMO${id.toString().removePrefix("-")}",
        accountId = accountId,
        stage = Stage.AT_STATION,
        codeKind = codeKind,
        goodsTitle = goodsTitle,
        goodsImage = goodsImage,
        goodsPrice = goodsPrice,
        shopName = shopName,
        orderedAt = orderedAt,
    )
}
