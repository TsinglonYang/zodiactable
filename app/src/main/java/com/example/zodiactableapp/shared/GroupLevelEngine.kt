package com.example.zodiactableapp.shared

import com.example.zodiactableapp.model.Zodiac
import kotlin.math.min

/**
 * 495 个小组等级推进引擎（纯业务类，不依赖 Android）
 *
 * 等级范围：1..15（对应金额表的 15 档）。
 * 规则（当日开奖为 X）：
 * - 若【该小组包含 X】且【该小组属于已投注/已激活集合】 => level = 1
 * - 否则 level = min(level + 1, 15)，到顶后保持 15 级
 */
class GroupLevelEngine(
    private val groupMembers: List<Set<Zodiac>>
) {
    companion object {
        const val GROUP_COUNT = 495
        const val MIN_LEVEL = 1
        const val MAX_LEVEL = 15
    }

    /** 推进一天后的完整等级状态 */
    class LevelState(
        val levels: IntArray
    )

    /** 内部状态（如果你用内部推进就用它）；按日期回算也会用 evolveOneDay() */
    private val levels: IntArray = IntArray(GROUP_COUNT) { MIN_LEVEL }

    /** 直接推进一天（会改动内部状态） */
    fun onNewDay(draw: Zodiac, todayBetGroupIds: Set<Int>) {
        val active = BooleanArray(GROUP_COUNT) { false }
        todayBetGroupIds.forEach { gid ->
            if (gid in 0 until GROUP_COUNT) active[gid] = true
        }
        val next = evolveOneDay(levels, active, draw)
        for (i in levels.indices) {
            levels[i] = next.levels[i]
        }
    }

    /**
     * 核心：给定“某天开始前的 levels + activeBet + draw”，返回推进后的状态。
     * ✅ 不依赖内部状态，方便在 ViewModel 里按日期回算历史。
     */
    fun evolveOneDay(
        currentLevels: IntArray,
        activeBet: BooleanArray,
        draw: Zodiac
    ): LevelState {
        val outLevels = IntArray(GROUP_COUNT) { MIN_LEVEL }

        for (gid in 0 until GROUP_COUNT) {
            val contains = groupMembers[gid].contains(draw)
            val isActive = activeBet[gid]

            outLevels[gid] = when {
                // 命中当日生肖且该小组已激活：降到 1 级
                contains && isActive -> MIN_LEVEL
                // 逐级上升，到最高等级后保持
                else -> min(currentLevels[gid] + 1, MAX_LEVEL)
            }
        }
        return LevelState(outLevels)
    }

    fun getLevel(groupId: Int): Int {
        require(groupId in 0 until GROUP_COUNT) { "Invalid groupId=$groupId" }
        return levels[groupId]
    }

    fun setLevel(groupId: Int, level: Int) {
        require(groupId in 0 until GROUP_COUNT) { "Invalid groupId=$groupId" }
        require(level in MIN_LEVEL..MAX_LEVEL) { "Invalid level=$level" }
        levels[groupId] = level
    }

    fun resetAllTo1() {
        levels.fill(MIN_LEVEL)
    }
}
