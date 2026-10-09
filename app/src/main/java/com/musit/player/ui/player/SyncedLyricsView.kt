package com.musit.player.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musit.player.data.repository.LyricLine

/**
 * Karaoke-style synced lyrics view.
 * Highlights the current line based on playback position and auto-scrolls.
 */
@Composable
fun SyncedLyricsView(
    lines: List<LyricLine>,
    currentPositionMs: Long,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Find current line index
    val currentIndex = lines.indexOfLast { it.timestampMs <= currentPositionMs }
        .coerceAtLeast(0)

    // Auto-scroll to current line
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(
                index = (currentIndex - 2).coerceAtLeast(0)
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(lines) { index, line ->
            val isCurrent = index == currentIndex
            val isPast = index < currentIndex

            val textColor by animateColorAsState(
                targetValue = when {
                    isCurrent -> Color(0xFF9D4EDD)
                    isPast -> Color.White.copy(alpha = 0.4f)
                    else -> Color.White.copy(alpha = 0.7f)
                },
                label = "lyricColor"
            )
            val alpha by animateFloatAsState(
                targetValue = if (isCurrent) 1f else 0.7f,
                label = "lyricAlpha"
            )
            val fontSize = if (isCurrent) 18.sp else 16.sp
            val fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal

            Text(
                text = line.text,
                color = textColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alpha)
                    .padding(horizontal = 24.dp)
            )
        }
    }
}
