package com.google.ai.edge.gallery.customtasks.hermesagent.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "HermesSkills"

data class Skill(
  val name: String,
  val description: String,
  val version: String = "1.0.0",
  val author: String = "",
  val category: String = "general",
  val instructions: String,
  val tools: List<String> = emptyList(),
  val metadata: Map<String, String> = emptyMap()
)

class HermesSkills(private val context: Context) {

  private val skillsDir: File by lazy {
    File(context.filesDir, "hermes/skills").also { it.mkdirs() }
  }

  private val loadedSkills = mutableMapOf<String, Skill>()

  init {
    loadBuiltinSkills()
  }

  private fun loadBuiltinSkills() {
    val builtinSkills = listOf(
      Skill(
        name = "memory-recall",
        description = "Search and recall past conversations and stored memories",
        category = "memory",
        instructions = """
          When the user asks to recall information from past conversations:
          1. Use the memoryRecall tool with the user's query
          2. Present the results in a clear, organized format
          3. If no results are found, suggest the user store the information for future recall
          
          Example queries:
          - "What did we discuss about project X?"
          - "Recall my preferences for..."
          - "What do you remember about..."
        """.trimIndent(),
        tools = listOf("memoryRecall", "memoryStore")
      ),
      Skill(
        name = "skill-manager",
        description = "Manage and list available agent skills",
        category = "tools",
        instructions = """
          When the user asks about available skills or wants to manage them:
          1. Use listSkills to show available skills
          2. Provide descriptions and categories
          3. Suggest relevant skills based on the user's needs
          
          Available skill categories:
          - memory: Information storage and retrieval
          - research: Web search and information gathering
          - code: Programming assistance
          - productivity: Task management and scheduling
          - tools: Device operations and file management
        """.trimIndent(),
        tools = listOf("listSkills")
      ),
      Skill(
        name = "web-research",
        description = "Research topics using web search and information gathering",
        category = "research",
        instructions = """
          When the user asks to research a topic:
          1. Break down the research question into key components
          2. Use webSearch to find relevant information
          3. Synthesize the findings into a comprehensive answer
          4. Cite sources when possible
          5. Suggest follow-up research directions
          
          Research workflow:
          - Start with broad searches
          - Narrow down to specific aspects
          - Cross-reference multiple sources
          - Summarize key findings
        """.trimIndent(),
        tools = listOf("webSearch")
      ),
      Skill(
        name = "code-assistant",
        description = "Help with programming tasks and code-related questions",
        category = "code",
        instructions = """
          When the user asks for coding help:
          1. Understand the programming language and context
          2. Provide clear, well-commented code examples
          3. Explain the logic and approach
          4. Suggest best practices and optimizations
          5. Help debug issues when asked
          
          Supported languages:
          - Kotlin/Java (Android)
          - Python
          - JavaScript/TypeScript
          - Shell scripting
          
          Use readFile and writeFile tools when working with code files.
        """.trimIndent(),
        tools = listOf("readFile", "writeFile", "executeCommand")
      ),
      Skill(
        name = "task-scheduler",
        description = "Schedule and manage tasks for later execution",
        category = "productivity",
        instructions = """
          When the user wants to schedule a task:
          1. Understand the task description and timing
          2. Use scheduleTask with appropriate parameters
          3. Confirm the scheduled task with the user
          4. Provide options to modify or cancel
          
          Schedule formats:
          - "now" for immediate execution
          - "daily" for daily recurring tasks
          - "weekly" for weekly recurring tasks
          - Specific times: "9am", "2:30pm"
          - Intervals: "every 30 minutes", "every 2 hours"
          
          Task types:
          - Reminders
          - Reports
          - Backups
          - Monitoring
        """.trimIndent(),
        tools = listOf("scheduleTask")
      ),
      Skill(
        name = "file-manager",
        description = "Manage files and directories on the device",
        category = "tools",
        instructions = """
          When the user asks to work with files:
          1. Use readFile to view file contents
          2. Use writeFile to create or modify files
          3. Use executeCommand for advanced operations
          
          File operations:
          - Read: View file contents
          - Write: Create or update files
          - List: Show directory contents
          - Search: Find files by name or content
          
          Safety rules:
          - Always confirm before deleting files
          - Never modify system files
          - Keep backups of important files
        """.trimIndent(),
        tools = listOf("readFile", "writeFile", "executeCommand")
      ),
      Skill(
        name = "delegate-task",
        description = "Break complex tasks into subtasks and delegate to subagents",
        category = "tools",
        instructions = """
          When the user has a complex task:
          1. Analyze the task and identify subtasks
          2. Use delegateTask for parallel processing
          3. Coordinate results from subagents
          4. Synthesize final output
          
          Delegation strategy:
          - Break large tasks into smaller, focused subtasks
          - Run independent subtasks in parallel
          - Provide clear context for each subtask
          - Collect and merge results
          
          Example:
          - "Research and summarize 5 topics" -> delegate each topic
          - "Analyze code and write tests" -> delegate analysis and test writing
        """.trimIndent(),
        tools = listOf("delegateTask")
      )
    )

    builtinSkills.forEach { skill ->
      loadedSkills[skill.name] = skill
    }
    Log.d(TAG, "Loaded ${builtinSkills.size} builtin skills")
  }

  suspend fun loadSkillsFromDisk(): List<Skill> = withContext(Dispatchers.IO) {
    val skills = mutableListOf<Skill>()
    skillsDir.listFiles()?.forEach { dir ->
      if (dir.isDirectory) {
        val skillFile = File(dir, "SKILL.md")
        if (skillFile.exists()) {
          try {
            val content = skillFile.readText()
            val skill = parseSkillMd(content, dir.name)
            loadedSkills[skill.name] = skill
            skills.add(skill)
          } catch (e: Exception) {
            Log.e(TAG, "Failed to load skill from ${dir.name}: ${e.message}")
          }
        }
      }
    }
    skills
  }

  private fun parseSkillMd(content: String, dirName: String): Skill {
    val lines = content.lines()
    var name = dirName
    var description = ""
    var instructions = StringBuilder()
    var inFrontmatter = false
    var inBody = false

    for (line in lines) {
      when {
        line.trim() == "---" && !inBody -> {
          inFrontmatter = !inFrontmatter
          if (!inFrontmatter && !inBody) inBody = true
        }
        inFrontmatter && line.startsWith("name:") -> {
          name = line.substringAfter("name:").trim()
        }
        inFrontmatter && line.startsWith("description:") -> {
          description = line.substringAfter("description:").trim()
        }
        inBody -> {
          instructions.appendLine(line)
        }
      }
    }

    return Skill(
      name = name,
      description = description,
      instructions = instructions.toString().trim()
    )
  }

  fun getSkill(name: String): Skill? = loadedSkills[name]

  fun getAllSkills(): List<Skill> = loadedSkills.values.toList()

  fun getSkillsByCategory(category: String): List<Skill> =
    loadedSkills.values.filter { it.category == category }

  fun getCategories(): Set<String> = loadedSkills.values.map { it.category }.toSet()

  suspend fun saveSkill(skill: Skill) = withContext(Dispatchers.IO) {
    val skillDir = File(skillsDir, skill.name)
    skillDir.mkdirs()
    val skillFile = File(skillDir, "SKILL.md")
    skillFile.writeText(buildString {
      appendLine("---")
      appendLine("name: ${skill.name}")
      appendLine("description: ${skill.description}")
      appendLine("---")
      appendLine()
      appendLine("# ${skill.name}")
      appendLine()
      appendLine(skill.instructions)
    })
    loadedSkills[skill.name] = skill
    Log.d(TAG, "Saved skill: ${skill.name}")
  }

  suspend fun deleteSkill(name: String) = withContext(Dispatchers.IO) {
    val skillDir = File(skillsDir, name)
    if (skillDir.exists()) {
      skillDir.deleteRecursively()
    }
    loadedSkills.remove(name)
    Log.d(TAG, "Deleted skill: $name")
  }

  fun getSkillPrompt(): String {
    val categories = getCategories()
    val skillList = categories.joinToString("\n") { category ->
      val skills = getSkillsByCategory(category)
      "  $category: ${skills.joinToString(", ") { it.name }}"
    }
    return """
      AVAILABLE SKILLS:
      $skillList
      
      To use a skill, mention it by name or describe what you want to do.
      The agent will automatically select and apply the relevant skill.
    """.trimIndent()
  }
}
