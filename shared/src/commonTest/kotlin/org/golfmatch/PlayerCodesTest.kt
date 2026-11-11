package org.golfmatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlayerCodesTest {

    @Test
    fun prenomSimple() = assertEquals("CHCU", PlayerCodes.propose("Christophe", "Cuq", emptySet()))

    @Test
    fun prenomCompose() = assertEquals("JPMA", PlayerCodes.propose("Jean-Pierre", "Martin", emptySet()))

    @Test
    fun accentsEtMajuscules() = assertEquals("ELLE", PlayerCodes.propose("Élodie", "Lefèvre", emptySet()))

    @Test
    fun doublonSurLeNom() = assertEquals("CHCQ", PlayerCodes.propose("Christophe", "Cuq", setOf("CHCU")))

    @Test
    fun doublonNomEpuise() = assertEquals("CRCU", PlayerCodes.propose("Christophe", "Cuq", setOf("CHCU", "CHCQ")))

    @Test
    fun prenomComposeSansVarianteDePrenom() =
        assertEquals(listOf("JPCU", "JPCQ"), PlayerCodes.candidates("Jean-Pierre", "Cuq"))

    @Test
    fun aucuneVarianteLibre() = assertNull(PlayerCodes.propose("Jo", "Li", setOf("JOLI")))

    @Test
    fun controleSaisie() {
        assertNull(PlayerCodes.check("DANO", setOf("CHCU")))
        assertNotNull(PlayerCodes.check("CHCU", setOf("CHCU")))
        assertNotNull(PlayerCodes.check("A", emptySet()))
        assertNotNull(PlayerCodes.check("ABCDE", emptySet()))
    }
}
