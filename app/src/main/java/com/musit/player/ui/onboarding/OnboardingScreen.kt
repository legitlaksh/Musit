package com.musit.player.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spotify-style onboarding: 3 questions on first launch
 * to personalize music recommendations.
 */
@Composable
fun OnboardingScreen(
    onComplete: (Set<String>, Set<String>, String) -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var selectedGenres by remember { mutableStateOf(setOf<String>()) }
    var selectedArtists by remember { mutableStateOf(setOf<String>()) }
    var selectedMood by remember { mutableStateOf("") }

    val genres = listOf(
        "Pop", "Hip-Hop", "Rock", "Electronic", "R&B",
        "Bollywood", "Punjabi", "Indie", "Jazz", "Classical"
    )
    val artists = listOf(
        "Arijit Singh", "AP Dhillon", "Diljit Dosanjh", "Taylor Swift",
        "Drake", "The Weeknd", "A.R. Rahman", "Shreya Ghoshal",
        "Bad Bunny", "BTS", "Ed Sheeran", "Neha Kakkar"
    )
    val moods = listOf(
        "Chill" to "Relax and unwind",
        "Workout" to "High energy beats",
        "Party" to "Dance all night",
        "Focus" to "Concentration mode",
        "Romantic" to "Love songs"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A1A2E), Color(0xFF0A0A0A))
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress
        Text(
            "${step + 1} of 3",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (step) {
            0 -> {
                Text(
                    "What genres do you love?",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Pick at least 3 to get started",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(genres.chunked(2)) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { genre ->
                                val selected = genre in selectedGenres
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) Color(0xFF9D4EDD)
                                            else Color.White.copy(alpha = 0.1f)
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) Color(0xFF9D4EDD)
                                            else Color.Transparent,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            selectedGenres = if (selected) {
                                                selectedGenres - genre
                                            } else {
                                                selectedGenres + genre
                                            }
                                        }
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(genre, color = Color.White, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                Text(
                    "Pick your favorite artists",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "We'll play more from them",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(artists) { artist ->
                        val selected = artist in selectedArtists
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) Color(0xFF9D4EDD).copy(alpha = 0.3f)
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .border(
                                    1.dp,
                                    if (selected) Color(0xFF9D4EDD)
                                    else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedArtists = if (selected) {
                                        selectedArtists - artist
                                    } else {
                                        selectedArtists + artist
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                artist,
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Text("✓", color = Color(0xFF9D4EDD), fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
            2 -> {
                Text(
                    "What's your vibe?",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Pick one that fits you best",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(moods) { (mood, desc) ->
                        val selected = mood == selectedMood
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) Brush.linearGradient(
                                        listOf(Color(0xFF6A1B9A), Color(0xFF9D4EDD))
                                    ) else Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.08f),
                                            Color.White.copy(alpha = 0.05f)
                                        )
                                    )
                                )
                                .clickable { selectedMood = mood }
                                .padding(20.dp)
                        ) {
                            Text(mood, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(desc, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                TextButton(onClick = { step-- }) {
                    Text("Back", color = Color.White.copy(alpha = 0.7f))
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            val canProceed = when (step) {
                0 -> selectedGenres.size >= 3
                1 -> true // Artists optional
                2 -> selectedMood.isNotBlank()
                else -> false
            }

            Button(
                onClick = {
                    if (step < 2) {
                        step++
                    } else {
                        onComplete(selectedGenres, selectedArtists, selectedMood)
                    }
                },
                enabled = canProceed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9D4EDD),
                    disabledContainerColor = Color.White.copy(alpha = 0.1f)
                ),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
            ) {
                Text(
                    if (step < 2) "Next" else "Done",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }
    }
}
