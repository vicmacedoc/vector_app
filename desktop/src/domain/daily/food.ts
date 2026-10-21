// Food calendar rules: FoodEntry helpers, preset expansion, save baking, import drafts and day totals.
import { dayOfWeek } from "@/domain/dates";
import { clipboardItemsFor, num, str, type CalendarAiMode } from "./clipboard";
import { normFood, type AuditStatus, type FoodEntry } from "./models";
import { normalizeMealTimeOrBlank } from "./time";

export const effectiveTime = (e: FoodEntry): string =>
  (e.time && e.time.trim()) || (e.plannedTime && e.plannedTime.trim()) || "12:00";

export const effectiveAmount = (e: FoodEntry): number => e.amount ?? e.plannedAmount ?? 0;

export const hasPresetBaseline = (e: FoodEntry): boolean => e.plannedTime !== null || e.plannedAmount !== null;

export const currentMacros = (e: FoodEntry) => ({
  kcal: e.kcal * e.quantityMultiplier,
  protein: e.protein * e.quantityMultiplier,
  carbs: e.carbs * e.quantityMultiplier,
  fats: e.fats * e.quantityMultiplier,
});

/** Stored macros relate to [amount]; scale back to plannedAmount. Unplanned meals contribute 0. */
function plannedNutrient(e: FoodEntry, stored: number): number {
  if (!hasPresetBaseline(e)) return 0;
  if (e.plannedAmount === null) return stored;
  const base = effectiveAmount(e);
  if (base === 0) return stored;
  return stored * (e.plannedAmount / base);
}

export const plannedMacros = (e: FoodEntry) => ({
  kcal: plannedNutrient(e, e.kcal),
  protein: plannedNutrient(e, e.protein),
  carbs: plannedNutrient(e, e.carbs),
  fats: plannedNutrient(e, e.fats),
});

/** PLANNED when the preset row is untouched, ADJUSTED when slider/time/amount differ, UNPLANNED for manual rows. */
export function resolveAuditStatus(e: FoodEntry, multiplier = e.quantityMultiplier): AuditStatus {
  if (!hasPresetBaseline(e)) return "UNPLANNED";
  const timeDiffers = e.plannedTime !== null && effectiveTime(e) !== e.plannedTime;
  const amountDiffers = e.plannedAmount !== null && effectiveAmount(e) !== e.plannedAmount;
  return multiplier !== 1 || timeDiffers || amountDiffers ? "ADJUSTED" : "PLANNED";
}

/** FoodRepository.saveDay: bakes the multiplier into macros and amount, never into the plan baseline. */
export function bakeForSave(e: FoodEntry): FoodEntry {
  const m = e.quantityMultiplier;
  const baked: FoodEntry = {
    ...e,
    kcal: e.kcal * m,
    protein: e.protein * m,
    carbs: e.carbs * m,
    fats: e.fats * m,
    amount: effectiveAmount(e) * m,
    time: effectiveTime(e),
    quantityMultiplier: 1,
  };
  return { ...baked, status: resolveAuditStatus(baked, 1) };
}

export const foodWasEaten = (e: FoodEntry) => e.isChecked && !e.notEaten;

export interface FoodDayTotals {
  got: { kcal: number; protein: number; carbs: number; fats: number };
  planned: { kcal: number; protein: number; carbs: number; fats: number };
}

export function foodDayTotals(entries: FoodEntry[]): FoodDayTotals {
  const got = { kcal: 0, protein: 0, carbs: 0, fats: 0 };
  const planned = { kcal: 0, protein: 0, carbs: 0, fats: 0 };
  for (const e of entries) {
    if (foodWasEaten(e)) {
      const c = currentMacros(e);
      got.kcal += c.kcal; got.protein += c.protein; got.carbs += c.carbs; got.fats += c.fats;
    }
    if (hasPresetBaseline(e)) {
      const p = plannedMacros(e);
      planned.kcal += p.kcal; planned.protein += p.protein; planned.carbs += p.carbs; planned.fats += p.fats;
    }
  }
  return { got, planned };
}

/** Rows grouped by effective time (sticky headers), times ascending, rows by name. */
export function groupByTime(entries: FoodEntry[]): { time: string; entries: FoodEntry[] }[] {
  const map = new Map<string, FoodEntry[]>();
  for (const e of entries) {
    const t = effectiveTime(e);
    map.set(t, [...(map.get(t) ?? []), e]);
  }
  return [...map.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([time, rows]) => ({ time, entries: rows }));
}

// ---- preset ----

export interface FoodPlanEntry {
  id: string;
  time: string;
  name: string;
  kcal: number;
  protein: number;
  carbs: number;
  fats: number;
  amount: number;
  unit: string;
}

export type Weekday = "monday" | "tuesday" | "wednesday" | "thursday" | "friday" | "saturday" | "sunday";
export const WEEKDAYS: readonly Weekday[] = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"];

export const weekdayOf = (date: string): Weekday => dayOfWeek(date).toLowerCase() as Weekday;

export interface WeeklyFoodPlan {
  id: string;
  name: string;
  weekly_plan: Record<Weekday, FoodPlanEntry[]>;
}

export function parseFoodPlan(content: string): WeeklyFoodPlan {
  const root = JSON.parse(content) as Record<string, unknown>;
  const days = (root.weekly_plan ?? {}) as Record<string, unknown>;
  if (typeof root.name !== "string") throw new Error("Food preset needs a name");
  const weekly = {} as Record<Weekday, FoodPlanEntry[]>;
  for (const d of WEEKDAYS) {
    const list = Array.isArray(days[d]) ? (days[d] as Record<string, unknown>[]) : [];
    weekly[d] = list.map((x, i) => {
      if (typeof x.time !== "string" || typeof x.name !== "string" || num(x.kcal) === null || num(x.amount) === null || typeof x.unit !== "string") {
        throw new Error(`${d} meal #${i + 1} needs time, name, kcal, amount and unit`);
      }
      return {
        id: str(x.id), time: x.time, name: x.name, kcal: num(x.kcal)!, protein: num(x.protein) ?? 0,
        carbs: num(x.carbs) ?? 0, fats: num(x.fats) ?? 0, amount: num(x.amount)!, unit: x.unit,
      };
    });
  }
  return { id: str(root.id), name: root.name, weekly_plan: weekly };
}

/** Preset rows for [date]'s weekday. Ids are stable per slot so an untouched day equals itself. */
export function presetFoodEntries(plan: WeeklyFoodPlan, date: string): FoodEntry[] {
  return plan.weekly_plan[weekdayOf(date)].map((p, i) =>
    normFood({
      id: `${p.id || `gen_${i}`}_${date}`, date, plannedTime: p.time, name: p.name,
      kcal: p.kcal, protein: p.protein, carbs: p.carbs, fats: p.fats, plannedAmount: p.amount,
      time: p.time, amount: p.amount, quantityMultiplier: 1, unit: p.unit, isChecked: false, status: "PLANNED", timestamp: Date.now(),
    }),
  );
}

// ---- import drafts ----

export interface MealDraft {
  name: string;
  /** HH:mm; blank means "use the clock". */
  time: string;
  amount: number;
  unit: string;
  kcal: number;
  protein: number;
  carbs: number;
  fats: number;
  asPlanned: boolean;
}

export const emptyMealDraft = (): MealDraft => ({
  name: "", time: "", amount: 0, unit: "g", kcal: 0, protein: 0, carbs: 0, fats: 0, asPlanned: false,
});

export function parseMealClipboard(text: string, mode: CalendarAiMode): MealDraft[] {
  const objects = clipboardItemsFor(text, mode, "meals", "meal");
  return objects.map((o, i) => {
    const prefix = objects.length === 1 ? "Meal" : `Meal ${i + 1}`;
    const name = str(o.name).trim();
    if (!name) throw new Error(`${prefix} is missing a name`);
    const amount = num(o.amount) ?? num(o.plannedAmount);
    if (amount === null || amount <= 0) throw new Error(`${prefix} needs an amount greater than 0`);
    const kcal = num(o.kcal) ?? 0;
    if (kcal < 0) throw new Error(`${prefix} has negative kcal`);
    return {
      name, time: normalizeMealTimeOrBlank(str(o.time) || str(o.plannedTime)), amount, unit: str(o.unit) || "g", kcal,
      protein: Math.max(0, num(o.protein) ?? 0), carbs: Math.max(0, num(o.carbs) ?? 0), fats: Math.max(0, num(o.fats) ?? 0),
      asPlanned: false,
    };
  });
}

export function mealDraftError(d: MealDraft): string | null {
  if (!d.name.trim()) return "Name is required";
  if (!normalizeMealTimeOrBlank(d.time)) return "Time must be HH:mm";
  if (d.amount <= 0) return "Amount must be greater than 0";
  if (d.kcal < 0) return "Calories can't be negative";
  return null;
}

export function mealDraftToEntry(d: MealDraft, date: string): FoodEntry {
  const time = normalizeMealTimeOrBlank(d.time) || "12:00";
  return normFood({
    id: crypto.randomUUID(), date, plannedTime: d.asPlanned ? time : null, name: d.name.trim(),
    kcal: d.kcal, protein: d.protein, carbs: d.carbs, fats: d.fats, plannedAmount: d.asPlanned ? d.amount : null,
    time, amount: d.amount, quantityMultiplier: 1, unit: d.unit || "g", isChecked: false, notEaten: false,
    status: d.asPlanned ? "PLANNED" : "UNPLANNED", timestamp: Date.now(),
  });
}

/** Clipboard shape for copying a food row. */
export const foodEntryToClipboard = (e: FoodEntry) => ({
  name: e.name, time: effectiveTime(e), amount: effectiveAmount(e) * e.quantityMultiplier, unit: e.unit,
  ...currentMacros(e),
});
