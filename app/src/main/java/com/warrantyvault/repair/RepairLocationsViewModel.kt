package com.warrantyvault.repair

import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Where the current search origin came from. */
sealed interface OriginSource {
    object None : OriginSource
    data class Geocoded(val query: String) : OriginSource
    object DeviceLocation : OriginSource
}

data class RepairLocationsUiState(
    val searchText: String = "",
    val origin: Pair<Double, Double>? = null,
    val originSource: OriginSource = OriginSource.None,
    val originLabel: String? = null,
    val results: List<RepairLocation> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasSearched: Boolean = false,
    /** Purpose explanation shown before the location permission dialog. */
    val showLocationRationale: Boolean = false
)

/**
 * Orchestrates repair location search. The provider is injected and replaceable;
 * privacy: only coordinates/search text ever reach the provider.
 */
class RepairLocationsViewModel(
    private val provider: RepairLocationProvider
) : ViewModel() {

    private val _state = MutableStateFlow(RepairLocationsUiState())
    val state: StateFlow<RepairLocationsUiState> = _state

    /** Result of a geocode attempt (null = not found). */
    var geocoderFactory: ((android.content.Context) -> Geocoder?) = { ctx ->
        if (Geocoder.isPresent()) Geocoder(ctx, Locale.getDefault()) else null
    }

    fun updateSearchText(text: String) {
        _state.value = _state.value.copy(searchText = text)
    }

    /** Geocode the typed query (Nominatim-independent; uses Android Geocoder) then search. */
    fun searchByQuery(context: android.content.Context) {
        val query = _state.value.searchText.trim()
        if (query.isEmpty()) {
            _state.value = _state.value.copy(errorMessage = "Enter a city, area or postcode.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null, hasSearched = true)
            val coords = withContext(Dispatchers.IO) {
                runCatching {
                    geocoderFactory(context)?.getFromLocationName(query, 1)?.firstOrNull()?.let {
                        it.latitude to it.longitude
                    }
                }.getOrNull()
            }
            if (coords == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Couldn't find \"$query\". Try a nearby city or postcode."
                )
                return@launch
            }
            _state.value = _state.value.copy(
                origin = coords,
                originSource = OriginSource.Geocoded(query),
                originLabel = query
            )
            runSearch(coords.first, coords.second)
        }
    }

    /** Called after the user granted location permission. */
    fun onDeviceLocationReady(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                origin = latitude to longitude,
                originSource = OriginSource.DeviceLocation,
                originLabel = "My location",
                showLocationRationale = false,
                isLoading = true,
                hasSearched = true,
                errorMessage = null
            )
            runSearch(latitude, longitude)
        }
    }

    /** User tapped "Use my location": show purpose first, then the system dialog. */
    fun onUseMyLocationRequested() {
        _state.value = _state.value.copy(showLocationRationale = true)
    }

    fun dismissLocationRationale() {
        _state.value = _state.value.copy(showLocationRationale = false)
    }

    /** Product category biasing, set by the caller screen if desired. */
    fun setProductContext(category: String?) {
        _state.value = _state.value.copy(results = _state.value.results)
        productCategory = category
    }

    private var productCategory: String? = null

    private suspend fun runSearch(lat: Double, lon: Double) {
        try {
            val results = provider.searchNearby(
                latitude = lat,
                longitude = lon,
                radiusMeters = RepairLocationProvider.DEFAULT_RADIUS,
                productContext = productCategory?.let { ProductContext(it) }
            )
            _state.value = _state.value.copy(isLoading = false, results = results)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                results = emptyList(),
                errorMessage = "Couldn't reach the location service. Please try again shortly."
            )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    class Factory(private val provider: RepairLocationProvider) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RepairLocationsViewModel(provider) as T
    }
}
