package com.example.videoplayer.media

/**
 * 视频集名称智能归并。
 *
 * 目标：
 * 1. 识别「第一季」「第二部」「Season 2」这类明确分开的标识 → 不参与合并
 * 2. 剥离「1-7集」「第01集」这类集数后缀 → 提取出真正的公共部分
 * 3. 公共部分相同的文件归为同一视频集（如「咕咕嘎嘎 1-7集」+「咕咕嘎嘎8-10集」→「咕咕嘎嘎」）
 *
 * 所有归并建议都会经用户确认后才会生效。
 */
object NameMerger {

    /** 归并分组：memberIndices 为待导入项的下标 */
    data class MergeGroup(
        val memberIndices: List<Int>,
        val suggestedName: String
    )

    /**
     * 明确的「分开标识」：含有这些的名称视为独立作品，不参与自动合并。
     */
    private val SEPARATOR_PATTERNS = listOf(
        // 第一季 / 第二部 / 第3篇章
        Regex("第\\s*[一二三四五六七八九十百零〇0-9]+\\s*[季部篇章]"),
        // Season 1 / SEASON2
        Regex("(?i)season\\s*\\d+"),
        // Part 1 / part2
        Regex("(?i)part\\s*\\d+"),
        // S01 / S2
        Regex("(?i)\\bs\\d{1,2}\\b"),
        // 剧场版 / 番外 / 特别篇 / 总集篇
        Regex("剧场版|番外篇?|总集篇|特别篇|导演剪辑版"),
        // OVA / OAD / SP
        Regex("(?i)\\bova\\b|\\boad\\b|\\bsp\\b"),
        // 上部 / 下部 / 前篇 / 后篇
        Regex("上部|下部|前篇|后篇|上卷|下卷")
    )

    /** 集数范围后缀：1-7集 / 01-03 / 第1-7话 / 1~5集 */
    private val RANGE_SUFFIX = Regex(
        "\\s*[第]?\\s*\\d+\\s*[-~－—–到至]\\s*\\d+\\s*[集话話]?\\s*$"
    )

    /** 单集后缀：第01集 / 01集 / 第5话 */
    private val SINGLE_SUFFIX = Regex(
        "\\s*[第]?\\s*\\d+\\s*[集话話]\\s*$"
    )

    /** 尾部残留的分隔符：空格 / - / _ 等 */
    private val TRAILING_SEP = Regex("[\\s\\-_—－–]+$")

    /** 是否含有明确的分开标识（不参与自动合并） */
    fun hasSeparatorMarker(name: String): Boolean =
        SEPARATOR_PATTERNS.any { it.containsMatchIn(name) }

    /**
     * 剥离集数后缀，得到基础名（即"公共部分"）。
     * 「咕咕嘎嘎 1-7集」→「咕咕嘎嘎」，「咕咕嘎嘎8-10集」→「咕咕嘎嘎」
     */
    fun baseNameOf(name: String): String {
        var result = name
        result = result.replace(RANGE_SUFFIX, "")
        result = result.replace(SINGLE_SUFFIX, "")
        result = result.replace(TRAILING_SEP, "")
        return result.trim()
    }

    /**
     * 生成归并建议（只返回成员数 ≥2 的分组）。
     */
    fun suggest(names: List<String>): List<MergeGroup> {
        if (names.size < 2) return emptyList()

        val groups = mutableListOf<MergeGroup>()
        val used = mutableSetOf<Int>()

        // 1. 精确归并：剥离集数后缀后基础名相同
        val buckets = linkedMapOf<String, MutableList<Int>>()
        names.forEachIndexed { index, name ->
            if (hasSeparatorMarker(name)) return@forEachIndexed
            val base = baseNameOf(name)
            if (base.isBlank()) return@forEachIndexed
            buckets.getOrPut(base) { mutableListOf() }.add(index)
        }
        buckets.filter { it.value.size >= 2 }.forEach { (base, indices) ->
            val sorted = indices.sorted()
            groups.add(MergeGroup(sorted, base))
            used.addAll(sorted)
        }

        // 2. 兜底：对剩余项尝试公共前缀（要求前缀足够显著，避免误并）
        val rest = names.indices.filter {
            it !in used && !hasSeparatorMarker(names[it]) && baseNameOf(names[it]).isNotBlank()
        }
        if (rest.size >= 2) {
            val bases = rest.map { baseNameOf(names[it]) }
            val prefix = bases.reduce { acc, s -> commonPrefix(acc, s) }
            val minLen = bases.minOf { it.length }
            if (prefix.length >= 2 && prefix.length >= minLen * 0.6) {
                groups.add(MergeGroup(rest, prefix.trim()))
            }
        }

        return groups
    }

    /** 最长公共前缀 */
    fun commonPrefix(a: String, b: String): String {
        val n = minOf(a.length, b.length)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        return a.substring(0, i)
    }
}
