package com.example.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

object LocationGeocoderHelper {

    private val cache = ConcurrentHashMap<String, String>()
    private val homeWordRegex = Regex("\\bhome\\b", RegexOption.IGNORE_CASE)

    fun formatCoordinates(lat: Double, lng: Double): String {
        return "${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lng)}"
    }

    fun needsPlaceResolution(locationName: String?): Boolean {
        val label = locationName?.trim().orEmpty()
        return label.isBlank() ||
            label.startsWith("GPS Fix", ignoreCase = true) ||
            label.startsWith("Child GPS", ignoreCase = true) ||
            label.startsWith("On-Demand GPS Fix", ignoreCase = true) ||
            label == "Current Location"
    }

    fun sanitizePlaceLabel(rawLabel: String?, lat: Double, lng: Double): String {
        val coords = formatCoordinates(lat, lng)
        val cleaned = rawLabel
            ?.replace(homeWordRegex, "Residence Area")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.trim(',', '-', ' ')
            .orEmpty()

        return if (cleaned.isBlank()) {
            "Area ($coords)"
        } else if (cleaned.contains(coords)) {
            cleaned
        } else {
            "$cleaned ($coords)"
        }
    }

    fun formatAddressToPlaceName(address: Address?): String {
        if (address == null) return ""
        val streetOrArea = listOfNotNull(
            address.subLocality?.takeIf { it.isNotBlank() && !homeWordRegex.containsMatchIn(it) },
            address.thoroughfare?.takeIf { it.isNotBlank() && !homeWordRegex.containsMatchIn(it) },
            address.featureName?.takeIf {
                it.isNotBlank() &&
                    !homeWordRegex.containsMatchIn(it) &&
                    !it.all { ch -> ch.isDigit() || ch == '-' }
            },
            address.locality?.takeIf { it.isNotBlank() && !homeWordRegex.containsMatchIn(it) },
            address.subAdminArea?.takeIf { it.isNotBlank() && !homeWordRegex.containsMatchIn(it) },
            address.adminArea?.takeIf { it.isNotBlank() && !homeWordRegex.containsMatchIn(it) }
        )
            .map { it.trim() }
            .distinct()
            .take(2)

        if (streetOrArea.isNotEmpty()) {
            return streetOrArea.joinToString(", ")
        }
        val line0 = address.getAddressLine(0)?.trim().orEmpty()
        return line0.replace(homeWordRegex, "Residence Area")
    }

    suspend fun resolvePlaceLabel(context: Context, lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        val coordsKey = formatCoordinates(lat, lng)
        cache[coordsKey]?.let { return@withContext it }

        if (!Geocoder.isPresent()) {
            return@withContext "Area ($coordsKey)"
        }

        val address: Address? = try {
            val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    try {
                        geocoder.getFromLocation(lat, lng, 1) { list ->
                            if (cont.isActive) cont.resume(list.firstOrNull())
                        }
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
            }
        } catch (_: Exception) {
            null
        }

        val placeText = formatAddressToPlaceName(address)
        val formatted = sanitizePlaceLabel(placeText, lat, lng)
        if (placeText.isNotBlank()) {
            cache[coordsKey] = formatted
        }
        formatted
    }
}
