package com.dinoe.clockwise.ai

import kotlinx.serialization.json.JsonArray

data class BuiltContext(
    val messages: List<ChatMessage>,
    val trimmed: Boolean,
    val estimatedInputTokens: Int,
)

class AiContextOverflowException(
    val estimatedTokens: Int,
    val inputBudgetTokens: Int = AiTokenBudget.HARD_INPUT_TOKENS,
) : Exception(
    "Context uses $estimatedTokens tokens, over the $inputBudgetTokens-token input budget",
)

/**
 * Builds the request context in a cache-friendly order:
 * static system prompt -> compact completed turns -> current turn (with the dynamic tail folded
 * into a user message). A request never ends with a system message: dynamic context is either
 * merged into the final user message or emitted as its own user message immediately before it.
 * Completed turns replay the dynamic tail they stored, appended to their user message by the same
 * helper used for the current turn, so a replayed turn is byte-identical to what was sent.
 * Tool payloads from completed turns are never included.
 * History evictions are batched: when a completed turn no longer fits the history budget, one
 * more older turn is dropped with it (the newest turn always stays) so the cached request prefix
 * is rewritten half as often.
 */
class AiContextBuilder {
    fun build(
        completedTurns: List<CompletedTurn>,
        currentTurn: List<ChatMessage>,
        dynamicContext: String,
        staticPrompt: String = AiPrompt.STATIC_SYSTEM_PROMPT,
        toolDefinitions: JsonArray? = AiTools.definitions,
        embedDynamicInLastUser: Boolean = true,
        modelContextLength: Long? = null,
    ): BuiltContext {
        val keptTurns = ArrayDeque<CompletedTurn>()
        var historyTokens = 0
        for (turn in completedTurns.asReversed()) {
            if (keptTurns.size >= AiTokenBudget.MAX_COMPLETED_TURNS) break
            val cost = estimateTurn(turn)
            if (keptTurns.isNotEmpty() &&
                historyTokens + cost > AiTokenBudget.MAX_HISTORY_TOKENS
            ) {
                if (keptTurns.size > 1) keptTurns.removeFirst()
                break
            }
            keptTurns.addFirst(turn)
            historyTokens += cost
        }

        val dynamic = AiTokenBudget.truncateToTokens(
            dynamicContext,
            AiTokenBudget.MAX_DYNAMIC_TOKENS,
        )

        val messages = ArrayList<ChatMessage>(keptTurns.size * 2 + currentTurn.size + 2)
        messages += ChatMessage(role = ChatRole.SYSTEM, content = staticPrompt)
        keptTurns.forEach { turn ->
            messages += ChatMessage(role = ChatRole.USER, content = replayedUserContent(turn))
            messages += ChatMessage(
                role = ChatRole.ASSISTANT,
                content = turn.assistant,
                reasoningContent = turn.reasoningContent,
            )
        }
        appendCurrentTurn(
            messages = messages,
            currentTurn = currentTurn,
            dynamic = dynamic,
            embedDynamicInLastUser = embedDynamicInLastUser,
        )
        // Defensive invariant: a caller-supplied system message must never be the last message.
        if (messages.lastOrNull()?.role == ChatRole.SYSTEM) {
            messages += ChatMessage(
                role = ChatRole.USER,
                content = dynamic.ifBlank { "Continue." },
            )
        }

        val estimated = estimateMessages(messages) + toolDefinitionTokens(toolDefinitions)
        val hardInputTokens = AiTokenBudget.inputBudgetFor(modelContextLength)
        if (estimated > hardInputTokens) {
            throw AiContextOverflowException(estimated, hardInputTokens)
        }
        return BuiltContext(
            messages = messages,
            trimmed = keptTurns.size < completedTurns.size,
            estimatedInputTokens = estimated,
        )
    }

    private fun appendCurrentTurn(
        messages: MutableList<ChatMessage>,
        currentTurn: List<ChatMessage>,
        dynamic: String,
        embedDynamicInLastUser: Boolean,
    ) {
        val lastUserIndex = currentTurn.indexOfLast { it.role == ChatRole.USER }
        if (lastUserIndex < 0) {
            messages += currentTurn
            messages += ChatMessage(role = ChatRole.USER, content = dynamic)
            return
        }
        if (dynamic.isBlank()) {
            messages += currentTurn
            return
        }
        if (embedDynamicInLastUser) {
            currentTurn.forEachIndexed { index, message ->
                messages += if (index == lastUserIndex) {
                    message.copy(content = mergeDynamicIntoUser(message.content, dynamic))
                } else {
                    message
                }
            }
        } else {
            currentTurn.forEachIndexed { index, message ->
                if (index == lastUserIndex) {
                    messages += ChatMessage(role = ChatRole.USER, content = dynamic)
                }
                messages += message
            }
        }
    }

    /** Replays the user text with the stored tail, truncated exactly like the current tail. */
    private fun replayedUserContent(turn: CompletedTurn): String {
        val stored = turn.dynamicContext
            ?.let { AiTokenBudget.truncateToTokens(it, AiTokenBudget.MAX_DYNAMIC_TOKENS) }
        return if (stored.isNullOrEmpty()) turn.user else mergeDynamicIntoUser(turn.user, stored)
    }

    /** Single source of the tail format shared by the current turn and completed-turn replays. */
    private fun mergeDynamicIntoUser(content: String?, dynamic: String): String = buildString {
        append(content.orEmpty())
        appendLine()
        appendLine()
        append(dynamic)
    }

    /** Tool schemas are immutable, so the default definitions reuse the cached token estimate. */
    private fun toolDefinitionTokens(toolDefinitions: JsonArray?): Int = when {
        toolDefinitions == null -> 0
        toolDefinitions === AiTools.definitions -> AiTools.definitionTokens
        else -> AiTokenBudget.estimateTokens(toolDefinitions.toString())
    }

    fun estimateTurn(turn: CompletedTurn): Int =
        AiTokenBudget.estimateMessageTokens(turn.user) +
            AiTokenBudget.estimateMessageTokens(turn.assistant) +
            AiTokenBudget.estimateTokens(turn.reasoningContent) +
            AiTokenBudget.estimateTokens(turn.dynamicContext)

    fun estimateMessages(messages: List<ChatMessage>): Int =
        messages.sumOf { message ->
            AiTokenBudget.estimateMessageTokens(
                content = message.content,
                toolCallTokens = message.toolCalls.sumOf { call ->
                    AiTokenBudget.estimateTokens(call.name) +
                        AiTokenBudget.estimateTokens(call.arguments)
                } + AiTokenBudget.estimateTokens(message.reasoningContent),
                imageCount = message.images.size,
            )
        }

    /** Estimated tokens spent on tool result payloads already appended to a turn. */
    fun estimateToolResultTokens(messages: List<ChatMessage>): Int =
        messages.sumOf { message ->
            if (message.role == ChatRole.TOOL) {
                AiTokenBudget.estimateTokens(message.content)
            } else {
                0
            }
        }
}
