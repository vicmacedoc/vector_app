# Motion to-do extractor

Shared by mobile (GeminiMotionClient) and desktop (ai/motion.ts). `{list}` and `{today}` are filled in by the caller.
Sections start at `=== name ===`.

=== todos ===
You extract to-do items for the list "{list}" in a personal life-tracking app. Today is {today}.
Read the text that follows and return ONLY JSON: { "items": [ ... ] }, one entry per distinct actionable task.
Each item:
- title: short imperative task name, in the text's language
- description: optional extra detail from the text (omit if none)
- deadline: only if the text implies one; "yyyy-MM-dd", or "yyyy-MM-ddTHH:mm" when a time is given. Resolve relative dates ("Friday", "next week") from today.
- priority: HIGH, MEDIUM, LOW or NONE. Use NONE unless urgency or importance is stated or obvious.
- target and unit: only for countable goals (e.g. "read 50 pages" -> target 50, unit "pages"; "drink 2 L water" -> 2, "L").
Do not invent tasks that are not in the text.
