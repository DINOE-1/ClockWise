package com.dinoe.clockwise.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dinoe.clockwise.R
import com.dinoe.clockwise.data.Event
import com.dinoe.clockwise.data.Tag
import com.dinoe.clockwise.ui.design.Clockwise
import java.time.LocalTime

/** The tag stripe printed at the left edge of a timeline block. */
internal val TimelineStripeWidth = 3.dp

/** Below this height a block has room for its title and nothing else. */
private const val TimeLabelSeconds = 1_800

/** A narrow column only fits the start hour, so it shows earlier than a full-width block. */
private const val CompactTimeLabelSeconds = 900

/**
 * One event on an hour axis. The day view draws it at full width and reports the press so the
 * timeline stops scrolling under a drag; the week view draws it in a narrow column with only its
 * start hour and makes it tappable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TimelineEventBlock(
    event: Event,
    startSeconds: Int,
    endSeconds: Int,
    tags: List<Tag>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onPointerDownChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = Clockwise.colors
    val type = Clockwise.type
    val tagColors = tags.mapNotNull { tag -> if (tag.color == 0) null else Color(tag.color) }
    val durationSeconds = (endSeconds - startSeconds).coerceAtLeast(0)
    val timeRange = eventTimeRangeLabel(event)
    val startHour = rememberHourLabelFormatter()
        .format(LocalTime.ofSecondOfDay(startSeconds.toLong()))
    val label = if (compact) startHour else timeRange
    val showsLabel = durationSeconds >= if (compact) CompactTimeLabelSeconds else TimeLabelSeconds
    val description = buildString {
        append(event.title)
        if (timeRange != null) append(", ").append(timeRange)
    }

    val base = modifier
        .clip(MaterialTheme.shapes.small)
        .background(colors.wash)
        .semantics(mergeDescendants = true) { contentDescription = description }
    val interactive = when {
        onClick != null || onLongClick != null -> base.combinedClickable(
            onClick = onClick ?: {},
            onLongClick = onLongClick,
        )

        else -> base
    }
    // Report the whole press, not just the drag: the timeline has to stop scrolling from the moment
    // the finger lands on a block, or it wins the gesture before the drag starts.
    val pressed = if (onPointerDownChange != null) {
        interactive.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onPointerDownChange(true)
                try {
                    do {
                        val pointer = awaitPointerEvent()
                    } while (pointer.changes.any { it.pressed })
                } finally {
                    onPointerDownChange(false)
                }
            }
        }
    } else {
        interactive
    }

    Box(modifier = pressed) {
        if (tagColors.isNotEmpty()) {
            val blockShape = MaterialTheme.shapes.small
            TagColorStripe(
                colors = tagColors,
                modifier = Modifier
                    .width(TimelineStripeWidth)
                    .fillMaxHeight()
                    // Only the leading edge of the stripe meets the block's rounded corner.
                    .clip(
                        RoundedCornerShape(
                            topStart = blockShape.topStart,
                            topEnd = CornerSize(0.dp),
                            bottomEnd = CornerSize(0.dp),
                            bottomStart = blockShape.bottomStart,
                        ),
                    ),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = TimelineStripeWidth + if (compact) 5.dp else 9.dp,
                    end = if (compact) 4.dp else 8.dp,
                    top = if (compact) 3.dp else 6.dp,
                    bottom = 4.dp,
                ),
        ) {
            if (!compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.ink,
                        maxLines = if (durationSeconds >= TimeLabelSeconds) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (event.isRecurring) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.series_row_label),
                            tint = colors.inkMuted,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
            if (showsLabel && label != null) {
                Text(
                    text = label,
                    style = type.monoMicro,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
