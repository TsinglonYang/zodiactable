package com.example.zodiactableapp.model

/**
 * 12 组生肖号码对照表（硬编码，号码已打散）。
 *
 * 规则：
 * - 每组覆盖 1..49，且恰好一个生肖占 5 个号（其余各 4 个）；
 * - 跨组之间，同一个生肖拿到的号码完全不重复；
 * - 12 组中每个生肖恰好占 5 号一次（鼠→牛→…→猪 各一次）；
 * - 组1（鼠占5号）为默认展示组。
 */
object ZodiacNumberTables {

    /** 默认展示用的组：组1（鼠占5号） */
    const val DEFAULT_GROUP = 0

    /** 12 组对照，每组为一个 生肖 -> 号码列表 的映射 */
    val groups: List<Map<Zodiac, List<Int>>> = generateGroups()

    fun numbersFor(zodiac: Zodiac, group: Int = DEFAULT_GROUP): List<Int> =
        groups[group][zodiac] ?: emptyList()

    /**
     * 档案(应用)编号 -> 组索引 的固定映射。
     * 档案1 = 组1，档案2 = 组2，……，档案12 = 组12。
     */
    fun groupIndexForProfile(profileId: Int): Int = profileId - 1

    /** 号码 -> 生肖（按指定组反向查表） */
    fun zodiacForNumber(number: Int, group: Int = DEFAULT_GROUP): Zodiac =
        groups[group].entries.firstOrNull { number in it.value }?.key ?: Zodiac.values()[0]

    private fun generateGroups(): List<Map<Zodiac, List<Int>>> = listOf(
        // 组1
        mapOf(
            Zodiac.鼠 to listOf(1, 14, 28, 33, 35),
            Zodiac.牛 to listOf(41, 42, 43, 48),
            Zodiac.虎 to listOf(15, 20, 23, 24),
            Zodiac.兔 to listOf(12, 17, 25, 44),
            Zodiac.龙 to listOf(21, 26, 29, 36),
            Zodiac.蛇 to listOf(30, 31, 34, 37),
            Zodiac.马 to listOf(3, 13, 38, 46),
            Zodiac.羊 to listOf(2, 10, 39, 49),
            Zodiac.猴 to listOf(4, 7, 22, 47),
            Zodiac.鸡 to listOf(6, 11, 19, 32),
            Zodiac.狗 to listOf(5, 8, 16, 45),
            Zodiac.猪 to listOf(9, 18, 27, 40)
        ),
        // 组2
        mapOf(
            Zodiac.鼠 to listOf(3, 6, 19, 38),
            Zodiac.牛 to listOf(2, 9, 34, 37, 39),
            Zodiac.虎 to listOf(4, 7, 11, 12),
            Zodiac.兔 to listOf(14, 16, 21, 47),
            Zodiac.龙 to listOf(8, 27, 40, 43),
            Zodiac.蛇 to listOf(13, 28, 36, 41),
            Zodiac.马 to listOf(15, 26, 33, 44),
            Zodiac.羊 to listOf(5, 20, 23, 48),
            Zodiac.猴 to listOf(17, 18, 45, 46),
            Zodiac.鸡 to listOf(1, 22, 30, 35),
            Zodiac.狗 to listOf(10, 31, 32, 49),
            Zodiac.猪 to listOf(24, 25, 29, 42)
        ),
        // 组3
        mapOf(
            Zodiac.鼠 to listOf(4, 25, 29, 48),
            Zodiac.牛 to listOf(5, 6, 8, 45),
            Zodiac.虎 to listOf(3, 30, 33, 34, 49),
            Zodiac.兔 to listOf(9, 27, 36, 40),
            Zodiac.龙 to listOf(11, 16, 17, 38),
            Zodiac.蛇 to listOf(14, 15, 35, 42),
            Zodiac.马 to listOf(1, 18, 23, 24),
            Zodiac.羊 to listOf(21, 28, 31, 32),
            Zodiac.猴 to listOf(10, 13, 43, 44),
            Zodiac.鸡 to listOf(2, 7, 20, 41),
            Zodiac.狗 to listOf(12, 22, 37, 47),
            Zodiac.猪 to listOf(19, 26, 39, 46)
        ),
        // 组4
        mapOf(
            Zodiac.鼠 to listOf(8, 17, 34, 43),
            Zodiac.牛 to listOf(3, 14, 21, 40),
            Zodiac.虎 to listOf(25, 26, 35, 48),
            Zodiac.兔 to listOf(4, 5, 7, 15, 42),
            Zodiac.龙 to listOf(6, 20, 33, 41),
            Zodiac.蛇 to listOf(1, 10, 12, 19),
            Zodiac.马 to listOf(9, 30, 31, 32),
            Zodiac.羊 to listOf(27, 36, 38, 45),
            Zodiac.猴 to listOf(16, 24, 29, 37),
            Zodiac.鸡 to listOf(13, 18, 28, 47),
            Zodiac.狗 to listOf(23, 39, 44, 46),
            Zodiac.猪 to listOf(2, 11, 22, 49)
        ),
        // 组5
        mapOf(
            Zodiac.鼠 to listOf(18, 24, 37, 47),
            Zodiac.牛 to listOf(15, 36, 46, 49),
            Zodiac.虎 to listOf(1, 14, 17, 22),
            Zodiac.兔 to listOf(19, 26, 28, 39),
            Zodiac.龙 to listOf(5, 23, 30, 34, 45),
            Zodiac.蛇 to listOf(4, 9, 25, 38),
            Zodiac.马 to listOf(2, 8, 27, 35),
            Zodiac.羊 to listOf(12, 13, 33, 44),
            Zodiac.猴 to listOf(3, 11, 32, 40),
            Zodiac.鸡 to listOf(16, 21, 43, 48),
            Zodiac.狗 to listOf(7, 20, 29, 42),
            Zodiac.猪 to listOf(6, 10, 31, 41)
        ),
        // 组6
        mapOf(
            Zodiac.鼠 to listOf(9, 32, 36, 39),
            Zodiac.牛 to listOf(1, 12, 35, 44),
            Zodiac.虎 to listOf(28, 31, 42, 47),
            Zodiac.兔 to listOf(2, 10, 45, 49),
            Zodiac.龙 to listOf(3, 13, 18, 24),
            Zodiac.蛇 to listOf(6, 21, 27, 29, 46),
            Zodiac.马 to listOf(22, 25, 40, 43),
            Zodiac.羊 to listOf(11, 14, 30, 41),
            Zodiac.猴 to listOf(8, 19, 23, 34),
            Zodiac.鸡 to listOf(4, 15, 37, 38),
            Zodiac.狗 to listOf(17, 26, 33, 48),
            Zodiac.猪 to listOf(5, 7, 16, 20)
        ),
        // 组7
        mapOf(
            Zodiac.鼠 to listOf(23, 42, 45, 46),
            Zodiac.牛 to listOf(4, 17, 19, 22),
            Zodiac.虎 to listOf(9, 16, 18, 41),
            Zodiac.兔 to listOf(6, 13, 31, 32),
            Zodiac.龙 to listOf(10, 12, 37, 47),
            Zodiac.蛇 to listOf(5, 24, 44, 49),
            Zodiac.马 to listOf(7, 11, 20, 29, 34),
            Zodiac.羊 to listOf(1, 26, 35, 40),
            Zodiac.猴 to listOf(15, 21, 30, 38),
            Zodiac.鸡 to listOf(8, 33, 36, 39),
            Zodiac.狗 to listOf(2, 25, 27, 28),
            Zodiac.猪 to listOf(3, 14, 43, 48)
        ),
        // 组8
        mapOf(
            Zodiac.鼠 to listOf(13, 31, 40, 44),
            Zodiac.牛 to listOf(23, 26, 28, 33),
            Zodiac.虎 to listOf(10, 29, 32, 39),
            Zodiac.兔 to listOf(18, 34, 35, 43),
            Zodiac.龙 to listOf(1, 2, 4, 19),
            Zodiac.蛇 to listOf(7, 11, 20, 22),
            Zodiac.马 to listOf(6, 12, 45, 49),
            Zodiac.羊 to listOf(8, 16, 17, 37, 47),
            Zodiac.猴 to listOf(25, 27, 42, 48),
            Zodiac.鸡 to listOf(5, 9, 24, 46),
            Zodiac.狗 to listOf(3, 14, 30, 41),
            Zodiac.猪 to listOf(15, 21, 36, 38)
        ),
        // 组9
        mapOf(
            Zodiac.鼠 to listOf(2, 27, 30, 41),
            Zodiac.牛 to listOf(7, 10, 13, 20),
            Zodiac.虎 to listOf(19, 36, 38, 45),
            Zodiac.兔 to listOf(3, 8, 11, 22),
            Zodiac.龙 to listOf(25, 35, 44, 48),
            Zodiac.蛇 to listOf(17, 18, 39, 40),
            Zodiac.马 to listOf(5, 16, 42, 47),
            Zodiac.羊 to listOf(4, 15, 29, 46),
            Zodiac.猴 to listOf(1, 6, 9, 14, 31),
            Zodiac.鸡 to listOf(12, 23, 26, 49),
            Zodiac.狗 to listOf(21, 24, 34, 43),
            Zodiac.猪 to listOf(28, 32, 33, 37)
        ),
        // 组10
        mapOf(
            Zodiac.鼠 to listOf(5, 11, 12, 26),
            Zodiac.牛 to listOf(16, 24, 29, 31),
            Zodiac.虎 to listOf(21, 27, 44, 46),
            Zodiac.兔 to listOf(1, 30, 38, 41),
            Zodiac.龙 to listOf(7, 14, 22, 49),
            Zodiac.蛇 to listOf(32, 43, 47, 48),
            Zodiac.马 to listOf(28, 36, 37, 39),
            Zodiac.羊 to listOf(18, 19, 25, 34),
            Zodiac.猴 to listOf(2, 20, 33, 35),
            Zodiac.鸡 to listOf(3, 10, 17, 42, 45),
            Zodiac.狗 to listOf(6, 9, 15, 40),
            Zodiac.猪 to listOf(4, 8, 13, 23)
        ),
        // 组11
        mapOf(
            Zodiac.鼠 to listOf(7, 15, 16, 22),
            Zodiac.牛 to listOf(18, 25, 32, 47),
            Zodiac.虎 to listOf(2, 5, 37, 40),
            Zodiac.兔 to listOf(20, 29, 33, 48),
            Zodiac.龙 to listOf(9, 39, 42, 46),
            Zodiac.蛇 to listOf(8, 23, 26, 45),
            Zodiac.马 to listOf(4, 10, 17, 21),
            Zodiac.羊 to listOf(3, 6, 24, 43),
            Zodiac.猴 to listOf(12, 28, 41, 49),
            Zodiac.鸡 to listOf(14, 27, 31, 44),
            Zodiac.狗 to listOf(11, 13, 19, 36, 38),
            Zodiac.猪 to listOf(1, 30, 34, 35)
        ),
        // 组12
        mapOf(
            Zodiac.鼠 to listOf(10, 20, 21, 49),
            Zodiac.牛 to listOf(11, 27, 30, 38),
            Zodiac.虎 to listOf(6, 8, 13, 43),
            Zodiac.兔 to listOf(23, 24, 37, 46),
            Zodiac.龙 to listOf(15, 28, 31, 32),
            Zodiac.蛇 to listOf(2, 3, 16, 33),
            Zodiac.马 to listOf(14, 19, 41, 48),
            Zodiac.羊 to listOf(7, 9, 22, 42),
            Zodiac.猴 to listOf(5, 26, 36, 39),
            Zodiac.鸡 to listOf(25, 29, 34, 40),
            Zodiac.狗 to listOf(1, 4, 18, 35),
            Zodiac.猪 to listOf(12, 17, 44, 45, 47)
        )
    )
}
