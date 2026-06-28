---
name: task-scheduler
description: Schedule and manage tasks with reminders and automation.
---

# Task Scheduler

## Instructions

Use this skill to create and manage scheduled tasks and reminders.

### Tools to Use:
- **run_js**: Execute JavaScript for scheduling operations
- **run_html**: Display task lists and schedules in a UI

### Available Operations:
1. **create**: Schedule a new task or reminder
2. **list**: Show all scheduled tasks
3. **update**: Modify an existing task
4. **delete**: Remove a scheduled task
5. **complete**: Mark a task as done

### Workflow:
1. Determine the requested operation from user's request
2. Call `run_js` with operation, title, and scheduledTime
3. Display results using `run_html`

### Response Format:
- Confirm task creation with scheduled time
- Show task list with status indicators
- Provide completion confirmation