package com.google.ai.edge.gallery.customtasks.hermesagent

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.customtasks.hermesagent.core.HermesMemory
import com.google.ai.edge.gallery.customtasks.hermesagent.core.HermesSkills
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.tool
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val SYSTEM_PROMPT = """You are Hermes Agent, a self-improving AI assistant running entirely on your device.

You have access to powerful tools for memory, skills, web search, code assistance, and task scheduling. You learn from every interaction and improve your capabilities over time.

CORE PRINCIPLES:
1. Think step-by-step before acting
2. Remember important information using memory tools
3. Use the right skill for each task
4. Break complex problems into smaller steps
5. Be proactive and helpful

AVAILABLE TOOLS:
- memoryRecall / memoryStore: Manage persistent memory
- webSearch: Search the web for information
- scheduleTask: Schedule tasks for later
- executeCommand: Run device commands
- readFile / writeFile: Access files
- listSkills: Discover available skills
- delegateTask: Spawn subagents for parallel work

When the user asks you to do something, use the appropriate tools. Always explain your reasoning and what you're doing."""

class HermesAgentTask @Inject constructor() : CustomTask {
  private val _updateChannel = Channel<HermesAgentCommand>(Channel.BUFFERED)
  val commandFlow = _updateChannel.receiveAsFlow()

  override val task =
    Task(
      id = "hermes_agent",
      label = "Hermes Agent",
      description =
        "A self-improving AI agent with memory, skills, web search, code assistance, and task scheduling. " +
          "Powered by NousResearch's Hermes Agent framework, running entirely on-device.\n\n" +
          "Features:\n" +
          "- Persistent memory across sessions\n" +
          "- Modular skill system\n" +
          "- Web research capabilities\n" +
          "- Code assistance\n" +
          "- Task scheduling and delegation",
      shortDescription = "Self-improving on-device AI agent",
      docUrl = "https://github.com/NousResearch/hermes-agent",
      sourceCodeUrl =
        "https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/hermesagent",
      category = Category.LLM,
      icon = Icons.Rounded.SmartToy,
      agentNameRes = R.string.chat_agent_agent_name,
      models = mutableListOf(),
      handleModelConfigChangesInTask = true,
      experimental = true,
      newFeature = true,
      defaultSystemPrompt = SYSTEM_PROMPT,
    )

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    systemInstruction: Contents?,
    onDone: (String) -> Unit,
  ) {
    clearQueue()
    LlmChatModelHelper.initialize(
      context = context,
      model = model,
      taskId = task.id,
      supportImage = false,
      supportAudio = false,
      onDone = onDone,
      systemInstruction = Contents.of(SYSTEM_PROMPT),
      tools = listOf(
        tool(
          HermesAgentTools(
            memory = HermesMemory(context.applicationContext),
            skills = HermesSkills(context.applicationContext),
            onCommand = { cmd ->
              _updateChannel.trySend(cmd)
            }
          )
        )
      ),
    )
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    clearQueue()
    LlmChatModelHelper.cleanUp(model = model, onDone = onDone)
  }

  @Composable
  override fun MainScreen(data: Any) {
    val customTaskData = data as CustomTaskData
    val viewModel: HermesAgentViewModel = hiltViewModel()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(Unit) {
      scope.launch(kotlinx.coroutines.Dispatchers.Default) {
        commandFlow.collect { command ->
          viewModel.handleToolCommand(command)
        }
      }
    }

    HermesAgentScreen(
      viewModel = viewModel,
      model = customTaskData.modelManagerViewModel.uiState.value.selectedModel,
      bottomPadding = customTaskData.bottomPadding,
    )
  }

  private fun clearQueue() {
    while (_updateChannel.tryReceive().isSuccess) {}
  }
}
