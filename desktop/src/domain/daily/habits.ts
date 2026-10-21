// HabitRepository rules over shard records: inherited day selection, logs and score summaries.
import type { ShardRecords } from "@/sync/shardEngine";
import { CONFIG_SHARD, D, K, monthShard } from "@/sync/shards";
import { allDayRecords, configRecords, putRecord } from "./repo";
import {
  asJson, habitLogId, normHabit, normHabitConfig, normHabitLog,
  type HabitDayConfig, type HabitDefinition, type HabitLog, type HabitStatus,
} from "./models";

export type Shards = Record<string, ShardRecords>;

export const HABIT_STATUS_CYCLE: readonly HabitStatus[] = ["NONE", "NOT_DONE", "PARTIAL", "DONE", "EXCEEDED", "NA"];

export const habitStatusScore = (s: HabitStatus): number =>
  s === "PARTIAL" ? 0.5 : s === "DONE" ? 1 : s === "EXCEEDED" ? 1.5 : 0;

export const definitions = (shards: Shards): HabitDefinition[] =>
  configRecords(shards, K.HABITS, normHabit).sort((a, b) => a.createdAt - b.createdAt || a.name.localeCompare(b.name));

export function decodeIds(raw: string): string[] {
  try {
    const v = JSON.parse(raw) as unknown;
    return Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : [];
  } catch {
    return [];
  }
}

const allConfigs = (shards: Shards): HabitDayConfig[] => allDayRecords(shards, D.HABIT_CONFIGS, normHabitConfig);

/** Latest config on or before [date], filtered to habits that still exist. */
export function selectedHabitIds(shards: Shards, date: string): string[] {
  const latest = allConfigs(shards)
    .filter((c) => c.date <= date)
    .sort((a, b) => b.date.localeCompare(a.date))[0];
  if (!latest) return [];
  const known = new Set(definitions(shards).map((d) => d.id));
  return decodeIds(latest.habitIdsJson).filter((id) => known.has(id));
}

export function logsForDate(shards: Shards, date: string): HabitLog[] {
  return Object.values(shards[monthShard(date)]?.[D.HABIT_LOGS] ?? {})
    .map((r) => normHabitLog(r))
    .filter((l) => l.date === date);
}

/** Statuses for the selected habits on [date]; missing logs read as NONE. */
export function dayHabitStatuses(shards: Shards, date: string): { habit: HabitDefinition; status: HabitStatus }[] {
  const defs = new Map(definitions(shards).map((d) => [d.id, d]));
  const logs = new Map(logsForDate(shards, date).map((l) => [l.habitId, l.status]));
  return selectedHabitIds(shards, date)
    .map((id) => defs.get(id))
    .filter((d): d is HabitDefinition => !!d)
    .map((habit) => ({ habit, status: logs.get(habit.id) ?? "NONE" }));
}

export interface HabitScore {
  habitId: string;
  score: number;
  total: number;
}

export function scoreSummaries(shards: Shards): Map<string, HabitScore> {
  const out = new Map<string, HabitScore>();
  for (const log of allDayRecords(shards, D.HABIT_LOGS, normHabitLog)) {
    if (log.status === "NA") continue;
    const cur = out.get(log.habitId) ?? { habitId: log.habitId, score: 0, total: 0 };
    cur.score += habitStatusScore(log.status);
    cur.total += 1;
    out.set(log.habitId, cur);
  }
  return out;
}

export const formatScore = (s: HabitScore) => `[${s.score} / ${s.total}]`;

// ---- writes (return shard-level mutations so callers can route them through useDaily.mutate) ----

export interface ShardEdit {
  shard: string;
  fn: (r: ShardRecords) => ShardRecords;
}

export function createHabitEdit(shards: Shards, name: string): { edit: ShardEdit; habit: HabitDefinition } {
  const trimmed = name.trim();
  if (!trimmed) throw new Error("Habit name cannot be empty");
  if (definitions(shards).some((d) => d.name.toLowerCase() === trimmed.toLowerCase())) {
    throw new Error("A habit with this name already exists");
  }
  const habit: HabitDefinition = { id: `habit_${crypto.randomUUID()}`, name: trimmed, createdAt: Date.now() };
  return { habit, edit: { shard: CONFIG_SHARD, fn: (r) => putRecord(r, K.HABITS, habit.id, asJson(habit)) } };
}

export function renameHabitEdit(habit: HabitDefinition, name: string): ShardEdit {
  return { shard: CONFIG_SHARD, fn: (r) => putRecord(r, K.HABITS, habit.id, asJson({ ...habit, name: name.trim() })) };
}

/** Deletes the definition and every log of it. */
export function deleteHabitEdits(shards: Shards, habitId: string): ShardEdit[] {
  const edits: ShardEdit[] = [{ shard: CONFIG_SHARD, fn: (r) => putRecord(r, K.HABITS, habitId, null) }];
  for (const [shard, records] of Object.entries(shards)) {
    if (!shard.startsWith("days/")) continue;
    if (!Object.values(records[D.HABIT_LOGS] ?? {}).some((l) => l.habitId === habitId)) continue;
    edits.push({
      shard,
      fn: (r) => {
        const logs = Object.fromEntries(Object.entries(r[D.HABIT_LOGS] ?? {}).filter(([, l]) => l.habitId !== habitId));
        return { ...r, [D.HABIT_LOGS]: logs };
      },
    });
  }
  return edits;
}

/**
 * Sets the selection for [date] and every later day: writes the config, drops later configs,
 * creates NONE logs for selected habits and removes logs of deselected habits from [date] on.
 */
export function setSelectedHabitsEdits(shards: Shards, date: string, habitIds: string[]): ShardEdit[] {
  const known = new Set(definitions(shards).map((d) => d.id));
  const cleaned = [...new Set(habitIds.filter((id) => known.has(id)))];
  const selected = new Set(cleaned);
  const now = Date.now();
  const config: HabitDayConfig = { date, habitIdsJson: JSON.stringify(cleaned), updatedAt: now };
  const edits: ShardEdit[] = [];
  const shardsToTouch = new Set([monthShard(date), ...Object.keys(shards).filter((s) => s.startsWith("days/") && s > monthShard(date))]);
  for (const shard of shardsToTouch) {
    edits.push({
      shard,
      fn: (r) => {
        const configs = Object.fromEntries(
          Object.entries(r[D.HABIT_CONFIGS] ?? {}).filter(([, c]) => typeof c.date !== "string" || c.date <= date),
        );
        if (shard === monthShard(date)) configs[date] = asJson(config);
        const logs = Object.fromEntries(
          Object.entries(r[D.HABIT_LOGS] ?? {}).filter(
            ([, l]) => typeof l.date !== "string" || l.date < date || selected.has(String(l.habitId)),
          ),
        );
        if (shard === monthShard(date)) {
          for (const id of cleaned) {
            const key = habitLogId(date, id);
            const existing = Object.values(logs).some((l) => l.date === date && l.habitId === id);
            if (!existing) logs[key] = asJson<HabitLog>({ id: key, date, habitId: id, status: "NONE", timestamp: now });
          }
        }
        return { ...r, [D.HABIT_CONFIGS]: configs, [D.HABIT_LOGS]: logs };
      },
    });
  }
  return edits;
}

export function setHabitStatusEdit(shards: Shards, date: string, habitId: string, status: HabitStatus): ShardEdit {
  const existing = logsForDate(shards, date).find((l) => l.habitId === habitId);
  const log: HabitLog = existing
    ? { ...existing, status, timestamp: Date.now() }
    : { id: habitLogId(date, habitId), date, habitId, status, timestamp: Date.now() };
  return { shard: monthShard(date), fn: (r) => putRecord(r, D.HABIT_LOGS, log.id, asJson(log)) };
}

/** Dates in [from, to] with any non-NONE habit log. */
export function datesWithHabitData(shards: Shards, from: string, to: string): Set<string> {
  return new Set(
    allDayRecords(shards, D.HABIT_LOGS, normHabitLog)
      .filter((l) => l.status !== "NONE" && l.date >= from && l.date <= to)
      .map((l) => l.date),
  );
}
