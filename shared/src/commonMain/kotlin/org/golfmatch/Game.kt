package org.golfmatch

import kotlinx.serialization.Serializable

@Serializable
enum class GameMode { MATCH_PLAY, STROKE_PLAY }

/** Formule d'un camp de deux joueurs. */
@Serializable
enum class PairFormat {
    /** Chacun joue, on retient la meilleure balle à chaque coup : un score d'équipe par trou. */
    SCRAMBLE,

    /** Chacun joue sa balle, le meilleur score de l'équipe compte. */
    FOURBALL,

    /** Une balle par équipe, jouée alternativement. */
    FOURSOME,
}

/** Un camp : un joueur seul ou deux joueurs. */
@Serializable
data class Side(val playerIds: List<String>)

/** Balle dont on note le score : un joueur, ou une équipe en scramble/foursome. */
data class Ball(val index: Int, val side: Int, val playerIds: List<String>)

data class Totals(val strokes: Int, val toPar: Int, val holes: Int)

sealed class HoleResult {
    data class Won(val side: Int) : HoleResult()
    data object Halved : HoleResult()
}

data class MatchDecision(val winner: Int, val margin: Int, val toPlay: Int)

data class MatchStatus(
    /** > 0 : camp 0 devant ; < 0 : camp 1 devant. */
    val lead: Int,
    val played: Int,
    val remaining: Int,
    val decision: MatchDecision?,
) {
    val isFinished: Boolean get() = decision != null || remaining == 0

    fun describe(sideNames: List<String>): String {
        decision?.let { d ->
            val name = sideNames[d.winner]
            return if (d.toPlay == 0) "$name gagne ${d.margin} Up" else "$name gagne ${d.margin} & ${d.toPlay}"
        }
        if (played == 0) return "Pas commencé"
        if (remaining == 0) return "Match nul"
        if (lead == 0) return "Égalité · $remaining à jouer"
        val name = sideNames[if (lead > 0) 0 else 1]
        val dormie = if (kotlin.math.abs(lead) == remaining) " · dormie" else ""
        return "$name ${kotlin.math.abs(lead)} Up · $remaining à jouer$dormie"
    }
}

@Serializable
data class Game(
    val id: String,
    val createdAt: Long,
    val courseId: String,
    val courseName: String,
    val holes: List<PlayedHole>,
    val mode: GameMode,
    val sides: List<Side>,
    val pairFormat: PairFormat? = null,
    /** [balle][ordre du trou] ; null = pas encore saisi. */
    val strokes: List<List<Int?>>,
) {
    val balls: List<Ball>
        get() {
            val result = mutableListOf<Ball>()
            sides.forEachIndexed { s, side ->
                if (side.playerIds.size == 2 && pairFormat == PairFormat.FOURBALL) {
                    side.playerIds.forEach { result += Ball(result.size, s, listOf(it)) }
                } else {
                    result += Ball(result.size, s, side.playerIds)
                }
            }
            return result
        }

    fun ballsOf(side: Int): List<Ball> = balls.filter { it.side == side }

    fun score(ball: Int, hole: Int): Int? = strokes[ball][hole]

    fun withScore(ball: Int, hole: Int, value: Int?): Game =
        copy(strokes = strokes.mapIndexed { b, row -> if (b == ball) row.mapIndexed { h, v -> if (h == hole) value else v } else row })

    /** Score du camp sur un trou : la meilleure balle, une fois toutes ses balles saisies. */
    fun sideScore(side: Int, hole: Int): Int? {
        val values = ballsOf(side).map { score(it.index, hole) }
        if (values.any { it == null }) return null
        return values.filterNotNull().minOrNull()
    }

    fun holeResult(hole: Int): HoleResult? {
        if (mode != GameMode.MATCH_PLAY) return null
        val a = sideScore(0, hole) ?: return null
        val b = sideScore(1, hole) ?: return null
        return when {
            a < b -> HoleResult.Won(0)
            b < a -> HoleResult.Won(1)
            else -> HoleResult.Halved
        }
    }

    /** État du match play, dans l'ordre de jeu. Le match est acquis dès que l'écart dépasse les trous restants. */
    fun matchStatus(): MatchStatus {
        var lead = 0
        var played = 0
        var decision: MatchDecision? = null
        for (h in holes.indices) {
            val r = holeResult(h) ?: continue
            played++
            if (r is HoleResult.Won) lead += if (r.side == 0) 1 else -1
            val remaining = holes.size - played
            if (decision == null && kotlin.math.abs(lead) > remaining) {
                decision = MatchDecision(if (lead > 0) 0 else 1, kotlin.math.abs(lead), remaining)
            }
        }
        return MatchStatus(lead, played, holes.size - played, decision)
    }

    /** Premier trou (dans l'ordre de jeu) où il manque un score. */
    fun firstIncompleteHole(): Int =
        holes.indices.firstOrNull { h -> balls.any { score(it.index, h) == null } } ?: holes.lastIndex

    private fun totals(tour: Int?, value: (Int) -> Int?): Totals {
        var strokes = 0
        var toPar = 0
        var count = 0
        holes.forEach { h ->
            if (tour != null && h.tour != tour) return@forEach
            val v = value(h.order) ?: return@forEach
            strokes += v
            toPar += v - h.par
            count++
        }
        return Totals(strokes, toPar, count)
    }

    fun ballTotals(ball: Int, tour: Int? = null): Totals = totals(tour) { score(ball, it) }

    fun sideTotals(side: Int, tour: Int? = null): Totals = totals(tour) { sideScore(side, it) }

    /** Classement en stroke play : écart au par, puis nombre de coups. */
    fun strokePlayRanking(): List<Pair<Int, Totals>> =
        sides.indices.map { it to sideTotals(it) }
            .sortedWith(compareBy({ it.second.toPar }, { it.second.strokes }))

    companion object {
        fun create(
            id: String,
            createdAt: Long,
            course: Course,
            plan: RoundPlan,
            mode: GameMode,
            sides: List<Side>,
            pairFormat: PairFormat? = null,
        ): Game {
            val players = sides.flatMap { it.playerIds }
            require(players.size in 1..4) { "Une partie compte de 1 à 4 joueurs." }
            require(players.toSet().size == players.size) { "Un joueur ne peut figurer qu'une fois." }
            when (mode) {
                GameMode.MATCH_PLAY -> {
                    require(sides.size == 2) { "Le match play oppose deux camps." }
                    require(sides.all { it.playerIds.size in 1..2 }) { "Un camp compte un ou deux joueurs." }
                    require(sides.none { it.playerIds.size == 2 } || pairFormat != null) {
                        "Choisissez la formule du camp de deux joueurs."
                    }
                }
                GameMode.STROKE_PLAY ->
                    require(sides.all { it.playerIds.size == 1 }) { "En stroke play, chacun joue pour soi." }
            }
            val holes = course.playedHoles(plan)
            val draft = Game(id, createdAt, course.id, course.name, holes, mode, sides,
                pairFormat.takeIf { sides.any { s -> s.playerIds.size == 2 } }, emptyList())
            return draft.copy(strokes = draft.balls.map { List(holes.size) { null } })
        }
    }
}
