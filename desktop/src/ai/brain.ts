// Voice notes (GeminiBrainClient): transcript + title, or a short summary from text or audio.
import { prompt } from "./prompts";
import { S, audioMime, blobToBase64, generateJson, type Part } from "./gemini";

const MAX_AUDIO_BYTES = 12 * 1024 * 1024;
const MAX_SUMMARY_CHARS = 100_000;

export interface Transcription {
  title: string;
  transcript: string;
}

async function audioPart(audio: Blob, mimeType: string): Promise<Part> {
  if (audio.size === 0) throw new Error("Audio file missing");
  if (audio.size > MAX_AUDIO_BYTES) throw new Error("Audio is too large for Gemini (max ~12 MB).");
  return { inlineData: { mimeType: audioMime(mimeType || audio.type), data: await blobToBase64(audio) } };
}

export async function transcribeAudio(audio: Blob, mimeType = ""): Promise<Transcription> {
  const out = await generateJson<Partial<Transcription>>({
    parts: [{ text: prompt("brain", "transcribe") }, await audioPart(audio, mimeType)],
    schema: S.obj({ title: S.str(), transcript: S.str() }, ["title", "transcript"]),
    temperature: 0.2,
  });
  return { title: (out.title ?? "").trim().slice(0, 80), transcript: (out.transcript ?? "").trim() };
}

async function summary(parts: Part[]): Promise<string> {
  const out = await generateJson<{ summary?: string }>({
    parts: [{ text: prompt("brain", "summary") }, ...parts],
    schema: S.obj({ summary: S.str() }, ["summary"]),
    temperature: 0.3,
  });
  const text = (out.summary ?? "").trim();
  if (!text) throw new Error("Gemini returned an empty summary");
  return text;
}

export function summarizeText(text: string): Promise<string> {
  const t = text.trim();
  if (!t) return Promise.reject(new Error("Nothing to summarize"));
  if (t.length > MAX_SUMMARY_CHARS) return Promise.reject(new Error("Transcript is too long to summarize."));
  return summary([{ text: t }]);
}

export async function summarizeAudio(audio: Blob, mimeType = ""): Promise<string> {
  return summary([await audioPart(audio, mimeType)]);
}
