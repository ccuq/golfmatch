package org.golfmatch

/** Club de test 9+9 : boucle rouge (classements impairs) et jaune (classements pairs), tous par 3. */
object Fixtures {
    private val redSi = listOf(5, 13, 1, 9, 17, 3, 11, 15, 7)
    private val yellowSi = listOf(6, 14, 2, 10, 18, 4, 12, 16, 8)

    val red = Loop("R", "Rouge", (1..9).map { Hole(it, 3, redSi[it - 1]) }, Rating(80, 27.0))
    val yellow = Loop("J", "Jaune", (1..9).map { Hole(it, 3, yellowSi[it - 1]) }, Rating(85, 28.5))
    val compact = Course("club", "Compact", CourseLayout.NINE_PLUS_NINE, listOf(red, yellow))

    val nine = Course(
        "nine", "Neuf trous", CourseLayout.NINE,
        listOf(Loop("L", "Boucle", (1..9).map { Hole(it, if (it % 3 == 0) 4 else 3, listOf(3, 7, 1, 9, 5, 2, 8, 4, 6)[it - 1]) })),
    )

    val eighteen = Course(
        "18", "Dix-huit", CourseLayout.EIGHTEEN,
        listOf(
            Loop("A", "Aller", (1..9).map { Hole(it, 4, 2 * it - 1) }),
            Loop("B", "Retour", (10..18).map { Hole(it, 4, 2 * (it - 9)) }),
        ),
        rating18 = Rating(130, 71.2),
    )
}
