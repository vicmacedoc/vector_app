// HomeRepository daily-inputs completion and per-category clears.
import { addDays } from "@/domain/dates";
import type { ShardRecords } from "@/sync/shardEngine";
import { D, monthShard } from "@/sync/shards";
import { dayRecord, dayRecords, putRecord, replaceForDate } from "./repo";
import { draftKey, type DraftCategory } from "./drafts";
import { normActivity, normFood, normSelf, normSleep, normWorkoutSet } from "./models";
import { hasLoggedSleep } from "./sleep";
import { hasAnyContent } from "./self";
import { workoutSetCountsAsCompleted } from "./stats";

type Shards = Record<string, ShardRecords>;

export interface DayCompletion {
  date: string;
  percent: number;
  hasData: boolean;
  hasDraft: boolean;
}

export const lastNDates = (end: string, n: number) => Array.from({ length: n }, (_, i) => addDays(end, i - (n - 1)));

export function dayCompletion(shards: Shards, category: DraftCategory, date: string, draftKeys: Set<string>): DayCompletion {
  const hasDraft = draftKeys.has(draftKey(category, date));
  switch (category) {
    case "ROUTINE": {
      const hasData =
        dayRecords(shards, D.ACTIVITIES, date, normActivity).length > 0 ||
        hasLoggedSleep(dayRecord(shards, D.SLEEP, addDays(date, -1), normSleep));
      return { date, percent: hasData ? 100 : 0, hasData, hasDraft };
    }
    case "FOOD": {
      const entries = dayRecords(shards, D.FOOD, date, normFood);
      const done = entries.filter((e) => e.isChecked).length;
      return { date, percent: entries.length ? Math.floor((100 * done) / entries.length) : 0, hasData: entries.length > 0, hasDraft };
    }
    case "EXERCISE": {
      const sets = dayRecords(shards, D.WORKOUT_SETS, date, normWorkoutSet);
      const done = sets.filter(workoutSetCountsAsCompleted).length;
      return { date, percent: sets.length ? Math.floor((100 * done) / sets.length) : 0, hasData: sets.length > 0, hasDraft };
    }
    case "SELF": {
      const has = hasAnyContent(dayRecord(shards, D.SELF, date, normSelf));
      return { date, percent: has ? 100 : 0, hasData: has, hasDraft };
    }
  }
}

export function completionRow(shards: Shards, category: DraftCategory, endDate: string, days: number, draftKeys: Set<string>) {
  return lastNDates(endDate, days).map((d) => dayCompletion(shards, category, d, draftKeys));
}

/** Shard edits that clear a category's saved data for [date]; Routine also drops last night's sleep. */
export function clearDayEdits(category: DraftCategory, date: string): { shard: string; fn: (r: ShardRecords) => ShardRecords }[] {
  const shard = monthShard(date);
  switch (category) {
    case "ROUTINE": {
      const prev = addDays(date, -1);
      return [
        { shard, fn: (r) => replaceForDate(r, D.ACTIVITIES, date, [], () => "") },
        { shard: monthShard(prev), fn: (r) => putRecord(r, D.SLEEP, prev, null) },
      ];
    }
    case "FOOD":
      return [{ shard, fn: (r) => replaceForDate(r, D.FOOD, date, [], () => "") }];
    case "EXERCISE":
      return [{ shard, fn: (r) => replaceForDate(r, D.WORKOUT_SETS, date, [], () => "") }];
    case "SELF":
      return [{ shard, fn: (r) => putRecord(r, D.SELF, date, null) }];
  }
}
