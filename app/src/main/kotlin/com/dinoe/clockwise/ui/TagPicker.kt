package com.dinoe.clockwise.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dinoe.clockwise.R
import com.dinoe.clockwise.data.MAX_TAGS_PER_ITEM
import com.dinoe.clockwise.data.Tag
import com.dinoe.clockwise.ui.design.Clockwise

/**
 * Twelve tag hues around the wheel from clay red to brown, at medium chroma and mid lightness so a
 * small dot reads on paper and on graphite, and nothing competes with the signal colour. Tag
 * colours come from data; this is the set that data is chosen from.
 */
val TagColorHues: List<Color> = listOf(
    Color(0xFFBF5A4A),
    Color(0xFFB4566F),
    Color(0xFF9C5AA6),
    Color(0xFF7766BE),
    Color(0xFF5273BE),
    Color(0xFF3E85A6),
    Color(0xFF3F9089),
    Color(0xFF4F8C57),
    Color(0xFF7A8F45),
    Color(0xFFA8892F),
    Color(0xFFB9793C),
    Color(0xFF8F6B52),
)

val TagColorStripeWidth = 3.dp

/** The chip that picks tags for an event or an alarm: a Material filter chip with a colour dot. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(
    allTags: List<Tag>,
    selectedTags: List<Tag>,
    onToggle: (Tag) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.tags_section),
                style = type.label,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.tag_limit_hint, MAX_TAGS_PER_ITEM),
                style = type.bodySmall,
                color = colors.inkMuted,
            )
        }
        if (allTags.isEmpty()) {
            Text(
                text = stringResource(R.string.tag_picker_empty),
                style = type.bodySmall,
                color = colors.inkMuted,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                allTags.forEach { tag ->
                    val selected = selectedTags.any { it.id == tag.id }
                    TagChip(
                        tag = tag,
                        selected = selected,
                        enabled = selected || selectedTags.size < MAX_TAGS_PER_ITEM,
                        onClick = { onToggle(tag) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagChip(
    tag: Tag,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { role = Role.Checkbox },
        shape = MaterialTheme.shapes.extraLarge,
        border = if (selected) null else BorderStroke(1.dp, colors.rule),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            selectedContainerColor = colors.wash,
            disabledContainerColor = Color.Transparent,
        ),
        leadingIcon = {
            TagMark(color = tag.color, size = 10.dp)
        },
        label = {
            Text(
                text = tag.name,
                style = type.label,
                color = when {
                    !enabled -> colors.inkFaint
                    selected -> colors.ink
                    else -> colors.inkMuted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** The palette: the no-colour fallback first, then the twelve hues as round swatches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagColorPicker(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.tag_color_label),
            style = type.label,
            color = colors.ink,
        )
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorSwatch(
                color = colors.tagFallback,
                selected = selectedColor == 0,
                description = stringResource(R.string.tag_ui_color_none),
                onClick = { onColorSelected(0) },
            )
            TagColorHues.forEachIndexed { index, color ->
                ColorSwatch(
                    color = color,
                    selected = selectedColor == color.toArgb(),
                    description = stringResource(R.string.tag_ui_color_option, index + 1),
                    onClick = { onColorSelected(color.toArgb()) },
                )
            }
        }
    }
}

/** A round swatch; the chosen one is ringed with the signal colour and ticked. */
@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val colors = Clockwise.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) colors.signal else colors.rule,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.paper,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * The tag marks of an event or an alarm, drawn as a thin bar that shares the height it is given:
 * one segment per tag colour, with rounded ends.
 */
@Composable
fun TagColorStripe(colors: List<Color>, modifier: Modifier = Modifier) {
    if (colors.isEmpty()) return
    Column(
        modifier = modifier
            .width(TagColorStripeWidth)
            .clip(RoundedCornerShape(1.dp)),
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(color),
            )
        }
    }
}

/** Tag names as small rounded pills with a colour dot. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPills(tags: List<Tag>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    val colors = Clockwise.colors
    val type = Clockwise.type
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(colors.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TagMark(color = tag.color, size = 8.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tag.name,
                    style = type.label,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A tag's colour as a round dot; tags without a colour fall back to the neutral grey. */
@Composable
private fun TagMark(color: Int, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (color == 0) Clockwise.colors.tagFallback else Color(color)),
    )
}
