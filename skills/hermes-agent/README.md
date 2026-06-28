# Hermes Agent Skills

This directory contains Hermes Agent compatible skills for the google-ai-edge/gallery Android app.

## Skills Overview

### memory-recall
Search and recall past conversations and user preferences. Simulates a memory system by querying conversation history.

### skill-manager
List, create, and manage agent skills and capabilities. Provides operations to maintain the skill library.

### web-research
Research topics using available web tools and APIs. Gathers information from multiple sources and presents findings.

### code-assistant
Help with coding tasks, debugging, and code generation. Supports multiple programming languages and provides clean, documented solutions.

### task-scheduler
Schedule and manage tasks with reminders and automation. Supports one-time and recurring schedules.

## Usage

Each skill follows the Gallery skill format with YAML frontmatter:
- `name`: Unique skill identifier
- `description`: Brief description of the skill's purpose

Skills use the following tools:
- `run_js`: Execute JavaScript for backend operations
- `run_html`: Display results in a formatted UI

## Integration

These skills are designed to work with the Hermes Agent system and can be:
1. Loaded dynamically by the agent
2. Extended with additional functionality
3. Combined for complex workflows

## Development

To add a new skill:
1. Create a directory under `hermes-agent/`
2. Add a `SKILL.md` file with YAML frontmatter
3. Implement the required tools and logic
4. Update this README with documentation