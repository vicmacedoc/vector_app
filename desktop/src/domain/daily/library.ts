// LibraryRepository over the config shard: meals, activities and exercise sessions keyed by name.
import type { ShardRecords } from "@/sync/shardEngine";
import { K } from "@/sync/shards";
import { normalizeActivityHhMm } from "./time";
import { parseActivityType, type RoutinePlanActivity } from "./activity";
import { effectiveAmount, type FoodPlanEntry } from "./food";
import {
  asJson, exerciseLibKey, nameKey, normActivityLib, normExerciseLib, normMealLib,
  type ActivityLibraryItem, type ActivityType, type ExerciseLibraryItem, type FoodEntry, type MealLibraryItem,
} from "./models";
import { sessionLibraryFields, type WorkoutDaySession } from "./workoutPlan";
import { putRecord } from "./repo";

type Rec = ShardRecords;

export interface MealInput {
  name: string;
  kcal: number;
  protein: number;
  carbs: number;
  fats: number;
  amount: number;
  unit: string;
}

export function upsertMeal(r: Rec, m: MealInput): Rec {
  const trimmed = m.name.trim();
  if (!trimmed) return r;
  const key = nameKey(trimmed);
  const existing = r[K.MEAL_LIBRARY]?.[key];
  const prev = existing ? normMealLib(existing) : null;
  const item: MealLibraryItem = {
    id: prev?.id ?? `mealib_${crypto.randomUUID()}`, name: trimmed, kcal: m.kcal, protein: m.protein, carbs: m.carbs,
    fats: m.fats, amount: m.amount, unit: m.unit || "g", nameKey: key, createdAt: prev?.createdAt ?? Date.now(),
  };
  return putRecord(r, K.MEAL_LIBRARY, key, asJson(item));
}

export const upsertMealsFromPlan = (r: Rec, entries: FoodPlanEntry[]) =>
  entries.reduce((acc, e) => upsertMeal(acc, { ...e, amount: e.amount }), r);

export const upsertMealsFromEntries = (r: Rec, entries: FoodEntry[]) =>
  entries.reduce((acc, e) => upsertMeal(acc, { ...e, amount: effectiveAmount(e) }), r);

export interface ActivityInput {
  title: string;
  startTime: string;
  endTime: string;
  labelName: string;
  type: ActivityType;
  description: string;
}

export function upsertActivity(r: Rec, a: ActivityInput): Rec {
  const trimmed = a.title.trim();
  if (!trimmed) return r;
  const key = nameKey(trimmed);
  const existing = r[K.ACTIVITY_LIBRARY]?.[key];
  const prev = existing ? normActivityLib(existing) : null;
  const item: ActivityLibraryItem = {
    id: prev?.id ?? `actlib_${crypto.randomUUID()}`, title: trimmed, startTime: a.startTime, endTime: a.endTime,
    labelName: a.labelName.trim(), type: a.type, description: a.description.trim(), nameKey: key,
    createdAt: prev?.createdAt ?? Date.now(),
  };
  return putRecord(r, K.ACTIVITY_LIBRARY, key, asJson(item));
}

export const upsertActivitiesFromPlan = (r: Rec, items: RoutinePlanActivity[]) =>
  items.reduce(
    (acc, i) =>
      upsertActivity(acc, {
        title: i.title, startTime: normalizeActivityHhMm(i.startTime, "09:00"), endTime: normalizeActivityHhMm(i.endTime, "10:00"),
        labelName: i.label, type: parseActivityType(i.type, "PLANNED"), description: i.description,
      }),
    r,
  );

export function upsertExerciseSession(r: Rec, session: WorkoutDaySession): Rec {
  const f = sessionLibraryFields(session);
  if (!f) return r;
  const typeKey = f.type.toLowerCase();
  const nk = f.name.toLowerCase();
  const key = exerciseLibKey(typeKey, nk);
  const existing = r[K.EXERCISE_LIBRARY]?.[key];
  const prev = existing ? normExerciseLib(existing) : null;
  const item: ExerciseLibraryItem = {
    id: prev?.id ?? `exlib_${crypto.randomUUID()}`, type: f.type, name: f.name, description: session.description,
    payloadJson: f.payloadJson, typeKey, nameKey: nk, createdAt: prev?.createdAt ?? Date.now(),
  };
  return putRecord(r, K.EXERCISE_LIBRARY, key, asJson(item));
}

export const upsertExerciseSessions = (r: Rec, sessions: WorkoutDaySession[]) => sessions.reduce(upsertExerciseSession, r);

export function deleteLibraryItem(r: Rec, collection: string, id: string): Rec {
  const entry = Object.entries(r[collection] ?? {}).find(([, v]) => v.id === id);
  return entry ? putRecord(r, collection, entry[0], null) : r;
}
