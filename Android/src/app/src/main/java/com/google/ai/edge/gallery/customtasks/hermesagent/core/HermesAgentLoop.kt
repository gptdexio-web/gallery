package com.google.ai.edge.gallery.customtasks.hermesagent.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "HermesAgentLoop"

data class AgentMessage(
  val role: String,
  val content: String,
  val toolCalls: List<ToolCall>? = null,
  val toolResults: List<ToolResult>? = null,
  val timestamp: Long = System.currentTimeMillis()
)

data class ToolCall(
  val id: String,
  val name: String,
  val arguments: Map<String, Any>
)

data class AgentState(
  val messages: List<AgentMessage> = emptyList(),
  val isProcessing: Boolean = false,
  val currentTool: String? = null,
  val memoryStats: Map<String, Any> = emptyMap(),
  val activeSkills: List<String> = emptyList()
)

class HermesAgentLoop(private val context: Context) {

  private val tools = HermesTools(context)
  private val skills = HermesSkills(context)
  private val messages = mutableListOf<AgentMessage>()
  private val toolCallHistory = mutableListOf<ToolCall>()

  private val systemPrompt = buildSystemPrompt()

  private fun buildSystemPrompt(): String {
    return """You are Hermes Agent, a self-improving AI assistant running entirely on-device.

IDENTITY:
- You are a personal AI agent that learns and improves from every interaction
- You have persistent memory that survives across conversations
- You can use tools to interact with the device and the web
- You have a modular skill system for specialized tasks

CORE CAPABILITIES:
1. MEMORY SYSTEM
   - Store important information for future recall
   - Search past conversations and stored memories
   - Organize memories by category and importance
   - Automatically manage memory lifecycle

2. SKILL SYSTEM
   - Access specialized skills for different tasks
   - Skills provide instructions and tool combinations
   - Can load custom skills from disk
   - Skills improve through use

3. TOOL SYSTEM
   - memoryRecall / memoryStore: Persistent memory management
   - webSearch: Search the web for information
   - readFile / writeFile: File operations
   - executeCommand: Run device commands
   - scheduleTask: Schedule tasks for later
   - listSkills: Discover available skills
   - delegateTask: Spawn subagents for parallel work

4. AGENT LOOP
   - Think step-by-step before acting
   - Use the right tool for each task
   - Learn from results and adjust approach
   - Provide clear explanations of actions

BEHAVIOR RULES:
1. Always explain what you're doing and why
2. Use memory to remember important information
3. Break complex tasks into smaller steps
4. Be proactive: suggest relevant skills and tools
5. Learn from mistakes and improve
6. Respect user privacy and security

${skills.getSkillPrompt()}

CURRENT TIME: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}

Remember: You are running on-device, so you have full privacy and control. All processing happens locally."""
  }

  suspend fun processUserMessage(
    userMessage: String,
    modelInference: suspend (String) -> String
  ): AgentState = withContext(Dispatchers.Default) {
    Log.d(TAG, "Processing user message: ${userMessage.take(100)}")

    // Add user message to history
    messages.add(AgentMessage(role = "user", content = userMessage))

    // Build conversation context
    val conversationContext = buildConversationContext()

    // Generate response with tool awareness
    val response = modelInference(conversationContext)

    // Parse and handle any tool calls in the response
    val processedResponse = processToolCalls(response)

    // Add assistant message to history
    messages.add(AgentMessage(role = "assistant", content = processedResponse))

    // Auto-store important memories
    autoStoreMemory(userMessage, processedResponse)

    AgentState(
      messages = messages.toList(),
      isProcessing = false,
      memoryStats = tools.getToolDefinitions().size.toString().let { mapOf("toolsAvailable" to it) },
      activeSkills = skills.getAllSkills().map { it.name }
    )
  }

  private fun buildConversationContext(): String {
    val contextBuilder = StringBuilder()
    contextBuilder.appendLine(systemPrompt)
    contextBuilder.appendLine()

    // Add recent conversation history
    val recentMessages = messages.takeLast(20)
    contextBuilder.appendLine("CONVERSATION HISTORY:")
    for (msg in recentMessages) {
      contextBuilder.appendLine("[${msg.role.uppercase()}]: ${msg.content}")
    }
    contextBuilder.appendLine()

    // Add tool definitions
    contextBuilder.appendLine("AVAILABLE TOOLS:")
    contextBuilder.appendLine(tools.getToolDefinitions().joinToString("\n") { tool ->
      "- ${tool["name"]}: ${tool["description"]}"
    })
    contextBuilder.appendLine()

    // Add instructions for tool usage
    contextBuilder.appendLine("""
      TOOL USAGE INSTRUCTIONS:
      When you need to use a tool, respond with a JSON block in this format:
      ```tool
      {
        "name": "toolName",
        "params": {"param1": "value1"}
      }
      ```
      
      After the tool executes, you'll receive the result and can continue your response.
      
      Available tool names: memoryRecall, memoryStore, webSearch, readFile, writeFile, 
      executeCommand, scheduleTask, listSkills, delegateTask
    """.trimIndent())

    return contextBuilder.toString()
  }

  private suspend fun processToolCalls(response: String): String {
    val toolPattern = Regex("""```tool\s*\n(\{.*?\})\s*\n```""", RegexOption.DOT_MATCHES_ALL)
    val matches = toolPattern.findAll(response)

    if (!matches.any()) return response

    var processedResponse = response
    val results = mutableListOf<String>()

    for (match in matches) {
      try {
        val toolJson = JSONObject(match.groupValues[1])
        val toolName = toolJson.getString("name")
        val params = mutableMapOf<String, Any>()

        toolJson.optJSONObject("params")?.let { paramsObj ->
          paramsObj.keys().forEach { key ->
            params[key] = paramsObj.get(key)
          }
        }

        val toolCall = ToolCall(
          id = "call_${System.currentTimeMillis()}",
          name = toolName,
          arguments = params
        )
        toolCallHistory.add(toolCall)

        // Execute the tool
        val result = tools.executeTool(toolName, params)
        val resultStr = when (result) {
          is ToolResult.Success -> "SUCCESS: ${result.data}"
          is ToolResult.Error -> "ERROR: ${result.message}"
        }

        results.add("Tool '$toolName' result:\n$resultStr")

        // Replace tool call with result
        processedResponse = processedResponse.replace(match.value, "[$toolName executed: $resultStr]")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to process tool call: ${match.value}", e)
        processedResponse = processedResponse.replace(match.value, "[Tool execution failed: ${e.message}]")
      }
    }

    return processedResponse
  }

  private suspend fun autoStoreMemory(userMessage: String, response: String) {
    // Auto-store conversation if it contains important information
    val importance = calculateImportance(userMessage, response)
    if (importance > 0.3) {
      val key = "conversation_${System.currentTimeMillis()}"
      val content = "User: ${userMessage.take(200)}\nAgent: ${response.take(200)}"
      tools.executeTool("memoryStore", mapOf(
        "key" to key,
        "content" to content,
        "category" to "conversation",
        "importance" to importance
      ))
    }
  }

  private fun calculateImportance(userMessage: String, response: String): Float {
    var importance = 0.3f

    // Increase importance for certain keywords
    val importantKeywords = listOf(
      "remember", "important", "critical", "deadline", "password",
      "api key", "secret", "preference", "always", "never"
    )
    for (keyword in importantKeywords) {
      if (userMessage.lowercase().contains(keyword)) {
        importance += 0.1f
      }
    }

    // Increase importance for questions (likely to be asked again)
    if (userMessage.contains("?")) {
      importance += 0.1f
    }

    // Increase importance for longer responses (more detailed information)
    if (response.length > 500) {
      importance += 0.1f
    }

    return importance.coerceIn(0f, 1f)
  }

  suspend fun recallContext(query: String): String {
    val result = tools.executeTool("memoryRecall", mapOf(
      "query" to query,
      "maxResults" to 5
    ))

    return when (result) {
      is ToolResult.Success -> {
        val data = result.data as? Map<*, *>
        data?.get("memories") as? String ?: "No relevant memories found"
      }
      is ToolResult.Error -> "Error recalling memories: ${result.message}"
    }
  }

  fun getState(): AgentState {
    return AgentState(
      messages = messages.toList(),
      isProcessing = false,
      memoryStats = mapOf("totalMessages" to messages.size, "toolCalls" to toolCallHistory.size),
      activeSkills = skills.getAllSkills().map { it.name }
    )
  }

  fun clearHistory() {
    messages.clear()
    toolCallHistory.clear()
  }

  fun getSystemPrompt(): String = systemPrompt

  fun close() {
    tools.close()
  }
}
