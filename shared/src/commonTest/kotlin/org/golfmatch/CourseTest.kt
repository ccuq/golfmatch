package org.golfmatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourseTest {

    private fun List<PlayedHole>.labels() = map { "${it.loopId}${it.number}" }

    @Test
    fun parcoursValides() {
        assertEquals(emptyList(), Fixtures.compact.validate())
        assertEquals(emptyList(), Fixtures.nine.validate())
        assertEquals(emptyList(), Fixtures.eighteen.validate())
    }

    @Test
    fun classementEnDouble() {
        val broken = Fixtures.compact.copy(
            loops = listOf(Fixtures.red, Fixtures.yellow.copy(holes = Fixtures.yellow.holes.map { it.copy(strokeIndex = 1) })),
        )
        assertTrue(broken.validate().any { it.contains("plusieurs trous") })
    }

    @Test
    fun shotgunRougePuisJaune() {
        val holes = Fixtures.compact.playedHoles(RoundPlan(listOf("R", "J"), startHole = 3))
        assertEquals(
            listOf("R3", "R4", "R5", "R6", "R7", "R8", "R9", "R1", "R2", "J3", "J4", "J5", "J6", "J7", "J8", "J9", "J1", "J2"),
            holes.labels(),
        )
        assertEquals(listOf(0, 1), holes.map { it.tour }.distinct())
        // Deux boucles différentes : classement sur 18 conservé.
        assertEquals(1, holes.first { it.loopId == "R" && it.number == 3 }.strokeIndex)
        assertEquals(2, holes.first { it.loopId == "J" && it.number == 3 }.strokeIndex)
    }

    @Test
    fun memeBoucleDeuxFois() {
        val holes = Fixtures.compact.playedHoles(RoundPlan(listOf("R", "R"), startHole = 1))
        // Rouge reclassée de 1 à 9 : trou 3 (SI 1) → 1, trou 5 (SI 17) → 9.
        val first = holes.filter { it.tour == 0 }.associate { it.number to it.strokeIndex }
        val second = holes.filter { it.tour == 1 }.associate { it.number to it.strokeIndex }
        assertEquals(1, first[3]); assertEquals(2, second[3])
        assertEquals(17, first[5]); assertEquals(18, second[5])
        assertEquals((1..18).toList(), holes.map { it.strokeIndex }.sorted())
    }

    @Test
    fun neufTrousSeul() {
        val holes = Fixtures.compact.playedHoles(RoundPlan(listOf("J"), startHole = 8))
        assertEquals(listOf("J8", "J9", "J1", "J2", "J3", "J4", "J5", "J6", "J7"), holes.labels())
        assertEquals((1..9).toList(), holes.map { it.strokeIndex }.sorted())
    }

    @Test
    fun parcoursNeufJoueDeuxFois() {
        val holes = Fixtures.nine.playedHoles(RoundPlan(listOf("L", "L")))
        assertEquals(18, holes.size)
        assertEquals(1, holes[2].strokeIndex)
        assertEquals(2, holes[11].strokeIndex)
    }

    @Test
    fun dixHuitTrousShotgun() {
        val holes = Fixtures.eighteen.playedHoles(RoundPlan(listOf("A", "B"), startHole = 3))
        assertEquals((3..18).toList() + listOf(1, 2), holes.map { it.number })
        assertEquals(18, holes.size)
    }

    @Test
    fun dixHuitTrousRetourSeul() {
        val holes = Fixtures.eighteen.playedHoles(RoundPlan(listOf("B"), startHole = 12))
        assertEquals(listOf(12, 13, 14, 15, 16, 17, 18, 10, 11), holes.map { it.number })
        assertEquals((1..9).toList(), holes.map { it.strokeIndex }.sorted())
    }

    @Test
    fun slopeEtSssCombines() {
        val r = Fixtures.compact.combinedRating(Fixtures.red, Fixtures.yellow)
        assertEquals(55.5, r.sss)
        assertEquals(83, r.slope) // (80 + 85) / 2 arrondi
        assertEquals(Rating(130, 71.2), Fixtures.eighteen.combinedRating(Fixtures.eighteen.loops[0], Fixtures.eighteen.loops[1]))
    }
}
