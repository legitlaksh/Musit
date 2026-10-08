package com.musit.player.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.musit.player.model.Song
import com.musit.player.ui.components.EmptyView

@Composable
fun QueueScreen(
    onBack: () -> Unit,
    viewModel: QueueViewModel = hiltViewModel()
) {
    val playback = viewModel.playback
    val queueState by playback.queueState.collectAsStateWithLifecycle()
    val upcoming = queueState.upcoming

    var showClearConfirm by remember { mutableStateOf(false) }

    // Drag-to-reorder state (display indices into `upcoming`).
    var draggingFrom by remember { mutableStateOf<Int?>(null) }
    var draggingCurrent by remember { mutableStateOf(0) }
    var accumulatedPx by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableIntStateOf(1) }

    fun endDrag() {
        draggingFrom = null
        accumulatedPx = 0f
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            if (upcoming.isNotEmpty()) {
                TextButton(onClick = { showClearConfirm = true }) {
                    Text("Clear")
                }
            }
        }

        if (queueState.current == null && upcoming.isEmpty()) {
            EmptyView(message = "Queue is empty", hint = "Play something to get started")
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = draggingFrom == null
        ) {
            queueState.current?.let { current ->
                item(key = "current") {
                    CurrentSongHeader(song = current)
                }
                if (upcoming.isNotEmpty()) {
                    item(key = "upcoming-label") {
                        Text(
                            "Up next",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            itemsIndexed(upcoming, key = { _, song -> song.id }) { index, song ->
                val dismissState = rememberSwipeToDismissBoxState()
                LaunchedEffect(dismissState.currentValue) {
                    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                        viewModel.removeAt(index)
                    }
                }

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                ) {
                    val isDragged = draggingFrom != null &&
                        upcoming.getOrNull(draggingCurrent)?.id == song.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { rowHeightPx = it.height.coerceAtLeast(1) }
                            .background(
                                if (isDragged) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { viewModel.playAt(index) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = song.artworkUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = song.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = listOfNotNull(song.artist, song.durationText)
                                    .joinToString(" • "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(32.dp)
                                .padding(4.dp)
                                .pointerInput(song.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingFrom = index
                                            draggingCurrent = index
                                            accumulatedPx = 0f
                                        },
                                        onDragEnd = { endDrag() },
                                        onDragCancel = { endDrag() },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            accumulatedPx += dragAmount.y
                                            val steps = (accumulatedPx / rowHeightPx).toInt()
                                            if (steps != 0) {
                                                val target = (draggingCurrent + steps)
                                                    .coerceIn(upcoming.indices)
                                                if (target != draggingCurrent) {
                                                    viewModel.move(draggingCurrent, target)
                                                    draggingCurrent = target
                                                }
                                                accumulatedPx -= steps * rowHeightPx
                                            }
                                        }
                                    )
                                }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear queue?") },
            text = { Text("This removes all upcoming songs.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clear()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CurrentSongHeader(song: Song) {
    Surface(
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Equalizer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Now playing",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
