package org.golfmatch

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: String,
    val firstName: String,
    val lastName: String,
    val code: String,
    val index: Double? = null,
    val colorHex: String = "#2E7D32",
    val email: String? = null,
    val phone: String? = null,
)

/**
 * Codes joueurs : 2 à 4 lettres majuscules, uniques.
 *
 * Proposition : 2 premières lettres d'un prénom simple, ou initiale de chaque prénom
 * d'un prénom composé, suivies des 2 premières lettres du nom (Christophe Cuq → CHCU).
 * En cas de doublon : 1re lettre du nom puis sa 3e, sa 4e… (CHCQ), puis, pour un
 * prénom simple, 1re lettre du prénom puis sa 3e, sa 4e… (CRCU).
 */
object PlayerCodes {

    private val accents = mapOf(
        'À' to 'A', 'Â' to 'A', 'Ä' to 'A', 'Á' to 'A', 'Ã' to 'A', 'Å' to 'A',
        'Ç' to 'C',
        'É' to 'E', 'È' to 'E', 'Ê' to 'E', 'Ë' to 'E',
        'Î' to 'I', 'Ï' to 'I', 'Í' to 'I', 'Ì' to 'I',
        'Ô' to 'O', 'Ö' to 'O', 'Ó' to 'O', 'Ò' to 'O', 'Õ' to 'O', 'Ø' to 'O',
        'Ù' to 'U', 'Û' to 'U', 'Ü' to 'U', 'Ú' to 'U',
        'Ÿ' to 'Y', 'Ý' to 'Y', 'Ñ' to 'N',
    )

    /** Majuscules sans accents, lettres A-Z uniquement. */
    fun letters(s: String): String =
        s.uppercase().map { accents[it] ?: it }.filter { it in 'A'..'Z' }.joinToString("")

    /** Prénoms séparés par un tiret ou une espace (Jean-Pierre, Marie Claire). */
    private fun givenNames(firstName: String): List<String> =
        firstName.split('-', ' ').map(::letters).filter { it.isNotEmpty() }

    fun isValid(code: String): Boolean =
        code.length in 2..4 && code.all { it in 'A'..'Z' }

    /** Candidats dans l'ordre de préférence, sans doublon. */
    fun candidates(firstName: String, lastName: String): List<String> {
        val names = givenNames(firstName)
        val last = letters(lastName)
        val compound = names.size > 1
        val first = names.firstOrNull() ?: ""
        val firstPart = if (compound) names.joinToString("") { it.take(1) }.take(2) else first.take(2)

        val result = LinkedHashSet<String>()
        result += firstPart + last.take(2)
        // Variantes sur le nom : 1re lettre puis 3e, 4e…
        for (k in 2 until last.length) result += firstPart + last[0] + last[k]
        // Variantes sur le prénom simple : 1re lettre puis 3e, 4e…
        if (!compound) {
            for (k in 2 until first.length) result += "" + first[0] + first[k] + last.take(2)
        }
        return result.filter(::isValid)
    }

    /** Premier candidat libre, ou null s'il faut saisir le code à la main. */
    fun propose(firstName: String, lastName: String, taken: Set<String>): String? =
        candidates(firstName, lastName).firstOrNull { it !in taken }

    /** Erreur à afficher pour un code saisi, ou null s'il est acceptable. */
    fun check(code: String, taken: Set<String>): String? = when {
        !isValid(code) -> "Le code doit compter 2 à 4 lettres."
        code in taken -> "Ce code est déjà utilisé par un autre joueur."
        else -> null
    }
}
