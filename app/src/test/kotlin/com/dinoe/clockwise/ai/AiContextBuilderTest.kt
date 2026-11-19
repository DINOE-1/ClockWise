package com.dinoe.clockwise.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiContextBuilderTest {
    private val builder = AiContextBuilder()

    @Test
    fun countsToolResultPayloadsOfTheCurrentTurn() {
        val messages = listOf(
            ChatMessage(role = ChatRole.USER, content = "list my alarms"),
            ChatMessage(role = ChatRole.ASSISTANT, content = null),
            ChatMessage(role = ChatRole.TOOL, content = "abcdefgh", toolCallId = "call_1"),
            ChatMessage(role = ChatRole.TOOL, content = "abcd", toolCallId = "call_2"),
        )
        assertEquals(
            AiTokenBudget.estimateTokens("abcdefgh") + AiTokenBudget.estimateTokens("abcd"),
            builder.estimateToolResultTokens(messages),
        )
        assertEquals(0, builder.estimateToolResultTokens(emptyList()))
    }

    @Test
    fun staticPromptAndToolsStayWithinBudget() {
        val tokens = AiTokenBudget.estimateTokens(AiPrompt.STATIC_SYSTEM_PROMPT) +
            AiTokenBudget.estimateTokens(AiTools.definitions.toString())
        assertTrue(
            "static prompt + tools used $tokens estimated tokens",
            tokens <= AiTokenBudget.MAX_STATIC_TOKENS,
        )
    }

    @Test
    fun buildsStaticThenTurnsThenDynamicInTheFinalUserMessage() {
        val completed = listOf(
            CompletedTurn(user = "hello", assistant = "hi"),
            CompletedTurn(user = "again", assistant = "sure"),
        )
        val current = listOf(ChatMessage(role = ChatRole.USER, content = "create alarms"))
        val built = builder.build(
            completedTurns = completed,
            currentTurn = current,
            dynamicContext = "CURRENT CONTEXT now",
        )
        assertEquals(ChatRole.SYSTEM, built.messages.first().role)
        assertEquals(AiPrompt.STATIC_SYSTEM_PROMPT, built.messages.first().content)
        assertEquals(ChatRole.USER, built.messages.last().role)
        assertTrue(built.messages.last().content!!.startsWith("create alarms"))
        assertTrue(built.messages.last().content!!.contains("CURRENT CONTEXT"))
        assertTrue(built.messages.none { it.role == ChatRole.SYSTEM && it != built.messages.first() })
        assertEquals(
            listOf(ChatRole.USER, ChatRole.ASSISTANT, ChatRole.USER, ChatRole.ASSISTANT, ChatRole.USER),
            built.messages.subList(1, built.messages.size).map { it.role },
        )
        assertTrue(!built.trimmed)
    }

    @Test
    fun keepsAttachedImageAndCountsItsTokens() {
        val built = builder.build(
            completedTurns = emptyList(),
            currentTurn = listOf(
                ChatMessage(
                    role = ChatRole.USER,
                    content = "read this",
                    images = listOf(ChatImage(base64 = "abc")),
                ),
            ),
            dynamicContext = "CURRENT CONTEXT now",
        )
        val user = built.messages.last()
        assertEquals(1, user.images.size)
        assertTrue(user.content!!.contains("CURRENT CONTEXT"))
        assertTrue(built.estimatedInputTokens > AiTokenBudget.IMAGE_INPUT_TOKENS)
    }

    @Test
    fun separateDynamicUserMessageComesImmediatelyBeforeTheFinalUserMessage() {
        val current = listOf(
            ChatMessage(ChatRole.USER, "one"),
            ChatMessage(ChatRole.ASSISTANT, "ok"),
            ChatMessage(ChatRole.USER, "two"),
        )
        val built = builder.build(
            completedTurns = emptyList(),
            currentTurn = current,
            dynamicContext = "CURRENT CONTEXT now",
            embedDynamicInLastUser = false,
        )
        assertTrue(built.messages.none { it.role == ChatRole.SYSTEM && it != built.messages.first() })
        assertEquals(ChatRole.USER, built.messages.last().role)
        assertEquals("two", built.messages.last().content)
        val dynamicMessage = built.messages[built.messages.lastIndex - 1]
        assertEquals(ChatRole.USER, dynamicMessage.role)
        assertTrue(dynamicMessage.content!!.startsWith("CURRENT CONTEXT"))
        assertEquals(
            listOf(ChatRole.USER, ChatRole.ASSISTANT, ChatRole.USER, ChatRole.USER),
            built.messages.subList(1, built.messages.size).map { it.role },
        )
    }

    @Test
    fun dynamicContextMergesIntoTheLastUserMessageOfAToolRound() {
        val current = listOf(
            ChatMessage(ChatRole.USER, "create alarms"),
            ChatMessage(
                role = ChatRole.ASSISTANT,
                content = null,
                toolCalls = listOf(
                    ChatToolCall(id = "call-1", name = AiTools.CREATE_ALARMS, arguments = "{}"),
                ),
            ),
            ChatMessage(role = ChatRole.TOOL, content = "ok", toolCallId = "call-1"),
        )
        val built = builder.build(
            completedTurns = emptyList(),
            currentTurn = current,
            dynamicContext = "CURRENT CONTEXT now",
        )
        assertEquals(ChatRole.TOOL, built.messages.last().role)
        assertTrue(built.messages.none { it.role == ChatRole.SYSTEM && it != built.messages.first() })
        val user = built.messages.first { it.role == ChatRole.USER }
        val userContent = user.content!!
        assertTrue(userContent.contains("create alarms"))
        assertTrue(userContent.contains("CURRENT CONTEXT"))
    }

    @Test
    fun addsADynamicUserMessageWhenTheCurrentTurnHasNoUserMessage() {
        val built = builder.build(
            completedTurns = emptyList(),
            currentTurn = listOf(ChatMessage(ChatRole.ASSISTANT, "orphan")),
            dynamicContext = "CURRENT CONTEXT now",
        )
        assertEquals(ChatRole.USER, built.messages.last().role)
        assertTrue(built.messages.last().content!!.contains("CURRENT CONTEXT"))
        assertTrue(built.messages.none { it.role == ChatRole.SYSTEM && it != built.messages.first() })
    }

    @Test
    fun keepsAtMostSixCompletedTurns() {
        val completed = (1..8).map { CompletedTurn("u$it", "a$it") }
        val built = builder.build(
            completedTurns = completed,
            currentTurn = listOf(ChatMessage(ChatRole.USER, "now")),
            dynamicContext = "ctx",
        )
        val started = built.messages.count { it.role == ChatRole.USER } - 1
        assertEquals(6, started)
        assertTrue(built.trimmed)
        assertTrue(built.messages.any { it.content == "u3" })
        assertTrue(built.messages.none { it.content == "u1" })
    }

    @Test
    fun trimsByHistoryBudget() {
        val long = "x".repeat(4_000)
        val completed = (1..6).map { CompletedTurn("$it$long", "$it$long") }
        val built = builder.build(
            completedTurns = completed,
            currentTurn = listOf(ChatMessage(ChatRole.USER, "now")),
            dynamicContext = "ctx",
        )
        assertTrue(built.trimmed)
        val keptHistory = built.messages.count {
            it.role == ChatRole.USER && it.content?.endsWith("x") == true
        }
        assertTrue(keptHistory in 1..5)
    }

    @Test
    fun keepsAllTurnsWhenTheyFitTheHistoryBudget() {
        val completed = (1..4).map { CompletedTurn("u$it", "a$it") }
        val built = builder.build(
            completedTurns = completed,
            currentTurn = listOf(ChatMessage(ChatRole.USER, "now")),
            dynamicContext = "ctx",
        )
        val historyUsers = built.messages.filter {
            it.role == ChatRole.USER && it.content!!.startsWith("u")
        }
        assertEquals(listOf("u1", "u2", "u3", "u4"), historyUsers.map { it.content })
        assertTrue(!built.trimmed)
    }

    @Test
    fun evictsTwoTurnsWhenTheHistoryBudgetForcesAnEviction() {
        // Two short turns plus one long one still fit, so the first overflow happens at the oldest
        // turn and the batch drops one more with it. Costs derive from MAX_HISTORY_TOKENS so the
        // calibration survives future budget tuning.
        val shortCost = AiTokenBudget.MAX_HISTORY_TOKENS / 5
        val longCost = AiTokenBudget.MAX_HISTORY_TOKENS * 7 / 20
        val u3 = turnWithCost(shortCost, "u3")
        val u4 = turnWithCost(shortCost, "u4")
        val completed = listOf(
            turnWithCost(longCost, "u1"),
            turnWithCost(longCost, "u2"),
            u3,
            u4,
        )
        val built = builder.build(
            completedTurns = completed,
            currentTurn = listOf(ChatMessage(ChatRole.USER, "now")),
            dynamicContext = "ctx",
        )
        val historyUsers = built.messages.filter {
            it.role == ChatRole.USER && it.content!!.startsWith("u")
        }
        assertEquals(listOf(u3.user, u4.user), historyUsers.map { it.content })
        assertTrue(built.trimmed)
    }

    @Test
    fun keepsAContiguousNewestSuffixWhenTheBudgetEvicts() {
        // The second-oldest turn overflows the budget, so the batch eviction lands on the turn
        // before the newest three. Costs derive from MAX_HISTORY_TOKENS to survive future tuning.
        val smallCost = AiTokenBudget.MAX_HISTORY_TOKENS / 10
        val largeCost = AiTokenBudget.MAX_HISTORY_TOKENS * 3 / 10
        val completed = (1..6).map { index ->
            turnWithCost(if (index % 2 == 0) largeCost else smallCost, "u$index")
        }
        val built = builder.build(
            completedTurns = completed,
            currentTurn = listOf(ChatMessage(ChatRole.USER, "now")),
            dynamicContext = "ctx",
        )
        val keptIndices = built.messages
            .filter { it.role == ChatRole.USER && it.content!!.startsWith("u") }
            .map { it.content!!.drop(1).takeWhile(Char::isDigit).toInt() }
        assertEquals(listOf(4, 5, 6), keptIndices)
        assertTrue(built.trimmed)
    }

    @Test
    fun keepsTheNewestTurnWhenBatchEvictionWouldEmptyTheHistory() {
        val long = "x".repeat(4_000)
        val completed = (1..6).map { CompletedTurn("$it$long", "$it$long") }
        val built = builder.build(
            completedTurns = completed,
            currentTurn = listOf(ChatMessage(ChatRole.USER, "now")),
            dynamicContext = "ctx",
        )
        val keptHistory = built.messages.count {
            it.role == ChatRole.USER && it.content?.firstOrNull()?.isDigit() == true
        }
        assertEquals(1, keptHistory)
        assertTrue(built.trimmed)
    }

    @Test
    fun includesReasoningForCompletedAssistantMessages() {
        val built = builder.build(
            completedTurns = listOf(
                CompletedTurn(user = "u", assistant = "a", reasoningContent = "because"),
            ),
            currentTurn = listOf(ChatMessage(ChatRole.USER, "now")),
            dynamicContext = "ctx",
        )
        val assistant = built.messages.first { it.role == ChatRole.ASSISTANT }
        assertEquals("because", assistant.reasoningContent)
    }

    @Test
    fun canEmbedDynamicContextInTheLastUserMessage() {
        val current = listOf(
            ChatMessage(ChatRole.USER, "one"),
            ChatMessage(ChatRole.ASSISTANT, "ok"),
            ChatMessage(ChatRole.USER, "two"),
        )
        val built = builder.build(
            completedTurns = emptyList(),
            currentTurn = current,
            dynamicContext = "CURRENT CONTEXT now",
            embedDynamicInLastUser = true,
        )
        assertEquals(ChatRole.USER, built.messages.last().role)
        assertTrue(built.messages.last().content!!.contains("CURRENT CONTEXT"))
        assertTrue(built.messages.none { it.role == ChatRole.SYSTEM && it.content!!.startsWith("CURRENT") })
    }

    @Test
    fun throwsWhenContextExceedsTheHardCap() {
        val huge = "x".repeat(60_000)
        try {
            builder.build(
                completedTurns = emptyList(),
                currentTurn = listOf(ChatMessage(ChatRole.USER, huge)),
                dynamicContext = "ctx",
            )
            fail("Expected AiContextOverflowException")
        } catch (_: AiContextOverflowException) {
            // expected
        }
    }

    @Test
    fun largeModelContextRaisesTheHardCap() {
        val huge = "x".repeat(60_000)
        try {
            builder.build(
                completedTurns = emptyList(),
                currentTurn = listOf(ChatMessage(ChatRole.USER, huge)),
                dynamicContext = "ctx",
            )
            fail("Expected the default budget to reject the request")
        } catch (_: AiContextOverflowException) {
            // expected: the 60k-character turn is larger than the default 12k cap
        }
        val built = builder.build(
            completedTurns = emptyList(),
            currentTurn = listOf(ChatMessage(ChatRole.USER, huge)),
            dynamicContext = "ctx",
            modelContextLength = 1_000_000,
        )
        assertTrue(built.estimatedInputTokens > AiTokenBudget.HARD_INPUT_TOKENS)
        assertEquals(ChatRole.USER, built.messages.last().role)
    }

    @Test
    fun smallModelContextLowersTheHardCap() {
        val content = "x".repeat(20_000)
        builder.build(
            completedTurns = emptyList(),
            currentTurn = listOf(ChatMessage(ChatRole.USER, content)),
            dynamicContext = "ctx",
        )
        try {
            builder.build(
                completedTurns = emptyList(),
                currentTurn = listOf(ChatMessage(ChatRole.USER, content)),
                dynamicContext = "ctx",
                modelContextLength = 8_192,
            )
            fail("Expected AiContextOverflowException for the smaller model context")
        } catch (_: AiContextOverflowException) {
            // expected: output reserve leaves less input room than the default budget
        }
    }

    @Test
    fun hugeModelContextIsStillCapped() {
        val enormous = "x".repeat(200_000)
        try {
            builder.build(
                completedTurns = emptyList(),
                currentTurn = listOf(ChatMessage(ChatRole.USER, enormous)),
                dynamicContext = "ctx",
                modelContextLength = 1_000_000,
            )
            fail("Expected AiContextOverflowException for the derived cap")
        } catch (e: AiContextOverflowException) {
            assertTrue(e.estimatedTokens > AiTokenBudget.MAX_DERIVED_INPUT_TOKENS)
        }
    }

    @Test
    fun replaysACompletedTurnByteIdenticallyWithItsStoredTail() {
        val userText = "create alarms"
        val tail = "CURRENT CONTEXT 2026-10-29 14:30"
        val sent = builder.build(
            completedTurns = emptyList(),
            currentTurn = listOf(ChatMessage(role = ChatRole.USER, content = userText)),
            dynamicContext = tail,
        ).messages.last().content!!

        val session = AiSessionState()
        session.completeTurn(
            userText = userText,
            assistantText = "done",
            dynamicContext = tail,
        )
        val newTail = "CURRENT CONTEXT 2026-10-29 14:35"
        val rebuilt = builder.build(
            completedTurns = session.completedTurns,
            currentTurn = listOf(ChatMessage(role = ChatRole.USER, content = "next request")),
            dynamicContext = newTail,
        )
        val replayedUsers = rebuilt.messages.filter { it.role == ChatRole.USER }.dropLast(1)
        assertEquals(listOf(sent), replayedUsers.map { it.content })
        assertTrue(rebuilt.messages.last().content!!.endsWith(newTail))
        assertTrue(!replayedUsers.single().content!!.contains(newTail))
        assertTrue(rebuilt.messages.last().content!!.startsWith("next request"))
    }

    @Test
    fun replaysAnOverlongStoredTailTruncatedLikeTheCurrentTail() {
        val overlong = "CURRENT CONTEXT " + "x".repeat(4_000)
        val sent = builder.build(
            completedTurns = emptyList(),
            currentTurn = listOf(ChatMessage(role = ChatRole.USER, content = "hi")),
            dynamicContext = overlong,
        ).messages.last().content!!

        val session = AiSessionState()
        session.completeTurn(userText = "hi", assistantText = "done", dynamicContext = overlong)
        val rebuilt = builder.build(
            completedTurns = session.completedTurns,
            currentTurn = listOf(ChatMessage(role = ChatRole.USER, content = "next")),
            dynamicContext = "ctx",
        )
        val replayed = rebuilt.messages.first { it.role == ChatRole.USER }
        val truncatedTail = AiTokenBudget.truncateToTokens(
            overlong,
            AiTokenBudget.MAX_DYNAMIC_TOKENS,
        )
        assertEquals("hi\n\n$truncatedTail", replayed.content)
        assertEquals(sent, replayed.content)
    }

    /** A turn whose estimated history cost is exactly [cost] tokens; [cost] must be even. */
    private fun turnWithCost(cost: Int, name: String): CompletedTurn {
        // Both message lengths are kept divisible by 4 so the ASCII estimate ceil(chars / 4) is
        // exact, and the body size derives from [cost] instead of a hardcoded filler.
        val body = "x".repeat(2 * cost - 18 - (name.length - 2).coerceAtLeast(0))
        return CompletedTurn("$name$body", "$name$body")
    }
}
