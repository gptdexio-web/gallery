---
name: web-research
description: Research topics using available web tools and APIs.
---

# Web Research

## Instructions

Use this skill to research topics using web resources.

### Tools to Use:
- **run_js**: Execute JavaScript for web searches and API calls
- **run_html**: Display research results in a formatted UI

### Workflow:
1. Extract research topic from user's request
2. Call `run_js` with search parameters to gather information
3. Compile results and present using `run_html`

### Parameters for run_js:
```json
{
  "topic": "research subject",
  "sources": ["wikipedia", "news", "academic"],
  "maxResults": 5
}
```

### Response Format:
- Summarize key findings from each source
- Provide source URLs when available
- Highlight conflicting information if found
- End with suggested follow-up questions