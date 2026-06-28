package com.google.ai.edge.gallery.customtasks.hermesagent

import android.util.Log
import com.google.ai.edge.gallery.customtasks.hermesagent.core.HermesMemory
import com.google.ai.edge.gallery.customtasks.hermesagent.core.HermesSkills
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private const val TAG = "AGHermesTools"

data class HermesAgentCommand(
  val action: String,
  val params: Map<String, Any> = emptyMap(),
  val ts: Long = System.currentTimeMillis()
)

class HermesAgentTools(
  private val memory: HermesMemory,
  private val skills: HermesSkills,
  val onCommand: (HermesAgentCommand) -> Unit
) : ToolSet {

  @Tool(description = "Search through stored memories and past conversations to recall relevant information.")
  fun memoryRecall(
    @ToolParam(description = "The search query to find relevant memories.") query: String,
    @ToolParam(description = "Maximum number of results to return.") maxResults: Int = 5
  ): Map<String, Any> {
    Log.d(TAG, "memoryRecall: query=$query, maxResults=$maxResults")
    onCommand(HermesAgentCommand("memory_recall", mapOf("query" to query)))

    val results = runBlocking {
      memory.recall(query, null, maxResults)
    }

    return if (results.isNotEmpty()) {
      val formatted = results.joinToString("\n\n") { entry ->
        "[${entry.category}] ${entry.key}: ${entry.content}"
      }
      mapOf("status" to "success", "memories" to formatted, "count" to results.size)
    } else {
      mapOf("status" to "success", "memories" to "No memories found for query: $query", "count" to 0)
    }
  }

  @Tool(description = "Store a new memory or important information for future recall.")
  fun memoryStore(
    @ToolParam(description = "A descriptive key for the memory.") key: String,
    @ToolParam(description = "The content to remember.") content: String,
    @ToolParam(description = "Category: 'fact', 'preference', 'task', or 'insight'.") category: String = "fact"
  ): Map<String, Any> {
    Log.d(TAG, "memoryStore: key=$key, category=$category")
    onCommand(HermesAgentCommand("memory_store", mapOf("key" to key, "content" to content)))

    val id = runBlocking {
      memory.store(key, content, category)
    }

    return mapOf("status" to "success", "id" to id, "key" to key, "message" to "Memory stored successfully.")
  }

  @Tool(description = "Search the web for information on a topic using DuckDuckGo.")
  fun webSearch(
    @ToolParam(description = "The search query.") query: String
  ): Map<String, Any> {
    Log.d(TAG, "webSearch: query=$query")
    onCommand(HermesAgentCommand("web_search", mapOf("query" to query)))

    return try {
      val url = URL("https://api.duckduckgo.com/?q=${query}&format=json&no_html=1")
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.connectTimeout = 5000
      connection.readTimeout = 5000
      connection.setRequestProperty("User-Agent", "HermesAgent/1.0")

      val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
      val json = JSONObject(response)

      val abstract = json.optString("AbstractText", "")
      val abstractSource = json.optString("AbstractSource", "")
      val relatedTopics = json.optJSONArray("RelatedTopics")
      val results = mutableListOf<String>()

      if (abstract.isNotBlank()) {
        results.add("Summary: $abstract (Source: $abstractSource)")
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

      mapOf("status" to "success", "results" to results.joinToString("\n"), "query" to query)
    } catch (e: Exception) {
      Log.e(TAG, "Web search failed", e)
      mapOf("status" to "error", "message" to "Web search failed: ${e.message}")
    }
  }

  @Tool(description = "Schedule a task to run at a specific time or interval.")
  fun scheduleTask(
    @ToolParam(description = "The task description.") taskDescription: String,
    @ToolParam(description = "Schedule: 'now', 'daily', 'weekly', or '30m', '2h'.") schedule: String = "now",
    @ToolParam(description = "Additional context for the task.") context: String = ""
  ): Map<String, Any> {
    Log.d(TAG, "scheduleTask: task=$taskDescription, schedule=$schedule")
    onCommand(HermesAgentCommand("schedule_task", mapOf("taskDescription" to taskDescription, "schedule" to schedule)))

    val taskId = "task_${System.currentTimeMillis()}"
    val nextRun = calculateNextRun(schedule)

    return mapOf(
      "status" to "success",
      "taskId" to taskId,
      "description" to taskDescription,
      "schedule" to schedule,
      "nextRun" to nextRun,
      "message" to "Task '$taskDescription' scheduled for $schedule."
    )
  }

  @Tool(description = "Execute a shell command on the device.")
  fun executeCommand(
    @ToolParam(description = "The command to execute.") command: String
  ): Map<String, Any> {
    Log.d(TAG, "executeCommand: $command")
    onCommand(HermesAgentCommand("execute_command", mapOf("command" to command)))

    return try {
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
        mapOf("status" to "success", "output" to result, "exitCode" to exitCode)
      } else {
        mapOf("status" to "error", "message" to "Command failed (exit $exitCode):\n$result")
      }
    } catch (e: Exception) {
      mapOf("status" to "error", "message" to "Command execution failed: ${e.message}")
    }
  }

  @Tool(description = "Read the contents of a file on the device.")
  fun readFile(
    @ToolParam(description = "The file path to read.") filePath: String
  ): Map<String, Any> {
    Log.d(TAG, "readFile: $filePath")
    onCommand(HermesAgentCommand("read_file", mapOf("filePath" to filePath)))

    return try {
      val file = File(filePath)
      if (!file.exists()) {
        mapOf("status" to "error", "message" to "File not found: $filePath")
      } else if (!file.canRead()) {
        mapOf("status" to "error", "message" to "Cannot read file: $filePath")
      } else {
        val content = file.readText()
        val preview = if (content.length > 5000) content.take(5000) + "\n... (truncated)" else content
        mapOf("status" to "success", "content" to preview, "size" to content.length)
      }
    } catch (e: Exception) {
      mapOf("status" to "error", "message" to "Failed to read file: ${e.message}")
    }
  }

  @Tool(description = "Write content to a file on the device.")
  fun writeFile(
    @ToolParam(description = "The file path to write to.") filePath: String,
    @ToolParam(description = "The content to write.") content: String
  ): Map<String, Any> {
    Log.d(TAG, "writeFile: $filePath")
    onCommand(HermesAgentCommand("write_file", mapOf("filePath" to filePath)))

    return try {
      val file = File(filePath)
      file.parentFile?.mkdirs()
      file.writeText(content)
      mapOf("status" to "success", "size" to content.length, "message" to "File written successfully to $filePath")
    } catch (e: Exception) {
      mapOf("status" to "error", "message" to "Failed to write file: ${e.message}")
    }
  }

  @Tool(description = "List available skills that can be loaded.")
  fun listSkills(): Map<String, Any> {
    Log.d(TAG, "listSkills")
    onCommand(HermesAgentCommand("list_skills"))

    val allSkills = skills.getAllSkills()
    val formatted = allSkills.joinToString("\n") { skill ->
      "- ${skill.name} (${skill.category}): ${skill.description}"
    }

    return mapOf(
      "status" to "success",
      "skills" to formatted,
      "count" to allSkills.size,
      "categories" to skills.getCategories().toList()
    )
  }

  @Tool(description = "Delegate a complex task to a subagent for parallel processing.")
  fun delegateTask(
    @ToolParam(description = "The goal for the subagent.") goal: String,
    @ToolParam(description = "Optional context to provide.") context: String = ""
  ): Map<String, Any> {
    Log.d(TAG, "delegateTask: goal=$goal")
    onCommand(HermesAgentCommand("delegate_task", mapOf("goal" to goal, "context" to context)))

    return mapOf(
      "status" to "success",
      "goal" to goal,
      "message" to "Task delegation initiated. Processing: $goal"
    )
  }

  private fun calculateNextRun(schedule: String): String {
    val now = System.currentTimeMillis()
    val nextMs = when {
      schedule == "now" -> now
      schedule == "daily" -> now + 24 * 60 * 60 * 1000
      schedule == "weekly" -> now + 7 * 24 * 60 * 60 * 1000
      schedule.matches(Regex("\\d+[smh]")) -> {
        val amount = schedule.dropLast(1).toLongOrNull() ?: return "unknown"
        val unit = schedule.last()
        val multiplier = when (unit) {
          's' -> 1000L
          'm' -> 60 * 1000L
          'h' -> 60 * 60 * 1000L
          else -> return "unknown"
        }
        now + amount * multiplier
      }
      else -> now
    }
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(nextMs))
  }
}
