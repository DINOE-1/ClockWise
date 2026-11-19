package com.dinoe.clockwise.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiSessionStateTest {
    @Test
    fun completeTurnStoresTheUserTextTrimmedButUntruncated() {
        val session = AiSessionState()
        val userText = "u" + "x".repeat(AiTokenBudget.MAX_USER_CHARS)
        session.completeTurn("  $userText  ", "ok")
        assertEquals(userText, session.completedTurns.single().user)
    }

    @Test
    fun dynamicContextRoundTripsThroughCompletedTurns() {
        val session = AiSessionState()
        session.completeTurn(
            userText = "hi",
            assistantText = "ok",
            reasoningContent = "because",
            dynamicContext = "CURRENT CONTEXT now",
        )
        session.completeTurn("again", "sure")
        assertEquals(
            listOf("CURRENT CONTEXT now", null),
            session.completedTurns.map { it.dynamicContext },
        )
        assertNull(session.completedTurns.last().dynamicContext)
    }

    @Test
    fun assistantAndReasoningKeepTheirStoreLimits() {
        val session = AiSessionState()
        session.completeTurn(
            userText = "hi",
            assistantText = "a".repeat(AiTokenBudget.COMPLETED_ASSISTANT_CHARS + 1),
            reasoningContent = "r".repeat(AiTokenBudget.COMPLETED_REASONING_CHARS + 1),
        )
        val turn = session.completedTurns.single()
        assertEquals(AiTokenBudget.COMPLETED_ASSISTANT_CHARS, turn.assistant.length)
        assertEquals(AiTokenBudget.COMPLETED_REASONING_CHARS, turn.reasoningContent!!.length)
    }

    @Test
    fun promptCacheKeyIsStablePerSessionAndRemintedOnClearAndRestore() {
        val session = AiSessionState()
        val initial = session.promptCacheKey
        session.recordUsage(AiUsage())
        assertEquals(initial, session.promptCacheKey)
        session.clear()
        val afterClear = session.promptCacheKey
        assertNotEquals(initial, afterClear)
        session.restore(emptyList(), emptyList(), AiUsage(), 0, "restored-key")
        assertEquals("restored-key", session.promptCacheKey)
        session.restore(emptyList(), emptyList(), AiUsage(), 0, null)
        assertNotEquals("restored-key", session.promptCacheKey)
    }
}
