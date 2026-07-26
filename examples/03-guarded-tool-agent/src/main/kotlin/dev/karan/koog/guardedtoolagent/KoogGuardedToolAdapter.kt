package dev.karan.koog.guardedtoolagent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

class KoogGuardedToolAdapter(
    private val tools: GuardedWorkspaceTools,
    private val apiKey: String? = System.getenv("OPENAI_API_KEY")
) {

    fun proposeAction(request: String): GuardedAction {
        val normalized = request.trim()
        if (normalized.isBlank()) {
            return GuardedAction(action = "list", targetPath = ".", operationCategory = "read")
        }

        val apiKeyValue = apiKey?.trim()
        if (apiKeyValue.isNullOrBlank()) {
            return GuardedAction(action = "list", targetPath = ".", operationCategory = "read")
        }

        val modelResponse = runBlocking {
            val agent = AIAgent(
                promptExecutor = simpleOpenAIExecutor(apiKeyValue),
                systemPrompt = "You are a guarded assistant. Reply with one action name only: list, read, write, delete.",
                llmModel = OpenAIModels.Chat.GPT4o
            )
            agent.run("$request\nRespond with one action name only.")
        }

        return when (modelResponse.trim().lowercase()) {
            "read" -> GuardedAction(action = "read", targetPath = ".", operationCategory = "read")
            "write" -> GuardedAction(action = "write", targetPath = "draft.txt", operationCategory = "write")
            "delete" -> GuardedAction(action = "delete", targetPath = "draft.txt", operationCategory = "delete", destructive = true)
            else -> GuardedAction(action = "list", targetPath = ".", operationCategory = "read")
        }
    }

    fun executeSuggestedAction(request: String, targetPath: String): Any? {
        val proposed = proposeAction(request).copy(targetPath = targetPath)
        return tools.execute(proposed)
    }
}
