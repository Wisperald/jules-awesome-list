package com.personal.clock.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A city from the built-in catalog. Display names come from string resources keyed by [id]. */
data class City(val id: String, val zoneId: String)

object CityCatalog {

    /** Cities shown on first launch. */
    val defaultIds: List<String> = listOf(
        "aktau", "astana", "almaty", "moscow", "dubai", "london", "new_york",
    )

    /**
     * Curated list of cities. Several Kazakhstan cities share Asia/Almaty because
     * the IANA database has no separate zone for them. Europe/Kiev is used instead
     * of Europe/Kyiv because older devices (API 26–28) do not know the new name.
     */
    val all: List<City> = listOf(
        City("aktau", "Asia/Aqtau"),
        City("astana", "Asia/Almaty"),
        City("almaty", "Asia/Almaty"),
        City("shymkent", "Asia/Almaty"),
        City("karaganda", "Asia/Almaty"),
        City("pavlodar", "Asia/Almaty"),
        City("oskemen", "Asia/Almaty"),
        City("atyrau", "Asia/Atyrau"),
        City("oral", "Asia/Oral"),
        City("aktobe", "Asia/Aqtobe"),
        City("kostanay", "Asia/Qostanay"),
        City("kyzylorda", "Asia/Qyzylorda"),
        City("moscow", "Europe/Moscow"),
        City("saint_petersburg", "Europe/Moscow"),
        City("yekaterinburg", "Asia/Yekaterinburg"),
        City("omsk", "Asia/Omsk"),
        City("novosibirsk", "Asia/Novosibirsk"),
        City("tashkent", "Asia/Tashkent"),
        City("bishkek", "Asia/Bishkek"),
        City("dushanbe", "Asia/Dushanbe"),
        City("ashgabat", "Asia/Ashgabat"),
        City("baku", "Asia/Baku"),
        City("tbilisi", "Asia/Tbilisi"),
        City("yerevan", "Asia/Yerevan"),
        City("minsk", "Europe/Minsk"),
        City("kyiv", "Europe/Kiev"),
        City("istanbul", "Europe/Istanbul"),
        City("dubai", "Asia/Dubai"),
        City("doha", "Asia/Qatar"),
        City("riyadh", "Asia/Riyadh"),
        City("tehran", "Asia/Tehran"),
        City("delhi", "Asia/Kolkata"),
        City("urumqi", "Asia/Urumqi"),
        City("beijing", "Asia/Shanghai"),
        City("hong_kong", "Asia/Hong_Kong"),
        City("singapore", "Asia/Singapore"),
        City("bangkok", "Asia/Bangkok"),
        City("seoul", "Asia/Seoul"),
        City("tokyo", "Asia/Tokyo"),
        City("sydney", "Australia/Sydney"),
        City("auckland", "Pacific/Auckland"),
        City("london", "Europe/London"),
        City("paris", "Europe/Paris"),
        City("berlin", "Europe/Berlin"),
        City("rome", "Europe/Rome"),
        City("madrid", "Europe/Madrid"),
        City("amsterdam", "Europe/Amsterdam"),
        City("prague", "Europe/Prague"),
        City("cairo", "Africa/Cairo"),
        City("new_york", "America/New_York"),
        City("chicago", "America/Chicago"),
        City("denver", "America/Denver"),
        City("los_angeles", "America/Los_Angeles"),
        City("toronto", "America/Toronto"),
        City("mexico_city", "America/Mexico_City"),
        City("sao_paulo", "America/Sao_Paulo"),
        City("buenos_aires", "America/Argentina/Buenos_Aires"),
        City("honolulu", "Pacific/Honolulu"),
    )

    private val byId: Map<String, City> = all.associateBy { it.id }

    fun find(id: String): City? = byId[id]

    /** Catalog entries whose zone is known to this device's tz database. */
    fun available(availableZoneIds: Set<String> = ZoneId.getAvailableZoneIds()): List<City> =
        all.filter { it.zoneId in availableZoneIds }
}

object TimeZoneMath {

    /** Offset of [target] minus offset of [reference] at [instant], in minutes (DST-aware). */
    fun offsetDifferenceMinutes(instant: Instant, reference: ZoneId, target: ZoneId): Int {
        val ref = reference.rules.getOffset(instant).totalSeconds
        val tgt = target.rules.getOffset(instant).totalSeconds
        return (tgt - ref) / 60
    }

    /** -1 = target is on the previous calendar day, 0 = same day, +1 = next day. */
    fun dayDifference(instant: Instant, reference: ZoneId, target: ZoneId): Int =
        ChronoUnit.DAYS.between(
            instant.atZone(reference).toLocalDate(),
            instant.atZone(target).toLocalDate(),
        ).toInt()

    fun offsetSeconds(instant: Instant, zone: ZoneId): Int = zone.rules.getOffset(instant).totalSeconds
}

enum class CitySort { MANUAL, NAME, OFFSET }

object CityOrdering {

    /**
     * Orders cities for display. [nameOf] supplies the localized name; [nameComparator]
     * should be a locale-aware Collator for correct Cyrillic/Kazakh ordering.
     */
    fun sort(
        cities: List<City>,
        mode: CitySort,
        instant: Instant,
        nameOf: (City) -> String,
        nameComparator: Comparator<String>,
    ): List<City> = when (mode) {
        CitySort.MANUAL -> cities
        CitySort.NAME -> cities.sortedWith(compareBy(nameComparator) { nameOf(it) })
        CitySort.OFFSET -> cities.sortedWith(
            compareBy<City> { TimeZoneMath.offsetSeconds(instant, ZoneId.of(it.zoneId)) }
                .thenBy(nameComparator) { nameOf(it) },
        )
    }

    /** Moves the element at [from] by [delta] positions; returns the list unchanged if out of range. */
    fun <T> move(list: List<T>, from: Int, delta: Int): List<T> {
        val to = from + delta
        if (from !in list.indices || to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Case-insensitive search over the localized name, the English id and the zone id. */
    fun matches(city: City, localizedName: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return localizedName.contains(q, ignoreCase = true) ||
            city.id.replace('_', ' ').contains(q, ignoreCase = true) ||
            city.zoneId.replace('_', ' ').contains(q, ignoreCase = true)
    }
}
