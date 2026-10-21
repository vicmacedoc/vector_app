// Gemini prompt texts shared with the phone (shared/ai/*.md, read there as assets/ai/*.md).
import activityMd from "@shared/ai/activity.md?raw";
import brainMd from "@shared/ai/brain.md?raw";
import exerciseMd from "@shared/ai/exercise.md?raw";
import learningMd from "@shared/ai/learning.md?raw";
import motionMd from "@shared/ai/motion.md?raw";
import nutritionMd from "@shared/ai/nutrition.md?raw";
import sleepMd from "@shared/ai/sleep.md?raw";

const MARKER = /^=== ([\w.:-]+) ===\s*$/;

/** Splits a prompt file into sections that start at `=== name ===` lines; text before the first marker is ignored. */
export function parsePrompts(text: string): Record<string, string> {
  const out: Record<string, string> = {};
  let name: string | null = null;
  let body: string[] = [];
  const flush = () => {
    if (name !== null) out[name] = body.join("\n").trim();
    body = [];
  };
  for (const line of text.replace(/\r\n/g, "\n").split("\n")) {
    const m = line.match(MARKER);
    if (m) {
      flush();
      name = m[1];
    } else if (name !== null) {
      body.push(line);
    }
  }
  flush();
  return out;
}

const FILES = {
  activity: activityMd,
  brain: brainMd,
  exercise: exerciseMd,
  learning: learningMd,
  motion: motionMd,
  nutrition: nutritionMd,
  sleep: sleepMd,
} as const;

export type PromptFile = keyof typeof FILES;

const cache = new Map<PromptFile, Record<string, string>>();

export function prompt(file: PromptFile, section: string): string {
  let sections = cache.get(file);
  if (!sections) {
    sections = parsePrompts(FILES[file]);
    cache.set(file, sections);
  }
  const text = sections[section];
  if (!text) throw new Error(`Missing prompt section '${section}' in shared/ai/${file}.md`);
  return text;
}
