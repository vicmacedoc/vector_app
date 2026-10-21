// AI day context (CalendarViewModel.*DayContext): what's on the day plus the weekday's preset.
import type { DayContext } from "@/ai/calendar";
import { effectiveEnd, effectiveStart } from "@/domain/daily/activity";
import { preset, routinePlan } from "@/domain/daily/day";
import { effectiveAmount, effectiveTime, parseFoodPlan, weekdayOf } from "@/domain/daily/food";
import type { ExerciseAiContext } from "@/domain/daily/exerciseDraft";
import { EXERCISE_DEFAULTS } from "@/domain/daily/exerciseDraft";
import { normExerciseLib, normWorkoutSet, type ActivityEntry, type ActivityLabel, type FoodEntry, type WorkoutSet } from "@/domain/daily/models";
import { allDayRecords, configRecords } from "@/domain/daily/repo";
import { decodeWorkoutPlan, encodeCompact, groupSessions, parseMuscles, sessionsForDate } from "@/domain/daily/workoutPlan";
import type { ShardRecords } from "@/sync/shardEngine";
import { D, K } from "@/sync/shards";

type Shards = Record<string, ShardRecords>;
const fmt = (v: number) => (Number.isInteger(v) ? String(v) : v.toFixed(1));

export function routineContext(shards: Shards, date: string, activities: ActivityEntry[], labels: ActivityLabel[]): DayContext {
  const names = new Map(labels.map((l) => [l.id, l.name]));
  const existingItems = activities
    .map((a) => `- ${effectiveStart(a)}–${effectiveEnd(a)} ${a.title} [${names.get(a.labelId) ?? ""}] ${a.type}`)
    .join("\n");
  const plan = routinePlan(shards);
  const day = plan?.weekly_plan[weekdayOf(date)];
  return { existingItems, presetJson: day?.length ? JSON.stringify(day) : null };
}

export function foodContext(shards: Shards, date: string, entries: FoodEntry[]): DayContext {
  const existingItems = [...entries]
    .sort((a, b) => effectiveTime(a).localeCompare(effectiveTime(b)))
    .map((e) => `- ${effectiveTime(e)} ${e.name} ${fmt(effectiveAmount(e))} ${e.unit} (${fmt(e.kcal)} kcal, P ${fmt(e.protein)} C ${fmt(e.carbs)} F ${fmt(e.fats)})`)
    .join("\n");
  const p = preset(shards, "Food");
  let presetJson: string | null = null;
  if (p) {
    try {
      const day = parseFoodPlan(p.content).weekly_plan[weekdayOf(date)];
      if (day.length) {
        const sum = (k: "kcal" | "protein" | "carbs" | "fats") => day.reduce((t, x) => t + x[k], 0);
        presetJson = `${JSON.stringify(day)}\nPreset totals: ${fmt(sum("kcal"))} kcal, P ${fmt(sum("protein"))} g, C ${fmt(sum("carbs"))} g, F ${fmt(sum("fats"))} g`;
      }
    } catch {
      presetJson = null;
    }
  }
  return { existingItems, presetJson };
}

export function exerciseContext(shards: Shards, date: string, sets: WorkoutSet[]): DayContext {
  const existingItems = groupSessions(sets)
    .map((g) => `- ${g.title}: ${[...new Set(g.rows.map((r) => r.exerciseName))].join(", ")}`)
    .join("\n");
  const p = preset(shards, "Workout");
  let presetJson: string | null = null;
  if (p) {
    try {
      const day = sessionsForDate(decodeWorkoutPlan(p.content), date);
      if (day.length) presetJson = JSON.stringify(encodeCompact(day));
    } catch {
      presetJson = null;
    }
  }
  return { existingItems, presetJson };
}

export function exerciseAiContext(shards: Shards, sets: WorkoutSet[]): ExerciseAiContext {
  const all = allDayRecords(shards, D.WORKOUT_SETS, normWorkoutSet);
  const muscles = new Set<string>(EXERCISE_DEFAULTS.MUSCLES);
  for (const s of all) for (const m of Object.keys(parseMuscles(s.targetMuscles))) muscles.add(m);
  const recent = [...all].sort((a, b) => b.date.localeCompare(a.date)).map((s) => s.exerciseName).filter(Boolean);
  const lib = configRecords(shards, K.EXERCISE_LIBRARY, normExerciseLib).map((l) => l.name);
  const loads = all.filter((s) => s.targetLoad !== null).map((s) => s.unitLoad);
  return {
    muscleVocabulary: [...muscles],
    loadUnit: loads[0] ?? EXERCISE_DEFAULTS.LOAD_UNIT,
    recentExercises: [...new Set([...recent, ...lib])].slice(0, 40),
    existingSessionTitles: [...new Set(sets.map((s) => s.sessionTitle).filter(Boolean))],
  };
}
