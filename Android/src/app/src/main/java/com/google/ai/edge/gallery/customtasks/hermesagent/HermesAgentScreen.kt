package com.google.ai.edge.gallery.customtasks.hermesagent

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.common.chat.ChatMessage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageWarning
import com.google.ai.edge.gallery.ui.common.chat.ChatSide

private const val TAG = "AGHermesScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesAgentScreen(
  viewModel: HermesAgentViewModel,
  model: Model,
  bottomPadding: androidx.compose.ui.unit.Dp,
) {
  val uiState by viewModel.uiState.collectAsState()
  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  LaunchedEffect(uiState.messages.size) {
    if (uiState.messages.isNotEmpty()) {
      listState.animateScrollToItem(uiState.messages.size - 1)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Rounded.SmartToy,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text("Hermes Agent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text(
                "Skill: ${uiState.activeSkill} | Tools: ${uiState.toolCalls.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        },
        actions = {
          IconButton(onClick = { viewModel.resetConversation() }) {
            Icon(Icons.Rounded.Brush, contentDescription = "Reset")
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(bottom = bottomPadding)
    ) {
      SkillBar(
        activeSkill = uiState.activeSkill,
        onSkillSelected = { viewModel.setActiveSkill(it) }
      )

      LazyColumn(
        state = listState,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
      ) {
        if (uiState.messages.isEmpty()) {
          item { WelcomeMessage() }
        }
        items(uiState.messages) { message ->
          ChatBubble(message = message)
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp)
          .imePadding(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = inputText,
          onValueChange = { inputText = it },
          modifier = Modifier.weight(1f),
          placeholder = { Text("Ask Hermes Agent...") },
          enabled = !uiState.processing,
          maxLines = 4
        )
        Spacer(modifier = Modifier.width(8.dp))
        FloatingActionButton(
          onClick = {
            if (inputText.isNotBlank()) {
              viewModel.processUserInput(model, inputText)
              inputText = ""
            }
          },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
          if (uiState.processing) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              color = MaterialTheme.colorScheme.onPrimary
            )
          } else {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
          }
        }
      }
    }
  }
}

@Composable
private fun WelcomeMessage() {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(
      Icons.Rounded.SmartToy,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(48.dp)
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      "Hermes Agent",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold
    )
    Text(
      "Your self-improving AI assistant with memory, skills, and tools.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
      "Try: \"Search for latest AI news\" or \"Remember my preferences\"",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun SkillBar(
  activeSkill: String,
  onSkillSelected: (String) -> Unit,
) {
  val skills = listOf("general", "memory", "skills", "research", "code", "schedule")

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
      .padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    skills.forEach { skill ->
      val isActive = skill == activeSkill
      val icon = when (skill) {
        "memory" -> Icons.Rounded.Memory
        "skills" -> Icons.Rounded.Workspaces
        else -> Icons.Rounded.SmartToy
      }
      Card(
        modifier = Modifier
          .clip(RoundedCornerShape(16.dp))
          .clickable { onSkillSelected(skill) },
        colors = CardDefaults.cardColors(
          containerColor = if (isActive) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.surface
        )
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isActive) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            skill.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }
  }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
  val isUser = when (message) {
    is ChatMessageText -> message.side == ChatSide.USER
    else -> false
  }

  val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
  val backgroundColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
  else MaterialTheme.colorScheme.secondaryContainer
  val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
  else MaterialTheme.colorScheme.onSecondaryContainer

  Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = alignment
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth(0.85f)
        .clip(RoundedCornerShape(16.dp)),
      colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
      when (message) {
        is ChatMessageText -> {
          Text(
            text = message.content,
            modifier = Modifier.padding(12.dp),
            color = textColor,
            style = MaterialTheme.typography.bodyMedium
          )
        }
        is ChatMessageWarning -> {
          Text(
            text = message.content,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
          )
        }
        else -> {}
      }
    }
  }
}
