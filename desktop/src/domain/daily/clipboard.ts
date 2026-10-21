// Clipboard JSON for the Calendar add flows (CalendarClipboard.kt).

export type CalendarAiMode = "SINGLE" | "MULTIPLE" | "PLAN_SCRATCH" | "PLAN_PRESET";

export const AI_MODES: readonly CalendarAiMode[] = ["SINGLE", "MULTIPLE", "PLAN_SCRATCH", "PLAN_PRESET"];

export const isPlanMode = (m: CalendarAiMode) => m === "PLAN_SCRATCH" || m === "PLAN_PRESET";

export type Obj = Record<string, unknown>;

const isObj = (v: unknown): v is Obj => typeof v === "object" && v !== null && !Array.isArray(v);

/**
 * Splits clipboard JSON into item objects: exactly one object for SINGLE, otherwise an array of objects
 * or `{ "<wrapperKey>": [...] }`. Throws with a user-facing message.
 */
export function clipboardItemsFor(text: string, mode: CalendarAiMode, wrapperKey: string, itemNoun: string): Obj[] {
  const trimmed = text.trim();
  if (!trimmed) throw new Error("Clipboard is empty");
  let element: unknown;
  try {
    element = JSON.parse(trimmed);
  } catch {
    throw new Error("Clipboard is not valid JSON");
  }
  const wrapped = isObj(element) && Array.isArray(element[wrapperKey]) ? (element[wrapperKey] as unknown[]) : null;
  if (mode === "SINGLE") {
    if (Array.isArray(element) || wrapped) throw new Error("Clipboard holds a list. Use Add multiple instead.");
    if (!isObj(element)) throw new Error(`Expected one JSON object for a ${itemNoun}`);
    return [element];
  }
  let array: unknown[];
  if (Array.isArray(element)) array = element;
  else if (wrapped) array = wrapped;
  else if (isObj(element)) {
    throw new Error(
      `Clipboard holds a single ${itemNoun}. Use a JSON array or { "${wrapperKey}": [...] }, or Add a single ${itemNoun}.`,
    );
  } else throw new Error(`Expected a JSON array of ${itemNoun}s`);
  const objects = array.map((it) => {
    if (!isObj(it)) throw new Error("Every list item must be a JSON object");
    return it;
  });
  if (!objects.length) throw new Error("The list is empty");
  return objects;
}

export const str = (v: unknown, d = ""): string => (typeof v === "string" ? v : d);
export const num = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);
export const int = (v: unknown): number | null => {
  const n = num(v);
  return n === null ? null : Math.trunc(n);
};
export { isObj };
