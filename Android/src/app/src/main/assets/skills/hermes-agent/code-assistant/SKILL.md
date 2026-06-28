---
name: code-assistant
description: Help with coding tasks, debugging, and code generation.
---

# Code Assistant

## Instructions

Use this skill to assist with programming tasks.

### Tools to Use:
- **run_js**: Execute JavaScript for code operations and testing
- **run_html**: Display code examples and documentation

### Capabilities:
1. **Code Generation**: Write new code based on requirements
2. **Debugging**: Identify and fix bugs in existing code
3. **Refactoring**: Improve code structure and readability
4. **Documentation**: Generate comments and documentation

### Workflow:
1. Understand the coding task from user's request
2. Call `run_js` with operation, language, and requirements
3. Present solution using `run_html` with syntax highlighting

### Response Format:
- Provide clean, commented code
- Explain key implementation decisions
- Include usage examples when helpful