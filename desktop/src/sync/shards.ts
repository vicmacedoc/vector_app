// Daily shard layout and merge policy. Protocol: docs/daily-sync-spec.md; mirrors mobile ShardSync.kt.
import type { SyncDevice } from "@/domain/models";
import type { JsonObject, MergePolicy, RecordSet } from "./merge";

export const SHARD_FORMAT_VERSION = 1;
export const CONFIG_SHARD = "config";

export const D = {
  FOOD: "food",
  ACTIVITIES: "activities",
  SLEEP: "sleep",
  WORKOUT_SETS: "workoutSets",
  SELF: "self",
  HOME: "home",
  HABIT_LOGS: "habitLogs",
  HABIT_CONFIGS: "habitConfigs",
} as const;

export const K = {
  LABELS: "labels",
  HABITS: "habits",
  MEAL_LIBRARY: "mealLibrary",
  EXERCISE_LIBRARY: "exerciseLibrary",
  ACTIVITY_LIBRARY: "activityLibrary",
  PRESETS: "presets",
  SETTINGS: "settings",
} as const;

export const DAY_COLLECTIONS = Object.values(D);
export const CONFIG_COLLECTIONS = Object.values(K);

const TEXT_FIELDS: Record<string, ReadonlySet<string>> = {
  [D.FOOD]: new Set(["name", "unit"]),
  [D.ACTIVITIES]: new Set(["title", "description"]),
  [D.WORKOUT_SETS]: new Set(["sessionTitle", "sessionDescription", "exerciseName", "exerciseDescription", "description", "timing"]),
  [D.SELF]: new Set(["intention", "highlight", "lowlight", "gratitude", "journalText"]),
  [D.HOME]: new Set(["dailyPlanText"]),
  [K.LABELS]: new Set(["name"]),
  [K.HABITS]: new Set(["name"]),
};

export const DAILY_POLICY: MergePolicy = {
  keepBoth: new Set(),
  newestRecordWins: {
    [K.MEAL_LIBRARY]: "createdAt",
    [K.EXERCISE_LIBRARY]: "createdAt",
    [K.ACTIVITY_LIBRARY]: "createdAt",
    [K.PRESETS]: "updatedAt",
    [D.HABIT_CONFIGS]: "updatedAt",
  },
  autoFields: new Set([
    D.FOOD, D.ACTIVITIES, D.SLEEP, D.WORKOUT_SETS, D.SELF, D.HOME, D.HABIT_LOGS, K.LABELS, K.HABITS, K.SETTINGS,
  ]),
  textFields: TEXT_FIELDS,
  finalize: (set) => {
    const out: RecordSet = new Map();
    for (const [c, records] of set) out.set(c, new Map(records));
    return out;
  },
};

export interface ShardFile {
  formatVersion: number;
  shard: string;
  rev: number;
  writtenBy: SyncDevice | null;
  writtenAt: number;
  records: Record<string, Record<string, JsonObject>>;
}

export const monthShard = (date: string) => `days/${date.slice(0, 7)}`;
export const shardMonth = (shard: string) => (shard.startsWith("days/") ? shard.slice(5) : null);
export const shardFileName = (shard: string) => (shard === CONFIG_SHARD ? "config.json" : `${shard.slice(5)}.json`);

export function toRecordSet(records: Record<string, Record<string, JsonObject>> | undefined): RecordSet {
  const out: RecordSet = new Map();
  for (const [c, byKey] of Object.entries(records ?? {})) out.set(c, new Map(Object.entries(byKey ?? {})));
  return out;
}

export function fromRecordSet(set: RecordSet): Record<string, Record<string, JsonObject>> {
  const out: Record<string, Record<string, JsonObject>> = {};
  for (const [c, records] of set) {
    if (records.size === 0) continue;
    out[c] = Object.fromEntries(records);
  }
  return out;
}

export function emptyShard(shard: string): ShardFile {
  return { formatVersion: SHARD_FORMAT_VERSION, shard, rev: 0, writtenBy: null, writtenAt: 0, records: {} };
}

/** Human label for a shard, e.g. "Calendar · Oct 2026". */
export function shardLabel(shard: string): string {
  const month = shardMonth(shard);
  if (!month) return "Settings & libraries";
  const [y, m] = month.split("-").map(Number);
  return `Calendar · ${new Date(y, m - 1, 1).toLocaleString("en", { month: "short" })} ${y}`;
}
