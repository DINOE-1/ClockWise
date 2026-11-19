package com.dinoe.clockwise.ui.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's colour roles. The dial is monochrome — ink on a porcelain face — with one vermillion
 * signal reserved for the current moment and for destruction. Depth is carried by the surface
 * ladder ([surfaceLow] upward), never by a printed hairline, so a screen separates its layers with
 * tonal steps the way a Material surface does.
 */
@Immutable
data class ClockwiseColors(
    val isDark: Boolean,
    val paper: Color,
    val sheet: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val rule: Color,
    val ruleStrong: Color,
    val signal: Color,
    val onSignal: Color,
    val wash: Color,
    val washSignal: Color,
    val tagFallback: Color,
    val surfaceLow: Color,
    val surfaceContainer: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
)

/** Enamel dial: a cool porcelain face, green-black ink, one vermillion signal. */
val EnamelLight = ClockwiseColors(
    isDark = false,
    paper = Color(0xFFF4F6F3),
    sheet = Color(0xFFFBFCFA),
    ink = Color(0xFF101B17),
    inkMuted = Color(0xFF4E5C56),
    inkFaint = Color(0xFF7A857F),
    rule = Color(0xFFD6DDD7),
    ruleStrong = Color(0xFF93A09A),
    signal = Color(0xFFC13B21),
    onSignal = Color(0xFFFFFBF9),
    wash = Color(0xFFDCE9E2),
    washSignal = Color(0xFFF9E2DC),
    tagFallback = Color(0xFF7C8A84),
    surfaceLow = Color(0xFFEEF2ED),
    surfaceContainer = Color(0xFFE8EDE7),
    surfaceHigh = Color(0xFFE0E6DF),
    surfaceHighest = Color(0xFFD8DFD7),
)

/** Graphite night: the same dial after dark. */
val GraphiteNight = ClockwiseColors(
    isDark = true,
    paper = Color(0xFF0E1211),
    sheet = Color(0xFF1E2523),
    ink = Color(0xFFE7ECE8),
    inkMuted = Color(0xFF9BA6A1),
    inkFaint = Color(0xFF74807A),
    rule = Color(0xFF333B37),
    ruleStrong = Color(0xFF4F5A55),
    signal = Color(0xFFFF7357),
    onSignal = Color(0xFF2B1009),
    wash = Color(0xFF25372F),
    washSignal = Color(0xFF3A211A),
    tagFallback = Color(0xFF8B9790),
    surfaceLow = Color(0xFF171C1A),
    surfaceContainer = Color(0xFF1E2523),
    surfaceHigh = Color(0xFF28302D),
    surfaceHighest = Color(0xFF333B37),
)

/** Gruvbox Material, mapped onto the same roles so the structure stays the app's own. */
val GruvboxDial = ClockwiseColors(
    isDark = true,
    paper = Color(0xFF1D2021),
    sheet = Color(0xFF2A2A2A),
    ink = Color(0xFFEBDBB2),
    inkMuted = Color(0xFFBDAE93),
    inkFaint = Color(0xFF928374),
    rule = Color(0xFF3C3836),
    ruleStrong = Color(0xFF504945),
    signal = Color(0xFFEA6962),
    onSignal = Color(0xFF1D2021),
    wash = Color(0xFF333B2F),
    washSignal = Color(0xFF3A2A26),
    tagFallback = Color(0xFF7C6F64),
    surfaceLow = Color(0xFF232526),
    surfaceContainer = Color(0xFF2A2C2D),
    surfaceHigh = Color(0xFF313435),
    surfaceHighest = Color(0xFF3C3836),
)

val LocalClockwiseColors = staticCompositionLocalOf { EnamelLight }

/** Short accessor for the current palette and type scale: `Clockwise.colors.signal`. */
object Clockwise {
    val colors: ClockwiseColors
        @Composable
        @ReadOnlyComposable
        get() = LocalClockwiseColors.current

    val type: ClockwiseType
        @Composable
        @ReadOnlyComposable
        get() = LocalClockwiseType.current
}

internal fun ClockwiseColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    // What sits on top of an ink fill: the brightest step in light, the page in dark.
    val onInk = if (isDark) paper else sheet
    return base.copy(
        primary = ink,
        onPrimary = onInk,
        primaryContainer = wash,
        onPrimaryContainer = ink,
        inversePrimary = if (isDark) paper else sheet,
        secondary = inkMuted,
        onSecondary = onInk,
        secondaryContainer = surfaceHigh,
        onSecondaryContainer = ink,
        tertiary = signal,
        onTertiary = onSignal,
        tertiaryContainer = washSignal,
        onTertiaryContainer = ink,
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = surfaceContainer,
        onSurfaceVariant = inkMuted,
        surfaceTint = ink,
        inverseSurface = ink,
        inverseOnSurface = paper,
        error = signal,
        onError = onSignal,
        errorContainer = washSignal,
        onErrorContainer = ink,
        outline = ruleStrong,
        outlineVariant = rule,
        scrim = Color(0xFF000000),
        surfaceBright = sheet,
        surfaceDim = surfaceContainer,
        surfaceContainerLowest = if (isDark) paper else sheet,
        surfaceContainerLow = surfaceLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHighest,
    )
}
