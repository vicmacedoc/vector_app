// Motion "From text (AI)" (GeminiMotionClient): free text into to-do drafts; points are computed locally.
import type { MotionPriority } from "@/domain/models";
import { prompt } from "./prompts";
import { S, generateJson } from "./gemini";

const MAX_CHARS = 20_000;
const DEFAULT_DUE_HOUR = 18;
const PRIORITIES: readonly MotionPriority[] = ["NONE", "LOW", "MEDIUM", "HIGH"];

export interface MotionTodoDraft {
  title: string;
  notes: string;
  priority: MotionPriority;
  dueAt: number | null;
  target: number | null;
  unit: string | null;
}

/** `yyyy-MM-dd` means 18:00 that day (like the item dialog); `yyyy-MM-ddTHH:mm` is exact local time. */
export function parseDeadline(value: string): number | null {
  const v = value.trim();
  const exact = v.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})/);
  if (exact) {
    const t = new Date(`${exact[1]}T${exact[2]}:${exact[3]}:00`).getTime();
    return Number.isFinite(t) ? t : null;
  }
  const day = v.slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) return null;
  const t = new Date(`${day}T${String(DEFAULT_DUE_HOUR).padStart(2, "0")}:00:00`).getTime();
  return Number.isFinite(t) ? t : null;
}

export async function parseTodos(text: string, listName: string, today = new Date()): Promise<MotionTodoDraft[]> {
  const t = text.trim();
  if (!t) throw new Error("Nothing to read");
  if (t.length > MAX_CHARS) throw new Error("Text is too long (max ~20k characters).");
  const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
  const weekday = today.toLocaleDateString("en-US", { weekday: "long" }).toLowerCase();
  const instructions = prompt("motion", "todos").replace("{today}", `${iso} (${weekday})`).replace("{list}", listName);
  const item = S.obj(
    { title: S.str(), description: S.str(), deadline: S.str(), priority: S.enumStr(PRIORITIES), target: S.num(), unit: S.str() },
    ["title"],
  );
  const out = await generateJson<{ items?: Record<string, unknown>[] }>({
    parts: [{ text: instructions }, { text: t }],
    schema: S.obj({ items: S.arr(item) }, ["items"]),
    temperature: 0.2,
  });
  return (out.items ?? [])
    .filter((i) => typeof i.title === "string" && i.title.trim())
    .map((i) => {
      const priority = String(i.priority ?? "").toUpperCase() as MotionPriority;
      const target = typeof i.target === "number" && i.target > 0 ? i.target : null;
      const unit = typeof i.unit === "string" && i.unit.trim() ? i.unit.trim() : null;
      return {
        title: String(i.title).trim().slice(0, 200),
        notes: typeof i.description === "string" ? i.description.trim() : "",
        priority: PRIORITIES.includes(priority) ? priority : "NONE",
        dueAt: typeof i.deadline === "string" ? parseDeadline(i.deadline) : null,
        target,
        unit,
      };
    });
}
