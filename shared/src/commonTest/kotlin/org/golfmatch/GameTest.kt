package org.golfmatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameTest {

    private fun game(mode: GameMode, sides: List<Side>, format: PairFormat? = null, tours: List<String> = listOf("R")) =
        Game.create("g", 0L, Fixtures.compact, RoundPlan(tours), mode, sides, format)

    private fun Game.play(vararg rows: List<Int?>): Game {
        var g = this
        rows.forEachIndexed { b, row -> row.forEachIndexed { h, v -> g = g.withScore(b, h, v) } }
        return g
    }

    private val names = listOf("CHCU", "DANO")

    @Test
    fun simpleMatchPlayEnCours() {
        val g = game(GameMode.MATCH_PLAY, listOf(Side(listOf("a")), Side(listOf("b"))))
            .play(listOf(3, 2, 3), listOf(3, 3, 4))
        val s = g.matchStatus()
        assertEquals(2, s.lead)
        assertEquals("CHCU 2 Up · 6 à jouer", s.describe(names))
    }

    @Test
    fun matchGagneAvantLaFin() {
        val g = game(GameMode.MATCH_PLAY, listOf(Side(listOf("a")), Side(listOf("b"))))
            .play(listOf(2, 2, 2, 2, 2, 3, 3), listOf(3, 3, 3, 3, 3, 3, 3))
        assertEquals("CHCU gagne 5 & 4", g.matchStatus().describe(names))
        // Les trous suivants restent saisissables pour le total de coups, sans changer le résultat.
        val after = g.withScore(1, 7, 1).withScore(0, 7, 5)
        assertEquals("CHCU gagne 5 & 4", after.matchStatus().describe(names))
        assertEquals(Totals(21, -3, 8), after.ballTotals(0))
    }

    @Test
    fun dormieEtNul() {
        val base = game(GameMode.MATCH_PLAY, listOf(Side(listOf("a")), Side(listOf("b"))))
        val dormie = base.play(listOf(2, 3, 3, 3, 3, 3, 3, 3), listOf(3, 3, 3, 3, 3, 3, 3, 3))
        assertEquals("CHCU 1 Up · 1 à jouer · dormie", dormie.matchStatus().describe(names))
        val nul = dormie.withScore(0, 8, 4).withScore(1, 8, 3)
        assertEquals("Match nul", nul.matchStatus().describe(names))
    }

    @Test
    fun unContreDeuxEnScramble() {
        val g = game(GameMode.MATCH_PLAY, listOf(Side(listOf("a")), Side(listOf("b", "c"))), PairFormat.SCRAMBLE)
        assertEquals(2, g.balls.size)
        val played = g.play(listOf(3), listOf(2))
        assertEquals(HoleResult.Won(1), played.holeResult(0))
    }

    @Test
    fun meilleureBalle() {
        val g = game(GameMode.MATCH_PLAY, listOf(Side(listOf("a", "b")), Side(listOf("c", "d"))), PairFormat.FOURBALL)
        assertEquals(4, g.balls.size)
        // Camp 0 : 4 et 3 → 3 ; camp 1 : 3 et 3 → 3 : partagé.
        var p = g.withScore(0, 0, 4).withScore(1, 0, 3).withScore(2, 0, 3)
        assertNull(p.holeResult(0)) // une balle manque encore
        p = p.withScore(3, 0, 3)
        assertEquals(HoleResult.Halved, p.holeResult(0))
    }

    @Test
    fun strokePlayClassement() {
        val g = game(GameMode.STROKE_PLAY, listOf(Side(listOf("a")), Side(listOf("b")), Side(listOf("c"))))
            .play(listOf(4, 4), listOf(2, 3), listOf(3, 3))
        assertEquals(listOf(1, 2, 0), g.strokePlayRanking().map { it.first })
        assertNull(g.holeResult(0))
    }

    @Test
    fun joueurSeul() {
        val g = game(GameMode.STROKE_PLAY, listOf(Side(listOf("a"))), tours = listOf("R", "J")).play(List(18) { 3 })
        assertEquals(Totals(54, 0, 18), g.sideTotals(0))
        assertEquals(Totals(27, 0, 9), g.sideTotals(0, tour = 1))
    }

    @Test
    fun reglesDeComposition() {
        assertFailsWith<IllegalArgumentException> {
            game(GameMode.MATCH_PLAY, listOf(Side(listOf("a")), Side(listOf("b", "c")))) // formule manquante
        }
        assertFailsWith<IllegalArgumentException> {
            game(GameMode.STROKE_PLAY, listOf(Side(listOf("a", "b"))))
        }
        assertFailsWith<IllegalArgumentException> {
            game(GameMode.MATCH_PLAY, listOf(Side(listOf("a")), Side(listOf("a"))))
        }
        assertTrue(game(GameMode.MATCH_PLAY, listOf(Side(listOf("a")), Side(listOf("b")))).pairFormat == null)
    }
}
