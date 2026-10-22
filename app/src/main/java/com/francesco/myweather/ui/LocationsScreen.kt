package com.francesco.myweather.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.francesco.myweather.data.GeoResult
import com.francesco.myweather.data.Place
import com.francesco.myweather.data.db.SavedPlace

@Composable
fun LocationsScreen(
    gpsPlace: Place?,
    saved: List<SavedPlace>,
    search: SearchState,
    onQueryChange: (String) -> Unit,
    onAdd: (GeoResult) -> Unit,
    onRemove: (SavedPlace) -> Unit,
    onMove: (SavedPlace, Int) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onQueryChange(it)
                },
                label = { Text("Search for a city") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (search) {
            SearchState.Idle -> Unit
            SearchState.Loading -> item {
                CircularProgressIndicator(Modifier.padding(16.dp))
            }
            is SearchState.Error -> item { Text(search.message, color = MaterialTheme.colorScheme.error) }
            is SearchState.Results -> {
                if (search.places.isEmpty()) item { Text("No matches") }
                items(search.places, key = { it.id }) { result ->
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            onAdd(result)
                            query = ""
                            onQueryChange("")
                        },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(result.name, style = MaterialTheme.typography.titleMedium)
                            result.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }

        item { Text("Your places", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }

        if (gpsPlace != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📍 ${gpsPlace.name}", style = MaterialTheme.typography.titleMedium)
                        Text("My location", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (saved.isEmpty()) item { Text("No saved places yet. Search above to add one.") }

        items(saved, key = { it.id }) { place ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(place.name, style = MaterialTheme.typography.titleMedium)
                        place.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    TextButton(onClick = { onMove(place, -1) }) { Text("↑") }
                    TextButton(onClick = { onMove(place, 1) }) { Text("↓") }
                    TextButton(onClick = { onRemove(place) }) { Text("✕") }
                }
            }
        }
    }
}
