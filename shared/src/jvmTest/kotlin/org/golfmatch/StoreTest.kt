package org.golfmatch

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.golfmatch.db.GolfDatabase
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreTest {

    private var counter = 0
    private fun store(url: String = JdbcSqliteDriver.IN_MEMORY) =
        GolfStore(JdbcSqliteDriver(url, Properties(), GolfDatabase.Schema), clock = { 1_000L }, newId = { "id${counter++}" })

    private fun GolfStore.addPlayer(first: String, last: String) =
        savePlayer(Player("", first, last, proposeCode(first, last) ?: error("pas de code")))

    @Test
    fun donneesConserveesEntreDeuxOuvertures() {
        val file = Files.createTempFile("golfmatch", ".db").toFile().apply { delete(); deleteOnExit() }
        val url = "jdbc:sqlite:${file.absolutePath}"
        val s = store(url)
        val a = s.addPlayer("Christophe", "Cuq")
        val b = s.addPlayer("Daniel", "Noël")
        val course = s.saveCourse(Fixtures.compact.copy(id = ""))
        val g = s.startGame(
            course.id, RoundPlan(listOf("R", "J"), startHole = 3), GameMode.MATCH_PLAY,
            listOf(Side(listOf(a.id)), Side(listOf(b.id))),
        )
        s.setScore(g.id, 0, 0, 3)
        s.setScore(g.id, 1, 0, 2)

        val reopened = store(url)
        assertEquals(listOf("CHCU", "DANO"), reopened.activePlayers.map { it.code })
        assertEquals(Fixtures.compact.copy(id = course.id), reopened.course(course.id))
        val game = reopened.games.single()
        assertEquals(g.holes, game.holes)
        assertEquals(2, game.score(1, 0))
        assertEquals("DANO 1 Up · 17 à jouer", game.matchStatus().describe(listOf("CHCU", "DANO")))
    }

    @Test
    fun effacerUnScore() {
        val s = store()
        val a = s.addPlayer("Christophe", "Cuq")
        val course = s.saveCourse(Fixtures.compact.copy(id = ""))
        val g = s.startGame(course.id, RoundPlan(listOf("R")), GameMode.STROKE_PLAY, listOf(Side(listOf(a.id))))
        s.setScore(g.id, 0, 4, 5)
        assertNull(s.setScore(g.id, 0, 4, null).score(0, 4))
    }

    @Test
    fun codeEnDoubleRefuse() {
        val s = store()
        s.addPlayer("Christophe", "Cuq")
        assertEquals("CHCQ", s.proposeCode("Charles", "Cuq"))
        val e = assertFailsWith<StoreException> { s.savePlayer(Player("", "Charles", "Cuq", "CHCU")) }
        assertTrue(e.message!!.contains("déjà utilisé"))
    }

    @Test
    fun modifierUnJoueurGardeSonCode() {
        val s = store()
        val p = s.addPlayer("Christophe", "Cuq")
        val updated = s.savePlayer(p.copy(index = 18.4))
        assertEquals("CHCU", updated.code)
        assertEquals(18.4, s.activePlayers.single().index)
    }

    @Test
    fun joueurEtParcoursUtilisesArchives() {
        val s = store()
        val a = s.addPlayer("Christophe", "Cuq")
        val b = s.addPlayer("Daniel", "Noël")
        val c = s.addPlayer("Paul", "Martin")
        val course = s.saveCourse(Fixtures.compact.copy(id = ""))
        val unused = s.saveCourse(Fixtures.nine.copy(id = ""))
        s.startGame(course.id, RoundPlan(listOf("R")), GameMode.STROKE_PLAY, listOf(Side(listOf(a.id)), Side(listOf(b.id))))
        s.removePlayer(b.id)
        s.removePlayer(c.id)
        assertEquals(listOf("CHCU"), s.activePlayers.map { it.code })
        assertNotNull(s.player(b.id)) // archivé, toujours lisible dans l'historique
        assertNull(s.player(c.id)) // jamais utilisé : supprimé
        s.removeCourse(course.id)
        s.removeCourse(unused.id)
        assertTrue(s.activeCourses.isEmpty())
        assertNotNull(s.course(course.id))
        assertNull(s.course(unused.id))
    }

    @Test
    fun parcoursInvalideRefuse() {
        val s = store()
        assertFailsWith<StoreException> { s.saveCourse(Fixtures.compact.copy(id = "", loops = listOf(Fixtures.red))) }
    }

    @Test
    fun supprimerUnePartie() {
        val s = store()
        val a = s.addPlayer("Christophe", "Cuq")
        val course = s.saveCourse(Fixtures.compact.copy(id = ""))
        val g = s.startGame(course.id, RoundPlan(listOf("R")), GameMode.STROKE_PLAY, listOf(Side(listOf(a.id))))
        s.setScore(g.id, 0, 0, 3)
        s.removeGame(g.id)
        assertTrue(s.games.isEmpty())
    }

    @Test
    fun exportImport() {
        val s = store()
        val a = s.addPlayer("Christophe", "Cuq")
        val course = s.saveCourse(Fixtures.compact.copy(id = ""))
        val g = s.startGame(course.id, RoundPlan(listOf("R")), GameMode.STROKE_PLAY, listOf(Side(listOf(a.id))))
        s.setScore(g.id, 0, 2, 4)
        val backup = s.exportJson()

        val other = store()
        other.addPlayer("Paul", "Martin") // remplacé par l'import
        other.importJson(backup)
        assertEquals(s.data, other.data)
        assertFailsWith<StoreException> { other.importJson("pas du json") }
        assertEquals(s.data, other.data) // un import raté ne touche à rien
    }
}
