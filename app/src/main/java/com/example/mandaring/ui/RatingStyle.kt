package com.example.mandaring.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.mandaring.pitch.Rating

/** Traffic-light colours for a rating. Fixed, as the theme's own colours follow the wallpaper. */
@Composable
fun Rating.color(): Color = when (this) {
    Rating.CLOSE -> Color(0xFF2E9E5B)
    Rating.NEAR -> Color(0xFFD99A00)
    Rating.OFF -> MaterialTheme.colorScheme.error
}
