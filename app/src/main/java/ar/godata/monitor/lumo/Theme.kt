package ar.godata.monitor.lumo

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ar.godata.monitor.lumo.foundation.ripple

object AppTheme {
    val colors: Colors
        @ReadOnlyComposable @Composable
        get() = LocalColors.current

    val typography: Typography
        @ReadOnlyComposable @Composable
        get() = LocalTypography.current
}

@Composable
fun AppTheme(
    // go-data's identity is dark; the light palette is kept only for Lumo's previews.
    isDarkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val rippleIndication = ripple()
    val colors = if (isDarkTheme) DarkColors else LightColors
    val selectionColors = rememberTextSelectionColors(colors)
    val typography = provideTypography()

    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides typography,
        LocalIndication provides rippleIndication,
        LocalTextSelectionColors provides selectionColors,
        LocalContentColor provides colors.contentColorFor(colors.background),
        LocalTextStyle provides typography.body1,
        content = content,
    )
}

@Composable
fun contentColorFor(color: Color): Color {
    return AppTheme.colors.contentColorFor(color)
}

@Composable
internal fun rememberTextSelectionColors(colorScheme: Colors): TextSelectionColors {
    val primaryColor = colorScheme.primary
    return remember(primaryColor) {
        TextSelectionColors(
            handleColor = primaryColor,
            backgroundColor = primaryColor.copy(alpha = TextSelectionBackgroundOpacity),
        )
    }
}

internal const val TextSelectionBackgroundOpacity = 0.4f

/** Spacing, radii and sizes shared by every screen. */
object Dimens {
    /** Side padding of screens. */
    val gutter = 16.dp
    /** Space between cards. */
    val gap = 12.dp
    val cardPadding = 16.dp
    val cardRadius = 22.dp
    val iconTile = 44.dp
    val iconTileSmall = 36.dp
    val iconTileRadius = 14.dp
    val barHeight = 6.dp
    val navRadius = 26.dp
}
