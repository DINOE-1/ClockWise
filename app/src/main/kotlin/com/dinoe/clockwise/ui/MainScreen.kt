package com.dinoe.clockwise.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dinoe.clockwise.R
import com.dinoe.clockwise.ui.design.Clockwise

/** The five tabs, in bar order. */
internal enum class MainTab(val order: Int) {
    CALENDAR(0),
    ALARM(1),
    AI(2),
    TAGS(3),
    SETTINGS(4),
}

private const val TAB_TRANSITION_MILLIS = 220
private const val TAB_FADE_OUT_MILLIS = 120

@Composable
fun MainScreen(
    openEventId: Long? = null,
    onOpenEventConsumed: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.CALENDAR) }
    var pendingEditEventId by remember { mutableStateOf<Long?>(null) }
    var openAiSettingsRequest by remember { mutableStateOf(false) }

    LaunchedEffect(openEventId) {
        val id = openEventId ?: return@LaunchedEffect
        pendingEditEventId = id
        selectedTab = MainTab.CALENDAR
        onOpenEventConsumed()
    }

    Scaffold(
        bottomBar = {
            TabBar(selected = selectedTab, onSelect = { selectedTab = it })
        },
        // Each tab screen owns its own window insets; only the bar is added here.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        containerColor = Clockwise.colors.paper,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                transitionSpec = {
                    // Material's tab change: the incoming page grows in while the outgoing one
                    // fades, so the bar stays put and only the page changes.
                    val enter = fadeIn(tween(TAB_TRANSITION_MILLIS)) +
                        scaleIn(
                            animationSpec = tween(
                                durationMillis = TAB_TRANSITION_MILLIS,
                                easing = FastOutSlowInEasing,
                            ),
                            initialScale = 0.96f,
                        )
                    val exit = fadeOut(tween(TAB_FADE_OUT_MILLIS))
                    enter togetherWith exit
                },
                label = "mainTabTransition",
            ) { tab ->
                when (tab) {
                    MainTab.ALARM -> AlarmScreen(
                        onOpenEvent = { eventId ->
                            pendingEditEventId = eventId
                            selectedTab = MainTab.CALENDAR
                        },
                    )

                    MainTab.AI -> AiScreen(
                        onOpenSettings = {
                            openAiSettingsRequest = true
                            selectedTab = MainTab.SETTINGS
                        },
                    )

                    MainTab.TAGS -> TagsScreen()
                    MainTab.SETTINGS -> SettingsScreen(
                        openAiSettingsRequest = openAiSettingsRequest,
                        onOpenAiSettingsRequestHandled = { openAiSettingsRequest = false },
                    )
                    MainTab.CALENDAR -> CalendarScreen(
                        pendingEventId = pendingEditEventId,
                        onPendingEventConsumed = { pendingEditEventId = null },
                    )
                }
            }
        }
    }
}

/**
 * The Material navigation bar: a tonal strip under the page, the current tab carried by a pill
 * behind its icon.
 */
@Composable
private fun TabBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    val colors = Clockwise.colors
    NavigationBar(
        containerColor = colors.surfaceLow,
        contentColor = colors.ink,
        tonalElevation = 0.dp,
    ) {
        MainTab.entries.forEach { tab ->
            val active = tab == selected
            NavigationBarItem(
                selected = active,
                onClick = { onSelect(tab) },
                icon = { TabIcon(tab = tab, active = active) },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.ink,
                    selectedTextColor = colors.ink,
                    indicatorColor = colors.wash,
                    unselectedIconColor = colors.inkMuted,
                    unselectedTextColor = colors.inkMuted,
                ),
            )
        }
    }
}

@Composable
private fun TabIcon(tab: MainTab, active: Boolean) {
    val modifier = Modifier.size(22.dp)
    val tint = if (active) Clockwise.colors.ink else Clockwise.colors.inkMuted
    when (tab) {
        MainTab.CALENDAR -> Icon(Icons.Filled.DateRange, null, tint = tint, modifier = modifier)
        MainTab.ALARM -> Icon(Icons.Filled.Notifications, null, tint = tint, modifier = modifier)
        MainTab.AI -> Icon(
            painter = painterResource(R.drawable.ic_ai),
            contentDescription = null,
            tint = tint,
            modifier = modifier,
        )
        MainTab.TAGS -> Icon(Icons.AutoMirrored.Filled.List, null, tint = tint, modifier = modifier)
        MainTab.SETTINGS -> Icon(Icons.Filled.Settings, null, tint = tint, modifier = modifier)
    }
}

private val MainTab.labelRes: Int
    get() = when (this) {
        MainTab.CALENDAR -> R.string.tab_calendar
        MainTab.ALARM -> R.string.tab_alarm
        MainTab.AI -> R.string.tab_ai
        MainTab.TAGS -> R.string.tab_tags
        MainTab.SETTINGS -> R.string.tab_settings
    }
