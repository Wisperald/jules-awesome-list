package com.personal.clock.ui.clock

import androidx.annotation.StringRes
import com.personal.clock.R

/** Localized city names (ru / kk / en string resources) for [com.personal.clock.domain.CityCatalog]. */
@StringRes
fun cityNameRes(id: String): Int = when (id) {
    "aktau" -> R.string.city_aktau
    "astana" -> R.string.city_astana
    "almaty" -> R.string.city_almaty
    "shymkent" -> R.string.city_shymkent
    "karaganda" -> R.string.city_karaganda
    "pavlodar" -> R.string.city_pavlodar
    "oskemen" -> R.string.city_oskemen
    "atyrau" -> R.string.city_atyrau
    "oral" -> R.string.city_oral
    "aktobe" -> R.string.city_aktobe
    "kostanay" -> R.string.city_kostanay
    "kyzylorda" -> R.string.city_kyzylorda
    "moscow" -> R.string.city_moscow
    "saint_petersburg" -> R.string.city_saint_petersburg
    "yekaterinburg" -> R.string.city_yekaterinburg
    "omsk" -> R.string.city_omsk
    "novosibirsk" -> R.string.city_novosibirsk
    "tashkent" -> R.string.city_tashkent
    "bishkek" -> R.string.city_bishkek
    "dushanbe" -> R.string.city_dushanbe
    "ashgabat" -> R.string.city_ashgabat
    "baku" -> R.string.city_baku
    "tbilisi" -> R.string.city_tbilisi
    "yerevan" -> R.string.city_yerevan
    "minsk" -> R.string.city_minsk
    "kyiv" -> R.string.city_kyiv
    "istanbul" -> R.string.city_istanbul
    "dubai" -> R.string.city_dubai
    "doha" -> R.string.city_doha
    "riyadh" -> R.string.city_riyadh
    "tehran" -> R.string.city_tehran
    "delhi" -> R.string.city_delhi
    "urumqi" -> R.string.city_urumqi
    "beijing" -> R.string.city_beijing
    "hong_kong" -> R.string.city_hong_kong
    "singapore" -> R.string.city_singapore
    "bangkok" -> R.string.city_bangkok
    "seoul" -> R.string.city_seoul
    "tokyo" -> R.string.city_tokyo
    "sydney" -> R.string.city_sydney
    "auckland" -> R.string.city_auckland
    "london" -> R.string.city_london
    "paris" -> R.string.city_paris
    "berlin" -> R.string.city_berlin
    "rome" -> R.string.city_rome
    "madrid" -> R.string.city_madrid
    "amsterdam" -> R.string.city_amsterdam
    "prague" -> R.string.city_prague
    "cairo" -> R.string.city_cairo
    "new_york" -> R.string.city_new_york
    "chicago" -> R.string.city_chicago
    "denver" -> R.string.city_denver
    "los_angeles" -> R.string.city_los_angeles
    "toronto" -> R.string.city_toronto
    "mexico_city" -> R.string.city_mexico_city
    "sao_paulo" -> R.string.city_sao_paulo
    "buenos_aires" -> R.string.city_buenos_aires
    "honolulu" -> R.string.city_honolulu
    else -> R.string.city_unknown
}
