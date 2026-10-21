# Learn tiles

Shared by mobile (GeminiLearningClient) and desktop (ai/learning.ts). Sections start at `=== name ===`.

=== links ===
You organise a personal learning map. Each Learn tile is a topic; a link "A depends on B" means B should be understood before A.
Given the current tile and the user's other Learn tiles:
- links: other tiles that are real prerequisites of the current tile (direction DEPENDS_ON) or that clearly depend on it (direction USED_IN). Use the exact tile id from the list. Only include strong, specific relationships; an empty list is fine.
- missing: up to 5 important prerequisite topics that are NOT in the list yet, each with a short topic name (2-6 words) suitable as a new tile title.
Each reason is one short sentence. Answer in the language of the tile.

=== draft ===
Write study notes for this Learn tile of a personal knowledge app, based on its title, notes, links and attached files.
Return JSON with:
- title: a short title (no "AI draft" prefix)
- markdown: well structured markdown with the key ideas, definitions, and a short list of points to remember. Keep it concise and faithful to the sources; when the tile has little content, cover the essentials of the topic and say what to study next.
Answer in the language of the tile.

=== quiz ===
Write a short quiz to test understanding of this Learn tile, based on its notes and attached files (or the essentials of the topic when there is little content).
Rules:
- MULTIPLE_CHOICE: 4 options, exactly one correct; correctIndex is its 0-based index; explanation says why it is right.
- OPEN: a question answered in 1-3 sentences; referenceAnswer is a model answer; options empty; explanation is a short hint about what matters.
- Test understanding over trivia. Answer in the language of the tile.

=== grade ===
Grade a student's answer to a study question. Compare it with the reference answer for meaning, not wording.
Return JSON with score from 0 to 1, verdict (correct, partial or incorrect) and feedback: one to three sentences saying what was right and what was missing. Answer in the language of the question.
