package net.maerkl.kassierapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KassierColorScheme = lightColorScheme(
    primary = Green800,
    onPrimary = Color.White,
    secondary = Green600,
    onSecondary = Color.White,
    background = Green50,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    error = ErrorRed,
    onError = Color.White,
    surfaceVariant = Green200,
)

@Composable
fun KassierappTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KassierColorScheme,
        typography = Typography,
        content = content
    )
}
