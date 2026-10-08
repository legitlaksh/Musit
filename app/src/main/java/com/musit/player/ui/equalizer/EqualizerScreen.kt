package com.musit.player.ui.equalizer

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musit.player.player.PlaybackRepository

/**
 * Equalizer screen with presets and manual band controls.
 * Uses Android AudioEffect API on the player's audio session.
 */
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel()
) {
    val audioSessionId by viewModel.audioSessionId.collectAsStateWithLifecycle()
    var enabled by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf("Normal") }
    var bassBoost by remember { mutableStateOf(0) }

    // Audio effects
    var equalizer: Equalizer? by remember { mutableStateOf(null) }
    var bassBoostEffect: BassBoost? by remember { mutableStateOf(null) }

    DisposableEffect(audioSessionId) {
        if (audioSessionId != 0) {
            try {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = true
                }
                bassBoostEffect = BassBoost(0, audioSessionId).apply {
                    enabled = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        onDispose {
            equalizer?.release()
            bassBoostEffect?.release()
        }
    }

    val presets = listOf("Normal", "Pop", "Rock", "Bass", "Classical", "Vocal", "Dance")

    fun applyPreset(preset: String) {
        selectedPreset = preset
        val eq = equalizer ?: return
        try {
            when (preset) {
                "Normal" -> {
                    for (i in 0 until eq.numberOfBands) {
                        eq.setBandLevel(i.toShort(), 0)
                    }
                    bassBoostEffect?.setStrength(0)
                    bassBoost = 0
                }
                "Pop" -> {
                    // Boost mids, slight bass and treble
                    val levels = listOf(200, 300, 100, 200, 300)
                    for (i in 0 until minOf(eq.numberOfBands.toInt(), levels.size)) {
                        eq.setBandLevel(i.toShort(), levels[i].toShort())
                    }
                }
                "Rock" -> {
                    // Boost bass and treble, cut mids
                    val levels = listOf(400, 300, -200, 300, 400)
                    for (i in 0 until minOf(eq.numberOfBands.toInt(), levels.size)) {
                        eq.setBandLevel(i.toShort(), levels[i].toShort())
                    }
                }
                "Bass" -> {
                    val levels = listOf(600, 400, 100, 0, 0)
                    for (i in 0 until minOf(eq.numberOfBands.toInt(), levels.size)) {
                        eq.setBandLevel(i.toShort(), levels[i].toShort())
                    }
                    bassBoostEffect?.setStrength(800)
                    bassBoost = 80
                }
                "Classical" -> {
                    val levels = listOf(300, 200, 0, 200, 300)
                    for (i in 0 until minOf(eq.numberOfBands.toInt(), levels.size)) {
                        eq.setBandLevel(i.toShort(), levels[i].toShort())
                    }
                }
                "Vocal" -> {
                    val levels = listOf(-200, 100, 400, 300, 100)
                    for (i in 0 until minOf(eq.numberOfBands.toInt(), levels.size)) {
                        eq.setBandLevel(i.toShort(), levels[i].toShort())
                    }
                }
                "Dance" -> {
                    val levels = listOf(500, 300, 100, 200, 400)
                    for (i in 0 until minOf(eq.numberOfBands.toInt(), levels.size)) {
                        eq.setBandLevel(i.toShort(), levels[i].toShort())
                    }
                    bassBoostEffect?.setStrength(600)
                    bassBoost = 60
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text("Equalizer", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        equalizer?.enabled = it
                        bassBoostEffect?.enabled = it
                    }
                )
            }
        }

        item {
            Text("Presets", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presets) { preset ->
                    val selected = selectedPreset == preset
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (selected) Color(0xFF9D4EDD) else Color.White.copy(alpha = 0.08f))
                            .clickable { applyPreset(preset) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(preset, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text("Bass Boost", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = bassBoost.toFloat(),
                    onValueChange = {
                        bassBoost = it.toInt()
                        bassBoostEffect?.setStrength((it * 10).toInt().toShort())
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF9D4EDD),
                        activeTrackColor = Color(0xFF9D4EDD)
                    )
                )
                Text("$bassBoost%", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
                    modifier = Modifier.padding(start = 8.dp))
            }
        }

        item {
            Text("Note: Equalizer works with the current audio session. " +
                "Some devices may not support all effects.",
                color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        }
    }
}
