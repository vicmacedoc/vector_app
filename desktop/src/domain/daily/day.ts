// Calendar day baselines (saved rows, else the active preset) and the shard writes behind Save.
import { addDays } from "@/domain/dates";
import type { ShardRecords } from "@/sync/shardEngine";
import { CONFIG_SHARD, D, K, monthShard } from "@/sync/shards";
import { decodeRoutinePlan, presetActivities, sortActivities, unknownPresetLabels, NEW_PRESET_LABEL_COLOR, type WeeklyRoutinePlan } from "./activity";
import type { ExerciseDraftPayload, FoodDraft, RoutineDraft, SelfDraft } from "./drafts";
import { bakeForSave, parseFoodPlan, presetFoodEntries } from "./food";
import {
  asJson, DEFAULT_LABELS, DEFAULT_SHARED_SETTINGS, normActivity, normFood, normLabel, normPreset, normSelf, normSettings,
  normSleep, normWorkoutSet, SLEEP_COLOR_ARGB, SLEEP_LABEL_ID,
  type ActivityLabel, type PresetCategory, type PresetRecord, type SelfEntry, type SharedSettings, type SleepEntry,
} from "./models";
import { configRecord, configRecords, dayRecord, dayRecords, putRecord, replaceForDate } from "./repo";
import { SELF_SCALES, applyScale, emptySelf, scaleValue } from "./self";
import { emptySleep, isEmptySleep } from "./sleep";
import { decodeWorkoutPlan, displayRows, planToRows, rowsToSave } from "./workoutPlan";

type Shards = Record<string, ShardRecords>;

export const SLEEP_LABEL: ActivityLabel = {
  id: SLEEP_LABEL_ID, name: "Sleep", colorArgb: SLEEP_COLOR_ARGB, isSystem: true, createdAt: 0,
};

const presetLabelId = (name: string) => `label_preset_${name.trim().toLowerCase().replace(/[^a-z0-9]+/g, "_")}`;

/** Saved labels (or the phone's defaults before the first sync) plus the system Sleep label. */
export function savedLabels(shards: Shards): ActivityLabel[] {
  const saved = configRecords(shards, K.LABELS, normLabel);
  const base = saved.length ? saved : DEFAULT_LABELS;
  const all = base.some((l) => l.id === SLEEP_LABEL_ID) ? base : [SLEEP_LABEL, ...base];
  return [...all].sort((a, b) => a.createdAt - b.createdAt || a.name.localeCompare(b.name));
}

export function preset(shards: Shards, category: PresetCategory): PresetRecord | null {
  return configRecord(shards, K.PRESETS, category, normPreset);
}

export function routinePlan(shards: Shards): WeeklyRoutinePlan | null {
  const p = preset(shards, "Routine");
  if (!p) return null;
  try {
    return decodeRoutinePlan(p.content);
  } catch {
    return null;
  }
}

/** Labels for the Calendar: saved ones plus labels the routine preset names but nobody created yet. */
export function labelsWithPreset(shards: Shards): ActivityLabel[] {
  const labels = savedLabels(shards);
  const plan = routinePlan(shards);
  if (!plan) return labels;
  const extra = unknownPresetLabels(plan, labels).map((name, i) => ({
    id: presetLabelId(name), name, colorArgb: NEW_PRESET_LABEL_COLOR, isSystem: false, createdAt: Date.now() + i,
  }));
  return [...labels, ...extra];
}

export const sharedSettings = (shards: Shards): SharedSettings =>
  shards[CONFIG_SHARD]?.[K.SETTINGS]?.shared ? normSettings(shards[CONFIG_SHARD][K.SETTINGS].shared) : DEFAULT_SHARED_SETTINGS;

// ---- baselines ----

export interface Baseline<T> {
  payload: T;
  /** No saved rows and no preset for this category. */
  noPreset: boolean;
  /** Error reading the preset, shown once. */
  presetError: string | null;
}

export function routineBaseline(shards: Shards, date: string): Baseline<RoutineDraft> {
  const saved = dayRecords(shards, D.ACTIVITIES, date, normActivity);
  const prev = addDays(date, -1);
  const nights = {
    previousNight: dayRecord(shards, D.SLEEP, prev, normSleep) ?? emptySleep(prev),
    tonight: dayRecord(shards, D.SLEEP, date, normSleep) ?? emptySleep(date),
  };
  const p = preset(shards, "Routine");
  if (saved.length) return { payload: { activities: sortActivities(saved), ...nights }, noPreset: !p, presetError: null };
  if (!p) return { payload: { activities: [], ...nights }, noPreset: true, presetError: null };
  try {
    const plan = decodeRoutinePlan(p.content);
    return { payload: { activities: presetActivities(plan, date, labelsWithPreset(shards)), ...nights }, noPreset: false, presetError: null };
  } catch (e) {
    return { payload: { activities: [], ...nights }, noPreset: false, presetError: `Routine preset: ${(e as Error).message}` };
  }
}

export function foodBaseline(shards: Shards, date: string): Baseline<FoodDraft> {
  const saved = dayRecords(shards, D.FOOD, date, normFood);
  const p = preset(shards, "Food");
  if (saved.length) return { payload: { entries: saved }, noPreset: false, presetError: null };
  if (!p) return { payload: { entries: [] }, noPreset: true, presetError: null };
  try {
    const entries = presetFoodEntries(parseFoodPlan(p.content), date).map((e) => ({ ...e, timestamp: 0 }));
    return { payload: { entries }, noPreset: false, presetError: entries.length ? null : "No food plan found for this day in the preset." };
  } catch (e) {
    return { payload: { entries: [] }, noPreset: false, presetError: `Food preset: ${(e as Error).message}` };
  }
}

export function exerciseBaseline(shards: Shards, date: string): Baseline<ExerciseDraftPayload> {
  const saved = dayRecords(shards, D.WORKOUT_SETS, date, normWorkoutSet);
  const p = preset(shards, "Workout");
  let blueprint: ReturnType<typeof planToRows> = [];
  let presetError: string | null = null;
  if (p) {
    try {
      blueprint = planToRows(decodeWorkoutPlan(p.content), date);
    } catch (e) {
      presetError = `Workout preset: ${(e as Error).message}`;
    }
  }
  const rows = displayRows(saved, blueprint);
  const order = new Map(blueprint.map((b, i) => [b.id, i]));
  if (saved.length) rows.sort((a, b) => (order.get(a.id) ?? 1e6) - (order.get(b.id) ?? 1e6) || a.id.localeCompare(b.id));
  return { payload: { sets: rows }, noPreset: !p, presetError: saved.length ? null : presetError };
}

export function selfToDraft(e: SelfEntry | null): SelfDraft {
  const scales: Record<string, number> = {};
  if (e) for (const s of SELF_SCALES) {
    const v = scaleValue(s, e);
    if (v !== null) scales[s.id] = v;
  }
  return {
    scales, intention: e?.intention ?? "", highlight: e?.highlight ?? "", lowlight: e?.lowlight ?? "",
    gratitude: e?.gratitude ?? "", journalText: e?.journalText ?? "", audioDriveFileId: e?.journalAudioDriveFileId ?? null,
  };
}

export function selfBaseline(shards: Shards, date: string): Baseline<SelfDraft> {
  return { payload: selfToDraft(dayRecord(shards, D.SELF, date, normSelf)), noPreset: false, presetError: null };
}

export function draftToSelf(date: string, d: SelfDraft): SelfEntry {
  const t = (v: string) => (v.trim() ? v.trim() : null);
  const base: SelfEntry = {
    ...emptySelf(date), intention: t(d.intention), highlight: t(d.highlight), lowlight: t(d.lowlight),
    gratitude: t(d.gratitude), journalText: d.journalText.trim() ? d.journalText : null,
    journalAudioDriveFileId: d.audioDriveFileId, timestamp: Date.now(),
  };
  return SELF_SCALES.reduce((acc, s) => applyScale(s, acc, d.scales[s.id] ?? null), base);
}

// ---- saves (shard edits) ----

export interface ShardEdit {
  shard: string;
  fn: (r: ShardRecords) => ShardRecords;
}

const stamp = <T extends { id: string; timestamp: number }>(items: T[], base: T[]) => {
  const prev = new Map(base.map((b) => [b.id, JSON.stringify({ ...b, timestamp: 0 })]));
  const now = Date.now();
  return items.map((i) => (prev.get(i.id) === JSON.stringify({ ...i, timestamp: 0 }) && i.timestamp ? i : { ...i, timestamp: now }));
};

function nightEdit(current: SleepEntry | null, base: SleepEntry | null): ShardEdit | null {
  if (!current) return null;
  const strip = (s: SleepEntry | null) => (s === null || isEmptySleep(s) ? null : JSON.stringify({ ...s, timestamp: 0 }));
  if (strip(current) === strip(base)) return null;
  const value = isEmptySleep(current) ? null : { ...current, timestamp: Date.now() };
  return { shard: monthShard(current.date), fn: (r) => putRecord(r, D.SLEEP, current.date, value && asJson(value)) };
}

export function saveRoutineEdits(shards: Shards, date: string, current: RoutineDraft, base: RoutineDraft): ShardEdit[] {
  const activities = stamp(current.activities, base.activities);
  const edits: ShardEdit[] = [
    { shard: monthShard(date), fn: (r) => replaceForDate(r, D.ACTIVITIES, date, activities.map(asJson), (a) => String(a.id)) },
  ];
  for (const [c, b] of [[current.previousNight, base.previousNight], [current.tonight, base.tonight]] as const) {
    const e = nightEdit(c, b);
    if (e) edits.push(e);
  }
  const saved = configRecords(shards, K.LABELS, normLabel);
  const known = new Set(saved.map((l) => l.id));
  const all = labelsWithPreset(shards);
  const missing = all.filter((l) => !known.has(l.id) && (!saved.length || activities.some((a) => a.labelId === l.id)));
  if (missing.length) {
    edits.push({ shard: CONFIG_SHARD, fn: (r) => missing.reduce((acc, l) => putRecord(acc, K.LABELS, l.id, asJson(l)), r) });
  }
  return edits;
}

export function saveFoodEdits(date: string, current: FoodDraft): ShardEdit[] {
  const now = Date.now();
  const baked = current.entries.map((e) => ({ ...bakeForSave(e), timestamp: e.timestamp || now }));
  return [{ shard: monthShard(date), fn: (r) => replaceForDate(r, D.FOOD, date, baked.map(asJson), (e) => String(e.id)) }];
}

export function saveExerciseEdits(date: string, current: ExerciseDraftPayload): ShardEdit[] {
  const rows = rowsToSave(date, current.sets);
  return [{ shard: monthShard(date), fn: (r) => replaceForDate(r, D.WORKOUT_SETS, date, rows.map(asJson), (s) => String(s.id)) }];
}

export function saveSelfEdits(date: string, current: SelfDraft): ShardEdit[] {
  const entry = draftToSelf(date, current);
  return [{ shard: monthShard(date), fn: (r) => putRecord(r, D.SELF, date, asJson(entry)) }];
}

export function savePresetEdit(category: PresetCategory, content: string | null, displayName: string | null): ShardEdit {
  const value: PresetRecord | null = content === null ? null : { category, displayName, content, updatedAt: Date.now() };
  return { shard: CONFIG_SHARD, fn: (r) => putRecord(r, K.PRESETS, category, value && asJson(value)) };
}

export function saveSettingsEdit(settings: SharedSettings): ShardEdit {
  return { shard: CONFIG_SHARD, fn: (r) => putRecord(r, K.SETTINGS, "shared", asJson(settings)) };
}
