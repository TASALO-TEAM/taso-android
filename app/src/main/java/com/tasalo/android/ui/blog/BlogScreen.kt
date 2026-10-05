package com.tasalo.android.ui.blog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tasalo.android.domain.BLOG_ACCOUNTS
import com.tasalo.android.domain.BlogPost
import com.tasalo.android.domain.blogAccountLabel
import com.tasalo.android.openUrl
import com.tasalo.android.ui.BlogUiState
import com.tasalo.android.ui.components.EmptyMessage
import com.tasalo.android.ui.components.ErrorState
import com.tasalo.android.ui.components.GlassCard
import com.tasalo.android.ui.components.MarkdownBlockView
import com.tasalo.android.ui.components.SkeletonBlock
import com.tasalo.android.ui.components.StatusBanner
import com.tasalo.android.util.MarkdownParser
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault())

/** Pestaña Blog: lista de los últimos posts y, al tocar uno, el post completo dentro de la app. */
@Composable
fun BlogScreen(
    state: BlogUiState,
    onRefresh: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val post = state.selected?.let { permlink -> state.posts.firstOrNull { it.permlink == permlink } }
    if (post != null) {
        PostDetail(post, onClose, modifier)
    } else {
        PostList(state, onRefresh, onSelectAccount, onOpen, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostList(
    state: BlogUiState,
    onRefresh: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier,
) {
    // Deslizar a izquierda/derecha cambia de cuenta, igual que entre fuentes de tasas.
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Blog",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, enabled = !state.loading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            BLOG_ACCOUNTS.forEachIndexed { index, handle ->
                SegmentedButton(
                    selected = handle == state.account,
                    onClick = { onSelectAccount(handle) },
                    shape = SegmentedButtonDefaults.itemShape(index, BLOG_ACCOUNTS.size),
                ) {
                    Text(blogAccountLabel(handle))
                }
            }
        }
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.account) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragCancel = { total = 0f },
                            onDragEnd = {
                                val current = BLOG_ACCOUNTS.indexOf(state.account)
                                val target = when {
                                    total <= -swipeThreshold -> current + 1
                                    total >= swipeThreshold -> current - 1
                                    else -> current
                                }
                                BLOG_ACCOUNTS.getOrNull(target)?.let(onSelectAccount)
                            },
                            onHorizontalDrag = { _, dx -> total += dx },
                        )
                    },
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state.posts.isEmpty() && (!state.loaded || state.loading) -> items(3) { SkeletonBlock(height = 150) }
                    state.posts.isEmpty() && state.error -> item {
                        ErrorState("No se pudieron cargar los posts. Revisa tu conexión.", onRetry = onRefresh)
                    }
                    state.posts.isEmpty() -> item { EmptyMessage("Todavía no hay posts en ${blogAccountLabel(state.account)}.") }
                    else -> {
                        if (state.error) {
                            item {
                                StatusBanner("No se pudo actualizar. Se muestran los últimos posts guardados.", isError = true)
                            }
                        }
                        items(state.posts, key = { it.permlink }) { post ->
                            PostCard(post, onClick = { onOpen(post.permlink) })
                        }
                    }
                }
            }
        }
    }
}

/** Portada (si hay) + título + resumen + fecha. */
@Composable
private fun PostCard(post: BlogPost, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        if (post.cover != null) {
            AsyncImage(
                model = post.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (post.summary.isNotEmpty()) {
                Text(
                    post.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            post.createdAt?.let {
                Text(
                    dateFormat.format(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Post completo con el lector Markdown. Lista perezosa: las imágenes se cargan al llegar a ellas. */
@Composable
private fun PostDetail(post: BlogPost, onClose: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val blocks = remember(post.body) { MarkdownParser.parse(post.body) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text("Blog", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(post.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    post.createdAt?.let {
                        Text(
                            dateFormat.format(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(blocks) { block -> MarkdownBlockView(block, images = true) }
            item {
                Button(onClick = { openUrl(context, post.url) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Abrir en Ecency")
                }
            }
        }
    }
}
