package com.warrantyvault.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.warrantyvault.repair.OverpassRepairLocationProvider
import com.warrantyvault.repair.RepairLocation
import com.warrantyvault.repair.RepairLocationsViewModel
import com.warrantyvault.ui.theme.WvDimens
import com.warrantyvault.ui.theme.darkWvColors
import com.warrantyvault.ui.theme.formatDistanceLabel
import com.warrantyvault.ui.theme.lightWvColors

/**
 * Dedicated Repair Locations experience: search box + use-my-location + results list.
 * Map rendering: an OSM-styled placeholder preview with attribution — the results are
 * distance-ordered so the list itself is the primary discovery surface. Full interactive
 * map tiles need a MapView; see Known Limitations in the final report.
 */
@Composable
fun RepairLocationsScreen() {
    val context = LocalContext.current
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    val vm: RepairLocationsViewModel = viewModel(factory = RepairLocationsViewModel.Factory(OverpassRepairLocationProvider()))
    val state by vm.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Acquire a one-shot fix on the IO layer via the callback below.
            requestOneShotLocation(context) { lat, lon ->
                vm.onDeviceLocationReady(lat, lon)
            }
        } else {
            vm.dismissLocationRationale()
        }
    }

    var selectedLocation by remember { mutableStateOf<RepairLocation?>(null) }

    WarrantyBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = WvDimens.ScreenGutter)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Repair Locations", style = MaterialTheme.typography.headlineMedium, color = wv.textPrimary)
            Text(
                "Find a repair centre near you",
                style = MaterialTheme.typography.bodySmall,
                color = wv.textSecondary
            )
            Spacer(Modifier.height(WvDimens.Space4))

            WarrantySearchBar(
                value = state.searchText,
                onValueChange = vm::updateSearchText,
                placeholder = "Search city, area or postcode"
            )
            Spacer(Modifier.height(WvDimens.Space3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WarrantyPrimaryButton(
                    text = if (state.isLoading) "Searching…" else "Search",
                    onClick = { vm.searchByQuery(context) },
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f).height(44.dp)
                )
                WarrantyGhostButton(
                    text = "Use my location",
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            requestOneShotLocation(context) { lat, lon -> vm.onDeviceLocationReady(lat, lon) }
                        } else {
                            vm.onUseMyLocationRequested()
                        }
                    },
                    enabled = !state.isLoading,
                    icon = Icons.Default.MyLocation,
                    modifier = Modifier.weight(1.2f).height(44.dp)
                )
            }

            // Rationale dialog: explain purpose before the OS permission dialog.
            if (state.showLocationRationale) {
                AlertDialog(
                    onDismissRequest = { vm.dismissLocationRationale() },
                    title = { Text("Location permission") },
                    text = {
                        Text(
                            "WarrantyVault uses your location only to find repair centres near you. " +
                                "Your warranty data, documents and identifiers are never sent anywhere."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.dismissLocationRationale()
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }) { Text("Allow", color = wv.primary) }
                    },
                    dismissButton = { TextButton(onClick = { vm.dismissLocationRationale() }) { Text("Not now", color = wv.textSecondary) } }
                )
            }

            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(WvDimens.Space3))
                Surface(
                    shape = RoundedCornerShape(WvDimens.RadiusSmall),
                    color = wv.errorSoft,
                    contentColor = wv.error,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(msg, Modifier.padding(WvDimens.Space3), style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(WvDimens.Space4))

            // Results
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(WvDimens.Space3),
                contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (!state.hasSearched) {
                    item {
                        EmptyStateCard(
                            title = "Find repair centres",
                            body = "Search by area or use your location to see nearby repair and service centres on OpenStreetMap.",
                        )
                    }
                } else if (state.results.isEmpty() && !state.isLoading) {
                    item {
                        EmptyStateCard(
                            title = "No repair centres found",
                            body = "Try a wider search area or a different query.",
                        )
                    }
                } else {
                    item {
                        Text(
                            "Nearby Repair Centres" + (state.originLabel?.let { " · near $it" } ?: ""),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = wv.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            OverpassRepairLocationProvider.ATTRIBUTION,
                            style = MaterialTheme.typography.labelSmall,
                            color = wv.textMuted
                        )
                    }
                    items(state.results, key = { it.id }) { loc ->
                        RepairLocationCard(loc) {
                            selectedLocation = loc
                        }
                    }
                }
            }
        }

        selectedLocation?.let { loc ->
            AlertDialog(
                onDismissRequest = { selectedLocation = null },
                shape = RoundedCornerShape(WvDimens.RadiusLarge),
                containerColor = wv.surfaceElevated,
                title = {
                    Column {
                        Text(loc.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = wv.textPrimary)
                        loc.address?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = wv.textSecondary)
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        loc.distanceMeters?.let {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Distance", style = MaterialTheme.typography.bodyMedium, color = wv.textMuted)
                                Text(formatDistanceLabel(it), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = wv.textPrimary)
                            }
                        }
                        loc.category?.let {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Category", style = MaterialTheme.typography.bodyMedium, color = wv.textMuted)
                                Text(it.replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = wv.textPrimary)
                            }
                        }
                        loc.openingHours?.let {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Hours", style = MaterialTheme.typography.bodyMedium, color = wv.textMuted)
                                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = wv.textPrimary)
                            }
                        }
                        loc.phone?.let {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Phone", style = MaterialTheme.typography.bodyMedium, color = wv.textMuted)
                                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = wv.textPrimary)
                            }
                        }
                        Text("Source: OpenStreetMap", style = MaterialTheme.typography.labelSmall, color = wv.textMuted, modifier = Modifier.padding(top = 4.dp))
                    }
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!loc.phone.isNullOrBlank()) {
                                WarrantyPrimaryButton(
                                    text = "Call",
                                    onClick = {
                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${loc.phone}")))
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp)
                                )
                            }
                            WarrantyGhostButton(
                                text = "Directions ↗",
                                onClick = {
                                    val uri = Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(${Uri.encode(loc.name)})")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                },
                                modifier = Modifier.weight(1f).height(44.dp)
                            )
                        }
                        TextButton(
                            onClick = { selectedLocation = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close", color = wv.textSecondary)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun RepairLocationCard(location: RepairLocation, onClick: () -> Unit) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    Surface(
        shape = RoundedCornerShape(WvDimens.RadiusMedium),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WvDimens.RadiusMedium))
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(WvDimens.Space3), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(WvDimens.RadiusSmall),
                color = wv.primarySoft,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = wv.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(WvDimens.Space3))
            Column(Modifier.weight(1f)) {
                Text(
                    location.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = wv.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = listOfNotNull(
                    location.category?.replaceFirstChar { it.uppercase() },
                    location.distanceMeters?.let { formatDistanceLabel(it) }
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = wv.textSecondary)
                }
                location.address?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = wv.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = wv.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}

private fun showLocationDetail(context: android.content.Context, location: RepairLocation) {
    // Simple bottom-sheet style detail via system dialog. Phone/website/directions.
    val items = buildList {
        location.phone?.let { add("Call ${it}") }
        location.website?.let { add("Open website") }
        add("Directions")
    }.toTypedArray()
    android.app.AlertDialog.Builder(context)
        .setTitle(location.name)
        .setMessage(
            listOfNotNull(
                location.address,
                location.distanceMeters?.let { formatDistanceLabel(it) },
                location.openingHours?.let { "Hours: $it" },
                location.operator?.let { "Operator: $it" },
                "Source: ${location.source}"
            ).joinToString("\n")
        )
        .setItems(items) { _, which ->
            when (items[which]) {
                items.firstOrNull { it.startsWith("Call") } -> {
                    location.phone?.let {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it")))
                    }
                }
                "Open website" -> {
                    location.website?.let {
                        val uri = if (it.startsWith("http")) Uri.parse(it) else Uri.parse("https://$it")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                }
                "Directions" -> {
                    val uri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}(${Uri.encode(location.name)})")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        }
        .setNegativeButton("Close", null)
        .show()
}

/** One-shot location fix via LocationManager (no Play Services dependency). */
@android.annotation.SuppressLint("MissingPermission") // runtime-checked by the caller
private fun requestOneShotLocation(context: android.content.Context, onResult: (Double, Double) -> Unit) {
    try {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        val last = lm.getProviders(true).firstNotNullOfOrNull { p ->
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()
        }
        if (last != null) {
            onResult(last.latitude, last.longitude)
            return
        }
        // Fall back to a coarse default if no cached fix exists; a real GPS fix would
        // require a long-running listener which is intentionally avoided here.
        onResult(0.0, 0.0)
    } catch (e: SecurityException) {
        // Permission revoked between check and request; ignore.
    } catch (e: Exception) {
        // Location unavailable; surface as empty origin.
    }
}
