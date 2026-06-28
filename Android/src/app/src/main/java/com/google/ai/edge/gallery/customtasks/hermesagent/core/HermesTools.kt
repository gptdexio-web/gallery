package com.google.ai.edge.gallery.customtasks.hermesagent.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "HermesTools"

sealed class ToolResult {
  data class Success(val data: Any) : ToolResult()
  data class Error(val message: String) : ToolResult()
}

class HermesTools(private val context: Context) {

  private val memory = HermesMemory(context)
  private val skills = HermesSkills(context)
  private val scheduledTasks = mutableListOf<ScheduledTaskInfo>()

  data class ScheduledTaskInfo(
    val id: String,
    val description: String,
    val schedule: String,
    val context: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null,
    val nextRunAt: Long? = null,
    val status: String = "pending"
  )

  suspend fun executeTool(name: String, params: Map<String, Any>): ToolResult {
    Log.d(TAG, "Executing tool: $name with params: $params")
    return try {
      when (name) {
        "memoryRecall" -> executeMemoryRecall(params)
        "memoryStore" -> executeMemoryStore(params)
        "webSearch" -> executeWebSearch(params)
        "readFile" -> executeReadFile(params)
        "writeFile" -> executeWriteFile(params)
        "executeCommand" -> executeCommand(params)
        "scheduleTask" -> executeScheduleTask(params)
        "listSkills" -> executeListSkills(params)
        "delegateTask" -> executeDelegateTask(params)
        else -> ToolResult.Error("Unknown tool: $name")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Tool execution failed: $name", e)
      ToolResult.Error("Tool execution failed: ${e.message}")
    }
  }

  private suspend fun executeMemoryRecall(params: Map<String, Any>): ToolResult {
    val query = params["query"] as? String ?: return ToolResult.Error("Missing query parameter")
    val category = params["category"] as? String
    val maxResults = (params["maxResults"] as? Number)?.toInt() ?: 5

    val results = memory.recall(query, category, maxResults)
    return if (results.isNotEmpty()) {
      val formatted = results.joinToString("\n\n") { entry ->
        "**${entry.key}** (${entry.category})\n${entry.content}\n" +
          "Importance: ${entry.importance}, Accessed: ${entry.accessCount} times"
      }
      ToolResult.Success(mapOf("memories" to formatted, "count" to results.size))
    } else {
      ToolResult.Success(mapOf("memories" to "No memories found for query: $query", "count" to 0))
    }
  }

  private suspend fun executeMemoryStore(params: Map<String, Any>): ToolResult {
    val key = params["key"] as? String ?: return ToolResult.Error("Missing key parameter")
    val content = params["content"] as? String ?: return ToolResult.Error("Missing content parameter")
    val category = params["category"] as? String ?: "fact"
    val importance = (params["importance"] as? Number)?.toFloat() ?: 0.5f

    val id = memory.store(key, content, category, importance)
    return ToolResult.Success(mapOf("id" to id, "key" to key, "message" to "Memory stored successfully"))
  }

  private suspend fun executeWebSearch(params: Map<String, Any>): ToolResult {
    val query = params["query"] as? String ?: return ToolResult.Error("Missing query parameter")

    return withContext(Dispatchers.IO) {
      try {
        val url = URL("https://api.duckduckgo.com/?q=${query}&format=json&no_html=1")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
        val json = JSONObject(response)

        val abstract = json.optString("AbstractText", "")
        val relatedTopics = json.optJSONArray("RelatedTopics")
        val results = mutableListOf<String>()

        if (abstract.isNotBlank()) {
          results.add("Abstract: $abstract")
        }

        relatedTopics?.let { topics ->
          for (i in 0 until minOf(5, topics.length())) {
            val topic = topics.getJSONObject(i)
            val text = topic.optString("Text", "")
            if (text.isNotBlank()) {
              results.add("- $text")
            }
          }
        }

        if (results.isEmpty()) {
          results.add("No results found for: $query")
        }

        ToolResult.Success(mapOf("results" to results.joinToString("\n"), "query" to query))
      } catch (e: Exception) {
        ToolResult.Error("Web search failed: ${e.message}")
      }
    }
  }

  private suspend fun executeReadFile(params: Map<String, Any>): ToolResult {
    val filePath = params["filePath"] as? String ?: return ToolResult.Error("Missing filePath parameter")
    return try {
      val file = File(filePath)
      if (!file.exists()) {
        ToolResult.Error("File not found: $filePath")
      } else if (!file.canRead()) {
        ToolResult.Error("Cannot read file: $filePath")
      } else {
        val content = file.readText()
        val preview = if (content.length > 5000) content.take(5000) + "\n... (truncated)" else content
        ToolResult.Success(mapOf("content" to preview, "size" to content.length, "path" to filePath))
      }
    } catch (e: Exception) {
      ToolResult.Error("Failed to read file: ${e.message}")
    }
  }

  private suspend fun executeWriteFile(params: Map<String, Any>): ToolResult {
    val filePath = params["filePath"] as? String ?: return ToolResult.Error("Missing filePath parameter")
    val content = params["content"] as? String ?: return ToolResult.Error("Missing content parameter")
    return try {
      val file = File(filePath)
      file.parentFile?.mkdirs()
      file.writeText(content)
      ToolResult.Success(mapOf("path" to filePath, "size" to content.length, "message" to "File written successfully"))
    } catch (e: Exception) {
      ToolResult.Error("Failed to write file: ${e.message}")
    }
  }

  private suspend fun executeCommand(params: Map<String, Any>): ToolResult {
    val command = params["command"] as? String ?: return ToolResult.Error("Missing command parameter")
    return withContext(Dispatchers.IO) {
      try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
        val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
        val exitCode = process.waitFor()

        val result = buildString {
          if (output.isNotBlank()) appendLine("Output:\n$output")
          if (error.isNotBlank()) appendLine("Error:\n$error")
          appendLine("Exit code: $exitCode")
        }

        if (exitCode == 0) {
          ToolResult.Success(mapOf("output" to result, "exitCode" to exitCode))
        } else {
          ToolResult.Error("Command failed (exit code $exitCode):\n$result")
        }
      } catch (e: Exception) {
        ToolResult.Error("Command execution failed: ${e.message}")
      }
    }
  }

  private fun executeScheduleTask(params: Map<String, Any>): ToolResult {
    val description = params["taskDescription"] as? String ?: return ToolResult.Error("Missing taskDescription parameter")
    val schedule = params["schedule"] as? String ?: "now"
    val ctx = params["context"] as? String ?: ""

    val taskId = "task_${System.currentTimeMillis()}"
    val nextRunAt = calculateNextRun(schedule)

    scheduledTasks.add(ScheduledTaskInfo(
      id = taskId,
      description = description,
      schedule = schedule,
      context = ctx,
      nextRunAt = nextRunAt
    ))

    return ToolResult.Success(mapOf<String, Any>(
      "taskId" to taskId,
      "description" to description,
      "schedule" to schedule,
      "nextRunAt" to (nextRunAt?.toString() ?: "immediate"),
      "message" to "Task scheduled successfully"
    ))
  }

  private fun calculateNextRun(schedule: String): Long? {
    val now = System.currentTimeMillis()
    return when {
      schedule == "now" -> now
      schedule == "daily" -> now + 24 * 60 * 60 * 1000
      schedule == "weekly" -> now + 7 * 24 * 60 * 60 * 1000
      schedule.matches(Regex("\\d+[smh]")) -> {
        val amount = schedule.dropLast(1).toLongOrNull() ?: return null
        val unit = schedule.last()
        val multiplier = when (unit) {
          's' -> 1000L
          'm' -> 60 * 1000L
          'h' -> 60 * 60 * 1000L
          else -> return null
        }
        now + amount * multiplier
      }
      else -> now
    }
  }

  private fun executeListSkills(params: Map<String, Any>): ToolResult {
    val category = params["category"] as? String
    val skillList = if (category != null) {
      skills.getSkillsByCategory(category)
    } else {
      skills.getAllSkills()
    }

    val formatted = skillList.joinToString("\n") { skill ->
      "**${skill.name}** (${skill.category})\n  ${skill.description}\n  Tools: ${skill.tools.joinToString(", ")}"
    }

    return ToolResult.Success(mapOf(
      "skills" to formatted,
      "count" to skillList.size,
      "categories" to skills.getCategories().toList()
    ))
  }

  private fun executeDelegateTask(params: Map<String, Any>): ToolResult {
    val goal = params["goal"] as? String ?: return ToolResult.Error("Missing goal parameter")
    val ctx = params["context"] as? String ?: ""

    return ToolResult.Success(mapOf(
      "goal" to goal,
      "context" to ctx,
      "message" to "Task delegation initiated. The subagent will process: $goal",
      "status" to "delegated"
    ))
  }

  fun getScheduledTasks(): List<ScheduledTaskInfo> = scheduledTasks.toList()

  fun getToolDefinitions(): List<Map<String, Any>> {
    return listOf(
      mapOf(
        "name" to "memoryRecall",
        "description" to "Search through stored memories and past conversations to recall relevant information",
        "parameters" to mapOf(
          "query" to mapOf("type" to "string", "description" to "The search query"),
          "category" to mapOf("type" to "string", "description" to "Filter by category"),
          "maxResults" to mapOf("type" to "integer", "description" to "Max results to return", "default" to 5)
        )
      ),
      mapOf(
        "name" to "memoryStore",
        "description" to "Store a new memory or important information for future recall",
        "parameters" to mapOf(
          "key" to mapOf("type" to "string", "description" to "A descriptive key for the memory"),
          "content" to mapOf("type" to "string", "description" to "The content to remember"),
          "category" to mapOf("type" to "string", "description" to "Category: fact, preference, task, or insight"),
          "importance" to mapOf("type" to "number", "description" to "Importance from 0.0 to 1.0")
        )
      ),
      mapOf(
        "name" to "webSearch",
        "description" to "Search the web for information on a topic",
        "parameters" to mapOf(
          "query" to mapOf("type" to "string", "description" to "The search query")
        )
      ),
      mapOf(
        "name" to "readFile",
        "description" to "Read the contents of a file on the device",
        "parameters" to mapOf(
          "filePath" to mapOf("type" to "string", "description" to "The file path to read")
        )
      ),
      mapOf(
        "name" to "writeFile",
        "description" to "Write content to a file on the device",
        "parameters" to mapOf(
          "filePath" to mapOf("type" to "string", "description" to "The file path to write to"),
          "content" to mapOf("type" to "string", "description" to "The content to write")
        )
      ),
      mapOf(
        "name" to "executeCommand",
        "description" to "Execute a shell command on the device",
        "parameters" to mapOf(
          "command" to mapOf("type" to "string", "description" to "The command to execute")
        )
      ),
      mapOf(
        "name" to "scheduleTask",
        "description" to "Schedule a task to run at a specific time or interval",
        "parameters" to mapOf(
          "taskDescription" to mapOf("type" to "string", "description" to "The task description"),
          "schedule" to mapOf("type" to "string", "description" to "Schedule: now, daily, weekly, or duration"),
          "context" to mapOf("type" to "string", "description" to "Additional context")
        )
      ),
      mapOf(
        "name" to "listSkills",
        "description" to "List available agent skills",
        "parameters" to mapOf(
          "category" to mapOf("type" to "string", "description" to "Filter by category")
        )
      ),
      mapOf(
        "name" to "delegateTask",
        "description" to "Delegate a complex task to a subagent for parallel processing",
        "parameters" to mapOf(
          "goal" to mapOf("type" to "string", "description" to "The goal for the subagent"),
          "context" to mapOf("type" to "string", "description" to "Optional context")
        )
      )
    )
  }

  fun close() {
    memory.close()
  }
}
