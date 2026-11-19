package com.dinoe.clockwise.ui

import android.graphics.Color as AndroidColor
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dinoe.clockwise.ui.design.ClockwiseTypo
import com.dinoe.clockwise.ui.design.ClockwiseTypography
import com.dinoe.clockwise.ui.design.EnamelLight
import com.dinoe.clockwise.ui.design.GraphiteNight
import com.dinoe.clockwise.ui.design.GruvboxDial
import com.dinoe.clockwise.ui.design.LocalClockwiseColors
import com.dinoe.clockwise.ui.design.LocalClockwiseType
import com.dinoe.clockwise.ui.design.toColorScheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Rounded on the Material 3 scale: fields and chips at [Shapes.small], cards and rows at
 * [Shapes.large], buttons and navigation indicators past [Shapes.extraLarge] so they read as pills.
 * Everything the app draws at scale comes from here, so a screen never picks its own radius.
 */
val ClockwiseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ClockwiseTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val colors = when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDark) GraphiteNight else EnamelLight
        ThemeMode.DARK -> GraphiteNight
        ThemeMode.LIGHT -> EnamelLight
        ThemeMode.GRUVBOX -> GruvboxDial
    }
    CompositionLocalProvider(
        LocalClockwiseColors provides colors,
        LocalClockwiseType provides ClockwiseTypo,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = ClockwiseTypography,
            shapes = ClockwiseShapes,
            content = content,
        )
    }
}

/**
 * Keeps the status/navigation bar icons in sync with the app theme. Activities should call
 * [enableEdgeToEdge] with [SystemBarStyle.auto] in `onCreate`, then use this effect to react to
 * a user-forced light/dark/Gruvbox theme that differs from the system setting.
 */
@Composable
fun ApplySystemBarStyle(activity: ComponentActivity, themeMode: ThemeMode) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK, ThemeMode.GRUVBOX -> true
        ThemeMode.LIGHT -> false
    }
    LaunchedEffect(activity, darkTheme) {
        val statusBarStyle = SystemBarStyle.auto(
            lightScrim = AndroidColor.TRANSPARENT,
            darkScrim = AndroidColor.TRANSPARENT,
            detectDarkMode = { darkTheme },
        )
        val navigationBarStyle = SystemBarStyle.auto(
            lightScrim = AndroidColor.TRANSPARENT,
            darkScrim = AndroidColor.TRANSPARENT,
            detectDarkMode = { darkTheme },
        )
        activity.enableEdgeToEdge(
            statusBarStyle = statusBarStyle,
            navigationBarStyle = navigationBarStyle,
        )
    }
}

/**
 * Locale used for all date and time labels. Reading it through Compose keeps formatters in sync
 * with configuration changes (the activity is recreated on a locale change).
 */
@Composable
fun rememberLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) { configuration.locales[0] }
}

/** Clock formatter that follows the device 12/24-hour setting and the current locale. */
@Composable
fun rememberClockFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = rememberLocale()
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(locale, is24Hour) {
        val skeleton = if (is24Hour) "Hm" else "hm"
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}

/**
 * A clock time whose hour is always two digits. Times that stand in a column — the alarm list's —
 * have to share one width, and a lone "9:00" next to "10:00" drops the colon out of the line the
 * mono face exists to draw.
 */
fun DateTimeFormatter.formatPaddedHour(time: LocalTime): String {
    val text = format(time)
    val hour = FirstDigitRun.find(text) ?: return text
    return if (hour.value.length == 1) {
        text.replaceRange(hour.range, "0${hour.value}")
    } else {
        text
    }
}

private val FirstDigitRun = Regex("\\d+")

/**
 * Hour labels for the day timeline: the hour alone, with the locale's day-period marker. Using the
 * full clock formatter here would print minutes the ruler does not need.
 */
@Composable
fun rememberHourLabelFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = rememberLocale()
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(locale, is24Hour) {
        val skeleton = if (is24Hour) "H" else "ha"
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}

/** Fully localized date format for the requested [FormatStyle]. */
@Composable
fun rememberLocalizedDateFormatter(
    style: FormatStyle = FormatStyle.MEDIUM,
): DateTimeFormatter {
    val locale = rememberLocale()
    return remember(locale, style) {
        DateTimeFormatter.ofLocalizedDate(style).withLocale(locale)
    }
}

/** Localized formatter for an ICU date skeleton such as `yMMMM` or `MMMdEEEE`. */
@Composable
fun rememberDatePatternFormatter(skeleton: String): DateTimeFormatter {
    val locale = rememberLocale()
    return remember(locale, skeleton) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}

/** Monday-first weekday labels (short form) supplied by the current locale. */
@Composable
fun rememberWeekdayLabels(): List<String> {
    val locale = rememberLocale()
    return remember(locale) {
        (1..7).map { day ->
            DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale)
        }
    }
}
