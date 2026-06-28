package com.google.ai.edge.gallery.customtasks.hermesagent

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.customtasks.hermesagent.core.HermesMemory
import com.google.ai.edge.gallery.customtasks.hermesagent.core.HermesSkills
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.common.chat.ChatMessage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageWarning
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import com.google.ai.edge.litertlm.Contents
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "AGHermesViewModel"

data class HermesAgentUiState(
  val processing: Boolean = false,
  val messages: List<ChatMessage> = listOf(),
  val activeSkill: String = "general",
  val toolCalls: List<ToolCallInfo> = listOf(),
)

data class ToolCallInfo(
  val name: String,
  val params: Map<String, Any> = emptyMap(),
  val result: String = "",
  val success: Boolean = true,
  val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class HermesAgentViewModel
@Inject
constructor(
  @ApplicationContext private val context: Context,
  val dataStoreRepository: DataStoreRepository,
) : ViewModel() {
  protected val _uiState = MutableStateFlow(HermesAgentUiState())
  val uiState = _uiState.asStateFlow()

  val memory: HermesMemory by lazy { HermesMemory(context) }
  val skills: HermesSkills by lazy { HermesSkills(context) }

  fun processUserInput(model: Model, instructionText: String) {
    if (instructionText.isBlank() || _uiState.value.processing) return

    val instance = model.instance as? LlmModelInstance ?: run {
      _uiState.update { state ->
        state.copy(messages = state.messages + ChatMessageWarning(content = "Model not initialized. Please wait for model to load."))
      }
      return
    }

    _uiState.update {
      it.copy(
        processing = true,
        messages = it.messages + ChatMessageText(content = instructionText, side = ChatSide.USER)
      )
    }

    viewModelScope.launch(Dispatchers.Default) {
      val conversation = instance.conversation
      val contents = mutableListOf<com.google.ai.edge.litertlm.Content>()
      contents.add(com.google.ai.edge.litertlm.Content.Text(instructionText))

      try {
        val responseMessage = conversation.sendMessage(Contents.of(contents))
        val response = responseMessage.toString()
        Log.d(TAG, "Response: $response")

        _uiState.update { state ->
          state.copy(
            processing = false,
            messages = state.messages + ChatMessageText(content = response, side = ChatSide.AGENT)
          )
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to run inference", e)
        _uiState.update { state ->
          state.copy(
            processing = false,
            messages = state.messages + ChatMessageWarning(content = "Error: ${e.message}")
          )
        }
      }
    }
  }

  fun handleToolCommand(command: HermesAgentCommand) {
    Log.d(TAG, "Tool command: ${command.action}")
    when (command.action) {
      "memory_store", "memory_recall", "web_search", "execute_command",
      "read_file", "write_file", "list_skills", "delegate_task", "schedule_task" -> {
        val info = ToolCallInfo(
          name = command.action,
          params = command.params,
          result = "executed",
          success = true
        )
        _uiState.update { state ->
          state.copy(toolCalls = state.toolCalls + info)
        }
      }
    }
  }

  fun resetConversation() {
    _uiState.update { HermesAgentUiState() }
  }

  fun setActiveSkill(skill: String) {
    _uiState.update { it.copy(activeSkill = skill) }
  }

  fun getMemoryStats(): Map<String, Any> {
    return kotlinx.coroutines.runBlocking { memory.getStats() }
  }

  companion object {
    fun getHermesSystemPrompt(): String {
      return """You are Hermes Agent, a self-improving AI assistant running on-device.

CORE CAPABILITIES:
- Memory: Store and recall information across conversations
- Skills: Load and use specialized skills for different tasks
- Tools: Execute commands, read/write files, search the web
- Delegation: Break complex tasks into subtasks
- Scheduling: Schedule tasks for later execution

BEHAVIOR:
- Always think step-by-step before acting
- Use the memory_store tool to remember important information
- Use memory_recall to retrieve past context
- Break complex tasks into smaller, manageable steps
- Be proactive: suggest relevant skills and tools
- Always explain what you're doing and why

AVAILABLE SKILLS:
- memory-recall: Search past conversations
- skill-manager: Manage agent skills
- web-research: Research topics
- code-assistant: Help with coding
- task-scheduler: Schedule and manage tasks
- file-manager: Manage files on device

When the user asks you to do something, use the appropriate tools. If unsure, ask for clarification."""
    }
  }
}
