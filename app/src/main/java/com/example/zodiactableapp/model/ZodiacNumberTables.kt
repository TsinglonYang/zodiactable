package com.example.zodiactableapp.model

/**
 * 12 组生肖号码对照表（硬编码，号码已打散）。
 *
 * 规则：
 * - 每组覆盖 1..49，且恰好一个生肖占 5 个号（其余各 4 个）；
 * - 跨组之间，同一个生肖拿到的号码完全不重复；
 * - 12 组中每个生肖恰好占 5 号一次（鼠→牛→…→猪 各一次）；
 * - 组7（马占5号）为原始固定映射。
 */
object ZodiacNumberTables {

    /** 默认展示用的组：组7（马占5号），即原先写死的那套固定映射 */
    const val DEFAULT_GROUP = 6

    /** 11 组对照，每组为一个 生肖 -> 号码列表 的映射 */
    val groups: List<Map<Zodiac, List<Int>>> = generateGroups()

    fun numbersFor(zodiac: Zodiac, group: Int = DEFAULT_GROUP): List<Int> =
        groups[group][zodiac] ?: emptyList()

    /**
     * 档案(应用)编号 -> 组索引 的固定映射。
     * 档案1 = 组7(马，原来那套)；档案2..7 = 组1..组6；档案8..12 = 组8..组12。
     */
    fun groupIndexForProfile(profileId: Int): Int = when {
        profileId <= 1 -> 6
        profileId <= 7 -> profileId - 2
        else -> profileId - 1
    }

    /** 号码 -> 生肖（按指定组反向查表） */
    fun zodiacForNumber(number: Int, group: Int = DEFAULT_GROUP): Zodiac =
        groups[group].entries.firstOrNull { number in it.value }?.key ?: Zodiac.values()[0]

    private fun generateGroups(): List<Map<Zodiac, List<Int>>> = listOf(
        // 组1
        mapOf(
            Zodiac.鼠 to listOf(11, 14, 28, 35, 37),
            Zodiac.牛 to listOf(3, 12, 25, 43),
            Zodiac.虎 to listOf(4, 10, 16, 40),
            Zodiac.兔 to listOf(7, 20, 41, 49),
            Zodiac.龙 to listOf(29, 38, 42, 44),
            Zodiac.蛇 to listOf(15, 24, 27, 47),
            Zodiac.马 to listOf(2, 5, 19, 46),
            Zodiac.羊 to listOf(8, 13, 31, 39),
            Zodiac.猴 to listOf(21, 32, 36, 45),
            Zodiac.鸡 to listOf(18, 23, 33, 48),
            Zodiac.狗 to listOf(6, 17, 22, 30),
            Zodiac.猪 to listOf(1, 9, 26, 34)
        ),
        // 组2
        mapOf(
            Zodiac.鼠 to listOf(2, 6, 30, 32),
            Zodiac.牛 to listOf(4, 10, 14, 20, 35),
            Zodiac.虎 to listOf(12, 37, 38, 44),
            Zodiac.兔 to listOf(1, 5, 31, 43),
            Zodiac.龙 to listOf(8, 19, 21, 22),
            Zodiac.蛇 to listOf(3, 34, 46, 49),
            Zodiac.马 to listOf(23, 33, 39, 48),
            Zodiac.羊 to listOf(16, 26, 28, 45),
            Zodiac.猴 to listOf(18, 24, 25, 41),
            Zodiac.鸡 to listOf(9, 13, 15, 40),
            Zodiac.狗 to listOf(27, 29, 42, 47),
            Zodiac.猪 to listOf(7, 11, 17, 36)
        ),
        // 组3
        mapOf(
            Zodiac.鼠 to listOf(3, 5, 25, 44),
            Zodiac.牛 to listOf(1, 8, 27, 34),
            Zodiac.虎 to listOf(7, 11, 23, 32, 39),
            Zodiac.兔 to listOf(12, 21, 22, 29),
            Zodiac.龙 to listOf(10, 33, 36, 47),
            Zodiac.蛇 to listOf(16, 30, 37, 48),
            Zodiac.马 to listOf(6, 17, 18, 40),
            Zodiac.羊 to listOf(2, 15, 38, 49),
            Zodiac.猴 to listOf(9, 13, 26, 42),
            Zodiac.鸡 to listOf(19, 20, 24, 41),
            Zodiac.狗 to listOf(28, 31, 43, 46),
            Zodiac.猪 to listOf(4, 14, 35, 45)
        ),
        // 组4
        mapOf(
            Zodiac.鼠 to listOf(13, 16, 17, 21),
            Zodiac.牛 to listOf(5, 31, 45, 49),
            Zodiac.虎 to listOf(2, 18, 20, 35),
            Zodiac.兔 to listOf(3, 9, 14, 33, 34),
            Zodiac.龙 to listOf(4, 6, 12, 24),
            Zodiac.蛇 to listOf(1, 10, 39, 40),
            Zodiac.马 to listOf(11, 15, 36, 41),
            Zodiac.羊 to listOf(19, 27, 32, 44),
            Zodiac.猴 to listOf(7, 29, 38, 46),
            Zodiac.鸡 to listOf(25, 28, 42, 47),
            Zodiac.狗 to listOf(8, 23, 26, 37),
            Zodiac.猪 to listOf(22, 30, 43, 48)
        ),
        // 组5
        mapOf(
            Zodiac.鼠 to listOf(4, 8, 46, 48),
            Zodiac.牛 to listOf(2, 33, 36, 41),
            Zodiac.虎 to listOf(3, 9, 24, 47),
            Zodiac.兔 to listOf(13, 15, 18, 39),
            Zodiac.龙 to listOf(1, 28, 31, 34, 37),
            Zodiac.蛇 to listOf(11, 23, 44, 45),
            Zodiac.马 to listOf(14, 32, 42, 43),
            Zodiac.羊 to listOf(5, 10, 17, 30),
            Zodiac.猴 to listOf(20, 22, 27, 49),
            Zodiac.鸡 to listOf(21, 26, 29, 35),
            Zodiac.狗 to listOf(7, 12, 16, 38),
            Zodiac.猪 to listOf(6, 19, 25, 40)
        ),
        // 组6
        mapOf(
            Zodiac.鼠 to listOf(9, 18, 22, 42),
            Zodiac.牛 to listOf(17, 24, 26, 29),
            Zodiac.虎 to listOf(30, 34, 43, 48),
            Zodiac.兔 to listOf(6, 25, 27, 44),
            Zodiac.龙 to listOf(2, 5, 11, 49),
            Zodiac.蛇 to listOf(8, 13, 20, 28, 41),
            Zodiac.马 to listOf(10, 16, 38, 45),
            Zodiac.羊 to listOf(7, 14, 23, 46),
            Zodiac.猴 to listOf(3, 19, 33, 39),
            Zodiac.鸡 to listOf(31, 32, 36, 37),
            Zodiac.狗 to listOf(1, 4, 35, 40),
            Zodiac.猪 to listOf(12, 15, 21, 47)
        ),
        // 组7
        mapOf(
            Zodiac.鼠 to listOf(7, 19, 31, 43),
            Zodiac.牛 to listOf(6, 18, 30, 42),
            Zodiac.虎 to listOf(5, 17, 29, 41),
            Zodiac.兔 to listOf(4, 16, 28, 40),
            Zodiac.龙 to listOf(3, 15, 27, 39),
            Zodiac.蛇 to listOf(2, 14, 26, 38),
            Zodiac.马 to listOf(1, 13, 25, 37, 49),
            Zodiac.羊 to listOf(12, 24, 36, 48),
            Zodiac.猴 to listOf(11, 23, 35, 47),
            Zodiac.鸡 to listOf(10, 22, 34, 46),
            Zodiac.狗 to listOf(9, 21, 33, 45),
            Zodiac.猪 to listOf(8, 20, 32, 44)
        ),
        // 组8
        mapOf(
            Zodiac.鼠 to listOf(27, 40, 45, 49),
            Zodiac.牛 to listOf(7, 9, 21, 44),
            Zodiac.虎 to listOf(14, 22, 26, 31),
            Zodiac.兔 to listOf(17, 19, 35, 47),
            Zodiac.龙 to listOf(18, 23, 46, 48),
            Zodiac.蛇 to listOf(5, 25, 32, 33),
            Zodiac.马 to listOf(3, 8, 12, 30),
            Zodiac.羊 to listOf(4, 29, 34, 37, 41),
            Zodiac.猴 to listOf(1, 6, 28, 43),
            Zodiac.鸡 to listOf(2, 11, 38, 39),
            Zodiac.狗 to listOf(10, 15, 20, 36),
            Zodiac.猪 to listOf(13, 16, 24, 42)
        ),
        // 组9
        mapOf(
            Zodiac.鼠 to listOf(12, 23, 26, 39),
            Zodiac.牛 to listOf(13, 19, 37, 47),
            Zodiac.虎 to listOf(8, 15, 36, 46),
            Zodiac.兔 to listOf(11, 24, 38, 45),
            Zodiac.龙 to listOf(14, 16, 25, 32),
            Zodiac.蛇 to listOf(9, 29, 31, 42),
            Zodiac.马 to listOf(7, 22, 28, 35),
            Zodiac.羊 to listOf(6, 20, 21, 43),
            Zodiac.猴 to listOf(2, 4, 10, 40, 44),
            Zodiac.鸡 to listOf(1, 17, 30, 49),
            Zodiac.狗 to listOf(5, 18, 34, 48),
            Zodiac.猪 to listOf(3, 27, 33, 41)
        ),
        // 组10
        mapOf(
            Zodiac.鼠 to listOf(20, 29, 34, 41),
            Zodiac.牛 to listOf(16, 28, 32, 38),
            Zodiac.虎 to listOf(21, 27, 33, 42),
            Zodiac.兔 to listOf(10, 23, 46, 48),
            Zodiac.龙 to listOf(7, 13, 30, 40),
            Zodiac.蛇 to listOf(6, 12, 17, 36),
            Zodiac.马 to listOf(4, 9, 26, 31),
            Zodiac.羊 to listOf(1, 22, 35, 47),
            Zodiac.猴 to listOf(8, 14, 15, 37),
            Zodiac.鸡 to listOf(3, 5, 43, 44, 45),
            Zodiac.狗 to listOf(11, 19, 24, 25),
            Zodiac.猪 to listOf(2, 18, 39, 49)
        ),
        // 组11
        mapOf(
            Zodiac.鼠 to listOf(10, 15, 24, 38),
            Zodiac.牛 to listOf(11, 23, 40, 48),
            Zodiac.虎 to listOf(1, 19, 25, 28),
            Zodiac.兔 to listOf(8, 26, 36, 37),
            Zodiac.龙 to listOf(17, 35, 41, 45),
            Zodiac.蛇 to listOf(18, 21, 22, 43),
            Zodiac.马 to listOf(20, 27, 44, 47),
            Zodiac.羊 to listOf(3, 9, 33, 42),
            Zodiac.猴 to listOf(12, 16, 30, 34),
            Zodiac.鸡 to listOf(4, 6, 7, 14),
            Zodiac.狗 to listOf(2, 13, 32, 39, 49),
            Zodiac.猪 to listOf(5, 29, 31, 46)
        ),
        // 组12
        mapOf(
            Zodiac.鼠 to listOf(1, 33, 36, 47),
            Zodiac.牛 to listOf(15, 22, 39, 46),
            Zodiac.虎 to listOf(6, 13, 45, 49),
            Zodiac.兔 to listOf(2, 30, 32, 42),
            Zodiac.龙 to listOf(9, 20, 26, 43),
            Zodiac.蛇 to listOf(4, 7, 19, 35),
            Zodiac.马 to listOf(21, 24, 29, 34),
            Zodiac.羊 to listOf(11, 18, 25, 40),
            Zodiac.猴 to listOf(5, 17, 31, 48),
            Zodiac.鸡 to listOf(8, 12, 16, 27),
            Zodiac.狗 to listOf(3, 14, 41, 44),
            Zodiac.猪 to listOf(10, 23, 28, 37, 38)
        )
    )
}
