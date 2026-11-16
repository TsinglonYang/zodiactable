package com.example.zodiactableapp.shared

import com.example.zodiactableapp.model.Zodiac
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupLevelEngineTest {

    private val mouse = Zodiac.鼠
    private val cow = Zodiac.牛

    private fun engineWithFirstGroup(members: Set<Zodiac>): GroupLevelEngine {
        val groups = MutableList<Set<Zodiac>>(GroupLevelEngine.GROUP_COUNT) { emptySet() }
        groups[0] = members
        return GroupLevelEngine(groups)
    }

    private fun state(level: Int): GroupLevelEngine.LevelState {
        val levels = IntArray(GroupLevelEngine.GROUP_COUNT) { GroupLevelEngine.MIN_LEVEL }
        levels[0] = level
        return GroupLevelEngine.LevelState(levels)
    }

    private fun firstActive(): BooleanArray =
        BooleanArray(GroupLevelEngine.GROUP_COUNT) { it == 0 }

    private fun noneActive(): BooleanArray =
        BooleanArray(GroupLevelEngine.GROUP_COUNT)

    private fun GroupLevelEngine.step(
        s: GroupLevelEngine.LevelState,
        active: BooleanArray,
        draw: Zodiac
    ): GroupLevelEngine.LevelState = evolveOneDay(s.levels, active, draw)

    @Test
    fun normalProgressionRisesByOne() {
        val engine = engineWithFirstGroup(setOf(mouse))

        val s = engine.step(state(1), noneActive(), cow)

        assertEquals(2, s.levels[0])
    }

    @Test
    fun normalProgressionReachesMax() {
        val engine = engineWithFirstGroup(setOf(mouse))
        var s = state(1)

        repeat(GroupLevelEngine.MAX_LEVEL - 1) { s = engine.step(s, noneActive(), cow) }

        assertEquals(GroupLevelEngine.MAX_LEVEL, s.levels[0])
    }

    @Test
    fun maxLevelStaysAtMaxNextDay() {
        val engine = engineWithFirstGroup(setOf(mouse))
        var s = state(GroupLevelEngine.MAX_LEVEL)

        s = engine.step(s, noneActive(), cow)

        assertEquals(GroupLevelEngine.MAX_LEVEL, s.levels[0])
    }

    @Test
    fun hitResetsLevelToOne() {
        val engine = engineWithFirstGroup(setOf(mouse))
        var s = state(10)

        s = engine.step(s, firstActive(), mouse)

        assertEquals(GroupLevelEngine.MIN_LEVEL, s.levels[0])
    }

    @Test
    fun hitAtMaxResetsToOne() {
        val engine = engineWithFirstGroup(setOf(mouse))
        var s = state(GroupLevelEngine.MAX_LEVEL)

        s = engine.step(s, firstActive(), mouse)

        assertEquals(GroupLevelEngine.MIN_LEVEL, s.levels[0])
    }

    @Test
    fun afterResetNormalProgressionResumes() {
        val engine = engineWithFirstGroup(setOf(mouse))
        var s = state(GroupLevelEngine.MAX_LEVEL)

        s = engine.step(s, firstActive(), mouse)
        assertEquals(GroupLevelEngine.MIN_LEVEL, s.levels[0])

        s = engine.step(s, noneActive(), cow)
        assertEquals(2, s.levels[0])
    }
}
