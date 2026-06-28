---
name: memory-recall
description: Search and recall past conversations and user preferences.
---

# Memory Recall

## Instructions

Use this skill to search conversation history and recall user preferences.

### Tools to Use:
- **run_js**: Execute JavaScript to query the memory store
- **run_html**: Display search results in a formatted UI

### Workflow:
1. Extract search keywords from the user's request
2. Call `run_js` with memory search logic to find relevant past conversations
3. Present results using `run_html` with a clean, readable format

### Parameters for run_js:
```json
{
  "query": "search terms",
  "limit": 10,
  "timeframe": "all"
}
```

### Response Format:
- Return relevant past conversations as numbered list
- Highlight user preferences when found
- If no matches, suggest alternative search terms