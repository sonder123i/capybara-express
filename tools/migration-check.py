"""库结构的静态一致性 + 迁移链的真跑。CI 里跑，不需要设备、不需要 Gradle。

为什么要有它：`data/` 一层没有测试框架（不引 Robolectric、不引 sqlite-jdbc，那条取舍仍然成立），
所以 SQL 写错在 JVM 单测里**够不着**——而这类错的后果不是难看，是老库升级后第一次读到就崩，
或者更坏：悄悄把用户已经做过的决定冲掉。这一轮它就抓出了一个真错
（`recordNotifyApp` 第一次到达把 `seen_count` 记成 2，见 `aee1f9e`）。

它管三件事，其中前两件是「写在两份文本里的同一件事会漂」那一类：

1. **加了表有没有忘了写迁移。** 只改 `onCreate` 的话新装机器一切正常，
   老机器升级后缺表、第一次读它才炸——这种错在开发机上永远看不见。
2. **一路升级出来的库，和新装的库，每张表的列是否一模一样**（v7 之后不能再用「两份 DDL 逐字比对」：
   新列在新装时写在 CREATE 里、在升级时靠一条 ALTER 补，两份文本本来就该不同）。
3. **迁移链在真 SQLite 上跑不跑得通**，以及 DAO 里每一条读语句打在这张升过级的库上
   是不是还认得那些列名。

第四件是它的私货，也是最容易由「顺手重写整行」破坏的一条：`notify_app` 那条 upsert
不许把用户关掉的开关冲掉，也不许把第一次到达记成两条。
"""
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/dev/showcode/data/ShowCodeDb.kt"

# 首版（v1）就存在的表。它们不该出现在迁移里——迁移是「后来加的」的意思。
FIRST_RELEASE = {"parcel", "unrecognized", "meta"}

CREATE = re.compile(r"CREATE TABLE (?:IF NOT EXISTS )?(\w+) \(([^)]*)\)", re.S)
LADDER = re.compile(r"if \(oldVersion < (\d+)\)")
# 只认字面量 SELECT。带模板（IN (${...})）的那几条拼不出稳定文本，由别的检查覆盖。
SELECT = re.compile(r'"(SELECT [^"]+)"')

fails = []


def check(name, ok, detail=""):
    print("  %-4s %s%s" % ("OK" if ok else "RED", name, "" if not detail else "  -> " + str(detail)))
    if not ok:
        fails.append(name)


def norm(text):
    return re.sub(r"\s+", " ", text).strip()


def joined_literals(text):
    """把 Kotlin 里跨行拼接的字面量缝起来：`"SELECT ..." +\\n  " FROM ..."` → 一整条。

    不缝就是半条语句，拿去执行必然报「no such table」——那这条检查报的是自己的写法，不是产品。
    """
    return re.sub(r'"\s*\+\s*\n\s*"', "", text)


def sections(text):
    """onCreate 区 / 迁移区（onUpgrade 及其调用的 upgradeToV3 都算）。"""
    cut_on = text.index("fun onCreate")
    cut_upgrade = text.index("fun onUpgrade")
    return text[cut_on:cut_upgrade], text[cut_upgrade:]


def tables_in(blob):
    out = {}
    for name, body in CREATE.findall(blob):
        out.setdefault(name, norm(body))
    return out


def main():
    ci = "--ci" in sys.argv
    text = SRC.read_text(encoding="utf-8")
    version = int(re.search(r"const val VERSION = (\d+)", text).group(1))
    create_zone, upgrade_zone = sections(text)
    created, migrated = tables_in(create_zone), tables_in(upgrade_zone)

    print("ShowCodeDb  VERSION=%d，onCreate %d 张表，迁移区 %d 张表" % (version, len(created), len(migrated)))

    print("== 加了表必须同时加迁移 ==")
    for name in created:
        if name in FIRST_RELEASE:
            continue
        check("%s 在迁移里有对应建表语句" % name, name in migrated)

    print("== 版本梯子必须覆盖 2..VERSION ==")
    ladder = sorted({int(n) for n in LADDER.findall(upgrade_zone)})
    check("分档 = %s，VERSION = %d" % (ladder, version),
          ladder == list(range(2, version + 1)))

    print("== 迁移链在真 SQLite 上连跑 ==")
    db = sqlite3.connect(":memory:")
    # 老库的形状 = onCreate 里那些**没有迁移对应**的表（有对应的说明它是某次升级才加的）。
    baseline = {n: b for n, b in created.items() if n not in migrated}
    for name, body in baseline.items():
        db.execute("CREATE TABLE %s (%s)" % (name, body))
    for ddl in re.findall(r'"(CREATE (?:UNIQUE )?INDEX [^"]+)"', create_zone):
        db.execute(ddl.replace("IF NOT EXISTS ", ""))
    db.commit()

    # ⚠️ 分两段执行，**先建表、再灌数据、最后跑改数据的语句**。
    # 顺序错了会让迁移里那些改用户已有行的语句全部空转——v3 的两条 DELETE、
    # v11 的 UPDATE 是整条链里唯一会动用户数据的语句，在空表上执行等于没测。
    # 实测过：把 v11 的 `AND picked_at IS NULL` 守卫删掉，先迁移后灌数据的探针仍然全绿。
    #
    # 为什么不能简单地把整段迁移推到灌数据之后：`message_seen` / `sender_mute` 等表
    # 本身就是迁移建的，灌数据要用到它们。所以按语句种类分两段，而不是按时间点分。
    REWRITE = re.compile(
        r'"((?:DELETE FROM|UPDATE) [^"]*)"',
        re.S,
    )
    version_of_db = version

    # 迁移链必须**按版本号顺序**回放，不能按源码出现顺序。
    # 这个文件里的 `upgradeToV*` 函数是倒着定义的（V11 在 317 行、V8 在 374 行），
    # 而 `onUpgrade` 里的调用是升序的（`if (oldVersion < 8)` … `< 12`）。
    # 按文本顺序回放等于把迁移链跑反：实测会得到「v8 那条谓词最终生效」的假结论，
    # 而真实的库跑完是 v11 那条——一个会把产品判成错的检查器比没有检查器更糟。
    def chunks_in_order():
        """按 onUpgrade 的调用顺序，把每个版本对应的源码片段切出来。"""
        # 先把每个 upgradeToV* 的函数体切出来（按定义位置）。
        bodies = {}
        for m in re.finditer(r"private fun upgradeToV(\d+)\(db: SQLiteDatabase\) \{", text):
            version = int(m.group(1))
            start = m.end()
            depth, i = 1, start
            while i < len(text) and depth:
                if text[i] == "{":
                    depth += 1
                elif text[i] == "}":
                    depth -= 1
                i += 1
            bodies[version] = text[start:i - 1]
        # onUpgrade 里内联写的那几档（v2..v7 没有独立函数）按 `if (oldVersion < N)` 切。
        ladder = {}
        up = text[text.index("fun onUpgrade"):]
        marks = [(int(m.group(1)), m.start()) for m in LADDER.finditer(up)]
        for idx, (version, pos) in enumerate(marks):
            end = marks[idx + 1][1] if idx + 1 < len(marks) else len(up)
            ladder[version] = up[pos:end]
        return [bodies.get(v) or ladder.get(v, "") for v in range(2, version_of_db + 1)]

    def structural():
        """只建表、加列、建索引——不碰任何已有行。"""
        for name, body in migrated.items():
            db.execute("CREATE TABLE IF NOT EXISTS %s (%s)" % (name, body))
        # v7 那一档不是建表，是给 identity_code **加一列**（走 Kotlin 侧的 addColumnIfMissing）。
        # 探针必须连它一起跑：不然「DAO 每条读都认得升过级的库」会因为探针没跟上而红，
        # 而那种红报的是检查器自己，不是产品——一个会乱报的检查器等于没有检查器。
        for table, column, ddl in re.findall(
            r'addColumnIfMissing\(db, "(\w+)", "(\w+)", "([^"]+)"\)', text
        ):
            have = [r[1] for r in db.execute("PRAGMA table_info(%s)" % table)]
            if column not in have:
                db.execute("ALTER TABLE %s ADD COLUMN %s %s" % (table, column, ddl))
        # 迁移区的索引也要真跑。原来只回放 onCreate 区的索引，于是 v8/v10/v11 三次
        # 索引重建从未被执行过——而索引谓词**就是去重语义本身**：写错会静默丢件
        # （insert 撞唯一约束返回 -1，界面照跳首页，用户看不到任何错）。
        # 实测过：把 v11 谓词里的 `retired = 0 AND` 删掉，只回放 onCreate 的探针仍然全绿。
        #
        # ⚠️ 按版本顺序、保留 `IF NOT EXISTS`：`parcel_dedupe` 被重建三次（v8/v10/v11），
        # 每次前面先 DROP——剥掉 IF NOT EXISTS 会撞「already exists」，那报的是探针自己。
        for chunk in chunks_in_order():
            for ddl in re.findall(r'"(DROP INDEX[^"]*|CREATE (?:UNIQUE )?INDEX[^"]*)"',
                                  joined_literals(chunk)):
                db.execute(ddl)

    def rewrites():
        """只跑会改已有行的迁移语句：DELETE FROM / UPDATE，且不带占位符。

        迁移链里真正的改写语句只有三条（v3 的两条 DELETE、v11 的一条 UPDATE），
        它们都是完整字面量。带 `?` 或 `${...}` 的是 DAO 的运行期语句，
        拼不出可执行文本，也不是迁移的一部分——跳过。
        """
        for chunk in chunks_in_order():
            for statement in REWRITE.findall(joined_literals(chunk)):
                if "?" in statement or "${" in statement:
                    continue
                db.execute(norm(statement))

    def migrate():
        structural()
        rewrites()

    # 第一步：把老库升到「表齐全」的状态，好让下面灌数据有地方放。
    structural()
    db.commit()
    check("v1 形状起手，一路建到 v%d 没报错" % version, True)

    print("== 老数据一段不能被改动 ==")
    db.execute(
        "INSERT INTO parcel (code,carrier,point_key,point_name,point_kind,owner,source,received_at,picked,picked_at,raw)"
        " VALUES ('5-2-7712','中通快递','pk1','示例小区菜鸟驿站','STATION','SELF','SMS',1,1,2,'原文一')")
    db.execute(
        "INSERT INTO parcel (code,carrier,point_key,point_name,point_kind,owner,source,received_at,picked,raw)"
        " VALUES ('8-9-0765','兔喜','pk2','示例精品超市','STATION','SELF','SMS',3,0,'原文二')")
    # v11 那一档会把「账号来源 + picked=1 + picked_at IS NULL」的退休行改成 picked=0。
    # 这四条就是为它准备的三类样本，缺一条那条 UPDATE 的 WHERE 就测不到边界：
    #   a) 用户真点过的已取（picked_at 有值）—— **绝不能被碰**
    #   b) 账号来源、已取但 picked_at 为空 —— 该被退休
    #   c) 短信来源、已取但 picked_at 为空 —— 不在 v11 的 WHERE 里（它限 source='ACCOUNT'）
    db.execute(
        "INSERT INTO parcel (code,carrier,point_key,point_name,point_kind,owner,source,received_at,picked,picked_at,raw)"
        " VALUES ('6-2-3391','中通快递','pk3','示例小区菜鸟驿站','STATION','SELF','ACCOUNT',10,1,11,'真点过已取')")
    db.execute(
        "INSERT INTO parcel (code,carrier,point_key,point_name,point_kind,owner,source,received_at,picked,raw)"
        " VALUES ('7-1-0001','京东','pk4','示例京东站点','STATION','ACCOUNT','ACCOUNT',12,1,'账号件无 picked_at')")
    db.execute(
        "INSERT INTO parcel (code,carrier,point_key,point_name,point_kind,owner,source,received_at,picked,raw)"
        " VALUES ('7-1-0002','中通快递','pk5','示例小区菜鸟驿站','STATION','SELF','SMS',13,1,'短信件无 picked_at')")
    db.execute("INSERT INTO message_seen VALUES ('fp1', 1, 'PARCEL:1')")
    db.execute("INSERT INTO sender_mute VALUES ('10690000', 1)")
    db.execute("INSERT INTO meta VALUES ('last_sync','999')")
    db.commit()

    # 迁移前把「用户真点过的已取」那几行记下来，迁移后逐列比。
    user_decided = db.execute(
        "SELECT id, code, picked, picked_at, retired FROM parcel WHERE picked_at IS NOT NULL ORDER BY id"
    ).fetchall()

    # 第二步：跑改数据的语句——这才是真正要测的那一段。
    rewrites()
    db.commit()

    # 三条数据断言，正对着 v11 那条 UPDATE 的 WHERE 三档。
    after = db.execute(
        "SELECT id, code, picked, picked_at, retired FROM parcel WHERE picked_at IS NOT NULL ORDER BY id"
    ).fetchall()
    check("v11 的 UPDATE 没碰 picked_at 有值的行（用户真点过的已取）",
          after == user_decided,
          "前 %s / 后 %s" % (user_decided, after))
    account_retired = db.execute(
        "SELECT picked, retired FROM parcel WHERE code = '7-1-0001'"
    ).fetchone()
    check("账号来源 + picked_at 为空的已取被退休（picked=0, retired=1）",
          account_retired == (0, 1), account_retired)
    sms_untouched = db.execute(
        "SELECT picked, retired FROM parcel WHERE code = '7-1-0002'"
    ).fetchone()
    check("短信来源的同类行不在 v11 的 WHERE 里，不许被动",
          sms_untouched == (1, 0), sms_untouched)

    # 索引真的建出来了，而且谓词里带着 retired 那一半。
    indexes = {r[0]: r[1] for r in db.execute(
        "SELECT name, sql FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL"
    )}
    check("迁移区的索引全部建出来了",
          {"parcel_dedupe", "parcel_tracking_dedupe"} <= set(indexes),
          sorted(indexes))
    dedupe_sql = (indexes.get("parcel_dedupe") or "").replace(" ", "")
    check("parcel_dedupe 的谓词带着 retired = 0（去掉它会让退休行挡住同码新件）",
          "retired=0" in dedupe_sql, dedupe_sql)

    snapshot = db.execute("SELECT * FROM parcel ORDER BY id").fetchall()
    for name in baseline:
        before = db.execute("SELECT COUNT(*) FROM %s" % name).fetchone()[0]
        migrate()  # 整段重跑一遍：建表带 IF NOT EXISTS，加列靠先查 PRAGMA——两样都必须幂等
        db.commit()
        check("%s 的行数没被重跑迁移改动" % name,
              db.execute("SELECT COUNT(*) FROM %s" % name).fetchone()[0] == before)
    check("parcel 整表逐列不变（picked / picked_at / raw 都不许动）",
          db.execute("SELECT * FROM parcel ORDER BY id").fetchall() == snapshot)

    print("== DAO 里每一条读都认得这张升过级的库 ==")
    stitched = joined_literals(text)
    readables = [norm(s) for s in SELECT.findall(stitched) if "${" not in s]
    broken = []
    for statement in readables:
        try:
            db.execute(statement, tuple([0] * statement.count("?"))).fetchall()
        except Exception as error:
            broken.append("%s <- %s" % (statement[:56], error))
    for problem in broken:
        check("有语句打不通", False, problem)
    check("%d 条 SELECT 全部可执行" % len(readables), not broken)

    print("== notify_app 那条 upsert 的两半 ==")
    insert = re.search(r'"(INSERT OR IGNORE INTO notify_app [^"]+)"', stitched)
    update = re.search(r'"(UPDATE notify_app SET label[^"]+)"', stitched)
    check("源码里能找到 recordNotifyApp 那两条语句", bool(insert and update))
    if insert and update:
        first = norm(insert.group(1))
        second = norm(update.group(1))
        pkg, label, at = "com.cainiao.wireless", "菜鸟驿站", 100
        # 参数顺序由列顺序定：pkg, label, forwards_family, last_seen_at
        db.execute(first, (pkg, label, 0, at))
        db.execute(second, (label, at, 0, pkg))
        count = db.execute("SELECT seen_count FROM notify_app WHERE pkg = ?", (pkg,)).fetchone()[0]
        check("第一次到达 = 1 条（今天差点是 2 条）", count == 1, count)

        # 用户把它关了。
        db.execute("UPDATE notify_app SET disabled = 1, forwards_family = 1 WHERE pkg = ?", (pkg,))
        # 关闭期间它还在推：这条 record 不许把开关打开。
        db.execute(first, (pkg, "菜鸟", 1, 300))
        db.execute(second, ("菜鸟", 300, 0, pkg))
        row = db.execute(
            "SELECT disabled, forwards_family, seen_count FROM notify_app WHERE pkg = ?", (pkg,)).fetchone()
        check("下一条通知到达，disabled 仍是 1（配置不是缓存）", row[0] == 1, row)
        check("forwards_family 取 MAX，不被后来的 false 冲掉", row[1] == 1, row)
        check("seen_count 只涨到 2", row[2] == 2, row)

    print("== 升级出来的库必须和新装的库长成同一个样子 ==")
    # 这条替代了原来的「两份 DDL 逐字一致」。逐字比对在 v7 之后必然假红：
    # `identity_code.kind` 在新装时写在 CREATE 里、在升级时靠一条 ALTER 补，
    # 两份文本本来就该不一样。真正要守的是**最终形状**——它同样抓得住那一类错：
    # 谁在建表语句里加了列却忘了配 ALTER，升级库就少一列，这里立刻红。
    fresh = sqlite3.connect(":memory:")
    for name, body in created.items():
        fresh.execute("CREATE TABLE %s (%s)" % (name, body))
    for ddl in re.findall(r'"(CREATE (?:UNIQUE )?INDEX [^"]+)"', create_zone):
        fresh.execute(ddl.replace("IF NOT EXISTS ", ""))
    fresh.commit()

    def shape(connection):
        tables = [t for (t,) in connection.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
        )]
        return {
            t: sorted((row[1], row[2].upper()) for row in connection.execute("PRAGMA table_info(%s)" % t))
            for t in tables
        }

    upgraded, installed = shape(db), shape(fresh)
    check("表集合一致", sorted(upgraded) == sorted(installed),
          "%s vs %s" % (sorted(upgraded), sorted(installed)))
    for table in sorted(set(upgraded) & set(installed)):
        check("%s 的列一致" % table, upgraded[table] == installed[table],
              "升级库 %s / 新装库 %s" % ([c[0] for c in upgraded[table]], [c[0] for c in installed[table]]))

    print()
    if fails:
        print("破了 %d 条：%s" % (len(fails), "、".join(fails)))
        if ci:
            print("::error::库结构检查破了 %d 条" % len(fails))
        sys.exit(1)
    print("库结构全守住。")


main()
