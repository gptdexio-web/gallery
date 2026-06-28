# Hermes Agent Integration for Google AI Edge Gallery

## Overview

This module integrates [NousResearch's Hermes Agent](https://github.com/NousResearch/hermes-agent) capabilities into the Google AI Edge Gallery Android app. The integration brings Hermes's core features — memory, skills, tools, and agent loop — to an on-device Android experience.

## Architecture

```
gallery-hermes/
├── Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/hermesagent/
│   ├── HermesAgentTask.kt          # Task definition and model initialization
│   ├── HermesAgentViewModel.kt     # ViewModel with agent state management
│   ├── HermesAgentScreen.kt        # Compose UI for the agent interface
│   ├── HermesAgentTools.kt         # Tool definitions (memory, skills, web, etc.)
│   ├── HermesAgentModule.kt        # Hilt dependency injection module
│   └── README.md                   # This file
└── skills/hermes-agent/            # Gallery-compatible skill definitions
    ├── memory-recall/SKILL.md
    ├── skill-manager/SKILL.md
    ├── web-research/SKILL.md
    ├── code-assistant/SKILL.md
    ├── task-scheduler/SKILL.md
    └── README.md
```

## Features

### On-Device Agent Capabilities
- **Memory System**: Store and recall information across conversations
- **Skill System**: Load specialized skills for different tasks
- **Tool Integration**: Execute commands, read/write files, search the web
- **Task Scheduling**: Schedule tasks for later execution
- **Delegation**: Break complex tasks into subtasks

### Hermes-Inspired Design
- Self-improving agent loop
- Modular skill architecture
- Persistent memory across sessions
- Proactive tool usage

## How It Works

1. **Model Initialization**: The agent uses the selected on-device LLM via LiteRT-LM
2. **Tool Calling**: The model calls tools defined in `HermesAgentTools.kt`
3. **Command Processing**: Commands are processed by the ViewModel
4. **State Management**: UI state is managed via Kotlin Flows

## Building

1. Open the project in Android Studio
2. Configure your HuggingFace developer application (see `DEVELOPMENT.md`)
3. Build and run on an Android 12+ device

## Integration with Hermes Agent

This integration ports key Hermes concepts to Kotlin/Android:
- **Memory**: Simplified key-value store (vs Hermes's FTS5-backed memory)
- **Skills**: Gallery-compatible SKILL.md format (vs Hermes's Python skills)
- **Tools**: Kotlin ToolSet interface (vs Hermes's Python tool registry)
- **Agent Loop**: Simplified conversation loop (vs Hermes's full AIAgent class)

## License

Apache License 2.0 (same as Google AI Edge Gallery)
