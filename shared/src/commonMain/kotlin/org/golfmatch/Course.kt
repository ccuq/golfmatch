package org.golfmatch

import kotlinx.serialization.Serializable

/** Type de parcours. */
@Serializable
enum class CourseLayout {
    /** Une seule boucle de 9 trous (classement HCP de 1 à 9). */
    NINE,

    /** Deux boucles de 9 trous nommées, jouables dans n'importe quel ordre (classement HCP de 1 à 18). */
    NINE_PLUS_NINE,

    /** Parcours de 18 trous : boucle aller (1 à 9) et retour (10 à 18), classement HCP de 1 à 18. */
    EIGHTEEN,
}

@Serializable
data class Hole(val number: Int, val par: Int, val strokeIndex: Int)

/** Slope et SSS officiels ; facultatifs. */
@Serializable
data class Rating(val slope: Int? = null, val sss: Double? = null)

@Serializable
data class Loop(
    val id: String,
    val name: String,
    val holes: List<Hole>,
    val rating: Rating = Rating(),
)

@Serializable
data class Course(
    val id: String,
    val name: String,
    val layout: CourseLayout,
    val loops: List<Loop>,
    /** Valeurs du 18 trous complet (layout EIGHTEEN). */
    val rating18: Rating = Rating(),
    /** Parcours retiré de la liste mais conservé pour l'historique des parties. */
    val archived: Boolean = false,
) {
    fun loop(id: String): Loop = loops.first { it.id == id }

    /** Liste des anomalies de saisie ; vide si le parcours est cohérent. */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        val expectedLoops = if (layout == CourseLayout.NINE) 1 else 2
        if (loops.size != expectedLoops) errors += "Le parcours doit compter $expectedLoops boucle(s) de 9 trous."
        loops.forEach { l ->
            if (l.holes.size != 9) errors += "La boucle ${l.name} doit compter 9 trous."
            l.holes.filter { it.par !in 2..6 }.forEach { errors += "Par invalide au trou ${it.number} (${l.name})." }
        }
        val all = loops.flatMap { it.holes }
        val maxSi = if (layout == CourseLayout.NINE) 9 else 18
        all.filter { it.strokeIndex !in 1..maxSi }
            .forEach { errors += "Classement HCP hors limites au trou ${it.number} (1 à $maxSi)." }
        all.groupBy { it.strokeIndex }.filter { it.value.size > 1 }.keys.sorted()
            .forEach { errors += "Classement HCP $it attribué à plusieurs trous." }
        return errors
    }

    /** Valeurs Slope/SSS d'une partie de 18 trous sur deux boucles : SSS additionnés, Slope moyennés (WHS). */
    fun combinedRating(a: Loop, b: Loop): Rating {
        if (layout == CourseLayout.EIGHTEEN && a.id != b.id) return rating18
        val sss = a.rating.sss?.let { x -> b.rating.sss?.let { x + it } }
        val slope = a.rating.slope?.let { x -> b.rating.slope?.let { y -> (x + y + 1) / 2 } }
        return Rating(slope, sss)
    }
}

/**
 * Composition d'une partie : une ou deux boucles (tours) et le trou de départ (shotgun).
 * Pour un 18 trous complet, `tours` contient l'aller puis le retour et `startHole` va de 1 à 18.
 */
@Serializable
data class RoundPlan(val tours: List<String>, val startHole: Int = 1)

/** Trou tel qu'il est joué dans une partie, dans l'ordre de jeu. */
@Serializable
data class PlayedHole(
    val order: Int,
    val tour: Int,
    val loopId: String,
    val loopName: String,
    val number: Int,
    val par: Int,
    /** Classement HCP pour cette partie (1 à 9 sur 9 trous, 1 à 18 sur 18 trous). */
    val strokeIndex: Int,
)

fun Course.playedHoles(plan: RoundPlan): List<PlayedHole> {
    require(plan.tours.size in 1..2) { "Une partie compte un ou deux tours." }
    val tourLoops = plan.tours.map(::loop)

    // 18 trous complet : une seule rotation sur les 18 trous (3 à 18, puis 1 et 2).
    if (layout == CourseLayout.EIGHTEEN && tourLoops.size == 2 && tourLoops[0].id != tourLoops[1].id) {
        val all = tourLoops.flatMap { l -> l.holes.map { l to it } }.sortedBy { it.second.number }
        return rotate(all, plan.startHole) { it.second.number }.mapIndexed { i, (l, h) ->
            PlayedHole(i, if (i < 9) 0 else 1, l.id, l.name, h.number, h.par, h.strokeIndex)
        }
    }

    val sameLoopTwice = tourLoops.size == 2 && tourLoops[0].id == tourLoops[1].id
    val distinctLoops = tourLoops.size == 2 && !sameLoopTwice
    var order = 0
    return tourLoops.flatMapIndexed { tour, l ->
        val holes = l.holes.sortedBy { it.number }
        // Sur une autre boucle, on repart du même rang (départ du 3 rouge → 3 jaune).
        val startRank = startRank(holes, plan.startHole, tourLoops[0])
        val rank = rankWithinLoop(l)
        rotate(holes, holes[startRank].number) { it.number }.map { h ->
            val si = when {
                distinctLoops -> h.strokeIndex
                sameLoopTwice -> if (tour == 0) 2 * rank.getValue(h.number) - 1 else 2 * rank.getValue(h.number)
                else -> rank.getValue(h.number)
            }
            PlayedHole(order++, tour, l.id, l.name, h.number, h.par, si)
        }
    }
}

/** Rang (0 à 8) du trou de départ, mesuré sur la première boucle. */
private fun startRank(holes: List<Hole>, startHole: Int, firstLoop: Loop): Int {
    val first = firstLoop.holes.sortedBy { it.number }
    val r = first.indexOfFirst { it.number == startHole }
    return if (r >= 0) r.coerceAtMost(holes.size - 1) else 0
}

/** Reclassement de 1 à 9 au sein d'une boucle, selon l'ordre de difficulté. */
private fun rankWithinLoop(loop: Loop): Map<Int, Int> =
    loop.holes.sortedBy { it.strokeIndex }.mapIndexed { i, h -> h.number to i + 1 }.toMap()

private fun <T> rotate(items: List<T>, start: Int, number: (T) -> Int): List<T> {
    val i = items.indexOfFirst { number(it) == start }
    return if (i <= 0) items else items.drop(i) + items.take(i)
}
