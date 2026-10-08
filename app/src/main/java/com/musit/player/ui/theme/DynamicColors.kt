package com.musit.player.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts dynamic theme colors from album artwork.
 * Like Spotify: the UI adapts to the music cover.
 */
data class DynamicColors(
    val vibrant: Color = Color(0xFF9D4EDD),
    val darkVibrant: Color = Color(0xFF1A1A1A),
    val muted: Color = Color(0xFF2A2A2A)
)

@Composable
fun rememberDynamicColors(artworkUrl: String?): DynamicColors {
    var colors by remember { mutableStateOf(DynamicColors()) }
    val context = LocalContext.current

    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrEmpty()) {
            colors = DynamicColors()
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                val drawable = result.drawable
                val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val vibrant = palette.getVibrantColor(0xFF9D4EDD.toInt())
                    val darkVibrant = palette.getDarkVibrantColor(0xFF1A1A1A.toInt())
                    val muted = palette.getMutedColor(0xFF2A2A2A.toInt())
                    colors = DynamicColors(
                        vibrant = Color(vibrant),
                        darkVibrant = Color(darkVibrant),
                        muted = Color(muted)
                    )
                }
            } catch (e: Exception) {
                // Keep default colors
            }
        }
    }
    return colors
}
