// Routine calendar rules: ActivityEntry helpers, weekly preset expansion and import drafts.
import { clipboardItemsFor, str, type CalendarAiMode } from "./clipboard";
import { weekdayOf, WEEKDAYS, type Weekday } from "./food";
import { normActivity, type ActivityEntry, type ActivityLabel, type ActivityLibraryItem, type ActivityType } from "./models";
import { normalizeActivityHhMm, optionalValue, parseActivityHhMm, sortMinutes } from "./time";

export const effectiveStart = (a: ActivityEntry) => a.adjustedStartTime ?? a.startTime;
export const effectiveEnd = (a: ActivityEntry) => a.adjustedEndTime ?? a.endTime;
export const isAdjusted = (a: ActivityEntry) => a.adjustedStartTime !== null || a.adjustedEndTime !== null;

/** Minutes for start–end, overnight ranges allowed. */
export function rangeMinutes(start: string, end: string): number | null {
  const s = parseActivityHhMm(start);
  const e = parseActivityHhMm(end);
  if (s === null || e === null) return null;
  return e >= s ? e - s : 24 * 60 - s + e;
}

export const plannedDuration = (a: ActivityEntry) => rangeMinutes(a.startTime, a.endTime);
export const effectiveDuration = (a: ActivityEntry) => rangeMinutes(effectiveStart(a), effectiveEnd(a));

/** min(effective, planned): minutes credited as done toward the plan. */
export function cappedDoneMinutes(a: ActivityEntry): number {
  const p = plannedDuration(a);
  const e = effectiveDuration(a);
  if (p === null || e === null) return 0;
  return Math.min(p, e);
}

export function sortActivities(list: ActivityEntry[]): ActivityEntry[] {
  return [...list].sort(
    (a, b) =>
      sortMinutes(effectiveStart(a)) - sortMinutes(effectiveStart(b)) ||
      sortMinutes(effectiveEnd(a)) - sortMinutes(effectiveEnd(b)) ||
      a.title.localeCompare(b.title),
  );
}

/** Sets the actual window; one equal to the plan clears the adjustment. */
export function adjustActivity(a: ActivityEntry, start: string, end: string): ActivityEntry {
  const same = start === a.startTime && end === a.endTime;
  return { ...a, adjustedStartTime: same ? null : start, adjustedEndTime: same ? null : end, timestamp: Date.now() };
}

// ---- weekly preset ----

export interface RoutinePlanActivity {
  title: string;
  startTime: string;
  endTime: string;
  label: string;
  type: string;
  description: string;
}

export interface WeeklyRoutinePlan {
  name: string;
  weekly_plan: Record<Weekday, RoutinePlanActivity[]>;
}

/** Accepts `{ name, weekly_plan: {...} }` or weekday keys at the top level. */
export function decodeRoutinePlan(content: string): WeeklyRoutinePlan {
  let root: unknown;
  try {
    root = JSON.parse(content.trim());
  } catch {
    throw new Error("Routine preset is not valid JSON");
  }
  if (typeof root !== "object" || root === null || Array.isArray(root)) throw new Error("Routine preset must be a JSON object");
  const r = root as Record<string, unknown>;
  let days: Record<string, unknown>;
  if ("weekly_plan" in r) days = (r.weekly_plan ?? {}) as Record<string, unknown>;
  else if (WEEKDAYS.some((d) => d in r)) days = r;
  else throw new Error("Routine preset needs weekday keys (monday … sunday)");
  const weekly = {} as Record<Weekday, RoutinePlanActivity[]>;
  for (const d of WEEKDAYS) {
    const list = Array.isArray(days[d]) ? (days[d] as Record<string, unknown>[]) : [];
    weekly[d] = list.map((x) => ({
      title: str(x.title), startTime: str(x.startTime), endTime: str(x.endTime), label: str(x.label),
      type: str(x.type, "PLANNED"), description: str(x.description),
    }));
  }
  return { name: str(r.name), weekly_plan: weekly };
}

export const allPlanActivities = (p: WeeklyRoutinePlan) => WEEKDAYS.flatMap((d) => p.weekly_plan[d]);

export function validateRoutinePlan(p: WeeklyRoutinePlan): void {
  allPlanActivities(p).forEach((item, i) => {
    if (!item.title.trim()) throw new Error(`Activity #${i + 1} has no title`);
    if (parseActivityHhMm(item.startTime) === null) throw new Error(`"${item.title}" has an invalid startTime`);
    if (parseActivityHhMm(item.endTime) === null) throw new Error(`"${item.title}" has an invalid endTime`);
  });
}

export function parseActivityType(raw: string | null | undefined, fallback: ActivityType = "PLANNED"): ActivityType {
  const v = optionalValue(raw)?.toUpperCase();
  return v === "PLANNED" || v === "NEW" ? v : fallback;
}

export const fallbackLabelId = (labels: ActivityLabel[]) => (labels.find((l) => !l.isSystem) ?? labels[0])?.id ?? "";

/** Preset activities for [date]'s weekday; ids `preset_{date}_{i}`, unknown labels fall back. */
export function presetActivities(plan: WeeklyRoutinePlan, date: string, labels: ActivityLabel[]): ActivityEntry[] {
  const fallback = fallbackLabelId(labels);
  const items = plan.weekly_plan[weekdayOf(date)].map((item, i) =>
    normActivity({
      id: `preset_${date}_${i}`, date, title: item.title.trim(),
      startTime: normalizeActivityHhMm(item.startTime, "09:00"), endTime: normalizeActivityHhMm(item.endTime, "10:00"),
      labelId: labels.find((l) => l.name.toLowerCase() === item.label.trim().toLowerCase())?.id ?? fallback,
      completion: "NONE", type: parseActivityType(item.type, "PLANNED"), description: item.description.trim(), timestamp: 0,
    }),
  );
  return items.sort((a, b) => sortMinutes(a.startTime) - sortMinutes(b.startTime) || sortMinutes(a.endTime) - sortMinutes(b.endTime));
}

export function unknownPresetLabels(plan: WeeklyRoutinePlan, labels: ActivityLabel[]): string[] {
  const known = new Set(labels.map((l) => l.name.toLowerCase()));
  const seen = new Set<string>();
  const out: string[] = [];
  for (const a of allPlanActivities(plan)) {
    const name = a.label.trim();
    const key = name.toLowerCase();
    if (name && !known.has(key) && !seen.has(key)) {
      seen.add(key);
      out.push(name);
    }
  }
  return out;
}

export const NEW_PRESET_LABEL_COLOR = 0xff546e7a | 0;

// ---- import drafts ----

export type ActivitySource = "AI" | "GOOGLE_CALENDAR" | "LIBRARY" | "CLIPBOARD";

export interface ActivityDraft {
  title: string;
  startTime: string;
  endTime: string;
  labelName: string | null;
  type: ActivityType;
  description: string;
  source: ActivitySource;
  externalId?: string | null;
}

export const emptyActivityDraft = (): ActivityDraft => ({
  title: "", startTime: "09:00", endTime: "10:00", labelName: null, type: "PLANNED", description: "", source: "AI",
});

export function parseActivityClipboard(text: string, mode: CalendarAiMode): ActivityDraft[] {
  const objects = clipboardItemsFor(text, mode, "activities", "activity");
  return objects.map((o, i) => {
    const title = str(o.title);
    if (!title.trim()) throw new Error(objects.length === 1 ? "Activity is missing a title" : `Activity ${i + 1} is missing a title`);
    return {
      title, startTime: normalizeActivityHhMm(str(o.startTime, "09:00"), "09:00"),
      endTime: normalizeActivityHhMm(str(o.endTime, "10:00"), "10:00"), labelName: optionalValue(str(o.label)),
      type: parseActivityType(str(o.type, "PLANNED")), description: str(o.description), source: "CLIPBOARD",
    };
  });
}

/** Resolves a draft against existing labels; unmatched labels are cleared so the default chip stays. */
export function mergeActivityDraft(d: ActivityDraft, labels: ActivityLabel[]): ActivityDraft {
  const labelName = optionalValue(d.labelName);
  const matched = labelName ? labels.find((l) => l.name.toLowerCase() === labelName.toLowerCase()) : undefined;
  return {
    ...d,
    title: optionalValue(d.title) ?? "",
    startTime: normalizeActivityHhMm(d.startTime, "09:00"),
    endTime: normalizeActivityHhMm(d.endTime, "10:00"),
    labelName: matched?.name ?? null,
    description: optionalValue(d.description) ?? "",
  };
}

export function resolveDraftLabelId(d: ActivityDraft, labels: ActivityLabel[], defaultLabelId: string): string {
  const name = optionalValue(d.labelName);
  if (!name) return defaultLabelId;
  return labels.find((l) => l.name.toLowerCase() === name.toLowerCase())?.id ?? defaultLabelId;
}

export function activityDraftError(d: ActivityDraft, labels: ActivityLabel[], defaultLabelId: string): string | null {
  if (!d.title.trim()) return "Title is required";
  if (parseActivityHhMm(d.startTime) === null || !/^\d{1,2}:\d{1,2}$/.test(d.startTime.trim())) return "Start time is invalid";
  if (parseActivityHhMm(d.endTime) === null || !/^\d{1,2}:\d{1,2}$/.test(d.endTime.trim())) return "End time is invalid";
  const id = resolveDraftLabelId(d, labels, defaultLabelId);
  if (!id || !labels.some((l) => l.id === id)) return "Select a label";
  return null;
}

export function activityDraftToEntry(d: ActivityDraft, date: string, labels: ActivityLabel[], defaultLabelId: string): ActivityEntry | null {
  const title = d.title.trim();
  if (!title) return null;
  const labelId = resolveDraftLabelId(d, labels, defaultLabelId) || labels[0]?.id || "";
  if (!labelId) return null;
  return normActivity({
    id: `act_${crypto.randomUUID()}`, date, title, startTime: normalizeActivityHhMm(d.startTime, "09:00"),
    endTime: normalizeActivityHhMm(d.endTime, "10:00"), labelId, completion: "NONE", type: d.type,
    description: d.description.trim(), timestamp: Date.now(),
  });
}

export const libraryToActivityDraft = (i: ActivityLibraryItem): ActivityDraft => ({
  title: i.title, startTime: normalizeActivityHhMm(i.startTime, "09:00"), endTime: normalizeActivityHhMm(i.endTime, "10:00"),
  labelName: optionalValue(i.labelName), type: i.type, description: i.description, source: "LIBRARY",
});

/** Clipboard shape for copying an activity. */
export const activityToClipboard = (a: ActivityEntry, labels: ActivityLabel[]) => ({
  title: a.title, startTime: a.startTime, endTime: a.endTime, label: labels.find((l) => l.id === a.labelId)?.name ?? "",
  completion: a.completion, type: a.type, description: a.description,
});
