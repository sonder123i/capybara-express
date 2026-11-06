package dev.showcode.data

import dev.showcode.core.IdentityCode
import dev.showcode.core.IdentityKind

/**
 * 演示身份码的三个写操作。只接收「演示列表」这一个参数——它没有仓库、没有 DB、
 * 也没有任何真实表的引用，所以不可能把演示操作写进真实库。
 *
 * 抽出来是为了单测能直接断言：新增 / 切换动态 / 删除只改传入的列表。
 */
object DemoIdentity {

    fun seed(now: Long): List<IdentityCode> = DemoData.identityCodes(now)

    fun add(
        codes: MutableList<IdentityCode>,
        label: String,
        value: String,
        kind: IdentityKind,
        dynamic: Boolean?,
        note: String?,
        appPackage: String?,
        now: Long,
    ): Boolean {
        if (dynamic == null) return false
        codes.add(
            IdentityCode(
                id = -(1000L + codes.size),
                label = label,
                value = value,
                kind = kind,
                dynamic = dynamic,
                note = note,
                appPackage = appPackage,
                createdAt = now,
            ),
        )
        return true
    }

    fun setDynamic(codes: MutableList<IdentityCode>, id: Long, dynamic: Boolean) {
        val index = codes.indexOfFirst { it.id == id }
        if (index >= 0) codes[index] = codes[index].copy(dynamic = dynamic)
    }

    fun delete(codes: MutableList<IdentityCode>, id: Long) {
        codes.removeAll { it.id == id }
    }
}
