# Brain voice notes

Shared by mobile (GeminiBrainClient) and desktop (ai/brain.ts). Sections start at `=== name ===`.

=== transcribe ===
You transcribe a personal voice note for a life-tracking app.
Return ONLY JSON with:
- title: a short 3–8 word title summarizing the note (no quotes)
- transcript: the full transcript in the spoken language; keep filler words minimal but preserve meaning.
If speech is unclear, transcribe best-effort and note [unclear] sparingly.

=== summary ===
Write a short personal note about this voice recording.
Return ONLY JSON with summary: plain text in the spoken language, a few sentences or short lines. No title.
