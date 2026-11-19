package com.dinoe.clockwise.ui

import com.dinoe.clockwise.data.MAX_TAGS_PER_ITEM
import org.junit.Assert.assertEquals
import org.junit.Test

class TagSelectionTest {
    @Test
    fun togglingAddsThenRemovesATag() {
        val selection = toggleTagSelection(longArrayOf(), tagId = 7)
        assertEquals(listOf(7L), selection.toList())
        assertEquals(emptyList<Long>(), toggleTagSelection(selection, tagId = 7).toList())
    }

    @Test
    fun selectionStopsAtThePerItemMaximum() {
        var selected = longArrayOf()
        repeat(MAX_TAGS_PER_ITEM) { index -> selected = toggleTagSelection(selected, index.toLong()) }
        assertEquals(MAX_TAGS_PER_ITEM, selected.size)
        // The next tag is silently ignored; removing an existing tag still works.
        assertEquals(selected.toList(), toggleTagSelection(selected, tagId = 999).toList())
        assertEquals(
            selected.drop(1),
            toggleTagSelection(selected, tagId = selected.first()).toList(),
        )
    }
}
