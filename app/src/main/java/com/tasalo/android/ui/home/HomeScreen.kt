package com.tasalo.android.ui.home

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import com.tasalo.android.ui.components.floatingContentPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tasalo.android.domain.Source
import com.tasalo.android.ui.RefreshError
import com.tasalo.android.ui.UiState
import com.tasalo.android.ui.components.edgeAwareSwipe
import com.tasalo.android.ui.components.EmptyMessage
import com.tasalo.android.ui.components.ErrorState
import com.tasalo.android.ui.components.QuoteCard
import com.tasalo.android.ui.components.RateCard
import com.tasalo.android.ui.components.SkeletonBlock
import com.tasalo.android.ui.components.StatusBanner
import com.tasalo.android.ui.components.YearCard
import com.tasalo.android.util.Format
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onSelectSource: (Source) -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = state.settings.source
    val snapshot = state.rates
    val rates = snapshot?.bySource?.get(source).orEmpty()
        .filter { !state.settings.isHidden(source, it.currency) }

    // Deslizar a izquierda/derecha cambia de fuente (El Toque / BCC / CADECA), además de tocar el selector.
    val sources = Source.entries
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 2 columnas en teléfono; 3 si el ancho es >= 600 dp (plan §4.1).
            val columns = if (maxWidth >= 600.dp) 3 else 2
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .edgeAwareSwipe(sources.indexOf(source), sources.size) { target -> onSelectSource(sources[target]) },
                contentPadding = floatingContentPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Header(
                        state.rates?.fetchedAt,
                        state.now,
                        state.refreshing,
                        onRefresh,
                        hasNotifications = state.update != null || state.unreadAlerts > 0,
                        onBell = onOpenNotifications,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SourceSelector(source, onSelectSource)
                }

                val banner = bannerFor(state)
                if (banner != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) { StatusBanner(banner.first, banner.second) }
                }

                when {
                    !state.loaded || (snapshot == null && (state.refreshing || state.ratesError == RefreshError.NONE)) -> {
                        items(4) { SkeletonBlock() }
                    }
                    snapshot == null -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ErrorState(
                                message = if (state.ratesError == RefreshError.OFFLINE) {
                                    "Sin conexión y sin datos guardados."
                                } else {
                                    "No se pudieron cargar las tasas."
                                },
                                onRetry = onRefresh,
                            )
                        }
                    }
                    rates.isEmpty() -> {
                        item(span = { GridItemSpan(maxLineSpan) }) { EmptyMessage("Sin datos para esta fuente") }
                    }
                    else -> {
                        items(rates, key = { "${source.name}:${it.currency}" }) { RateCard(it, source) }
                    }
                }

                state.year?.let { year ->
                    item(span = { GridItemSpan(maxLineSpan) }) { YearCard(year, state.now) }
                    year.quote?.let { quote ->
                        item(span = { GridItemSpan(maxLineSpan) }) { QuoteCard(quote) }
                    }
                }
            }
        }
    }
}

/** Texto del banner y si es de error, según plan §4.4. */
internal fun bannerFor(state: UiState): Pair<String, Boolean>? {
    val fetchedAt = state.rates?.fetchedAt ?: return null
    val ago = Format.relative(fetchedAt, state.now)
    return when {
        state.ratesError == RefreshError.OFFLINE -> "Sin conexión · datos de $ago" to true
        state.ratesError == RefreshError.API -> "No se pudo actualizar · datos de $ago" to true
        Format.isStale(fetchedAt, state.now) -> "Datos desactualizados · $ago" to true
        else -> null
    }
}

@Composable
private fun Header(
    fetchedAt: Instant?,
    now: Instant,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    hasNotifications: Boolean,
    onBell: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        androidx.compose.foundation.layout.Column {
            Text("TASALO", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                if (fetchedAt != null) "Actualizado ${Format.relative(fetchedAt, now)}" else "Sin datos aún",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Campana a la izquierda del botón de actualizar; el punto indica que hay algo nuevo.
            IconButton(onClick = onBell) {
                BadgedBox(badge = { if (hasNotifications) Badge() }) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notificaciones")
                }
            }
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Actualizar tasas")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSelector(selected: Source, onSelect: (Source) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Source.entries.forEachIndexed { index, source ->
            SegmentedButton(
                selected = source == selected,
                onClick = { onSelect(source) },
                shape = SegmentedButtonDefaults.itemShape(index, Source.entries.size),
            ) {
                Text(source.title)
            }
        }
    }
}
