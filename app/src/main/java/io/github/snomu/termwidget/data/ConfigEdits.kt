package io.github.snomu.termwidget.data

import io.github.snomu.termwidget.model.City

/** Pure list edits used by the settings screen, kept here so they can be unit tested. */
object ConfigEdits {
    /**
     * Inserts [city] (when [index] is null) or replaces the entry at [index],
     * then makes sure exactly one city is home.
     */
    fun upsertCity(cities: List<City>, index: Int?, city: City): List<City> {
        val next = cities.toMutableList()
        val position = if (index != null && index in next.indices) {
            next[index] = city
            index
        } else {
            next += city
            next.lastIndex
        }
        return normalizeHome(next, preferred = if (city.home) position else null)
    }

    /** Removes the entry at [index]. The last remaining city is never removed. */
    fun removeCity(cities: List<City>, index: Int): List<City> {
        if (cities.size <= 1 || index !in cities.indices) return cities
        return normalizeHome(cities.filterIndexed { i, _ -> i != index }, preferred = null)
    }

    fun <T> move(items: List<T>, index: Int, delta: Int): List<T> {
        val target = index + delta
        if (index !in items.indices || target !in items.indices) return items
        val next = items.toMutableList()
        next.add(target, next.removeAt(index))
        return next
    }

    /** True when [code] is already used by a city other than the one at [ignoreIndex]. */
    fun isDuplicateCode(cities: List<City>, code: String, ignoreIndex: Int?): Boolean =
        cities.withIndex().any { (i, c) -> i != ignoreIndex && c.code.equals(code, ignoreCase = true) }

    private fun normalizeHome(cities: List<City>, preferred: Int?): List<City> {
        if (cities.isEmpty()) return cities
        val homeIndex = preferred
            ?: cities.indexOfFirst { it.home }.takeIf { it >= 0 }
            ?: 0
        return cities.mapIndexed { i, c -> c.copy(home = i == homeIndex) }
    }
}
