// AnalysisStats, TrainingVolumeMath and ExerciseDayMath.
import { addDays, dayOfWeek } from "@/domain/dates";
import type { FoodEntry, HabitStatus, WorkoutSet } from "./models";
import { foodWasEaten } from "./food";
import { durationToMinutes, isAddedExercise, isDropRow, isRestDayAck, parseMuscles } from "./workoutPlan";

export const MIN_CORRELATION_PAIRS = 5;
const MPD = 24 * 60;

export interface TripleStat {
  high: number;
  avg: number;
  low: number;
}

export function eachDayInclusive(start: string, end: string): string[] {
  const out: string[] = [];
  for (let d = start; d <= end; d = addDays(d, 1)) out.push(d);
  return out;
}

export function mondayOf(date: string): string {
  const idx = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"].indexOf(dayOfWeek(date));
  return addDays(date, -idx);
}

export function weekStartsMonday(start: string, end: string): string[] {
  if (end < start) return [];
  const out: string[] = [];
  for (let m = mondayOf(start); m <= end; m = addDays(m, 7)) if (addDays(m, 6) >= start) out.push(m);
  return out;
}

export function longestConsecutiveTrue(flags: boolean[]): number {
  let best = 0;
  let cur = 0;
  for (const f of flags) {
    cur = f ? cur + 1 : 0;
    best = Math.max(best, cur);
  }
  return best;
}

export function trailingConsecutiveTrue(flags: boolean[]): number {
  let cur = 0;
  for (let i = flags.length - 1; i >= 0 && flags[i]; i--) cur++;
  return cur;
}

export const average = (v: number[]): number | null => (v.length ? v.reduce((a, b) => a + b, 0) / v.length : null);

export function maxAvgMin(v: number[]): TripleStat | null {
  if (!v.length) return null;
  return { high: Math.max(...v), avg: average(v)!, low: Math.min(...v) };
}

export function rollingAverage(values: (number | null)[], window: number): (number | null)[] {
  if (window <= 1) return values;
  return values.map((_, i) => average(values.slice(Math.max(0, i - window + 1), i + 1).filter((x): x is number => x !== null)));
}

export function linearTrend(values: (number | null)[]): (number | null)[] {
  const pts = values.flatMap((v, i) => (v === null ? [] : [[i, v] as const]));
  if (pts.length < 2) return values.map(() => null);
  const mx = pts.reduce((a, p) => a + p[0], 0) / pts.length;
  const my = pts.reduce((a, p) => a + p[1], 0) / pts.length;
  let num = 0;
  let den = 0;
  for (const [x, y] of pts) {
    num += (x - mx) * (y - my);
    den += (x - mx) ** 2;
  }
  if (den === 0) return values.map(() => null);
  const slope = num / den;
  const intercept = my - slope * mx;
  return values.map((_, i) => intercept + slope * i);
}

export function pearson(x: (number | null)[], y: (number | null)[]): number | null {
  const pairs: [number, number][] = [];
  x.forEach((a, i) => {
    const b = y[i];
    if (a !== null && a !== undefined && b !== null && b !== undefined) pairs.push([a, b]);
  });
  if (pairs.length < MIN_CORRELATION_PAIRS) return null;
  const mx = pairs.reduce((s, p) => s + p[0], 0) / pairs.length;
  const my = pairs.reduce((s, p) => s + p[1], 0) / pairs.length;
  let cov = 0;
  let vx = 0;
  let vy = 0;
  for (const [a, b] of pairs) {
    cov += (a - mx) * (b - my);
    vx += (a - mx) ** 2;
    vy += (b - my) ** 2;
  }
  if (vx <= 1e-9 || vy <= 1e-9) return null;
  return Math.min(1, Math.max(-1, cov / Math.sqrt(vx * vy)));
}

export function firstToLastDelta(values: (number | null)[]): number | null {
  const p = values.filter((v): v is number => v !== null);
  return p.length < 2 ? null : p[p.length - 1] - p[0];
}

export function halfSplitDelta(values: (number | null)[]): number | null {
  const idx = values.flatMap((v, i) => (v === null ? [] : [[i, v] as const]));
  if (idx.length < 2) return null;
  const mid = Math.floor(values.length / 2);
  const early = idx.filter(([i]) => i < mid).map(([, v]) => v);
  const late = idx.filter(([i]) => i >= mid).map(([, v]) => v);
  if (!early.length || !late.length) return null;
  return average(late)! - average(early)!;
}

function circular(values: number[]): [number, number] {
  let s = 0;
  let c = 0;
  for (const v of values) {
    const a = (2 * Math.PI * v) / MPD;
    s += Math.sin(a);
    c += Math.cos(a);
  }
  return [s / values.length, c / values.length];
}

/** Mean on the 24 h circle so bedtimes either side of midnight average to midnight. */
export function circularMeanMinutes(values: number[]): number | null {
  if (!values.length) return null;
  const [s, c] = circular(values);
  if (Math.abs(s) < 1e-9 && Math.abs(c) < 1e-9) return null;
  const m = Math.round((Math.atan2(s, c) / (2 * Math.PI)) * MPD);
  return ((m % MPD) + MPD) % MPD;
}

export function circularStdDevMinutes(values: number[]): number | null {
  if (values.length < 2) return null;
  const [s, c] = circular(values);
  const r = Math.min(1, Math.max(1e-9, Math.sqrt(s * s + c * c)));
  return (Math.sqrt(-2 * Math.log(r)) / (2 * Math.PI)) * MPD;
}

export const foodDoneCount = (entries: FoodEntry[]) => entries.filter(foodWasEaten).length;

export const foodPlanCompliantCount = (entries: FoodEntry[]) =>
  entries.filter((e) => (e.status === "PLANNED" || e.status === "ADJUSTED") && foodWasEaten(e)).length;

export function workoutSetCountsAsCompleted(s: WorkoutSet): boolean {
  if (s.isCompleted) return true;
  const st = s.workoutStatus?.trim().toLowerCase();
  return st === "partial" || st === "done" || st === "exceeded" || st === "exceed";
}

/** Habit completion credit; NA drops out of the denominator. */
export function habitScore(status: HabitStatus): number | null {
  switch (status) {
    case "NA": return null;
    case "PARTIAL": return 0.5;
    case "DONE": case "EXCEEDED": return 1;
    default: return 0;
  }
}

// ---- training volume ----

export const isResistance = (s: WorkoutSet) => s.exerciseType.toUpperCase() === "RESISTANCE";
export const targetWeights = (s: WorkoutSet) => parseMuscles(s.targetMuscles);

export function plannedLoadForMuscle(s: WorkoutSet, muscle: string): number {
  if (!isResistance(s) || isDropRow(s)) return 0;
  return targetWeights(s)[muscle.toLowerCase()] ?? 0;
}

export const fractionalLoadForMuscle = (s: WorkoutSet, muscle: string) =>
  workoutSetCountsAsCompleted(s) ? plannedLoadForMuscle(s, muscle) : 0;

export function tonnage(s: WorkoutSet): number {
  if (!workoutSetCountsAsCompleted(s) || s.actualLoad === null || s.actualReps === null) return 0;
  if (s.actualLoad <= 0 || s.actualReps <= 0) return 0;
  return s.actualLoad * s.actualReps;
}

/** Epley: load × (1 + reps / 30). */
export function estimatedOneRepMax(s: WorkoutSet): number | null {
  if (isDropRow(s) || !workoutSetCountsAsCompleted(s) || s.actualLoad === null || s.actualReps === null) return null;
  if (s.actualLoad <= 0 || s.actualReps <= 0) return null;
  return s.actualLoad * (1 + s.actualReps / 30);
}

// ---- exercise day ----

export type ExerciseBucket = "RESISTANCE" | "RUN" | "BIKE" | "SWIM" | "ENDURANCE" | "OTHER";
export type ExerciseGroup = "RESISTANCE" | "ENDURANCE" | "OTHER";
export const BUCKETS: readonly ExerciseBucket[] = ["RESISTANCE", "RUN", "BIKE", "SWIM", "ENDURANCE", "OTHER"];
export const GROUPS: readonly ExerciseGroup[] = ["RESISTANCE", "ENDURANCE", "OTHER"];
export const BUCKET_LABEL: Record<ExerciseBucket, string> = {
  RESISTANCE: "Resistance", RUN: "Run", BIKE: "Bike", SWIM: "Swim", ENDURANCE: "Endurance", OTHER: "Other",
};
export const GROUP_LABEL: Record<ExerciseGroup, string> = { RESISTANCE: "Resistance", ENDURANCE: "Endurance", OTHER: "Other" };
export const bucketGroup = (b: ExerciseBucket): ExerciseGroup =>
  b === "RESISTANCE" ? "RESISTANCE" : b === "OTHER" ? "OTHER" : "ENDURANCE";

export interface Progress {
  done: number;
  planned: number;
}

const ZERO: Progress = { done: 0, planned: 0 };
const plus = (a: Progress, b: Progress): Progress => ({ done: a.done + b.done, planned: a.planned + b.planned });

export interface ExerciseDaySummary {
  minutes: Progress;
  byBucket: { bucket: ExerciseBucket; minutes: Progress }[];
  muscles: { muscle: string; sets: Progress }[];
  byGroup: { group: ExerciseGroup; minutes: Progress }[];
  activities: { name: string; bucket: ExerciseBucket; minutes: Progress }[];
}

export function bucketOf(s: WorkoutSet): ExerciseBucket {
  switch (s.exerciseType.toUpperCase()) {
    case "RESISTANCE": return "RESISTANCE";
    case "ENDURANCE": {
      const sp = s.sport?.toUpperCase();
      return sp === "RUN" || sp === "BIKE" || sp === "SWIM" ? sp : "ENDURANCE";
    }
    default: return "OTHER";
  }
}

export function plannedMinutes(rows: WorkoutSet[]): number {
  const total = rows.find((r) => r.totalDuration !== null);
  if (total) return durationToMinutes(total.totalDuration!, total.unitTotalDuration);
  return rows.filter((r) => !isDropRow(r)).reduce(
    (acc, r) =>
      acc + (r.targetDuration !== null ? durationToMinutes(r.targetDuration, r.unitDuration) : 0) +
      (r.targetRest !== null ? durationToMinutes(r.targetRest, r.restUnit) : 0),
    0,
  );
}

function statusCredit(status: string | null): number {
  const s = status?.trim().toLowerCase();
  return s === "done" || s === "exceeded" || s === "exceed" ? 1 : s === "partial" ? 0.5 : 0;
}

export function completionCredits(mains: WorkoutSet[]): number[] {
  if (mains.some((m) => m.isCompleted)) return mains.map((m) => (m.isCompleted ? 1 : 0));
  return mains.map((m) => statusCredit(m.workoutStatus));
}

export function summarizeExerciseDay(sets: WorkoutSet[]): ExerciseDaySummary {
  const groups = new Map<string, WorkoutSet[]>();
  for (const s of sets) {
    if (isRestDayAck(s) || s.exerciseType === "REST_ACK") continue;
    groups.set(s.exerciseId, [...(groups.get(s.exerciseId) ?? []), s]);
  }
  const bucketTotals = new Map<ExerciseBucket, Progress>();
  const muscleTotals = new Map<string, Progress>();
  const activities: ExerciseDaySummary["activities"] = [];
  for (const rows of groups.values()) {
    const mains = rows.filter((r) => !isDropRow(r));
    if (!mains.length) continue;
    const credits = completionCredits(mains);
    const planned = plannedMinutes(rows);
    const share = credits.reduce((a, b) => a + b, 0) / mains.length;
    const measured = rows.find((r) => r.actualTotalDuration !== null)?.actualTotalDuration ?? null;
    const done = measured !== null && measured > 0 ? measured / 60 : planned * share;
    const first = rows[0];
    const bucket = bucketOf(first);
    const target = isAddedExercise(first.exerciseId) ? 0 : planned;
    bucketTotals.set(bucket, plus(bucketTotals.get(bucket) ?? ZERO, { done, planned: target }));
    activities.push({ name: first.exerciseName || first.sessionTitle || BUCKET_LABEL[bucket], bucket, minutes: { done, planned } });
    mains.forEach((s, i) => {
      if (!isResistance(s)) return;
      for (const [muscle, weight] of Object.entries(targetWeights(s))) {
        muscleTotals.set(muscle, plus(muscleTotals.get(muscle) ?? ZERO, { done: weight * credits[i], planned: weight }));
      }
    });
  }
  const byBucket = BUCKETS.filter((b) => bucketTotals.has(b)).map((bucket) => ({ bucket, minutes: bucketTotals.get(bucket)! }));
  const minutes = byBucket.reduce((acc, b) => plus(acc, b.minutes), ZERO);
  const muscles = [...muscleTotals.entries()]
    .map(([muscle, p]) => ({ muscle, sets: p }))
    .sort((a, b) => b.sets.planned - a.sets.planned || a.muscle.localeCompare(b.muscle));
  const byGroup = GROUPS.map((group) => ({
    group,
    minutes: byBucket.filter((b) => bucketGroup(b.bucket) === group).reduce((acc, b) => plus(acc, b.minutes), ZERO),
  }));
  return { minutes, byBucket, muscles, byGroup, activities };
}
