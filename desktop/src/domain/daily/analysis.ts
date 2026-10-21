// AnalysisRepository over local shards: one loader per dashboard, same figures as the phone.
import { addDays, todayKey } from "@/domain/dates";
import type { ShardRecords } from "@/sync/shardEngine";
import { D, K } from "@/sync/shards";
import { cappedDoneMinutes, effectiveDuration, effectiveStart, isAdjusted, plannedDuration } from "./activity";
import { savedLabels, exerciseBaseline } from "./day";
import { effectiveTime, foodWasEaten } from "./food";
import {
  normActivity, normFood, normHabit, normHabitLog, normHome, normSelf, normSleep, normWorkoutSet,
  SLEEP_COLOR_ARGB,
  type ActivityEntry, type FoodEntry, type HabitLog, type HabitStatus, type SelfEntry, type SleepEntry, type WorkoutSet,
} from "./models";
import { configRecords, rangeRecords } from "./repo";
import { hasAnyContent, hasReflection, SELF_SCALES, scaleValue, writtenWordCount } from "./self";
import { effectiveBedtime, effectiveWakeUp, latencyMinutes, stagePercent, totalSleepMinutes } from "./sleep";
import {
  average, eachDayInclusive, estimatedOneRepMax, foodDoneCount, foodPlanCompliantCount, fractionalLoadForMuscle, habitScore,
  halfSplitDelta, isResistance, linearTrend, longestConsecutiveTrue, maxAvgMin, pearson, rollingAverage, tonnage,
  trailingConsecutiveTrue, weekStartsMonday, workoutSetCountsAsCompleted, circularMeanMinutes, circularStdDevMinutes,
  firstToLastDelta, type TripleStat,
} from "./stats";
import { durationMinutes, parseHhMm } from "./time";
import { parseMuscles } from "./workoutPlan";

type Shards = Record<string, ShardRecords>;

export type AnalysisWindow = "DAYS_7" | "DAYS_30" | "DAYS_90" | "ALL" | "CUSTOM";

const GOOD_SLEEP_HOURS = 7;
const HIGH_HABIT_PERCENT = 80;
const UNLABELLED_COLOR = 0xff90a4ae | 0;
const SLEEP_LABEL_NAME = "Sleep";
export const ADHERENCE_COLORS = { Planned: "#0074d9", Adjusted: "#003366", Unplanned: "#ff9800", Skipped: "#d32f2f" } as const;
export const STAGE_COLORS = { Deep: "#003366", Light: "#0074d9", REM: "#392e5c", Awake: "#ff9800" } as const;

export interface StackedSeries {
  name: string;
  color: string;
  values: number[];
}

export const argbHex = (argb: number) => `#${((argb >>> 0) & 0xffffff).toString(16).padStart(6, "0")}`;

const groupBy = <T>(items: T[], key: (t: T) => string) => {
  const m = new Map<string, T[]>();
  for (const i of items) m.set(key(i), [...(m.get(key(i)) ?? []), i]);
  return m;
};
const byDate = <T extends { date: string }>(items: T[]) => new Map(items.map((i) => [i.date, i]));
const nonNull = <T>(v: (T | null | undefined)[]) => v.filter((x): x is T => x !== null && x !== undefined);
const inWeek = (date: string, monday: string) => date >= monday && date <= addDays(monday, 6);

export function resolveDateRange(shards: Shards, window: AnalysisWindow, customStart?: string | null, customEnd?: string | null): [string, string] {
  const end = todayKey();
  if (window === "CUSTOM") {
    const s = customStart || addDays(end, -29);
    const e = customEnd || end;
    return s <= e ? [s, e] : [e, s];
  }
  let start: string;
  switch (window) {
    case "DAYS_7": start = addDays(end, -6); break;
    case "DAYS_30": start = addDays(end, -29); break;
    case "DAYS_90": start = addDays(end, -89); break;
    default: {
      let earliest: string | null = null;
      for (const [shard, records] of Object.entries(shards)) {
        if (!shard.startsWith("days/")) continue;
        for (const c of [D.FOOD, D.WORKOUT_SETS, D.HOME, D.SLEEP, D.SELF, D.HABIT_LOGS, D.ACTIVITIES]) {
          for (const r of Object.values(records[c] ?? {})) {
            if (typeof r.date === "string" && (earliest === null || r.date < earliest)) earliest = r.date;
          }
        }
      }
      start = earliest && earliest < end ? earliest : end;
    }
  }
  return start > end ? [end, end] : [start, end];
}

// ---- sleep helpers (day D reads the night keyed D−1) ----

function sleepWithPrior(shards: Shards, start: string, end: string): Map<string, SleepEntry> {
  return byDate(rangeRecords(shards, D.SLEEP, addDays(start, -1), end, normSleep));
}

function actualSleepHours(sleep: Map<string, SleepEntry>, date: string): number | null {
  const night = sleep.get(addDays(date, -1));
  if (!night?.actualWakeUp) return null;
  const bed = effectiveBedtime(night);
  if (!bed) return null;
  const m = durationMinutes(bed, night.actualWakeUp);
  return m === null ? null : m / 60;
}

function plannedSleepHours(sleep: Map<string, SleepEntry>, date: string): number | null {
  const night = sleep.get(addDays(date, -1));
  if (!night?.plannedWakeUp || !night.plannedBedtime) return null;
  const m = durationMinutes(night.plannedBedtime, night.plannedWakeUp);
  return m === null ? null : m / 60;
}

// ---- Overview ----

export interface DomainSeries {
  name: string;
  values: (number | null)[];
  daysWithData: number;
  currentStreak: number;
  bestStreak: number;
  average: number | null;
  delta: number | null;
}

export interface OverviewData {
  dates: string[];
  domains: DomainSeries[];
  score: (number | null)[];
  scoreAverage: number | null;
  scoreDelta: number | null;
  daysLogged: number;
}

function domainSeries(name: string, values: (number | null)[]): DomainSeries {
  const flags = values.map((v) => v !== null);
  return {
    name, values,
    daysWithData: flags.filter(Boolean).length,
    currentStreak: trailingConsecutiveTrue(flags),
    bestStreak: longestConsecutiveTrue(flags),
    average: average(nonNull(values)),
    delta: halfSplitDelta(values),
  };
}

function routineCompletionPercent(logs: HabitLog[], activities: ActivityEntry[]): number | null {
  const habitScores = nonNull(logs.map((l) => habitScore(l.status)));
  const habitPart = habitScores.length ? habitScores.reduce((a, b) => a + b, 0) / habitScores.length : null;
  const planned = activities.filter((a) => a.type === "PLANNED");
  const activityPart = planned.length ? planned.filter((a) => a.completion === "DONE").length / planned.length : null;
  const parts = nonNull([habitPart, activityPart]);
  return parts.length ? (100 * parts.reduce((a, b) => a + b, 0)) / parts.length : null;
}

export function loadOverview(shards: Shards, start: string, end: string): OverviewData {
  const days = eachDayInclusive(start, end);
  const food = groupBy(rangeRecords(shards, D.FOOD, start, end, normFood), (e) => e.date);
  const workouts = groupBy(rangeRecords(shards, D.WORKOUT_SETS, start, end, normWorkoutSet), (e) => e.date);
  const self = byDate(rangeRecords(shards, D.SELF, start, end, normSelf));
  const habits = groupBy(rangeRecords(shards, D.HABIT_LOGS, start, end, normHabitLog), (e) => e.date);
  const activities = groupBy(rangeRecords(shards, D.ACTIVITIES, start, end, normActivity), (e) => e.date);

  const domains = [
    domainSeries("Routine", days.map((d) => routineCompletionPercent(habits.get(d) ?? [], activities.get(d) ?? []))),
    domainSeries("Food", days.map((d) => {
      const list = food.get(d) ?? [];
      return list.length ? (100 * foodDoneCount(list)) / list.length : null;
    })),
    domainSeries("Exercise", days.map((d) => {
      const list = workouts.get(d) ?? [];
      return list.length ? (100 * list.filter(workoutSetCountsAsCompleted).length) / list.length : null;
    })),
    domainSeries("Self", days.map((d) => {
      const e = self.get(d);
      return e ? (hasAnyContent(e) ? 100 : 0) : null;
    })),
  ];
  const score = days.map((_, i) => {
    const present = nonNull(domains.map((dm) => dm.values[i]));
    return present.length ? present.reduce((a, b) => a + b, 0) / present.length : null;
  });
  return {
    dates: days, domains, score,
    scoreAverage: average(nonNull(score)),
    scoreDelta: halfSplitDelta(score),
    daysLogged: score.filter((s) => s !== null).length,
  };
}

// ---- Routine ----

export interface LabelMinutes {
  name: string;
  color: string;
  plannedMinutes: number;
  doneMinutes: number;
}

export interface HabitRow {
  name: string;
  cells: (HabitStatus | null)[];
  scheduledDays: number;
  completionRate: number | null;
  currentStreak: number;
  bestStreak: number;
}

export interface RoutineData {
  dates: string[];
  labels: LabelMinutes[];
  avgPlannedHours: number;
  avgDoneHours: number;
  planCompletionPercent: number;
  weeks: string[];
  weeklyLabelHours: StackedSeries[];
  habits: HabitRow[];
  habitOverallRate: number | null;
  activitiesLogged: number;
  adjustedPercent: number | null;
  avgDriftMinutes: number | null;
}

const doneMinutesOf = (a: ActivityEntry) => (a.type === "PLANNED" ? cappedDoneMinutes(a) : effectiveDuration(a) ?? 0);

function buildHabitRows(shards: Shards, days: string[], start: string, end: string): HabitRow[] {
  const logs = rangeRecords(shards, D.HABIT_LOGS, start, end, normHabitLog);
  if (!logs.length) return [];
  const names = new Map(configRecords(shards, K.HABITS, normHabit).map((h) => [h.id, h.name]));
  const rows: HabitRow[] = [];
  for (const [habitId, habitLogs] of groupBy(logs, (l) => l.habitId)) {
    const name = names.get(habitId);
    if (!name) continue;
    const map = byDate(habitLogs);
    const cells = days.map((d) => map.get(d)?.status ?? null);
    const scored = nonNull(cells.map((c) => (c ? habitScore(c) : null)));
    const doneFlags = cells.map((c) => c !== null && habitScore(c) === 1);
    rows.push({
      name, cells,
      scheduledDays: cells.filter((c) => c !== null).length,
      completionRate: scored.length ? (100 * scored.reduce((a, b) => a + b, 0)) / scored.length : null,
      currentStreak: trailingConsecutiveTrue(doneFlags),
      bestStreak: longestConsecutiveTrue(doneFlags),
    });
  }
  return rows.sort((a, b) => (b.completionRate ?? -1) - (a.completionRate ?? -1));
}

export function loadRoutine(shards: Shards, start: string, end: string): RoutineData {
  const days = eachDayInclusive(start, end);
  const dayCount = Math.max(1, days.length);
  const activities = rangeRecords(shards, D.ACTIVITIES, start, end, normActivity);
  const labels = new Map(savedLabels(shards).map((l) => [l.id, l]));
  const sleep = sleepWithPrior(shards, start, end);
  const labelName = (a: ActivityEntry) => labels.get(a.labelId)?.name ?? "Unlabelled";

  const planned = new Map<string, number>();
  const done = new Map<string, number>();
  const colors = new Map<string, number>();
  for (const a of activities) {
    const name = labelName(a);
    colors.set(name, labels.get(a.labelId)?.colorArgb ?? UNLABELLED_COLOR);
    if (a.type === "PLANNED") planned.set(name, (planned.get(name) ?? 0) + (plannedDuration(a) ?? 0));
    if (a.completion === "DONE") done.set(name, (done.get(name) ?? 0) + doneMinutesOf(a));
  }
  let sleepMinutes = 0;
  for (const d of days) sleepMinutes += (actualSleepHours(sleep, d) ?? 0) * 60;
  if (sleepMinutes > 0) {
    colors.set(SLEEP_LABEL_NAME, SLEEP_COLOR_ARGB);
    planned.set(SLEEP_LABEL_NAME, (planned.get(SLEEP_LABEL_NAME) ?? 0) + sleepMinutes);
    done.set(SLEEP_LABEL_NAME, (done.get(SLEEP_LABEL_NAME) ?? 0) + sleepMinutes);
  }
  const labelMinutes = [...colors.keys()]
    .map((name) => ({
      name, color: argbHex(colors.get(name) ?? UNLABELLED_COLOR),
      plannedMinutes: (planned.get(name) ?? 0) / dayCount,
      doneMinutes: (done.get(name) ?? 0) / dayCount,
    }))
    .filter((l) => l.plannedMinutes > 0 || l.doneMinutes > 0)
    .sort((a, b) => Math.max(b.plannedMinutes, b.doneMinutes) - Math.max(a.plannedMinutes, a.doneMinutes));
  const plannedTotal = labelMinutes.reduce((s, l) => s + l.plannedMinutes, 0);
  const doneTotal = labelMinutes.reduce((s, l) => s + l.doneMinutes, 0);

  const weeks = weekStartsMonday(start, end);
  const weeklyLabelHours = labelMinutes
    .map((label) => ({
      name: label.name,
      color: label.color,
      values: weeks.map((monday) => {
        let minutes = 0;
        for (const a of activities) {
          if (a.completion !== "DONE" || labelName(a) !== label.name || !inWeek(a.date, monday)) continue;
          minutes += doneMinutesOf(a);
        }
        if (label.name === SLEEP_LABEL_NAME) {
          for (const d of days) if (inWeek(d, monday)) minutes += (actualSleepHours(sleep, d) ?? 0) * 60;
        }
        return minutes / 60;
      }),
    }))
    .filter((s) => s.values.some((v) => v > 0));

  const habits = buildHabitRows(shards, days, start, end);
  const drifts = nonNull(activities.filter((a) => a.type === "PLANNED").map((a) => {
    const p = parseHhMm(a.startTime);
    const e = parseHhMm(effectiveStart(a));
    if (p === null || e === null) return null;
    const raw = Math.abs(e - p);
    return Math.min(raw, 24 * 60 - raw);
  }));

  return {
    dates: days, labels: labelMinutes,
    avgPlannedHours: plannedTotal / 60,
    avgDoneHours: doneTotal / 60,
    planCompletionPercent: plannedTotal <= 0 ? 0 : Math.min(100, Math.max(0, Math.trunc((doneTotal / plannedTotal) * 100))),
    weeks, weeklyLabelHours, habits,
    habitOverallRate: average(nonNull(habits.map((h) => h.completionRate))),
    activitiesLogged: activities.length,
    adjustedPercent: activities.length ? (100 * activities.filter(isAdjusted).length) / activities.length : null,
    avgDriftMinutes: average(drifts),
  };
}

// ---- Food ----

export interface DayTimes {
  date: string;
  minutes: number[];
}

export interface FoodData {
  dates: string[];
  kcal: (number | null)[];
  kcalRolling: (number | null)[];
  kcalStat: TripleStat | null;
  proteinStat: TripleStat | null;
  avgKcal: number;
  avgProteinG: number;
  avgCarbsG: number;
  avgFatsG: number;
  proteinKcalShare: number;
  carbsKcalShare: number;
  fatsKcalShare: number;
  weeks: string[];
  adherence: StackedSeries[];
  compliancePercent: number | null;
  mealTimes: DayTimes[];
  avgFirstMealMinutes: number | null;
  avgLastMealMinutes: number | null;
  avgEatingWindowHours: number | null;
  daysLogged: number;
}

export function loadFood(shards: Shards, start: string, end: string): FoodData {
  const days = eachDayInclusive(start, end);
  const all = rangeRecords(shards, D.FOOD, start, end, normFood);
  const map = groupBy(all, (e) => e.date);
  const dailySum = (sel: (e: FoodEntry) => number) => days.map((d) => {
    const list = map.get(d) ?? [];
    return list.length ? list.filter(foodWasEaten).reduce((s, e) => s + sel(e), 0) : null;
  });
  const kcal = dailySum((e) => e.kcal * e.quantityMultiplier);
  const protein = dailySum((e) => e.protein * e.quantityMultiplier);
  const carbs = dailySum((e) => e.carbs * e.quantityMultiplier);
  const fats = dailySum((e) => e.fats * e.quantityMultiplier);
  const avgP = average(nonNull(protein)) ?? 0;
  const avgC = average(nonNull(carbs)) ?? 0;
  const avgF = average(nonNull(fats)) ?? 0;
  const pK = avgP * 4;
  const cK = avgC * 4;
  const fK = avgF * 9;
  const macroK = pK + cK + fK;

  const weeks = weekStartsMonday(start, end);
  const weekly = (pred: (e: FoodEntry) => boolean) => weeks.map((m) => all.filter((e) => inWeek(e.date, m) && pred(e)).length);
  const adherence: StackedSeries[] = [
    { name: "Planned", color: ADHERENCE_COLORS.Planned, values: weekly((e) => foodWasEaten(e) && e.status === "PLANNED") },
    { name: "Adjusted", color: ADHERENCE_COLORS.Adjusted, values: weekly((e) => foodWasEaten(e) && e.status === "ADJUSTED") },
    { name: "Unplanned", color: ADHERENCE_COLORS.Unplanned, values: weekly((e) => foodWasEaten(e) && e.status === "UNPLANNED") },
    { name: "Skipped", color: ADHERENCE_COLORS.Skipped, values: weekly((e) => e.notEaten) },
  ].filter((s) => s.values.some((v) => v > 0));

  const mealTimes = nonNull(days.map((d) => {
    const minutes = nonNull((map.get(d) ?? []).filter(foodWasEaten).map((e) => parseHhMm(effectiveTime(e)))).sort((a, b) => a - b);
    return minutes.length ? { date: d, minutes } : null;
  }));
  const firsts = mealTimes.map((r) => r.minutes[0]);
  const lasts = mealTimes.map((r) => r.minutes[r.minutes.length - 1]);
  const windows = mealTimes.map((r) => (r.minutes[r.minutes.length - 1] - r.minutes[0]) / 60);
  const avgFirst = average(firsts);
  const avgLast = average(lasts);

  return {
    dates: days, kcal,
    kcalRolling: rollingAverage(kcal, 7),
    kcalStat: maxAvgMin(nonNull(kcal)),
    proteinStat: maxAvgMin(nonNull(protein)),
    avgKcal: average(nonNull(kcal)) ?? 0,
    avgProteinG: avgP, avgCarbsG: avgC, avgFatsG: avgF,
    proteinKcalShare: macroK > 0 ? (100 * pK) / macroK : 0,
    carbsKcalShare: macroK > 0 ? (100 * cK) / macroK : 0,
    fatsKcalShare: macroK > 0 ? (100 * fK) / macroK : 0,
    weeks, adherence,
    compliancePercent: all.length ? (100 * foodPlanCompliantCount(all)) / all.length : null,
    mealTimes,
    avgFirstMealMinutes: avgFirst === null ? null : Math.trunc(avgFirst),
    avgLastMealMinutes: avgLast === null ? null : Math.trunc(avgLast),
    avgEatingWindowHours: average(windows),
    daysLogged: map.size,
  };
}

// ---- Training ----

export interface MuscleVolume {
  name: string;
  setEquivalents: number;
}

export interface TrainingData {
  weeks: string[];
  muscleRanking: MuscleVolume[];
  selectedMuscle: string | null;
  muscleWeekly: (number | null)[];
  muscleStat: TripleStat | null;
  tonnageWeekly: (number | null)[];
  totalTonnage: number;
  exercises: string[];
  selectedExercise: string | null;
  e1rmDates: string[];
  e1rm: (number | null)[];
  bestE1rm: number | null;
  e1rmDelta: number | null;
  avgRpe: number | null;
  avgRir: number | null;
  setsCompleted: number;
  setsLogged: number;
  completionPercent: number | null;
  avgLoadDelta: number | null;
  sessionDays: number;
}

/** Saved sets per day with preset muscle targets back-filled; days without saves use the preset (loadWorkoutForDateDisplay). */
export function mergedSetsInRange(shards: Shards, start: string, end: string): WorkoutSet[] {
  return eachDayInclusive(start, end).flatMap((d) => exerciseBaseline(shards, d).payload.sets);
}

export function loadTraining(sets: WorkoutSet[], start: string, end: string, preferredMuscle: string | null, preferredExercise: string | null): TrainingData {
  const withMuscles = sets.filter((s) => isResistance(s) && Object.keys(parseMuscles(s.targetMuscles)).length > 0);
  const weeks = weekStartsMonday(start, end);
  const muscleNames = [...new Set(withMuscles.flatMap((s) => Object.keys(parseMuscles(s.targetMuscles))))];
  const ranking = muscleNames
    .map((name) => ({ name, setEquivalents: withMuscles.reduce((a, s) => a + fractionalLoadForMuscle(s, name), 0) }))
    .filter((m) => m.setEquivalents > 0)
    .sort((a, b) => b.setEquivalents - a.setEquivalents);
  const selectedMuscle =
    (preferredMuscle && ranking.find((m) => m.name.toLowerCase() === preferredMuscle.toLowerCase())?.name) || ranking[0]?.name || null;
  const muscleWeekly = selectedMuscle === null
    ? weeks.map(() => null)
    : weeks.map((m) => withMuscles.reduce((a, s) => a + (inWeek(s.date, m) ? fractionalLoadForMuscle(s, selectedMuscle.toLowerCase()) : 0), 0));
  const tonnageWeekly = weeks.map((m) => sets.reduce((a, s) => a + (inWeek(s.date, m) ? tonnage(s) : 0), 0));

  const counts = new Map<string, number>();
  for (const s of sets) if (s.exerciseName.trim()) counts.set(s.exerciseName, (counts.get(s.exerciseName) ?? 0) + 1);
  const exercises = [...counts.keys()].sort();
  const selectedExercise =
    (preferredExercise && counts.has(preferredExercise) ? preferredExercise : null) ??
    ([...counts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? null);
  const e1rmByDate = new Map<string, number>();
  if (selectedExercise) {
    for (const s of sets) {
      if (s.exerciseName !== selectedExercise) continue;
      const v = estimatedOneRepMax(s);
      if (v !== null) e1rmByDate.set(s.date, Math.max(v, e1rmByDate.get(s.date) ?? -Infinity));
    }
  }
  const e1rmDates = [...e1rmByDate.keys()].sort();
  const e1rm = e1rmDates.map((d) => e1rmByDate.get(d) ?? null);
  const rpes = nonNull(sets.map((s) => s.actualRpe)).filter((v) => v > 0);
  const rirs = nonNull(sets.map((s) => s.actualRir));
  const loadDeltas = nonNull(sets.map((s) => (s.actualLoad === null || s.targetLoad === null || s.targetLoad <= 0 ? null : s.actualLoad - s.targetLoad)));
  const completed = sets.filter(workoutSetCountsAsCompleted).length;
  return {
    weeks, muscleRanking: ranking, selectedMuscle, muscleWeekly,
    muscleStat: maxAvgMin(nonNull(muscleWeekly)),
    tonnageWeekly,
    totalTonnage: nonNull(tonnageWeekly).reduce((a, b) => a + b, 0),
    exercises, selectedExercise, e1rmDates, e1rm,
    bestE1rm: e1rm.length ? Math.max(...nonNull(e1rm)) : null,
    e1rmDelta: firstToLastDelta(e1rm),
    avgRpe: average(rpes),
    avgRir: average(rirs),
    setsCompleted: completed,
    setsLogged: sets.length,
    completionPercent: sets.length ? (100 * completed) / sets.length : null,
    avgLoadDelta: average(loadDeltas),
    sessionDays: new Set(sets.map((s) => s.date)).size,
  };
}

// ---- Body ----

export interface BodyData {
  dates: string[];
  weight: (number | null)[];
  bodyFat: (number | null)[];
  leanMass: (number | null)[];
  weightTrend: (number | null)[];
  latestWeight: number | null;
  latestBodyFat: number | null;
  weightDelta: number | null;
  bodyFatDelta: number | null;
  leanMassDelta: number | null;
}

export function loadBody(shards: Shards, start: string, end: string): BodyData {
  const days = eachDayInclusive(start, end);
  const home = byDate(rangeRecords(shards, D.HOME, start, end, normHome));
  const weight = days.map((d) => home.get(d)?.weightKg ?? null);
  const bodyFat = days.map((d) => home.get(d)?.bodyFatPercent ?? null);
  const leanMass = days.map((_, i) => (weight[i] !== null && bodyFat[i] !== null ? weight[i]! * (1 - bodyFat[i]! / 100) : null));
  return {
    dates: days, weight, bodyFat, leanMass,
    weightTrend: linearTrend(weight),
    latestWeight: nonNull(weight).pop() ?? null,
    latestBodyFat: nonNull(bodyFat).pop() ?? null,
    weightDelta: firstToLastDelta(weight),
    bodyFatDelta: firstToLastDelta(bodyFat),
    leanMassDelta: firstToLastDelta(leanMass),
  };
}

// ---- Sleep ----

export interface Correlation {
  label: string;
  r: number;
}

export interface SleepData {
  dates: string[];
  sleepHours: (number | null)[];
  sleepStat: TripleStat | null;
  sleepTargetHours: number | null;
  avgBedtimeMinutes: number | null;
  avgWakeMinutes: number | null;
  bedtimeVarianceMinutes: number | null;
  stageSeries: StackedSeries[];
  efficiency: (number | null)[];
  latencyMinutes: (number | null)[];
  avgEfficiency: number | null;
  avgLatencyMinutes: number | null;
  bedtimeRows: DayTimes[];
  wakeRows: DayTimes[];
  correlations: Correlation[];
}

const corr = (label: string, x: (number | null)[], y: (number | null)[]): Correlation | null => {
  const r = pearson(x, y);
  return r === null ? null : { label, r };
};

export function loadSleep(shards: Shards, start: string, end: string): SleepData {
  const days = eachDayInclusive(start, end);
  const sleep = sleepWithPrior(shards, start, end);
  const self = byDate(rangeRecords(shards, D.SELF, start, end, normSelf));
  const workouts = groupBy(rangeRecords(shards, D.WORKOUT_SETS, start, end, normWorkoutSet), (s) => s.date);
  const night = (d: string) => sleep.get(addDays(d, -1));

  const sleepHours = days.map((d) => actualSleepHours(sleep, d));
  const plannedSleep = nonNull(days.map((d) => plannedSleepHours(sleep, d)));
  const bedtimes = nonNull(days.map((d) => { const n = night(d); return n ? parseHhMm(effectiveBedtime(n)) : null; }));
  const wakes = nonNull(days.map((d) => { const n = night(d); return n ? parseHhMm(effectiveWakeUp(n)) : null; }));
  const stage = (pick: (n: SleepEntry) => number | null) => days.map((d) => { const n = night(d); const v = n ? pick(n) : null; return v === null ? 0 : v / 60; });
  const efficiency = days.map((d) => {
    const n = night(d);
    if (!n) return null;
    const inBed = durationMinutes(effectiveBedtime(n), effectiveWakeUp(n));
    const asleep = totalSleepMinutes(n);
    return inBed === null || asleep === null || inBed <= 0 ? null : (100 * asleep) / inBed;
  });
  const latency = days.map((d) => { const n = night(d); return n ? latencyMinutes(n) : null; });
  const pct = (pick: (n: SleepEntry) => number | null) => days.map((d) => { const n = night(d); return n ? stagePercent(pick(n), totalSleepMinutes(n)) : null; });
  const remPct = pct((n) => n.remMinutes);
  const deepPct = pct((n) => n.deepMinutes);
  const mood = days.map((d) => self.get(d)?.mood ?? null);
  const energy = days.map((d) => self.get(d)?.energy ?? null);
  const trainingVol = days.map((d) => { const s = workouts.get(d) ?? []; return s.length ? s.reduce((a, x) => a + tonnage(x), 0) : null; });

  const rows = (pick: (n: SleepEntry) => string | null) => nonNull(days.map((d) => {
    const n = night(d);
    const m = n ? parseHhMm(pick(n)) : null;
    return m === null ? null : { date: d, minutes: [m] };
  }));

  return {
    dates: days, sleepHours,
    sleepStat: maxAvgMin(nonNull(sleepHours)),
    sleepTargetHours: average(plannedSleep),
    avgBedtimeMinutes: circularMeanMinutes(bedtimes),
    avgWakeMinutes: circularMeanMinutes(wakes),
    bedtimeVarianceMinutes: circularStdDevMinutes(bedtimes),
    stageSeries: [
      { name: "Deep", color: STAGE_COLORS.Deep, values: stage((n) => n.deepMinutes) },
      { name: "Light", color: STAGE_COLORS.Light, values: stage((n) => n.lightMinutes) },
      { name: "REM", color: STAGE_COLORS.REM, values: stage((n) => n.remMinutes) },
      { name: "Awake", color: STAGE_COLORS.Awake, values: stage((n) => n.awakeMinutes) },
    ],
    efficiency, latencyMinutes: latency,
    avgEfficiency: average(nonNull(efficiency)),
    avgLatencyMinutes: average(nonNull(latency)),
    bedtimeRows: rows(effectiveBedtime),
    wakeRows: rows(effectiveWakeUp),
    correlations: nonNull([
      corr("REM % vs mood", remPct, mood),
      corr("Deep % vs mood", deepPct, mood),
      corr("Hours vs mood", sleepHours, mood),
      corr("Efficiency vs mood", efficiency, mood),
      corr("Latency vs mood", latency, mood),
      corr("Hours vs energy", sleepHours, energy),
      corr("Hours vs training", sleepHours, trainingVol),
      corr("REM % vs energy", remPct, energy),
    ]).sort((a, b) => Math.abs(b.r) - Math.abs(a.r)),
  };
}

// ---- Self ----

export interface SelfScaleSeries {
  label: string;
  lowLabel: string;
  highLabel: string;
  values: (number | null)[];
  average: number | null;
  delta: number | null;
  daysRated: number;
}

export interface MoodGridRow {
  monday: string;
  cells: (number | null)[];
}

export interface SelfData {
  dates: string[];
  scales: SelfScaleSeries[];
  moodCorrelations: (Correlation & { pairedDays: number })[];
  mood: (number | null)[];
  energy: (number | null)[];
  stress: (number | null)[];
  moodTrend: (number | null)[];
  moodAverage: number | null;
  energyAverage: number | null;
  stressAverage: number | null;
  moodDelta: number | null;
  moodRows: MoodGridRow[];
  moodDistribution: number[];
  moodOnGoodSleep: number | null;
  moodOnPoorSleep: number | null;
  goodSleepThresholdHours: number;
  sleepRatingOnGoodSleep: number | null;
  sleepRatingOnPoorSleep: number | null;
  moodOnTrainingDays: number | null;
  moodOnRestDays: number | null;
  moodDayAfterTraining: number | null;
  moodOnHighHabitDays: number | null;
  moodOnLowHabitDays: number | null;
  habitThresholdPercent: number;
  intentionDays: number;
  moodWithIntention: number | null;
  moodWithoutIntention: number | null;
  loggedDays: number;
  journalDays: number;
  highlightDays: number;
  lowlightDays: number;
  gratitudeDays: number;
  avgWordsPerJournalDay: number | null;
  journalRateOnLowMoodDays: number | null;
  journalRateOnHighMoodDays: number | null;
}

const reflectionRate = (entries: SelfEntry[]) => (entries.length ? (100 * entries.filter(hasReflection).length) / entries.length : null);
const filled = (v: string | null) => !!v && !!v.trim();

export function loadSelf(shards: Shards, start: string, end: string): SelfData {
  const days = eachDayInclusive(start, end);
  const entries = byDate(rangeRecords(shards, D.SELF, start, end, normSelf));
  const sleep = sleepWithPrior(shards, start, end);
  const trainingDays = new Set(rangeRecords(shards, D.WORKOUT_SETS, start, end, normWorkoutSet).map((s) => s.date));
  const habits = groupBy(rangeRecords(shards, D.HABIT_LOGS, start, end, normHabitLog), (l) => l.date);

  const scales = SELF_SCALES.map((scale) => {
    const values = days.map((d) => { const e = entries.get(d); return e ? scaleValue(scale, e) : null; });
    return {
      label: scale.label, lowLabel: scale.lowLabel, highLabel: scale.highLabel, values,
      average: average(nonNull(values)), delta: halfSplitDelta(values), daysRated: values.filter((v) => v !== null).length,
    };
  });
  const series = (id: string) => scales[SELF_SCALES.findIndex((s) => s.id === id)].values;
  const mood = series("mood");
  const energy = series("energy");
  const stress = series("stress");
  const moodValues = nonNull(mood);

  const moodCorrelations = nonNull(SELF_SCALES.map((scale, i) => {
    if (scale.id === "mood") return null;
    const values = scales[i].values;
    const r = pearson(mood, values);
    return r === null ? null : { label: scale.label, r, pairedDays: days.filter((_, j) => mood[j] !== null && values[j] !== null).length };
  })).sort((a, b) => Math.abs(b.r) - Math.abs(a.r));

  const goodMood: number[] = [], poorMood: number[] = [], goodRating: number[] = [], poorRating: number[] = [];
  const trainMood: number[] = [], restMood: number[] = [], afterTrain: number[] = [], highHabit: number[] = [], lowHabit: number[] = [];
  days.forEach((d, i) => {
    const hours = actualSleepHours(sleep, d);
    const e = entries.get(d);
    if (e?.sleepQuality != null && hours !== null) (hours >= GOOD_SLEEP_HOURS ? goodRating : poorRating).push(e.sleepQuality);
    const m = e?.mood;
    if (m == null) return;
    if (hours !== null) (hours >= GOOD_SLEEP_HOURS ? goodMood : poorMood).push(m);
    (trainingDays.has(d) ? trainMood : restMood).push(m);
    if (i > 0 && trainingDays.has(days[i - 1])) afterTrain.push(m);
    const scores = nonNull((habits.get(d) ?? []).map((l) => habitScore(l.status)));
    if (scores.length) ((100 * scores.reduce((a, b) => a + b, 0)) / scores.length >= HIGH_HABIT_PERCENT ? highHabit : lowHabit).push(m);
  });

  const logged = nonNull(days.map((d) => entries.get(d))).filter(hasAnyContent);
  const withIntention = logged.filter((e) => filled(e.intention));
  const withoutIntention = logged.filter((e) => !filled(e.intention));
  const reflected = logged.filter(hasReflection);

  const moodByDate = new Map(days.map((d, i) => [d, mood[i]]));
  const moodRows = days.length
    ? weekStartsMonday(start, end).map((monday) => ({
      monday,
      cells: Array.from({ length: 7 }, (_, k) => {
        const d = addDays(monday, k);
        return d < start || d > end ? null : moodByDate.get(d) ?? null;
      }),
    }))
    : [];

  return {
    dates: days, scales, moodCorrelations, mood, energy, stress,
    moodTrend: linearTrend(mood),
    moodAverage: average(moodValues),
    energyAverage: average(nonNull(energy)),
    stressAverage: average(nonNull(stress)),
    moodDelta: halfSplitDelta(mood),
    moodRows,
    moodDistribution: [1, 2, 3, 4, 5].map((lvl) => moodValues.filter((v) => Math.trunc(v) === lvl).length),
    moodOnGoodSleep: average(goodMood),
    moodOnPoorSleep: average(poorMood),
    goodSleepThresholdHours: GOOD_SLEEP_HOURS,
    sleepRatingOnGoodSleep: average(goodRating),
    sleepRatingOnPoorSleep: average(poorRating),
    moodOnTrainingDays: average(trainMood),
    moodOnRestDays: average(restMood),
    moodDayAfterTraining: average(afterTrain),
    moodOnHighHabitDays: average(highHabit),
    moodOnLowHabitDays: average(lowHabit),
    habitThresholdPercent: HIGH_HABIT_PERCENT,
    intentionDays: withIntention.length,
    moodWithIntention: average(nonNull(withIntention.map((e) => e.mood))),
    moodWithoutIntention: average(nonNull(withoutIntention.map((e) => e.mood))),
    loggedDays: logged.length,
    journalDays: reflected.length,
    highlightDays: logged.filter((e) => filled(e.highlight)).length,
    lowlightDays: logged.filter((e) => filled(e.lowlight)).length,
    gratitudeDays: logged.filter((e) => filled(e.gratitude)).length,
    avgWordsPerJournalDay: average(reflected.map(writtenWordCount)),
    journalRateOnLowMoodDays: reflectionRate(logged.filter((e) => (e.mood ?? 0) >= 1 && (e.mood ?? 0) <= 2)),
    journalRateOnHighMoodDays: reflectionRate(logged.filter((e) => (e.mood ?? 0) >= 4 && (e.mood ?? 0) <= 5)),
  };
}
