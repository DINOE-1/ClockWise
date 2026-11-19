package com.dinoe.clockwise.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dinoe.clockwise.R
import com.dinoe.clockwise.ui.design.Clockwise
import com.dinoe.clockwise.ui.design.ClockwiseCard
import com.dinoe.clockwise.ui.design.ClockwiseColors
import com.dinoe.clockwise.ui.design.EnamelLight
import com.dinoe.clockwise.ui.design.GraphiteNight
import com.dinoe.clockwise.ui.design.GruvboxDial
import com.dinoe.clockwise.ui.design.InkButton
import com.dinoe.clockwise.ui.design.QuietButton
import com.dinoe.clockwise.ui.design.SectionLabel
import kotlinx.coroutines.launch

private const val PREFERENCES_NAME = "onboarding"
private const val COMPLETED_KEY = "completed"

class OnboardingPreferences(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val shouldShow: Boolean
        get() = !preferences.getBoolean(COMPLETED_KEY, false)

    fun markCompleted() {
        preferences.edit().putBoolean(COMPLETED_KEY, true).apply()
    }
}

private enum class OnboardingIllustration {
    CALENDAR_MONTH,
    CALENDAR_DAY,
    ALARMS,
    AI,
    TAGS,
    SETTINGS,
}

private data class OnboardingTutorialPage(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val illustration: OnboardingIllustration,
)

private val tutorialPages = listOf(
    OnboardingTutorialPage(
        R.string.onboarding_calendar_month_title,
        R.string.onboarding_calendar_month_body,
        OnboardingIllustration.CALENDAR_MONTH,
    ),
    OnboardingTutorialPage(
        R.string.onboarding_calendar_day_title,
        R.string.onboarding_calendar_day_body,
        OnboardingIllustration.CALENDAR_DAY,
    ),
    OnboardingTutorialPage(
        R.string.onboarding_alarm_title,
        R.string.onb_alarm_body,
        OnboardingIllustration.ALARMS,
    ),
    OnboardingTutorialPage(
        R.string.onboarding_ai_title,
        R.string.onboarding_ai_body,
        OnboardingIllustration.AI,
    ),
    OnboardingTutorialPage(
        R.string.onboarding_tags_title,
        R.string.onboarding_tags_body,
        OnboardingIllustration.TAGS,
    ),
    OnboardingTutorialPage(
        R.string.onboarding_settings_title,
        R.string.onboarding_settings_body,
        OnboardingIllustration.SETTINGS,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val themePreferences = remember(context) { ThemePreferences(context.applicationContext) }
    val languagePreferences = remember(context) { LanguagePreferences(context.applicationContext) }
    val themeMode by themePreferences.mode.collectAsStateWithLifecycle(
        initialValue = ThemeMode.SYSTEM,
    )
    var language by remember { mutableStateOf(languagePreferences.language) }
    val pageCount = tutorialPages.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == pageCount - 1

    val goToPage: (Int) -> Unit = { target ->
        scope.launch { pagerState.animateScrollToPage(target.coerceIn(0, pageCount - 1)) }
    }

    BackHandler(enabled = currentPage > 0) { goToPage(currentPage - 1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Clockwise.colors.paper),
    ) {
        ManualHeader(
            pageNumber = currentPage + 1,
            pageCount = pageCount,
            onSkip = onFinish,
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            if (page == 0) {
                SetupPage(
                    themeMode = themeMode,
                    language = language,
                    onThemeSelected = { mode ->
                        scope.launch { themePreferences.setMode(mode) }
                    },
                    onLanguageSelected = { selected ->
                        if (selected != language) {
                            languagePreferences.setLanguage(selected)
                            language = selected
                            activity?.recreate()
                        }
                    },
                )
            } else {
                TutorialPage(
                    page = tutorialPages[page - 1],
                    showSwipeHint = page < pageCount - 1,
                )
            }
        }
        ManualFooter(
            isLastPage = isLastPage,
            showBack = currentPage > 0,
            onBack = { goToPage(currentPage - 1) },
            onNext = { if (isLastPage) onFinish() else goToPage(currentPage + 1) },
        )
    }
}

/** Running head: the page progress as an ink bar, and the way out. */
@Composable
private fun ManualHeader(
    pageNumber: Int,
    pageCount: Int,
    onSkip: () -> Unit,
) {
    val colors = Clockwise.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinearProgressIndicator(
            progress = { pageNumber.toFloat() / pageCount },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(MaterialTheme.shapes.extraLarge),
            color = colors.ink,
            trackColor = colors.surfaceHigh,
            strokeCap = StrokeCap.Round,
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
            onClick = onSkip,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_skip),
                style = MaterialTheme.typography.labelLarge,
                color = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun ManualFooter(
    isLastPage: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = Clockwise.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.paper)
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            QuietButton(
                text = stringResource(R.string.onboarding_back),
                onClick = onBack,
            )
        }
        InkButton(
            text = stringResource(
                if (isLastPage) R.string.onboarding_start else R.string.onboarding_next,
            ),
            onClick = onNext,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SetupPage(
    themeMode: ThemeMode,
    language: AppLanguage,
    onThemeSelected: (ThemeMode) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_setup_title),
            style = type.headline,
            color = colors.ink,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onb_setup_body),
            style = type.body,
            color = colors.inkMuted,
        )

        SectionLabel(text = stringResource(R.string.settings_section_appearance))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PaletteSwatch(
                label = stringResource(R.string.onb_palette_enamel),
                palettes = listOf(EnamelLight),
                selected = themeMode == ThemeMode.LIGHT,
                onSelect = { onThemeSelected(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f),
            )
            PaletteSwatch(
                label = stringResource(R.string.onb_palette_graphite),
                palettes = listOf(GraphiteNight),
                selected = themeMode == ThemeMode.DARK,
                onSelect = { onThemeSelected(ThemeMode.DARK) },
                modifier = Modifier.weight(1f),
            )
            PaletteSwatch(
                label = stringResource(R.string.onb_palette_gruvbox),
                palettes = listOf(GruvboxDial),
                selected = themeMode == ThemeMode.GRUVBOX,
                onSelect = { onThemeSelected(ThemeMode.GRUVBOX) },
                modifier = Modifier.weight(1f),
            )
            PaletteSwatch(
                label = stringResource(R.string.theme_system),
                palettes = listOf(EnamelLight, GraphiteNight),
                selected = themeMode == ThemeMode.SYSTEM,
                onSelect = { onThemeSelected(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f),
            )
        }

        SectionLabel(text = stringResource(R.string.settings_section_language))
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LanguageOption(
                label = stringResource(R.string.language_system),
                selected = language == AppLanguage.SYSTEM,
                onSelect = { onLanguageSelected(AppLanguage.SYSTEM) },
            )
            LanguageOption(
                label = stringResource(R.string.language_traditional_chinese),
                selected = language == AppLanguage.TRADITIONAL_CHINESE,
                onSelect = { onLanguageSelected(AppLanguage.TRADITIONAL_CHINESE) },
            )
            LanguageOption(
                label = stringResource(R.string.language_english),
                selected = language == AppLanguage.ENGLISH,
                onSelect = { onLanguageSelected(AppLanguage.ENGLISH) },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onboarding_setup_hint),
            style = type.bodySmall,
            color = colors.inkMuted,
        )
    }
}

/** One look on a card: a rounded miniature of its page, its name beneath. */
@Composable
private fun PaletteSwatch(
    label: String,
    palettes: List<ClockwiseColors>,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Column(
        modifier = modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onSelect,
        ),
    ) {
        ClockwiseCard(color = if (selected) colors.wash else null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                palettes.forEach { palette ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(palette.paper),
                    ) {
                        // A miniature page: one heading, two lines of text, one signal dot.
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.62f)
                                        .height(4.dp)
                                        .clip(MaterialTheme.shapes.extraLarge)
                                        .background(palette.ink),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.44f)
                                        .height(3.dp)
                                        .clip(MaterialTheme.shapes.extraLarge)
                                        .background(palette.inkMuted),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.54f)
                                        .height(3.dp)
                                        .clip(MaterialTheme.shapes.extraLarge)
                                        .background(palette.inkMuted),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(palette.signal),
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = type.label,
            color = if (selected) colors.ink else colors.inkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A language on a rounded row; the picked one sits on `wash` with a radio button. */
@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) colors.wash else colors.surfaceLow)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.ink,
                unselectedColor = colors.inkFaint,
            ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = type.body,
            color = if (selected) colors.ink else colors.inkMuted,
        )
    }
}

@Composable
private fun TutorialPage(page: OnboardingTutorialPage, showSwipeHint: Boolean) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
    ) {
        Text(
            text = stringResource(page.titleRes),
            style = type.headline,
            color = colors.ink,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(page.bodyRes),
            style = type.body,
            color = colors.inkMuted,
        )
        Spacer(modifier = Modifier.height(22.dp))
        // Canvas illustrations need the whole card; list illustrations hug their content so the
        // card never opens onto empty paper.
        val fillsCard = page.illustration.let {
            it == OnboardingIllustration.CALENDAR_MONTH ||
                it == OnboardingIllustration.CALENDAR_DAY ||
                it == OnboardingIllustration.AI
        }
        ClockwiseCard(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillsCard) Modifier.weight(1f) else Modifier)
                .clearAndSetSemantics {},
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fillsCard) Modifier.fillMaxHeight() else Modifier)
                    .padding(16.dp),
            ) {
                when (page.illustration) {
                    OnboardingIllustration.CALENDAR_MONTH -> CalendarMonthIllustration()
                    OnboardingIllustration.CALENDAR_DAY -> CalendarDayIllustration()
                    OnboardingIllustration.ALARMS -> AlarmIllustration()
                    OnboardingIllustration.AI -> AiIllustration()
                    OnboardingIllustration.TAGS -> TagsIllustration()
                    OnboardingIllustration.SETTINGS -> SettingsIllustration()
                }
            }
        }
        if (showSwipeHint) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_swipe_hint),
                style = type.monoMeta,
                color = colors.inkMuted,
            )
        }
    }
}
