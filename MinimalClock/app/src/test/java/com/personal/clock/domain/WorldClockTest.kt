package com.personal.clock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class WorldClockTest {

    private val summer = Instant.parse("2026-07-01T12:00:00Z")
    private val winter = Instant.parse("2026-01-15T12:00:00Z")

    @Test
    fun defaultCitiesExistInCatalogAndTzdb() {
        val zones = ZoneId.getAvailableZoneIds()
        CityCatalog.defaultIds.forEach { id ->
            val city = CityCatalog.find(id)
            requireNotNull(city) { "missing $id" }
            assertTrue("unknown zone ${city.zoneId}", city.zoneId in zones)
        }
        assertEquals(CityCatalog.all.size, CityCatalog.all.map { it.id }.toSet().size)
    }

    @Test
    fun availableFiltersUnknownZones() {
        val subset = CityCatalog.available(setOf("Asia/Almaty"))
        assertTrue(subset.isNotEmpty())
        assertTrue(subset.all { it.zoneId == "Asia/Almaty" })
    }

    @Test
    fun offsetDifferenceFollowsDst() {
        val almaty = ZoneId.of("Asia/Almaty")
        val london = ZoneId.of("Europe/London")
        // Asia/Almaty is UTC+5 since 2024; London is UTC+1 in summer, UTC+0 in winter.
        assertEquals(-4 * 60, TimeZoneMath.offsetDifferenceMinutes(summer, almaty, london))
        assertEquals(-5 * 60, TimeZoneMath.offsetDifferenceMinutes(winter, almaty, london))
    }

    @Test
    fun halfHourZones() {
        val delhi = ZoneId.of("Asia/Kolkata")
        assertEquals(330, TimeZoneMath.offsetDifferenceMinutes(winter, ZoneId.of("UTC"), delhi))
        assertEquals("UTC+5:30", DurationFormat.utcOffset(330 * 60))
    }

    @Test
    fun dayDifference() {
        val lateEvening = Instant.parse("2026-09-24T20:00:00Z")
        val almaty = ZoneId.of("Asia/Almaty") // 01:00 next day
        val newYork = ZoneId.of("America/New_York") // 16:00 same day
        assertEquals(-1, TimeZoneMath.dayDifference(lateEvening, almaty, newYork))
        assertEquals(1, TimeZoneMath.dayDifference(lateEvening, newYork, almaty))
        assertEquals(0, TimeZoneMath.dayDifference(lateEvening, almaty, almaty))
    }

    @Test
    fun sortByNameAndOffset() {
        val cities = listOf("london", "almaty", "new_york").map { CityCatalog.find(it)!! }
        val names = mapOf("london" to "Лондон", "almaty" to "Алматы", "new_york" to "Нью-Йорк")
        val collator = Collator.getInstance(Locale.forLanguageTag("ru"))
        val byName = CityOrdering.sort(cities, CitySort.NAME, summer, { names.getValue(it.id) }, collator::compare)
        assertEquals(listOf("almaty", "london", "new_york"), byName.map { it.id })
        val byOffset = CityOrdering.sort(cities, CitySort.OFFSET, summer, { names.getValue(it.id) }, collator::compare)
        assertEquals(listOf("new_york", "london", "almaty"), byOffset.map { it.id })
        assertEquals(cities, CityOrdering.sort(cities, CitySort.MANUAL, summer, { it.id }, collator::compare))
    }

    @Test
    fun moveAndSearch() {
        assertEquals(listOf("b", "a", "c"), CityOrdering.move(listOf("a", "b", "c"), 1, -1))
        assertEquals(listOf("a", "b", "c"), CityOrdering.move(listOf("a", "b", "c"), 0, -1))
        val ny = CityCatalog.find("new_york")!!
        assertTrue(CityOrdering.matches(ny, "Нью-Йорк", "нью"))
        assertTrue(CityOrdering.matches(ny, "Нью-Йорк", "new york"))
        assertTrue(CityOrdering.matches(ny, "Нью-Йорк", "America"))
        assertFalse(CityOrdering.matches(ny, "Нью-Йорк", "Paris"))
    }
}
