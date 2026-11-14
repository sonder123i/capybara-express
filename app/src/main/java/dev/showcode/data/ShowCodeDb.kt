package dev.showcode.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.showcode.core.CodeKind
import dev.showcode.core.ExistingParcel
import dev.showcode.core.IdentityCode
import dev.showcode.core.IdentityKind
import dev.showcode.core.NotifyApp
import dev.showcode.core.Owner
import dev.showcode.core.Parcel
import dev.showcode.core.PointAlias
import dev.showcode.core.PointKind
import dev.showcode.core.Source
import dev.showcode.core.Stage
import dev.showcode.core.UnrecognizedState
import dev.showcode.core.account.AccountState
import dev.showcode.core.account.BoundAccount
import dev.showcode.core.account.Platform
import dev.showcode.core.bodyFingerprint
import dev.showcode.core.enumOrDefault
import dev.showcode.core.parse.IncomingMessage
import dev.showcode.core.parse.MessageDomain
import dev.showcode.core.parse.MessageDomains
import dev.showcode.core.parse.ParseRule
import dev.showcode.core.parse.UnrecognizedRecord
import dev.showcode.core.parse.UnrecognizedReason

/**
 * 一条没认出来的取件消息。原文必须留着——用户要靠原文判断自己漏了什么。
 *
 * [state] 是用户对这条消息表过的态。它和 [dev.showcode.core.UnrecognizedState.RESOLVED]
 * 一起构成「决定日志」而不是「待办列表」：以前「忽略」是 DELETE，
 * 于是下一次刷新把同一条短信重新读进来时，库里根本没有记录可查，只能再插一条。
 */
data class UnrecognizedMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val reason: UnrecognizedReason,
    val receivedAt: Long,
    val domain: MessageDomain,
)

/**
 * 用 SQLiteOpenHelper 而不是 Room：批 1 只有两张表 + 一张键值表，
 * Room 要拖进 KSP 与一整套注解处理器，在这台机器上是纯粹的构建风险，换不到任何东西。
 * 表结构稳定后若要迁移，成本也只在这一层。
 */
class ShowCodeDb(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE parcel (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              code TEXT NOT NULL,
              locker_no TEXT,
              carrier TEXT NOT NULL,
              point_key TEXT NOT NULL,
              point_name TEXT NOT NULL,
              point_kind TEXT NOT NULL,
              owner TEXT NOT NULL,
              source TEXT NOT NULL,
              received_at INTEGER NOT NULL,
              free_until_at INTEGER,
              closes_at_minute INTEGER,
              picked INTEGER NOT NULL DEFAULT 0,
              picked_at INTEGER,
              raw TEXT NOT NULL,
              tracking_no TEXT,
              order_sn TEXT,
              account_id TEXT,
              stage TEXT NOT NULL DEFAULT 'AT_STATION',
              code_kind TEXT NOT NULL DEFAULT 'PICKUP',
              last_synced_at INTEGER,
              goods_title TEXT,
              goods_image TEXT,
              goods_price TEXT,
              shop_name TEXT,
              ordered_at INTEGER,
              retired INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        // 去重只在「未取」的件之间做：取件码会被复用，取过的旧件不该挡住新到的同码件。
        // 用部分索引而不是 (code, point_key) 全表唯一索引，正是为了这个。
        //
        // 注意这条索引**不是**防重放的那道闸——防重放在 message_seen 表。
        // 曾经它就是：`ParcelRepository` 只查 `picked = 0` 的行，于是已取件对去重不可见，
        // 每次刷新都给同一个码插一条新卡。现在它只兜「同码同点同时未取」这一种。
        // 账号件的 code 可能为空（只有运单号、没有取件码），所以去重索引必须排除空码，
        // 否则两件无码件落在同一个点会撞唯一约束。
        // SUFFIX（单号后五位）只用于展示，**不**参与身份判定：两件末五位恰好相同的
        // 不同运单必须各自成行。它们由 `parcel_tracking_dedupe` 按完整运单号去重。
        db.execSQL(
            "CREATE UNIQUE INDEX parcel_dedupe ON parcel(code, point_key)" +
                " WHERE picked = 0 AND retired = 0 AND code <> '' AND code_kind <> 'SUFFIX'"
        )
        db.execSQL("CREATE INDEX parcel_point ON parcel(point_key, picked)")
        db.execSQL("CREATE INDEX parcel_lookup ON parcel(code, point_key, received_at DESC)")
        // 账号渠道按运单号去重；短信件没有运单号，不会进这个索引。
        db.execSQL(
            "CREATE UNIQUE INDEX parcel_tracking_dedupe ON parcel(tracking_no)" +
                " WHERE picked = 0 AND retired = 0 AND tracking_no IS NOT NULL"
        )
        db.execSQL("CREATE INDEX parcel_account ON parcel(account_id, stage, picked)")
        db.execSQL(
            """
            CREATE TABLE unrecognized (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              sender TEXT NOT NULL,
              body TEXT NOT NULL,
              body_fp TEXT NOT NULL DEFAULT '',
              reason TEXT NOT NULL,
              domain TEXT NOT NULL DEFAULT 'PARCEL',
              state TEXT NOT NULL DEFAULT 'NEW',
              decided_at INTEGER,
              received_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX unrecognized_state ON unrecognized(state, received_at DESC)")
        db.execSQL("CREATE INDEX unrecognized_fp ON unrecognized(body_fp)")
        db.execSQL("CREATE TABLE meta (mkey TEXT PRIMARY KEY, mvalue TEXT NOT NULL)")
        // 源消息账本。整条链路唯一能回答「这条消息我到底读过没有」的东西——
        // 解析结果回答不了，因为「已取」「忽略」「已提取」都会让结果离开可比较的集合。
        db.execSQL(
            """
            CREATE TABLE message_seen (
              fp TEXT PRIMARY KEY,
              seen_at INTEGER NOT NULL,
              outcome TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE TABLE sender_mute (mute_key TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
        // 合并登记。parcel 表里没有外键指向它——别名只在读取侧解析，
        // 所以「拆开」就是删掉这一行，不需要把任何包裹搬回去。
        db.execSQL(
            """
            CREATE TABLE point_alias (
              alias_key TEXT PRIMARY KEY,
              alias_name TEXT NOT NULL,
              target_key TEXT NOT NULL,
              target_name TEXT NOT NULL,
              created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        // 用户已经否认过的疑似重复对。V2 的社区别名数据不得覆盖本地表态，所以要先查这张表。
        db.execSQL("CREATE TABLE point_dismiss (pair_key TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
        // 用户自己写的解析规则。`pattern` 是一段由用户提供、在解析时被编译的正则，
        // 因此它的执行位置只可能在 [dev.showcode.core.parse.ParseRules] 里那条只读路径上：
        // 规则只做加法（内置认出件时一律不参与），且坏正则在录入时就被拒、解析时静默失效。
        // 这里不存「命中过什么」——一条规则该不该生效由当下这次解析决定，历史记录不算判据。
        db.execSQL(
            """
            CREATE TABLE parse_rule (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              pattern TEXT NOT NULL,
              point_name TEXT NOT NULL,
              locker_no TEXT,
              enabled INTEGER NOT NULL DEFAULT 1,
              created_at INTEGER NOT NULL,
              sort_order INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        // 身份码。它**不是** parcel 的一种：没有取件点、没有时效、没有来源消息，
        // 硬塞进 parcel 只会让分组和紧急度那些判据多一个恒为空的分支。
        // `dynamic` 那一列存的不是备注，是一句以后要当真的话：这张码会变。
        db.execSQL(
            """
            CREATE TABLE identity_code (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              label TEXT NOT NULL,
              value TEXT NOT NULL,
              kind TEXT NOT NULL DEFAULT 'CODE',
              dynamic INTEGER NOT NULL DEFAULT 0,
              note TEXT,
              app_pkg TEXT,
              created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        // 推过通知的 App。一行 = 一个来源，`disabled` 是用户在那一行的配置。
        // 默认 0（全收）：这一屏不减少任何现有噪音，它只是把「能关」交出去。
        // 没有外键指向 parcel：关掉一个 App 不动它以前收进来的件。
        db.execSQL(
            """
            CREATE TABLE notify_app (
              pkg TEXT PRIMARY KEY,
              label TEXT NOT NULL,
              disabled INTEGER NOT NULL DEFAULT 0,
              forwards_family INTEGER NOT NULL DEFAULT 0,
              last_seen_at INTEGER NOT NULL,
              seen_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        // 平台账号绑定。secret 是 Keystore AES-GCM 的密文（base64(iv):base64(ct)），
        // 这一列永远不存明文 Cookie。v1 一平台一条，id 直接用平台 id。
        db.execSQL(
            """
            CREATE TABLE bound_account (
              id TEXT PRIMARY KEY,
              platform TEXT NOT NULL,
              label TEXT NOT NULL,
              secret TEXT NOT NULL,
              enabled INTEGER NOT NULL DEFAULT 1,
              state TEXT NOT NULL DEFAULT 'ACTIVE',
              created_at INTEGER NOT NULL,
              last_sync_at INTEGER,
              last_sync_result TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 这里绝不能走「删表重建」——那会把用户已经收进来的取件码全清掉。
        if (oldVersion < 2) {
            // v1 → v2：story-6 的两张表是纯新增，旧库里没有，直接补建即可。
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS point_alias (
                  alias_key TEXT PRIMARY KEY,
                  alias_name TEXT NOT NULL,
                  target_key TEXT NOT NULL,
                  target_name TEXT NOT NULL,
                  created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS point_dismiss (pair_key TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
        }
        if (oldVersion < 3) upgradeToV3(db)
        if (oldVersion < 4) {
            // v3 → v4：解析规则表是纯新增，旧库里没有，补建即可。
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS parse_rule (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  pattern TEXT NOT NULL,
                  point_name TEXT NOT NULL,
                  locker_no TEXT,
                  enabled INTEGER NOT NULL DEFAULT 1,
                  created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 5) {
            // v4 → v5：身份码同样是纯新增表。它和 parcel 一点关系都没有，
            // 所以这一格迁移不需要任何收口或回填——不动老行是老库升级唯一的红线。
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS identity_code (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  label TEXT NOT NULL,
                  value TEXT NOT NULL,
                  dynamic INTEGER NOT NULL DEFAULT 0,
                  note TEXT,
                  app_pkg TEXT,
                  created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 6) {
            // v5 → v6：按 App 的通知开关。也是纯新增表——它不认识任何已有的行，
            // 所以老库升级完，那张开关列表是空的，直到下一个通知进来。
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS notify_app (
                  pkg TEXT PRIMARY KEY,
                  label TEXT NOT NULL,
                  disabled INTEGER NOT NULL DEFAULT 0,
                  forwards_family INTEGER NOT NULL DEFAULT 0,
                  last_seen_at INTEGER NOT NULL,
                  seen_count INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 7) {
            // v6 → v7：身份码那一栏多一种「入口」（只跳转、不存码）。
            //
            // ⚠️ 为什么是加列而不是把 `value` 的 NOT NULL 去掉：**SQLite 不能 ALTER 掉列约束**。
            // 走正路只能「建新表 + INSERT SELECT + 删旧表 + 重建索引」，那是老库升级里唯一
            // 能把用户数据搬丢的一类操作。`kind` 带默认值，ENTRY 存空串——
            // 「这一行没有码」由 kind 说，不由约束说。
            addColumnIfMissing(db, "identity_code", "kind", "TEXT NOT NULL DEFAULT 'CODE'")
        }
        if (oldVersion < 8) upgradeToV8(db)
        if (oldVersion < 9) upgradeToV9(db)
        if (oldVersion < 10) upgradeToV10(db)
        if (oldVersion < 11) upgradeToV11(db)
        if (oldVersion < 12) upgradeToV12(db)
    }

    /**
     * v10 → v11：退休与「用户已取」分家。
     *
     * 旧实现把退休写成 `picked=1` + `picked_at=NULL`，之后同步只会 Skip(ALREADY_PICKED)，
     * 误判永远自愈不了。新列 `retired` 只表示「这一行不该再出现在首页」，与 picked 无关。
     *
     * 老库收口规则只动 `source='ACCOUNT'`、`picked=1`、`picked_at IS NULL` 的行：
     * 用户真点过「已取」的行一定带 picked_at（`markPicked` 与账号同步都写），
     * 所以这条 WHERE 不会碰到它们。把这些行恢复成 retired 行，平台再给出 AT_STATION 时
     * 会经 `accountUpdateValues` 自动回到待取。
     */
    private fun upgradeToV11(db: SQLiteDatabase) {
        addColumnIfMissing(db, "parcel", "retired", "INTEGER NOT NULL DEFAULT 0")
        /* 先拆索引再改 picked：退休行原本因 picked=1 被排除在唯一索引外，
           同码的新件可能已经插进来；先改 picked=0 会撞上那条索引，迁移直接失败。
           新索引都会排除 retired=0 之外的行，所以拆完再建是安全的。 */
        db.execSQL("DROP INDEX IF EXISTS parcel_dedupe")
        db.execSQL("DROP INDEX IF EXISTS parcel_tracking_dedupe")
        db.execSQL(
            "UPDATE parcel SET retired = 1, picked = 0" +
                " WHERE source = 'ACCOUNT' AND picked = 1 AND picked_at IS NULL"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS parcel_dedupe ON parcel(code, point_key)" +
                " WHERE picked = 0 AND retired = 0 AND code <> '' AND code_kind <> 'SUFFIX'"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS parcel_tracking_dedupe ON parcel(tracking_no)" +
                " WHERE picked = 0 AND retired = 0 AND tracking_no IS NOT NULL"
        )
    }

    /**
     * v9 → v10：后五位只用于展示，不再参与身份判定。
     *
     * 只**重建索引**、不删任何行：新判据比旧的更宽（SUFFIX 行不再进这条唯一索引），
     * 所以老库不可能因为这条迁移撞约束。靠后五位并错的那一件不会凭空补回来，
     * 但下一次账号同步会按运单号把它插成独立的一行；已经并错的行也保留原样，不硬删。
     */
    private fun upgradeToV10(db: SQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS parcel_dedupe")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS parcel_dedupe ON parcel(code, point_key)" +
                " WHERE picked = 0 AND code <> '' AND code_kind <> 'SUFFIX'"
        )
    }

    /**
     * v8 → v9：订单商品信息。五列全是可空的纯新增列，老行一个字段都不动；
     * 没有商品信息的件（短信/手动/未绑定）保持 null，详情页据此不显示商品卡。
     */
    private fun upgradeToV9(db: SQLiteDatabase) {
        addColumnIfMissing(db, "parcel", "goods_title", "TEXT")
        addColumnIfMissing(db, "parcel", "goods_image", "TEXT")
        addColumnIfMissing(db, "parcel", "goods_price", "TEXT")
        addColumnIfMissing(db, "parcel", "shop_name", "TEXT")
        addColumnIfMissing(db, "parcel", "ordered_at", "INTEGER")
    }

    /**
     * v7 → v8：账号绑定与「件可能没有取件码」。
     *
     * 老行一个字段都不动：新列全带默认值，短信件的 stage 是 AT_STATION、code_kind 是 PICKUP，
     * 所以首页/现场看到的还是同一张卡。
     *
     * 唯一需要重建的是 `parcel_dedupe`：它原来不排除空码，而账号件允许 code=""；
     * 两件无码件落在同一个点会撞唯一约束。重建只换判据，不删任何行。
     */
    private fun upgradeToV8(db: SQLiteDatabase) {
        addColumnIfMissing(db, "parcel", "tracking_no", "TEXT")
        addColumnIfMissing(db, "parcel", "order_sn", "TEXT")
        addColumnIfMissing(db, "parcel", "account_id", "TEXT")
        addColumnIfMissing(db, "parcel", "stage", "TEXT NOT NULL DEFAULT 'AT_STATION'")
        addColumnIfMissing(db, "parcel", "code_kind", "TEXT NOT NULL DEFAULT 'PICKUP'")
        addColumnIfMissing(db, "parcel", "last_synced_at", "INTEGER")

        db.execSQL("DROP INDEX IF EXISTS parcel_dedupe")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS parcel_dedupe ON parcel(code, point_key)" +
                " WHERE picked = 0 AND code <> ''"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS parcel_tracking_dedupe ON parcel(tracking_no)" +
                " WHERE picked = 0 AND tracking_no IS NOT NULL"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS parcel_account ON parcel(account_id, stage, picked)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bound_account (
              id TEXT PRIMARY KEY,
              platform TEXT NOT NULL,
              label TEXT NOT NULL,
              secret TEXT NOT NULL,
              enabled INTEGER NOT NULL DEFAULT 1,
              state TEXT NOT NULL DEFAULT 'ACTIVE',
              created_at INTEGER NOT NULL,
              last_sync_at INTEGER,
              last_sync_result TEXT
            )
            """.trimIndent()
        )
    }

    /**
     * v2 → v3：账本、发件人屏蔽、未识别的表态列，外加一次脏数据收口。
     *
     * 收口必须在加列之后、`reload()` 之前跑完：老库里躺着「已取 → 刷新 → 复活」攒下的重复行，
     * 它们共享同一个 (code, point_key, raw)，留下哪一条都不对，除了最早那条——
     * 它是真正被用户点过「已取」的那一行，复活出来的都排在它后面。
     */
    private fun upgradeToV3(db: SQLiteDatabase) {
        addColumnIfMissing(db, "unrecognized", "body_fp", "TEXT NOT NULL DEFAULT ''")
        addColumnIfMissing(db, "unrecognized", "domain", "TEXT NOT NULL DEFAULT 'PARCEL'")
        addColumnIfMissing(db, "unrecognized", "state", "TEXT NOT NULL DEFAULT 'NEW'")
        addColumnIfMissing(db, "unrecognized", "decided_at", "INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS unrecognized_state ON unrecognized(state, received_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS unrecognized_fp ON unrecognized(body_fp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS parcel_lookup ON parcel(code, point_key, received_at DESC)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_seen (
              fp TEXT PRIMARY KEY,
              seen_at INTEGER NOT NULL,
              outcome TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS sender_mute (mute_key TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")

        // 旧库里的未识别行既没有正文指纹也没有域。SQLite 没有 sha256，所以在 Kotlin 侧补。
        // 域必须一起补：老库里躺着的美团/蜂鸟短信如果仍然算 PARCEL，
        // 用户升级之后打开这一屏看到的还是同一排牛皮癣——那这次分流对他等于没发生。
        db.rawQuery("SELECT id, body FROM unrecognized", null).use { c ->
            val ids = ArrayList<Long>()
            val bodies = ArrayList<String>()
            while (c.moveToNext()) {
                ids.add(c.getLong(0))
                bodies.add(c.getString(1))
            }
            ids.forEachIndexed { i, id ->
                db.update(
                    "unrecognized",
                    ContentValues().apply {
                        put("body_fp", bodyFingerprint(bodies[i]))
                        put("domain", MessageDomains.classify(bodies[i]).name)
                    },
                    "id = ?",
                    arrayOf(id.toString()),
                )
            }
        }

        // 同一条正文被不同发件人各记一次（号码 + App 通知）是旧版 (sender, body) 精确匹配的产物。
        db.execSQL("DELETE FROM unrecognized WHERE id NOT IN (SELECT MIN(id) FROM unrecognized GROUP BY body_fp)")
        // 「已取 → 刷新」攒下的复活行。
        db.execSQL("DELETE FROM parcel WHERE id NOT IN (SELECT MIN(id) FROM parcel GROUP BY code, point_key, raw)")
    }

    /**
     * v11 → v12：解析规则支持手动排序。
     *
     * 为什么要这一列：`ParseRules.apply` 按列表顺序**取第一条命中**，
     * 所以规则顺序是**语义**，不只是偏好——两条正则都能抓到时，谁在前就决定了
     * 这件货被归到哪个取件点。原来靠 `created_at DESC`（新规则优先）隐式排序，
     * 用户想让某条老规则优先就没有任何办法。
     *
     * 回填用 `created_at DESC` 的序号，让升级后的顺序**和升级前完全一致**——
     * 迁移不能顺手改变谁先命中，那是用户看不见却会改变结果的事。
     */
    private fun upgradeToV12(db: SQLiteDatabase) {
        addColumnIfMissing(db, "parse_rule", "sort_order", "INTEGER NOT NULL DEFAULT 0")
        /* 按原来的顺序（created_at DESC）编号：0 是最高优先级。 */
        db.execSQL(
            """
            UPDATE parse_rule SET sort_order = (
              SELECT COUNT(*) FROM parse_rule AS r2
              WHERE r2.created_at > parse_rule.created_at
                 OR (r2.created_at = parse_rule.created_at AND r2.id > parse_rule.id)
            )
            """.trimIndent()
        )
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, type: String) {
        val present = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val name = c.getColumnIndexOrThrow("name")
            generateSequence { if (c.moveToNext()) c.getString(name) else null }.any { it == column }
        }
        if (!present) db.execSQL("ALTER TABLE $table ADD COLUMN $column $type")
    }

    // ---------- 事务 ----------

    /** 回填一次要跑几百次写，逐条 autocommit 就是几百次 fsync。包起来。 */
    fun <T> inTransaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val result = block()
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    // ---------- 源消息账本 ----------

    fun isMessageSeen(fp: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM message_seen WHERE fp = ? LIMIT 1",
        arrayOf(fp),
    ).use { it.moveToFirst() }

    fun markMessageSeen(fp: String, at: Long, outcome: String) {
        writableDatabase.insertWithOnConflict(
            "message_seen",
            null,
            ContentValues().apply { put("fp", fp); put("seen_at", at); put("outcome", outcome) },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    // ---------- 发件人屏蔽 ----------

    fun isSenderMuted(muteKey: String): Boolean = muteKey.isNotEmpty() && readableDatabase.rawQuery(
        "SELECT 1 FROM sender_mute WHERE mute_key = ? LIMIT 1",
        arrayOf(muteKey),
    ).use { it.moveToFirst() }

    fun muteSender(muteKey: String, at: Long) {
        if (muteKey.isEmpty()) return
        writableDatabase.insertWithOnConflict(
            "sender_mute",
            null,
            ContentValues().apply { put("mute_key", muteKey); put("created_at", at) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun allMutedSenders(): List<String> = readableDatabase
        .rawQuery("SELECT mute_key FROM sender_mute ORDER BY created_at DESC", null)
        .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun unmuteSender(muteKey: String) {
        writableDatabase.delete("sender_mute", "mute_key = ?", arrayOf(muteKey))
    }

    // ---------- 包裹 ----------

    /**
     * 首页只取没退休的行。账号同步匹配要 `includeRetired = true`：
     * 退休行必须能被平台重新给出的 `AT_STATION` 找回来，否则它就永久消失了。
     */
    fun allParcels(includeRetired: Boolean = false): List<Parcel> =
        readableDatabase.queryAll(includeRetired).use { c ->
            buildList { while (c.moveToNext()) add(c.toParcel()) }
        }

    /**
     * 一条消息入库前要看的候选行：同码同点、同运单号、或同点的后五位。
     * 完整运单号是跨通道的硬身份；后五位只在双方都没有运单号时才参与合并。
     */
    fun findParcelCandidates(code: String, pointKey: String, trackingNo: String?): List<ExistingParcel> {
        val clauses = mutableListOf("(code = ? AND point_key = ?)")
        val args = mutableListOf(code, pointKey)
        val tracking = trackingNo?.trim().orEmpty()
        if (tracking.isNotEmpty()) {
            clauses += "tracking_no = ?"
            args += tracking
            val suffix = tracking.takeLast(5)
            if (suffix.length == 5 && suffix != code) {
                clauses += "(code = ? AND point_key = ?)"
                args += suffix
                args += pointKey
            }
        }
        return readableDatabase.rawQuery(
            "SELECT id, picked, picked_at, received_at, code, point_key, tracking_no, code_kind" +
                " FROM parcel WHERE ${clauses.joinToString(" OR ")}" +
                " ORDER BY picked ASC, received_at DESC LIMIT 8",
            args.toTypedArray(),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ExistingParcel(
                            id = c.getLong(0),
                            picked = c.getInt(1) != 0,
                            pickedAt = if (c.isNull(2)) null else c.getLong(2),
                            receivedAt = c.getLong(3),
                            code = c.getString(4),
                            pointKey = c.getString(5),
                            trackingNo = if (c.isNull(6)) null else c.getString(6),
                            codeKind = enumOrDefault(c.getString(7), CodeKind.PICKUP),
                        )
                    )
                }
            }
        }
    }

    // ---------- 平台账号 ----------

    fun accounts(): List<BoundAccount> = readableDatabase.rawQuery(
        "SELECT id, platform, label, secret, enabled, state, created_at, last_sync_at, last_sync_result" +
            " FROM bound_account ORDER BY created_at DESC",
        null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val platform = Platform.of(c.getString(1)) ?: continue
                add(
                    BoundAccount(
                        id = c.getString(0),
                        platform = platform,
                        label = c.getString(2),
                        secret = c.getString(3),
                        enabled = c.getInt(4) != 0,
                        state = enumOrDefault(c.getString(5), AccountState.ACTIVE),
                        createdAt = c.getLong(6),
                        lastSyncAt = if (c.isNull(7)) null else c.getLong(7),
                        lastSyncResult = if (c.isNull(8)) null else c.getString(8),
                    )
                )
            }
        }
    }

    /** 一平台一条：同 id 覆盖写，调用方负责保住 createdAt/enabled。 */
    fun upsertAccount(account: BoundAccount) {
        writableDatabase.insertWithOnConflict(
            "bound_account",
            null,
            ContentValues().apply {
                put("id", account.id)
                put("platform", account.platform.id)
                put("label", account.label)
                put("secret", account.secret)
                put("enabled", if (account.enabled) 1 else 0)
                put("state", account.state.name)
                put("created_at", account.createdAt)
                if (account.lastSyncAt != null) put("last_sync_at", account.lastSyncAt) else putNull("last_sync_at")
                if (account.lastSyncResult != null) {
                    put("last_sync_result", account.lastSyncResult)
                } else {
                    putNull("last_sync_result")
                }
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /** 只换密文：抓取过程中平台刷新了 Cookie，不该顺带改状态或同步时间。 */
    fun updateAccountSecret(id: String, secret: String) {
        writableDatabase.update(
            "bound_account",
            ContentValues().apply { put("secret", secret) },
            "id = ?",
            arrayOf(id),
        )
    }

    fun updateAccountEnabled(id: String, enabled: Boolean) {
        writableDatabase.update(
            "bound_account",
            ContentValues().apply { put("enabled", if (enabled) 1 else 0) },
            "id = ?",
            arrayOf(id),
        )
    }

    fun updateAccountState(id: String, state: AccountState, lastSyncAt: Long?, result: String?) {
        writableDatabase.update(
            "bound_account",
            ContentValues().apply {
                put("state", state.name)
                if (lastSyncAt != null) put("last_sync_at", lastSyncAt) else putNull("last_sync_at")
                if (result != null) put("last_sync_result", result) else putNull("last_sync_result")
            },
            "id = ?",
            arrayOf(id),
        )
    }

    fun deleteAccount(id: String) {
        writableDatabase.delete("bound_account", "id = ?", arrayOf(id))
    }

    /**
     * 这条消息说的那件，库里现在是什么状态。
     *
     * **不带 `picked = 0` 过滤**——旧版带了，于是已取行对去重不可见，
     * 刷新重放的旧短信每次都绕过它插一条新卡。优先取未取的那行：
     * 同一码被复用后新旧并存时，要更新的是还活着的那件。
     */
    fun findParcelForUpdate(code: String, pointKey: String): ExistingParcel? = readableDatabase.rawQuery(
        "SELECT id, picked, picked_at, received_at FROM parcel" +
            " WHERE code = ? AND point_key = ? ORDER BY picked ASC, received_at DESC LIMIT 1",
        arrayOf(code, pointKey),
    ).use { c ->
        if (!c.moveToFirst()) return@use null
        ExistingParcel(
            id = c.getLong(0),
            picked = c.getInt(1) != 0,
            pickedAt = if (c.isNull(2)) null else c.getLong(2),
            receivedAt = c.getLong(3),
        )
    }

    /**
     * 插入新行。`SQLiteDatabase.insert()` 撞约束时**返回 -1 而不是抛**，
     * 所以返回值 ≤ 0 就是「没写进去」。调用方必须检查——不检查的话界面会跳到首页
     * 像是存好了，而那个码根本不在库里。
     */
    fun insertParcel(values: ContentValues): Long = writableDatabase.insert("parcel", null, values)

    fun updateParcel(id: Long, values: ContentValues) {
        writableDatabase.update("parcel", values, "id = ?", arrayOf(id.toString()))
    }

    // ---------- 未识别 ----------

    fun allUnrecognized(): List<UnrecognizedMessage> = readableDatabase
        .rawQuery(
            "SELECT id, sender, body, reason, received_at, domain FROM unrecognized" +
                " WHERE state = 'NEW' ORDER BY received_at DESC",
            null,
        )
        .use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        UnrecognizedMessage(
                            id = c.getLong(0),
                            sender = c.getString(1),
                            reason = enumOrDefault(c.getString(3), UnrecognizedReason.NO_CODE_FOUND),
                            body = c.getString(2),
                            receivedAt = c.getLong(4),
                            domain = enumOrDefault(c.getString(5), MessageDomain.PARCEL),
                        )
                    )
                }
            }
        }

    /** 这条正文在库里的现状。返回 null 才是「没见过」。 */
    fun unrecognizedState(bodyFp: String): UnrecognizedState? = readableDatabase.rawQuery(
        "SELECT state FROM unrecognized WHERE body_fp = ? LIMIT 1",
        arrayOf(bodyFp),
    ).use { if (!it.moveToFirst()) null else enumOrDefault(it.getString(0), UnrecognizedState.NEW) }

    fun insertUnrecognized(values: ContentValues) {
        writableDatabase.insert("unrecognized", null, values)
    }

    /** 表态是 UPDATE 不是 DELETE。删掉就等于用户什么也没说过。 */
    fun setUnrecognizedState(ids: List<Long>, state: UnrecognizedState, at: Long) {
        if (ids.isEmpty()) return
        val args = ArrayList<Any?>(ids.size + 2).apply {
            add(state.name)
            add(at)
            addAll(ids)
        }.toTypedArray()
        writableDatabase.execSQL(
            "UPDATE unrecognized SET state = ?, decided_at = ? WHERE id IN (${ids.joinToString(",") { "?" }})",
            args,
        )
    }

    /** 手动提取成功 = 这条已经解决了。命中 0 行是无害的：手动导入未必是从未识别页进来的。 */
    fun resolveUnrecognizedByBody(body: String, at: Long) {
        writableDatabase.execSQL(
            "UPDATE unrecognized SET state = 'RESOLVED', decided_at = ? WHERE body_fp = ?",
            arrayOf<Any>(at, bodyFingerprint(body)),
        )
    }

    /**
     * 保留策略。表只增不减的话，「没认出来的取件短信」迟早变成一个没人打开的 archive。
     * 表态满 [decidedTtlMs] 的丢掉；还在看的按时间留最新 [newCap] 条。
     */
    fun pruneUnrecognized(now: Long, decidedTtlMs: Long, newCap: Int) {
        writableDatabase.execSQL(
            "DELETE FROM unrecognized WHERE state != 'NEW' AND decided_at IS NOT NULL AND decided_at < ?",
            arrayOf<Any>(now - decidedTtlMs),
        )
        // LIMIT 待在子查询里：Android 自带的 SQLite 没开 UPDATE/DELETE-LIMIT 编译开关，
        // 直接写 "DELETE ... LIMIT n" 会当场报语法错。
        writableDatabase.execSQL(
            "DELETE FROM unrecognized WHERE state = 'NEW' AND id NOT IN" +
                " (SELECT id FROM unrecognized WHERE state = 'NEW' ORDER BY received_at DESC LIMIT ?)",
            arrayOf<Any>(newCap),
        )
    }

    // ---------- 其它 ----------

    fun allAliases(): List<PointAlias> = readableDatabase
        .rawQuery("SELECT alias_key, alias_name, target_key, target_name, created_at FROM point_alias", null)
        .use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        PointAlias(
                            aliasKey = c.getString(0),
                            aliasName = c.getString(1),
                            targetKey = c.getString(2),
                            targetName = c.getString(3),
                            createdAt = c.getLong(4),
                        )
                    )
                }
            }
        }

    fun putAlias(alias: PointAlias) {
        writableDatabase.insertWithOnConflict(
            "point_alias",
            null,
            ContentValues().apply {
                put("alias_key", alias.aliasKey)
                put("alias_name", alias.aliasName)
                put("target_key", alias.targetKey)
                put("target_name", alias.targetName)
                put("created_at", alias.createdAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun deleteAlias(aliasKey: String) {
        writableDatabase.delete("point_alias", "alias_key = ?", arrayOf(aliasKey))
    }

    fun allDismissals(): Set<String> = readableDatabase
        .rawQuery("SELECT pair_key FROM point_dismiss", null)
        .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) }.toSet() }

    /**
     * 写一批「不是同一个点」的键（一次表态 = 精确键 + 地点键两把）。
     *
     * 必须包在一个事务里：两把键只落下一把，下一次那对写法还会再弹一次——
     * 用户看到的是「我明明点过不是」，查起来却是数据半成功，最难交代。
     */
    fun putDismissals(keys: Collection<String>, at: Long) {
        if (keys.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (key in keys) {
                db.insertWithOnConflict(
                    "point_dismiss",
                    null,
                    ContentValues().apply { put("pair_key", key); put("created_at", at) },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------- 解析规则 ----------

    fun allRules(): List<ParseRule> = readableDatabase
        .rawQuery(
            /* 按 sort_order 而不是 created_at：用户拖过顺序之后，谁先命中必须由那个顺序决定。 */
            "SELECT id, pattern, point_name, locker_no, enabled, created_at FROM parse_rule" +
                " ORDER BY sort_order ASC, created_at DESC",
            null,
        )
        .use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ParseRule(
                            id = c.getLong(0),
                            pattern = c.getString(1),
                            pointName = c.getString(2),
                            lockerNo = if (c.isNull(3)) null else c.getString(3),
                            enabled = c.getInt(4) != 0,
                            createdAt = c.getLong(5),
                        )
                    )
                }
            }
        }

    /**
     * 和 [insertParcel] 同一条规矩：撞约束只返回 -1 不抛，调用方必须查返回值。
     *
     * 新规则排到**末尾**（sort_order = 现有最大值 + 1）。规则是「补一条内置没认出来的写法」，
     * 新写的通常更窄、更专用，不该抢在已经调好的规则前面——那是升级后顺序突变的反向。
     */
    fun insertRule(pattern: String, pointName: String, lockerNo: String?, at: Long): Long {
        val nextOrder = readableDatabase
            .rawQuery("SELECT COALESCE(MAX(sort_order) + 1, 0) FROM parse_rule", null)
            .use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        return writableDatabase.insert(
            "parse_rule",
            null,
            ContentValues().apply {
                put("pattern", pattern)
                put("point_name", pointName)
                put("locker_no", lockerNo)
                put("enabled", 1)
                put("created_at", at)
                put("sort_order", nextOrder)
            },
        )
    }

    /**
     * 拖拽之后整体重写顺序。[orderedIds] 是新的从上到下的 id 序列，index 就是 sort_order。
     *
     * 整表重写而不是「只挪动那一条」：只改一条的话，中间那些行的 sort_order 会出现重复，
     * 而 `apply()` 取第一条命中——重复序号意味着谁先命中变成未定义的。
     * 规则上限 20 条，整表重写是几十微秒的事。
     */
    fun reorderRules(orderedIds: List<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            orderedIds.forEachIndexed { index, id ->
                db.update(
                    "parse_rule",
                    ContentValues().apply { put("sort_order", index) },
                    "id = ?",
                    arrayOf(id.toString()),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setRuleEnabled(id: Long, enabled: Boolean) {
        writableDatabase.update(
            "parse_rule",
            ContentValues().apply { put("enabled", if (enabled) 1 else 0) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun deleteRule(id: Long) {
        writableDatabase.delete("parse_rule", "id = ?", arrayOf(id.toString()))
    }

    // ---------- 身份码 ----------

    fun allIdentityCodes(): List<IdentityCode> = readableDatabase
        .rawQuery(
            "SELECT id, label, value, dynamic, note, app_pkg, created_at, kind FROM identity_code" +
                " ORDER BY created_at DESC",
            null,
        )
        .use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        IdentityCode(
                            id = c.getLong(0),
                            label = c.getString(1),
                            value = c.getString(2),
                            dynamic = c.getInt(3) != 0,
                            note = if (c.isNull(4)) null else c.getString(4),
                            appPackage = if (c.isNull(5)) null else c.getString(5),
                            createdAt = c.getLong(6),
                            // 库里出现没见过的 kind 时退成 CODE：那一行的码值还在，
                            // 按 CODE 出示顶多多问一句，按 ENTRY 出示会把码藏起来。
                            kind = enumOrDefault(c.getString(7), IdentityKind.CODE),
                        ),
                    )
                }
            }
        }

    /** 和 [insertRule] 同一条规矩：撞约束只返回 -1 不抛，调用方必须查返回值。 */
    fun insertIdentityCode(
        label: String,
        value: String,
        kind: IdentityKind,
        dynamic: Boolean,
        note: String?,
        appPackage: String?,
        at: Long,
    ): Long = writableDatabase.insert(
        "identity_code",
        null,
        ContentValues().apply {
            put("label", label)
            put("value", value)
            put("kind", kind.name)
            put("dynamic", if (dynamic) 1 else 0)
            put("note", note)
            put("app_pkg", appPackage)
            put("created_at", at)
        },
    )

    /**
     * 只改「这张码会不会变」那一格。
     *
     * 单独开这一条是因为它的触发时机：用户是**站在机器前扫过一次失败之后**才确定
     * 「这玩意儿是动态的」。那种时候让他删掉重录一遍码值，等于赌他还记得原串。
     */
    fun setIdentityDynamic(id: Long, dynamic: Boolean) {
        writableDatabase.update(
            "identity_code",
            ContentValues().apply { put("dynamic", if (dynamic) 1 else 0) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun deleteIdentityCode(id: Long) {
        writableDatabase.delete("identity_code", "id = ?", arrayOf(id.toString()))
    }

    // ---------- 按 App 的通知开关 ----------

    /**
     * 记一次「这个 App 推过通知」。已存在就只更新见到时间与次数。
     *
     * 两条语句而不是 upsert：`ON CONFLICT DO UPDATE` 要 SQLite 3.24，而 minSdk 26 那两台
     * 出厂带的是 3.19——写在一个设备上才会坏的 SQL 是这个仓库不做的交易。
     *
     * ⚠️ 这里刻意**不碰 `disabled`**。每一次通知都重写整行，会把用户关掉的那个开关
     * 在下一条通知到达时静默打开——那是把配置当成缓存来刷。
     */
    fun recordNotifyApp(pkg: String, label: String, forwardsFamily: Boolean, at: Long) {
        val db = writableDatabase
        // 插入时给 **0** 而不是 1：下面那条 UPDATE 无条件 +1，两条一起才等于「到达一次」。
        // 写成 1 的话第一次到达就计成两条，而那一屏把这个数字念给人听。
        db.execSQL(
            "INSERT OR IGNORE INTO notify_app (pkg, label, disabled, forwards_family, last_seen_at, seen_count)" +
                " VALUES (?, ?, 0, ?, ?, 0)",
            arrayOf(pkg, label, if (forwardsFamily) 1 else 0, at),
        )
        db.execSQL(
            "UPDATE notify_app SET label = ?, last_seen_at = ?, seen_count = seen_count + 1," +
                " forwards_family = MAX(forwards_family, ?) WHERE pkg = ?",
            arrayOf(label, at, if (forwardsFamily) 1 else 0, pkg),
        )
    }

    fun allNotifyApps(): List<NotifyApp> = readableDatabase
        .rawQuery(
            "SELECT pkg, label, disabled, forwards_family, last_seen_at, seen_count FROM notify_app",
            null,
        )
        .use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        NotifyApp(
                            packageName = c.getString(0),
                            label = c.getString(1),
                            disabled = c.getInt(2) != 0,
                            forwardsFamily = c.getInt(3) != 0,
                            lastSeenAt = c.getLong(4),
                            seenCount = c.getInt(5),
                        ),
                    )
                }
            }
        }

    /** 判据要的那一份：只取关掉的包名。每条通知都读一次，所以这一条必须是廉价的。 */
    fun disabledNotifyPackages(): Set<String> = readableDatabase
        .rawQuery("SELECT pkg FROM notify_app WHERE disabled = 1", null)
        .use { c ->
            buildList {
                while (c.moveToNext()) add(c.getString(0))
            }.toSet()
        }

    /** 这张表里有没有这个包。用于「用户见过的来源继续记到达」那条例外。 */
    fun knowsNotifyApp(pkg: String): Boolean = readableDatabase
        .rawQuery("SELECT 1 FROM notify_app WHERE pkg = ? LIMIT 1", arrayOf(pkg))
        .use { it.moveToFirst() }

    /**
     * 出现过的收录来源。巡检提醒用它判「这个用户有没有推送盲区」——
     * 只要有一条件是手动录进来的，就说明有平台根本不往手机推东西。
     */
    fun distinctSources(): Set<Source> = readableDatabase
        .rawQuery("SELECT DISTINCT source FROM parcel", null)
        .use { c ->
            buildSet {
                while (c.moveToNext()) add(enumOrDefault(c.getString(0), Source.SMS))
            }
        }

    /** 用户在那一屏按下的开关。关掉一个 App 不动它以前收进来的件——那只影响以后。 */
    fun setNotifyAppDisabled(pkg: String, disabled: Boolean) {
        writableDatabase.execSQL(
            "UPDATE notify_app SET disabled = ? WHERE pkg = ?",
            arrayOf(if (disabled) 1 else 0, pkg),
        )
    }

    /**
     * 还挂着的未识别正文，按时间从新到旧，最多 [limit] 条。
     *
     * 只取重判要用的那三个字段：这一步是「用户刚写完一条规则」触发的，
     * 不是后台常驻，所以要把上界钉死——库里攒了几百条时不该一次全跑。
     */
    fun newUnrecognizedBodies(limit: Int): List<UnrecognizedRecord> = readableDatabase
        .rawQuery(
            "SELECT id, sender, body, received_at FROM unrecognized WHERE state = 'NEW'" +
                " ORDER BY received_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        )
        .use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(UnrecognizedRecord(id = c.getLong(0), sender = c.getString(1), body = c.getString(2), receivedAt = c.getLong(3)))
                }
            }
        }

    /** 规则救回了这条正文：表态成 RESOLVED，和手动提取同一个语义。 */
    fun resolveUnrecognizedByIds(ids: List<Long>, at: Long) {
        if (ids.isEmpty()) return
        val args = ArrayList<Any?>(ids.size + 1).apply { add(at); addAll(ids) }.toTypedArray()
        writableDatabase.execSQL(
            "UPDATE unrecognized SET state = 'RESOLVED', decided_at = ? WHERE id IN (${ids.joinToString(",") { "?" }})",
            args,
        )
    }

    fun metaGet(key: String): String? = readableDatabase
        .rawQuery("SELECT mvalue FROM meta WHERE mkey = ?", arrayOf(key))
        .use { if (it.moveToFirst()) it.getString(0) else null }

    fun metaPut(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "meta",
            null,
            ContentValues().apply { put("mkey", key); put("mvalue", value) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun countPickedSince(since: Long): Int = readableDatabase
        .rawQuery("SELECT COUNT(*) FROM parcel WHERE picked = 1 AND picked_at >= ?", arrayOf(since.toString()))
        .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun lastPickupAt(): Long? = readableDatabase
        .rawQuery("SELECT MAX(picked_at) FROM parcel WHERE picked = 1", null)
        .use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

    private fun SQLiteDatabase.queryAll(includeRetired: Boolean): Cursor = rawQuery(
        "SELECT id, code, locker_no, carrier, point_key, point_name, point_kind, owner, source," +
            " received_at, free_until_at, closes_at_minute, picked, picked_at, raw," +
            " tracking_no, order_sn, account_id, stage, code_kind, last_synced_at," +
            " goods_title, goods_image, goods_price, shop_name, ordered_at, retired" +
            " FROM parcel" + (if (includeRetired) "" else " WHERE retired = 0") +
            " ORDER BY picked ASC, received_at DESC",
        null,
    )

    private fun Cursor.toParcel() = Parcel(
        id = getLong(0),
        code = getString(1),
        lockerNo = if (isNull(2)) null else getString(2),
        carrier = getString(3),
        pointKey = getString(4),
        pointName = getString(5),
        // 认不出类型时退成驿站：柜件的 closes_at_minute 那列是 null，于是 deadline 为空 → 安全态。
        // 宁可少一个颜色，也不编造一个截止时间把人吓得半夜跑一趟。
        pointKind = enumOrDefault(getString(6), PointKind.STATION),
        owner = enumOrDefault(getString(7), Owner.SELF),
        source = enumOrDefault(getString(8), Source.SMS),
        receivedAt = getLong(9),
        freeUntilAt = if (isNull(10)) null else getLong(10),
        closesAtMinuteOfDay = if (isNull(11)) null else getInt(11),
        picked = getInt(12) != 0,
        pickedAt = if (isNull(13)) null else getLong(13),
        raw = getString(14),
        trackingNo = if (isNull(15)) null else getString(15),
        orderSn = if (isNull(16)) null else getString(16),
        accountId = if (isNull(17)) null else getString(17),
        stage = enumOrDefault(getString(18), Stage.AT_STATION),
        codeKind = enumOrDefault(getString(19), CodeKind.PICKUP),
        lastSyncedAt = if (isNull(20)) null else getLong(20),
        goodsTitle = if (isNull(21)) null else getString(21),
        goodsImage = if (isNull(22)) null else getString(22),
        goodsPrice = if (isNull(23)) null else getString(23),
        shopName = if (isNull(24)) null else getString(24),
        orderedAt = if (isNull(25)) null else getLong(25),
        retired = getInt(26) != 0,
    )

    private companion object {
        const val NAME = "showcode.db"
        const val VERSION = 12
    }
}
