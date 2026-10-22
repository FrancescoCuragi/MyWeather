package com.francesco.myweather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.francesco.myweather.data.Place

/** One forecast page per place; swipe sideways to move between them. */
@Composable
fun ForecastPager(
    places: List<Place>,
    forecasts: Map<String, WeatherUiState>,
    focusKey: String?,
    onFocusHandled: () -> Unit,
    onRefresh: (Place) -> Unit,
) {
    if (places.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "No places yet.\nAllow location access, or add a city in the Places tab.",
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val pagerState = rememberPagerState { places.size }

    LaunchedEffect(focusKey, places) {
        val index = places.indexOfFirst { it.key == focusKey }
        if (index >= 0) {
            pagerState.animateScrollToPage(index)
            onFocusHandled()
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (places.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                places.forEachIndexed { i, place ->
                    val active = i == pagerState.currentPage
                    Text(
                        if (place.isCurrentLocation) "📍" else "●",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        },
                        modifier = Modifier.padding(horizontal = 3.dp),
                    )
                }
            }
        }
        HorizontalPager(state = pagerState, key = { places[it].key }, modifier = Modifier.weight(1f)) { page ->
            val place = places[page]
            HomeScreen(
                state = forecasts[place.key] ?: WeatherUiState.Loading,
                onRefresh = { onRefresh(place) },
            )
        }
    }
}
