package com.dinoe.clockwise.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The top of a screen: what this screen is, with its controls on the same line. It behaves like a
 * Material top app bar — porcelain behind, no printed rule — so the content below it scrolls under
 * a surface instead of under a border.
 */
@Composable
fun ClockwiseScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    titleStyle: TextStyle? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.paper)
            .statusBarsPadding()
            .heightIn(min = 64.dp)
            .padding(start = if (leading == null) 20.dp else 4.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(2.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (overline != null) {
                Text(
                    text = overline,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                style = titleStyle ?: MaterialTheme.typography.headlineSmall,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = actions,
        )
    }
}

/** A group of entries under one heading. The heading is a word, not a bulleted marker. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = type.title,
            color = colors.inkMuted,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (meta != null) {
            Text(text = meta, style = type.monoMeta, color = colors.inkMuted)
        }
    }
}

/**
 * One entry: a rounded surface, a fixed leading area for the number that anchors it (a time, a day,
 * a count), the content beside it, and optional controls on the right. Entries are cards separated
 * by space; a list that reads as one sheet can pass [merged] to drop the card fill.
 *
 * The content sits on the gutter's first line by default, which is what a multi-line entry needs.
 * An entry that can shrink to a single line beside a tall gutter value passes
 * [contentAlignment] so it stays centered instead of hanging off the top.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimetableRow(
    modifier: Modifier = Modifier,
    gutterWidth: Dp = 52.dp,
    divider: Boolean = false,
    selected: Boolean = false,
    contentAlignment: Alignment.Vertical = Alignment.Top,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    gutter: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Clockwise.colors
    val interactive = when {
        onClick != null || onLongClick != null -> modifier.combinedClickable(
            onClick = onClick ?: {},
            onLongClick = onLongClick,
        )

        else -> modifier
    }
    Surface(
        modifier = interactive.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.wash else colors.surfaceLow,
        contentColor = colors.ink,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(modifier = Modifier.width(gutterWidth)) { gutter() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(contentAlignment)
                    .padding(start = 12.dp),
            ) {
                content()
            }
            trailing()
        }
    }
}

/** A hairline. Content layers are separated by tonal surfaces; this is only for dense rows. */
@Composable
fun Rule(modifier: Modifier = Modifier, strong: Boolean = false) {
    val colors = Clockwise.colors
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = if (strong) colors.ruleStrong else colors.rule,
    )
}

/** Empty is a place to start, not a mood: say what is missing and offer the verb that fixes it. */
@Composable
fun EmptyNotice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceLow,
        contentColor = colors.ink,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.ink)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = type.body,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(20.dp))
                InkButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

/** The app's primary action: an ink pill. */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Clockwise.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.ink,
            contentColor = if (colors.isDark) colors.paper else colors.sheet,
            disabledContainerColor = colors.surfaceHigh,
            disabledContentColor = colors.inkFaint,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** The secondary action: an outlined pill of the same weight. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Clockwise.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, colors.ruleStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.ink,
            disabledContentColor = colors.inkFaint,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** The destructive action: the one signal colour, never decoration. */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Clockwise.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.signal,
            contentColor = colors.onSignal,
            disabledContainerColor = colors.washSignal,
            disabledContentColor = colors.inkFaint,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A rounded panel that groups related rows: the one container a screen uses for content that has
 * to read as a unit. Tonal, not outlined — depth comes from the surface ladder.
 */
@Composable
fun ClockwiseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Clockwise.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = color ?: colors.surfaceLow,
        contentColor = colors.ink,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
    }
}

/**
 * A form field in the app's Material shape: a filled rounded container, the label riding the top
 * edge, no outline until it is focused. Every text field in the app goes through this so a form
 * never mixes outlined and filled fields.
 */
@Composable
fun ClockwiseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = Clockwise.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        singleLine = singleLine,
        isError = isError,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceLow,
            unfocusedContainerColor = colors.surfaceLow,
            disabledContainerColor = colors.surfaceContainer,
            errorContainerColor = colors.washSignal,
            focusedBorderColor = colors.ink,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = colors.signal,
            focusedTextColor = colors.ink,
            unfocusedTextColor = colors.ink,
            focusedLabelColor = colors.ink,
            unfocusedLabelColor = colors.inkMuted,
            cursorColor = colors.ink,
        ),
        trailingIcon = trailingIcon,
        supportingText = when {
            supportingText != null -> {
                { Text(text = supportingText) }
            }

            isError -> null
            else -> null
        },
    )
}

/** A dialog body in the Material shape: rounded on every corner, elevated off the page. */
@Composable
fun SheetSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Clockwise.colors
    Surface(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.sheet,
        contentColor = colors.ink,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(content = content)
    }
}

/**
 * A bottom sheet for content that grows from the bottom edge — the Material home for what used to
 * be a dialog with a printed edge. Callers own their own padding inside [content].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockwiseBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Clockwise.colors
    // Open at the content's full height. A half-height stop hides fields behind a drag the reader
    // has no reason to expect; the expanded state still scrolls when content outgrows the screen.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )
    key(Unit) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier,
            // In dark the page and the sheet are both near-black; a step up the ladder is what makes
            // the sheet read as a surface instead of a hole in the page.
            containerColor = if (colors.isDark) colors.surfaceHigh else colors.sheet,
            contentColor = colors.ink,
            dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle(color = colors.ruleStrong) },
        ) {
            Column(content = content)
        }
    }
}
