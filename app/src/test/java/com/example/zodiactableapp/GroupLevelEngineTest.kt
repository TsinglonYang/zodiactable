package com.example.zodiactableapp.shared

import com.example.zodiactableapp.model.Zodiac
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupLevelEngineTest {

    private val mouse = Zodiac.鼠
    private val cow = Zodiac.牛

    private fun engineWithFirstGroup(members: Set<Zodiac>): GroupLevelEngine {
        val groups = MutableList<Set<Zodiac>>(GroupLevelEngine.GROUP_COUNT) { emptySet() }
        groups[0] = members
        return GroupLevelEngine(groups)
    }

    private fun state(level: Int, fixedAtFive: Boolean = false): GroupLevelEngine.LevelState {
        val levels = IntArray(GroupLevelEngine.GROUP_COUNT) { GroupLevelEngine.MIN_LEVEL }
        levels[0] = level
        val fixed = BooleanArray(GroupLevelEngine.GROUP_COUNT)
        fixed[0] = fixedAtFive
        return GroupLevelEngine.LevelState(levels, fixed)
    }

    private fun firstActive(): BooleanArray =
        BooleanArray(GroupLevelEngine.GROUP_COUNT) { it == 0 }

    private fun noneActive(): BooleanArray =
        BooleanArray(GroupLevelEngine.GROUP_COUNT)

    private fun GroupLevelEngine.step(
        s: GroupLevelEngine.LevelState,
        active: BooleanArray,
        draw: Zodiac
    ): GroupLevelEngine.LevelState = evolveOneDay(s.levels, s.fixedAtFive, active, draw)

    @Test
    fun normalProgressionStopsAtNine() {
        val engine = engineWithFirstGroup(setOf(mouse))
        var s = state(1)

        repeat(8) { s = engine.step(s, noneActive(), cow) }

        assertEquals(GroupLevelEngine.MAX_LEVEL, s.levels[0])
        assertFalse(s.fixedAtFive[0])
    }

    @Test
    fun upgradingFromNineFixesAtFive() {
        val engine = engineWithFirstGroup(setOf(mouse))
        var s = state(GroupLevelEngine.MAX_LEVEL)

        s = engine.step(s, noneActive(), cow)

        assertEquals(GroupLevelEngine.FIXED_LEVEL, s.levels[0])
        assertTrue(s.fixedAtFive[0])
    }

    @Test
    fun fixedFiveStaysFiveUntilHit() {
        val engine = engineWithFirstGroup(setOf(mouse))
        var s = state(GroupLevelEngine.FIXED_LEVEL, fixedAtFive = true)

        repeat(10) { s = engine.step(s, firstActive(), cow) }

        assertEquals(GroupLevelEngine.FIXED_LEVEL, s.levels[0])
        assertTrue(s.fixedAtFive[0])
    }

    @Test
    fun hitResetsFixedFiveToOne() {
        val engine = engineWithFirstGroup(setOf(mouse))
        var s = state(GroupLevelEngine.FIXED_LEVEL, fixedAtFive = true)

        s = engine.step(s, firstActive(), mouse)

        assertEquals(GroupLevelEngine.MIN_LEVEL, s.levels[0])
        assertFalse(s.fixedAtFive[0])
    }

    @Test
    fun hitAtNineResetsToOneInsteadOfFive() {
        val engine = engineWithFirstGroup(setOf(mouse))
        var s = state(GroupLevelEngine.MAX_LEVEL)

        s = engine.step(s, firstActive(), mouse)

        assertEquals(GroupLevelEngine.MIN_LEVEL, s.levels[0])
        assertFalse(s.fixedAtFive[0])
    }

    @Test
    fun afterResetNormalProgressionResumes() {
        val engine = engineWithFirstGroup(setOf(mouse))
        var s = state(GroupLevelEngine.FIXED_LEVEL, fixedAtFive = true)

        s = engine.step(s, firstActive(), mouse)
        s = engine.step(s, noneActive(), cow)

        assertEquals(2, s.levels[0])
        assertFalse(s.fixedAtFive[0])
    }
}
