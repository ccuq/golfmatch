package org.golfmatch

import app.cash.sqldelight.db.SqlDriver
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.golfmatch.db.GolfDatabase
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.golfmatch.db.Course as CourseRow
import org.golfmatch.db.Course_loop as LoopRow
import org.golfmatch.db.Game as GameRow
import org.golfmatch.db.Game_hole as GameHoleRow
import org.golfmatch.db.Game_side as GameSideRow
import org.golfmatch.db.Hole as HoleRow
import org.golfmatch.db.Player as PlayerRow

/** Ensemble des données de l'appli ; c'est le format du fichier d'export/import (JSON). */
@Serializable
data class AppData(
    val version: Int = 1,
    val players: List<Player> = emptyList(),
    val courses: List<Course> = emptyList(),
    val games: List<Game> = emptyList(),
)

class StoreException(message: String) : Exception(message)

/**
 * Point d'accès unique aux données, stockées dans une base SQLite.
 * Les joueurs et parcours déjà utilisés dans une partie sont archivés au lieu d'être supprimés,
 * pour que l'historique reste lisible.
 */
@OptIn(ExperimentalUuidApi::class)
class GolfStore(
    driver: SqlDriver,
    private val clock: () -> Long,
    private val newId: () -> String = { Uuid.random().toString() },
) {
    private val db = GolfDatabase(driver)
    private val q = db.golfQueries

    /** Données en mémoire, relues après chaque modification (volumes faibles). */
    var data: AppData = loadAll()
        private set

    private fun refresh() { data = loadAll() }

    // ---- Lecture ----

    private fun loadAll(): AppData {
        val players = q.allPlayers().executeAsList().map { it.toPlayer() }

        val holesByLoop = q.allHoles().executeAsList().groupBy { it.course_id to it.loop_id }
        val loopsByCourse = q.allLoops().executeAsList().groupBy { it.course_id }
        val courses = q.allCourses().executeAsList().map { c ->
            c.toCourse(loopsByCourse[c.id].orEmpty().map { l -> l.toLoop(holesByLoop[c.id to l.id].orEmpty()) })
        }

        val holesByGame = q.allGameHoles().executeAsList().groupBy { it.game_id }
        val sidesByGame = q.allGameSides().executeAsList().groupBy { it.game_id }
        val scoresByGame = q.allScores().executeAsList().groupBy { it.game_id }
        val games = q.allGames().executeAsList().map { g ->
            val holes = holesByGame[g.id].orEmpty().map { it.toPlayedHole() }
            val sides = sidesByGame[g.id].orEmpty().groupBy { it.side_index }.entries.sortedBy { it.key }
                .map { (_, rows) -> Side(rows.sortedBy { it.position }.map { it.player_id }) }
            val draft = Game(
                g.id, g.created_at, g.course_id, g.course_name, holes, GameMode.valueOf(g.mode), sides,
                g.pair_format?.let(PairFormat::valueOf), emptyList(),
            )
            val strokes = draft.balls.map { MutableList<Int?>(holes.size) { null } }
            scoresByGame[g.id].orEmpty().forEach { s ->
                strokes.getOrNull(s.ball_index.toInt())?.let { row ->
                    if (s.hole_order.toInt() in row.indices) row[s.hole_order.toInt()] = s.strokes.toInt()
                }
            }
            draft.copy(strokes = strokes)
        }
        return AppData(players = players, courses = courses, games = games)
    }

    // ---- Joueurs ----

    val activePlayers: List<Player>
        get() = data.players.filterNot { it.archived }.sortedBy { it.code }

    fun player(id: String): Player? = data.players.firstOrNull { it.id == id }

    private fun takenCodes(exceptId: String? = null): Set<String> =
        data.players.filter { it.id != exceptId }.map { it.code }.toSet()

    /** Code proposé pour un joueur, ou null s'il faut le saisir à la main. */
    fun proposeCode(firstName: String, lastName: String, exceptId: String? = null): String? =
        PlayerCodes.propose(firstName, lastName, takenCodes(exceptId))

    /** Crée le joueur (id vide) ou met à jour celui qui porte cet id. */
    fun savePlayer(player: Player): Player {
        val code = PlayerCodes.letters(player.code)
        PlayerCodes.check(code, takenCodes(player.id.ifEmpty { null }))?.let { throw StoreException(it) }
        if (player.firstName.isBlank() && player.lastName.isBlank()) throw StoreException("Indiquez au moins un prénom ou un nom.")
        val saved = player.copy(id = player.id.ifEmpty { newId() }, code = code)
        q.upsertPlayer(saved.toRow())
        refresh()
        return saved
    }

    fun removePlayer(id: String) {
        if (q.playerIsUsed(id).executeAsOne() > 0) q.archivePlayer(id) else q.deletePlayer(id)
        refresh()
    }

    // ---- Parcours ----

    val activeCourses: List<Course>
        get() = data.courses.filterNot { it.archived }.sortedBy { it.name }

    fun course(id: String): Course? = data.courses.firstOrNull { it.id == id }

    fun saveCourse(course: Course): Course {
        course.validate().firstOrNull()?.let { throw StoreException(it) }
        val saved = course.copy(id = course.id.ifEmpty { newId() })
        q.transaction { writeCourse(saved) }
        refresh()
        return saved
    }

    private fun writeCourse(c: Course) {
        q.deleteCourseHoles(c.id)
        q.deleteCourseLoops(c.id)
        q.upsertCourse(CourseRow(c.id, c.name, c.layout.name, c.rating18.slope?.toLong(), c.rating18.sss, c.archived.toLong()))
        c.loops.forEachIndexed { i, l ->
            q.insertLoop(LoopRow(l.id, c.id, i.toLong(), l.name, l.rating.slope?.toLong(), l.rating.sss))
            l.holes.forEach { h -> q.insertHole(HoleRow(c.id, l.id, h.number.toLong(), h.par.toLong(), h.strokeIndex.toLong())) }
        }
    }

    fun removeCourse(id: String) {
        q.transaction {
            if (q.courseIsUsed(id).executeAsOne() > 0) {
                q.archiveCourse(id)
            } else {
                q.deleteCourseHoles(id)
                q.deleteCourseLoops(id)
                q.deleteCourse(id)
            }
        }
        refresh()
    }

    // ---- Parties ----

    /** Parties, les plus récentes d'abord. */
    val games: List<Game> get() = data.games

    fun game(id: String): Game? = data.games.firstOrNull { it.id == id }

    fun startGame(courseId: String, plan: RoundPlan, mode: GameMode, sides: List<Side>, pairFormat: PairFormat? = null): Game {
        val course = course(courseId) ?: throw StoreException("Parcours introuvable.")
        val game = try {
            Game.create(newId(), clock(), course, plan, mode, sides, pairFormat)
        } catch (e: IllegalArgumentException) {
            throw StoreException(e.message ?: "Composition de partie invalide.")
        }
        q.transaction { writeGame(game) }
        refresh()
        return game
    }

    private fun writeGame(g: Game) {
        q.insertGame(GameRow(g.id, g.createdAt, g.courseId, g.courseName, g.mode.name, g.pairFormat?.name))
        g.holes.forEach { h ->
            q.insertGameHole(
                GameHoleRow(g.id, h.order.toLong(), h.tour.toLong(), h.loopId, h.loopName, h.number.toLong(), h.par.toLong(), h.strokeIndex.toLong()),
            )
        }
        g.sides.forEachIndexed { s, side ->
            side.playerIds.forEachIndexed { p, playerId -> q.insertGameSide(GameSideRow(g.id, s.toLong(), p.toLong(), playerId)) }
        }
        g.strokes.forEachIndexed { b, row ->
            row.forEachIndexed { h, v -> if (v != null) q.upsertScore(g.id, b.toLong(), h.toLong(), v.toLong()) }
        }
    }

    fun setScore(gameId: String, ball: Int, hole: Int, value: Int?): Game {
        val game = game(gameId) ?: throw StoreException("Partie introuvable.")
        if (ball !in game.balls.indices || hole !in game.holes.indices) throw StoreException("Trou ou balle inconnus.")
        if (value == null) q.deleteScore(gameId, ball.toLong(), hole.toLong())
        else q.upsertScore(gameId, ball.toLong(), hole.toLong(), value.toLong())
        refresh()
        return game(gameId)!!
    }

    fun removeGame(id: String) {
        q.transaction {
            q.deleteGameScores(id)
            q.deleteGameSides(id)
            q.deleteGameHoles(id)
            q.deleteGame(id)
        }
        refresh()
    }

    /** Nom d'un camp pour l'affichage : codes des joueurs (« CHCU & DANO »). */
    fun sideName(side: Side): String = side.playerIds.joinToString(" & ") { player(it)?.code ?: "?" }

    // ---- Export / import ----

    fun exportJson(): String = json.encodeToString(AppData.serializer(), data)

    /** Remplace toutes les données par celles du fichier importé. */
    fun importJson(text: String) {
        val imported = try {
            json.decodeFromString<AppData>(text)
        } catch (e: Exception) {
            throw StoreException("Fichier de sauvegarde illisible.")
        }
        q.transaction {
            q.clearScores(); q.clearGameSides(); q.clearGameHoles(); q.clearGames()
            q.clearHoles(); q.clearLoops(); q.clearCourses(); q.clearPlayers()
            imported.players.forEach { q.upsertPlayer(it.toRow()) }
            imported.courses.forEach(::writeCourse)
            imported.games.forEach(::writeGame)
        }
        refresh()
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    }
}

private fun Boolean.toLong() = if (this) 1L else 0L

private fun PlayerRow.toPlayer() =
    Player(id, first_name, last_name, code, hcp_index, color, email, phone, archived != 0L)

private fun Player.toRow() =
    PlayerRow(id, firstName, lastName, code, index, colorHex, email, phone, archived.toLong())

private fun CourseRow.toCourse(loops: List<Loop>) =
    Course(id, name, CourseLayout.valueOf(layout), loops, Rating(slope18?.toInt(), sss18), archived != 0L)

private fun LoopRow.toLoop(holes: List<HoleRow>) =
    Loop(id, name, holes.map { Hole(it.number.toInt(), it.par.toInt(), it.stroke_index.toInt()) }, Rating(slope?.toInt(), sss))

private fun GameHoleRow.toPlayedHole() =
    PlayedHole(hole_order.toInt(), tour.toInt(), loop_id, loop_name, number.toInt(), par.toInt(), stroke_index.toInt())
