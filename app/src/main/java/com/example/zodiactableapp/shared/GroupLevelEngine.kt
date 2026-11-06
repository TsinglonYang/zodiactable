package com.example.zodiactableapp.shared

import com.example.zodiactableapp.model.Zodiac
import kotlin.math.min

/**
 * 495 个小组等级推进引擎（纯业务类，不依赖 Android）
 *
 * 等级范围：1..9。
 * 规则（当日开奖为 X）：
 * - 若【该小组包含 X】且【该小组属于已投注/已激活集合】 => level = 1（同时解除“固定 5 级”）
 * - 若该小组已处于“固定 5 级”状态 => 保持 5 级
 * - 否则若 level == 9（需要再升级） => level = 5，并进入“固定 5 级”状态
 * - 否则 level = min(level + 1, 9)
 */
class GroupLevelEngine(
    private val groupMembers: List<Set<Zodiac>>
) {
    companion object {
        const val GROUP_COUNT = 495
        const val MIN_LEVEL = 1
        const val MAX_LEVEL = 9
        const val FIXED_LEVEL = 5
    }

    /** 推进一天后的完整等级状态 */
    class LevelState(
        val levels: IntArray,
        val fixedAtFive: BooleanArray
    )

    /** 内部状态（如果你用内部推进就用它）；按日期回算也会用 evolveOneDay() */
    private val levels: IntArray = IntArray(GROUP_COUNT) { MIN_LEVEL }
    private val fixedAtFive: BooleanArray = BooleanArray(GROUP_COUNT)

    /** 直接推进一天（会改动内部状态） */
    fun onNewDay(draw: Zodiac, todayBetGroupIds: Set<Int>) {
        val active = BooleanArray(GROUP_COUNT) { false }
        todayBetGroupIds.forEach { gid ->
            if (gid in 0 until GROUP_COUNT) active[gid] = true
        }
        val next = evolveOneDay(levels, fixedAtFive, active, draw)
        for (i in levels.indices) {
            levels[i] = next.levels[i]
            fixedAtFive[i] = next.fixedAtFive[i]
        }
    }

    /**
     * 核心：给定“某天开始前的 levels + 固定状态 + activeBet + draw”，返回推进后的状态。
     * ✅ 不依赖内部状态，方便在 ViewModel 里按日期回算历史。
     */
    fun evolveOneDay(
        currentLevels: IntArray,
        currentFixedAtFive: BooleanArray,
        activeBet: BooleanArray,
        draw: Zodiac
    ): LevelState {
        val outLevels = IntArray(GROUP_COUNT) { MIN_LEVEL }
        val outFixed = BooleanArray(GROUP_COUNT)

        for (gid in 0 until GROUP_COUNT) {
            val contains = groupMembers[gid].contains(draw)
            val isActive = activeBet[gid]

            when {
                // 命中当日生肖且该小组已激活：降到 1 级，解除固定
                contains && isActive -> {
                    outLevels[gid] = MIN_LEVEL
                    outFixed[gid] = false
                }
                // 已固定：保持 5 级，直到命中条件把它降到 1
                currentFixedAtFive[gid] -> {
                    outLevels[gid] = FIXED_LEVEL
                    outFixed[gid] = true
                }
                // 9 级再升级：变成固定 5 级
                currentLevels[gid] >= MAX_LEVEL -> {
                    outLevels[gid] = FIXED_LEVEL
                    outFixed[gid] = true
                }
                // 正常升级
                else -> {
                    outLevels[gid] = min(currentLevels[gid] + 1, MAX_LEVEL)
                    outFixed[gid] = false
                }
            }
        }
        return LevelState(outLevels, outFixed)
    }

    fun getLevel(groupId: Int): Int {
        require(groupId in 0 until GROUP_COUNT) { "Invalid groupId=$groupId" }
        return levels[groupId]
    }

    fun setLevel(groupId: Int, level: Int) {
        require(groupId in 0 until GROUP_COUNT) { "Invalid groupId=$groupId" }
        require(level in MIN_LEVEL..MAX_LEVEL) { "Invalid level=$level" }
        levels[groupId] = level
        fixedAtFive[groupId] = false
    }

    fun resetAllTo1() {
        for (i in levels.indices) {
            levels[i] = MIN_LEVEL
            fixedAtFive[i] = false
        }
    }
}
