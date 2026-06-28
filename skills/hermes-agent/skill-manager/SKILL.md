---
name: skill-manager
description: List, create, and manage agent skills and capabilities.
---

# Skill Manager

## Instructions

Use this skill to manage the agent's skill library.

### Tools to Use:
- **run_js**: Execute JavaScript for skill operations
- **run_html**: Display skill information in a UI

### Available Operations:
1. **list**: Show all available skills
2. **create**: Create a new custom skill
3. **update**: Modify existing skill parameters
4. **delete**: Remove a skill from the library

### Workflow:
1. Determine the requested operation from user input
2. Call `run_js` with operation and skillName parameters
3. Display results using `run_html`

### Response Format:
- List: Show skills with name, description, and status
- Create/update: Confirm changes and show updated skill
- Delete: Confirm removal