package io.github.snomu.termwidget.data

import io.github.snomu.termwidget.model.City
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigEditsTest {
    private val cities = Defaults.cities
    private val paris = City("PAR", 48.86, 2.35, "Europe/Paris")

    private fun homes(list: List<City>) = list.filter { it.home }.map { it.code }

    @Test
    fun addingACityAppendsItAndKeepsTheHomeCity() {
        val next = ConfigEdits.upsertCity(cities, index = null, city = paris)

        assertEquals(cities.map { it.code } + "PAR", next.map { it.code })
        assertEquals(listOf("NYC"), homes(next))
    }

    @Test
    fun editingReplacesTheEntryAtItsIndexEvenWhenTheCodeChanges() {
        val next = ConfigEdits.upsertCity(cities, index = 2, city = paris)

        assertEquals(listOf("NYC", "SEA", "PAR", "BJS", "TYO"), next.map { it.code })
    }

    @Test
    fun markingACityHomeUnmarksTheOthers() {
        val next = ConfigEdits.upsertCity(cities, index = null, city = paris.copy(home = true))

        assertEquals(listOf("PAR"), homes(next))
    }

    @Test
    fun unmarkingTheOnlyHomeCityFallsBackToTheFirstCity() {
        val next = ConfigEdits.upsertCity(cities, index = 0, city = cities[0].copy(home = false))

        assertEquals(listOf("NYC"), homes(next))
    }

    @Test
    fun removingTheHomeCityPromotesTheFirstRemainingCity() {
        val next = ConfigEdits.removeCity(cities, 0)

        assertEquals(listOf("SEA", "LON", "BJS", "TYO"), next.map { it.code })
        assertEquals(listOf("SEA"), homes(next))
    }

    @Test
    fun theLastCityCannotBeRemoved() {
        val one = cities.take(1)

        assertEquals(one, ConfigEdits.removeCity(one, 0))
    }

    @Test
    fun moveSwapsNeighboursAndIgnoresMovesOffTheEnds() {
        assertEquals(listOf("SEA", "NYC", "LON", "BJS", "TYO"), ConfigEdits.move(cities, 0, 1).map { it.code })
        assertEquals(cities, ConfigEdits.move(cities, 0, -1))
        assertEquals(cities, ConfigEdits.move(cities, cities.lastIndex, 1))
    }

    @Test
    fun duplicateCodesAreDetectedExceptForTheCityBeingEdited() {
        assertTrue(ConfigEdits.isDuplicateCode(cities, "sea", ignoreIndex = null))
        assertFalse(ConfigEdits.isDuplicateCode(cities, "SEA", ignoreIndex = 1))
        assertFalse(ConfigEdits.isDuplicateCode(cities, "PAR", ignoreIndex = null))
    }
}
