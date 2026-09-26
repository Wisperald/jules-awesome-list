package com.personal.clock.ui.clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.clock.data.SettingsRepository
import com.personal.clock.data.WorldClockRepository
import com.personal.clock.domain.CityCatalog
import com.personal.clock.domain.CityOrdering
import com.personal.clock.domain.CitySort
import com.personal.clock.domain.City
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClockViewModel(
    private val repository: WorldClockRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Cities in the user's manual order (null until loaded). */
    val cities: StateFlow<List<City>?> = repository.cityIds
        .map { ids -> ids.mapNotNull { CityCatalog.find(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(city: City) = viewModelScope.launch { repository.add(city.id) }

    fun remove(city: City) = viewModelScope.launch { repository.remove(city.id) }

    fun move(city: City, delta: Int) = viewModelScope.launch {
        val current = cities.value ?: return@launch
        val index = current.indexOf(city)
        repository.setOrder(CityOrdering.move(current, index, delta).map { it.id })
    }

    fun setSort(sort: CitySort) = viewModelScope.launch { settings.setCitySort(sort) }
}
