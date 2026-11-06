package dev.showcode.core

/**
 * 三态时效。frame.json → design_decisions.urgency_thresholds 已锁定为三态，不是四态：
 * 紧迫度梯度不靠第四个色阶，靠倒计时数字本身的连续变化承载。
 * order 就是默认排序权重（Story 2 AC3：最急的在最上面，不是最新到达的在最上面）。
 */
enum class Urgency(val order: Int) {
    AT_RISK(0),
    DUE_SOON(1),
    SAFE(2),
}

enum class PointKind { STATION, LOCKER, SELFPICK }

/** 代取件是一等类别（edge s4-onsite.boundary-daqu），不是附注。 */
enum class Owner { SELF, FAMILY }

enum class Source { SMS, NOTIFICATION, MANUAL, ACCOUNT, DEMO }

/**
 * 物流阶段。账号同步进来的件带这个；短信/手动件的默认值是 [AT_STATION]，
 * 所以首页「只看待取」的过滤对老路不产生任何行为变化。
 */
enum class Stage { IN_TRANSIT, DELIVERING, AT_STATION, DONE, ABNORMAL, UNKNOWN }

/**
 * 码的性质。拼多多菜鸟驿站件屏上写的是「出示单号后五位」，
 * 这时 `code` 存的就是运单号后五位，标签必须跟着换，不能冒充取件码。
 */
enum class CodeKind { PICKUP, SUFFIX, NONE }

/**
 * 读库时的枚举兜底：名字对不上就当不认识，用调用方给的默认值，**绝不抛**。
 *
 * `valueOf` 直接用在行解码上意味着：只要以后给这些枚举改过一次名（或者用户装了更旧的 APK），
 * `reload()` 每次读表都会炸，整个 App 打不开。这里宁可丢那一个字段的精度，
 * 也不能让一列文本决定用户能不能看见自己的取件码。
 */
inline fun <reified E : Enum<E>> enumOrDefault(raw: String?, fallback: E): E =
    raw?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: fallback

/**
 * 一件待取包裹。
 *
 * 时效不入库：urgency 是 (deadline, now) 的派生值，存下来就会过期。
 * 入库的只有两个截止时刻来源——柜子的免费保管截止 [freeUntilAt]，
 * 以及驿站/自提点的关门时刻 [closesAtMinuteOfDay]。
 */
data class Parcel(
    val id: Long,
    val code: String,
    /** 格口号 / 货架号。可能缺失；缺失时现场模式整块隐藏、取件码变大（不是留白，是重排）。 */
    val lockerNo: String?,
    val carrier: String,
    /** 归一化后的取件点标识，同时用作首页一级 IA 的分组键。 */
    val pointKey: String,
    /** 取件点显示名。首页可截断，现场模式必须完整显示（用户靠它确认「我来对地方了」）。 */
    val pointName: String,
    val pointKind: PointKind,
    val owner: Owner,
    val source: Source,
    val receivedAt: Long,
    /** 快递柜免费保管截止（epoch millis）。非柜类为 null。 */
    val freeUntilAt: Long?,
    /** 驿站/自提点关门时刻，一天内的分钟数。未知为 null——不编造营业时间。 */
    val closesAtMinuteOfDay: Int?,
    val picked: Boolean,
    val pickedAt: Long?,
    /** 原始短信/通知正文。合并历史与「未识别」复核都要用原文，不能只留解析结果。 */
    val raw: String,
    /** 账号渠道的主键；短信件为空。 */
    val trackingNo: String? = null,
    /** 平台订单号；拼多多列表接口只有它，运单号要到详情页才有。 */
    val orderSn: String? = null,
    /** bound_account.id；短信件为空。 */
    val accountId: String? = null,
    /** 物流阶段。账号件才有意义；短信件默认到站。 */
    val stage: Stage = Stage.AT_STATION,
    /** `code` 是取件码、单号后五位、还是根本没有。 */
    val codeKind: CodeKind = CodeKind.PICKUP,
    /** 该件最近一次账号同步时间。 */
    val lastSyncedAt: Long? = null,
    /** 商品标题；只对账号件有意义，拿不到就是 null。 */
    val goodsTitle: String? = null,
    /** 商品缩略图 URL。 */
    val goodsImage: String? = null,
    /** 展示用的价格原文（已带 ¥），不存数值以免各平台单位不同。 */
    val goodsPrice: String? = null,
    /** 店铺名。 */
    val shopName: String? = null,
    /** 下单时间（epoch millis）。 */
    val orderedAt: Long? = null,
    /**
     * 已退休：历史单 / 这次抓取彻底不见的残留在途行。
     *
     * 它与 [picked] 是两件事——退休不是用户取走了件，绝不能写 `picked=1`，
     * 否则下一次同步只会 Skip(ALREADY_PICKED)，误判永远自愈不了。
     */
    val retired: Boolean = false,
)

/** 首页的一级公民：取件点，不是包裹。 */
data class PickupPointGroup(
    val key: String,
    val name: String,
    val kind: PointKind,
    val closesAtMinuteOfDay: Int?,
    /** 已按紧急度排序的待取件。 */
    val items: List<Parcel>,
    /** 该点的时效代表 = 所有待取件里最急的一档。 */
    val worst: Urgency,
)
