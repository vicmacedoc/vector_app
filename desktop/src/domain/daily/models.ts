// Daily-life entities, field for field with core/src/main/java/com/vm/core/models (kotlinx JSON names).
// Normalizers fill the Kotlin defaults so a record written here compares equal to the phone's.
import type { JsonObject } from "@/sync/merge";

export type AuditStatus = "PLANNED" | "ADJUSTED" | "UNPLANNED";
export type ActivityCompletion = "NONE" | "DONE" | "NOT_DONE" | "CANCELLED" | "NA";
export type ActivityType = "PLANNED" | "NEW";
export type HabitStatus = "NONE" | "NOT_DONE" | "PARTIAL" | "DONE" | "EXCEEDED" | "NA";

export interface FoodEntry {
  id: string;
  date: string;
  plannedTime: string | null;
  name: string;
  kcal: number;
  protein: number;
  carbs: number;
  fats: number;
  plannedAmount: number | null;
  time: string | null;
  amount: number | null;
  quantityMultiplier: number;
  unit: string;
  isChecked: boolean;
  notEaten: boolean;
  status: AuditStatus;
  timestamp: number;
}

export interface ActivityEntry {
  id: string;
  date: string;
  title: string;
  startTime: string;
  endTime: string;
  adjustedStartTime: string | null;
  adjustedEndTime: string | null;
  labelId: string;
  completion: ActivityCompletion;
  type: ActivityType;
  description: string;
  timestamp: number;
}

export interface SleepEntry {
  date: string;
  plannedBedtime: string | null;
  plannedWakeUp: string | null;
  actualBedtime: string | null;
  actualWakeUp: string | null;
  fellAsleepAt: string | null;
  deepMinutes: number | null;
  lightMinutes: number | null;
  remMinutes: number | null;
  awakeMinutes: number | null;
  timestamp: number;
}

export interface WorkoutSet {
  id: string;
  exerciseId: string;
  setNumber: number;
  date: string;
  sessionTitle: string;
  sessionDescription: string;
  exerciseName: string;
  exerciseDescription: string;
  timing: string | null;
  exerciseType: string;
  groupingId: string | null;
  workoutStatus: string | null;
  description: string;
  cadence: string | null;
  sport: string | null;
  stroke: string | null;
  setType: string;
  dropNumber: number;
  unitLoad: string;
  unitDuration: string;
  unitDistance: string;
  unitVelocity: string;
  unitCadence: string;
  targetLoad: number | null;
  targetReps: number | null;
  targetDuration: number | null;
  targetDistance: number | null;
  targetVelocity: number | null;
  targetRir: number | null;
  targetRest: number | null;
  restUnit: string;
  targetZone: number | null;
  targetRpe: number | null;
  targetCadence: number | null;
  totalDuration: number | null;
  unitTotalDuration: string;
  actualLoad: number | null;
  actualReps: number | null;
  actualDuration: number | null;
  actualDistance: number | null;
  actualVelocity: number | null;
  actualRpe: number | null;
  actualRir: number | null;
  actualRest: number | null;
  actualCadence: number | null;
  actualTotalDuration: number | null;
  isCompleted: boolean;
  targetMuscles: string;
}

export interface SelfEntry {
  date: string;
  mood: number | null;
  energy: number | null;
  stress: number | null;
  cognition: number | null;
  motivation: number | null;
  physical: number | null;
  sleepQuality: number | null;
  social: number | null;
  selfEsteem: number | null;
  actions: number | null;
  external: number | null;
  future: number | null;
  intention: string | null;
  highlight: string | null;
  lowlight: string | null;
  gratitude: string | null;
  journalText: string | null;
  journalAudioDriveFileId: string | null;
  timestamp: number;
}

/** `DailyHomeEntry` minus the device-local `dailyPlanAudioPath`. */
export interface HomeEntry {
  date: string;
  weightKg: number | null;
  bodyFatPercent: number | null;
  dailyPlanText: string | null;
  timestamp: number;
}

export interface HabitLog {
  id: string;
  date: string;
  habitId: string;
  status: HabitStatus;
  timestamp: number;
}

export interface HabitDayConfig {
  date: string;
  habitIdsJson: string;
  updatedAt: number;
}

export interface ActivityLabel {
  id: string;
  name: string;
  colorArgb: number;
  isSystem: boolean;
  createdAt: number;
}

export interface HabitDefinition {
  id: string;
  name: string;
  createdAt: number;
}

export interface MealLibraryItem {
  id: string;
  name: string;
  kcal: number;
  protein: number;
  carbs: number;
  fats: number;
  amount: number;
  unit: string;
  nameKey: string;
  createdAt: number;
}

export interface ExerciseLibraryItem {
  id: string;
  type: string;
  name: string;
  description: string;
  payloadJson: string;
  typeKey: string;
  nameKey: string;
  createdAt: number;
}

export interface ActivityLibraryItem {
  id: string;
  title: string;
  startTime: string;
  endTime: string;
  labelName: string;
  type: ActivityType;
  description: string;
  nameKey: string;
  createdAt: number;
}

export type PresetCategory = "Workout" | "Food" | "Routine";

export interface PresetRecord {
  category: PresetCategory;
  displayName: string | null;
  content: string;
  updatedAt: number;
}

export interface SharedSettings {
  sleepTargetBedtimeMinutes: number | null;
  sleepTargetWakeupMinutes: number | null;
  filloutReminderMinutes: number | null;
  learnOnHoldDays: number;
  learnReviewIntervals: number[];
  learnQuizSize: number;
  learnPassPercent: number;
}

export const DEFAULT_SHARED_SETTINGS: SharedSettings = {
  sleepTargetBedtimeMinutes: null,
  sleepTargetWakeupMinutes: null,
  filloutReminderMinutes: null,
  learnOnHoldDays: 14,
  learnReviewIntervals: [3, 7, 21, 60, 180],
  learnQuizSize: 5,
  learnPassPercent: 80,
};

export const SLEEP_LABEL_ID = "label_sleep";
export const SLEEP_COLOR_ARGB = 0xff78909c | 0;
export const REST_DAY_ACK_ID = "__vector_rest_day_ack__";

export const DEFAULT_LABELS: ActivityLabel[] = (
  [
    ["Meditative", 0xff424242], ["Reflective", 0xff7b1fa2], ["Useless", 0xffd81b60], ["Leisure", 0xffd32f2f],
    ["Social", 0xfff57c00], ["Self-care", 0xfff9a825], ["Duties", 0xff7cb342], ["Work", 0xff2e7d32],
    ["Study", 0xff00897b], ["Project", 0xff1565c0], ["Exercise", 0xff6d4c41],
  ] as [string, number][]
).map(([name, color], i) => ({
  id: `label_default_${name.toLowerCase().replace("-", "_")}`,
  name,
  colorArgb: color | 0,
  isSystem: false,
  createdAt: i + 1,
}));

// ---- normalizers ----

type Raw = Record<string, unknown>;
const s = (v: unknown, d = ""): string => (typeof v === "string" ? v : v == null ? d : String(v));
const sn = (v: unknown): string | null => (typeof v === "string" ? v : null);
const n = (v: unknown, d = 0): number => (typeof v === "number" && Number.isFinite(v) ? v : d);
const nn = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);
const b = (v: unknown, d = false): boolean => (typeof v === "boolean" ? v : d);
function oneOf<T extends string>(v: unknown, all: readonly T[], d: T): T {
  return typeof v === "string" && (all as readonly string[]).includes(v) ? (v as T) : d;
}

export const AUDIT: readonly AuditStatus[] = ["PLANNED", "ADJUSTED", "UNPLANNED"];
export const COMPLETIONS: readonly ActivityCompletion[] = ["NONE", "DONE", "NOT_DONE", "CANCELLED", "NA"];
export const HABIT_STATUSES: readonly HabitStatus[] = ["NONE", "NOT_DONE", "PARTIAL", "DONE", "EXCEEDED", "NA"];

export function normFood(r: Raw): FoodEntry {
  return {
    id: s(r.id), date: s(r.date), plannedTime: sn(r.plannedTime), name: s(r.name),
    kcal: n(r.kcal), protein: n(r.protein), carbs: n(r.carbs), fats: n(r.fats),
    plannedAmount: nn(r.plannedAmount), time: sn(r.time), amount: nn(r.amount),
    quantityMultiplier: n(r.quantityMultiplier, 1), unit: s(r.unit), isChecked: b(r.isChecked), notEaten: b(r.notEaten),
    status: oneOf(r.status, AUDIT, "PLANNED"), timestamp: n(r.timestamp, Date.now()),
  };
}

export function normActivity(r: Raw): ActivityEntry {
  return {
    id: s(r.id), date: s(r.date), title: s(r.title), startTime: s(r.startTime), endTime: s(r.endTime),
    adjustedStartTime: sn(r.adjustedStartTime), adjustedEndTime: sn(r.adjustedEndTime), labelId: s(r.labelId),
    completion: oneOf(r.completion, COMPLETIONS, "NONE"), type: oneOf(r.type, ["PLANNED", "NEW"] as const, "NEW"),
    description: s(r.description), timestamp: n(r.timestamp, Date.now()),
  };
}

export function normSleep(r: Raw): SleepEntry {
  return {
    date: s(r.date), plannedBedtime: sn(r.plannedBedtime), plannedWakeUp: sn(r.plannedWakeUp),
    actualBedtime: sn(r.actualBedtime), actualWakeUp: sn(r.actualWakeUp), fellAsleepAt: sn(r.fellAsleepAt),
    deepMinutes: nn(r.deepMinutes), lightMinutes: nn(r.lightMinutes), remMinutes: nn(r.remMinutes), awakeMinutes: nn(r.awakeMinutes),
    timestamp: n(r.timestamp, Date.now()),
  };
}

export function normWorkoutSet(r: Raw): WorkoutSet {
  return {
    id: s(r.id), exerciseId: s(r.exerciseId), setNumber: n(r.setNumber), date: s(r.date),
    sessionTitle: s(r.sessionTitle), sessionDescription: s(r.sessionDescription),
    exerciseName: s(r.exerciseName), exerciseDescription: s(r.exerciseDescription), timing: sn(r.timing),
    exerciseType: s(r.exerciseType, "RESISTANCE"), groupingId: sn(r.groupingId), workoutStatus: sn(r.workoutStatus),
    description: s(r.description), cadence: sn(r.cadence), sport: sn(r.sport), stroke: sn(r.stroke),
    setType: s(r.setType, "NORMAL"), dropNumber: n(r.dropNumber),
    unitLoad: s(r.unitLoad, "kg"), unitDuration: s(r.unitDuration, "s"), unitDistance: s(r.unitDistance, "km"),
    unitVelocity: s(r.unitVelocity, "m/s"), unitCadence: s(r.unitCadence, "rpm"),
    targetLoad: nn(r.targetLoad), targetReps: nn(r.targetReps), targetDuration: nn(r.targetDuration),
    targetDistance: nn(r.targetDistance), targetVelocity: nn(r.targetVelocity), targetRir: nn(r.targetRir),
    targetRest: nn(r.targetRest), restUnit: s(r.restUnit, "s"), targetZone: nn(r.targetZone), targetRpe: nn(r.targetRpe),
    targetCadence: nn(r.targetCadence), totalDuration: nn(r.totalDuration), unitTotalDuration: s(r.unitTotalDuration, "min"),
    actualLoad: nn(r.actualLoad), actualReps: nn(r.actualReps), actualDuration: nn(r.actualDuration),
    actualDistance: nn(r.actualDistance), actualVelocity: nn(r.actualVelocity), actualRpe: nn(r.actualRpe),
    actualRir: nn(r.actualRir), actualRest: nn(r.actualRest), actualCadence: nn(r.actualCadence),
    actualTotalDuration: nn(r.actualTotalDuration), isCompleted: b(r.isCompleted), targetMuscles: s(r.targetMuscles),
  };
}

export function normSelf(r: Raw): SelfEntry {
  return {
    date: s(r.date), mood: nn(r.mood), energy: nn(r.energy), stress: nn(r.stress), cognition: nn(r.cognition),
    motivation: nn(r.motivation), physical: nn(r.physical), sleepQuality: nn(r.sleepQuality), social: nn(r.social),
    selfEsteem: nn(r.selfEsteem), actions: nn(r.actions), external: nn(r.external), future: nn(r.future),
    intention: sn(r.intention), highlight: sn(r.highlight), lowlight: sn(r.lowlight), gratitude: sn(r.gratitude),
    journalText: sn(r.journalText), journalAudioDriveFileId: sn(r.journalAudioDriveFileId), timestamp: n(r.timestamp, Date.now()),
  };
}

export function normHome(r: Raw): HomeEntry {
  return {
    date: s(r.date), weightKg: nn(r.weightKg), bodyFatPercent: nn(r.bodyFatPercent),
    dailyPlanText: sn(r.dailyPlanText), timestamp: n(r.timestamp, Date.now()),
  };
}

export function normHabitLog(r: Raw): HabitLog {
  return { id: s(r.id), date: s(r.date), habitId: s(r.habitId), status: oneOf(r.status, HABIT_STATUSES, "NONE"), timestamp: n(r.timestamp, Date.now()) };
}

export function normHabitConfig(r: Raw): HabitDayConfig {
  return { date: s(r.date), habitIdsJson: s(r.habitIdsJson, "[]"), updatedAt: n(r.updatedAt, Date.now()) };
}

export function normLabel(r: Raw): ActivityLabel {
  return { id: s(r.id), name: s(r.name), colorArgb: n(r.colorArgb) | 0, isSystem: b(r.isSystem), createdAt: n(r.createdAt, Date.now()) };
}

export function normHabit(r: Raw): HabitDefinition {
  return { id: s(r.id), name: s(r.name), createdAt: n(r.createdAt, Date.now()) };
}

export function normMealLib(r: Raw): MealLibraryItem {
  return {
    id: s(r.id), name: s(r.name), kcal: n(r.kcal), protein: n(r.protein), carbs: n(r.carbs), fats: n(r.fats),
    amount: n(r.amount), unit: s(r.unit, "g"), nameKey: s(r.nameKey), createdAt: n(r.createdAt, Date.now()),
  };
}

export function normExerciseLib(r: Raw): ExerciseLibraryItem {
  return {
    id: s(r.id), type: s(r.type), name: s(r.name), description: s(r.description), payloadJson: s(r.payloadJson, "{}"),
    typeKey: s(r.typeKey), nameKey: s(r.nameKey), createdAt: n(r.createdAt, Date.now()),
  };
}

export function normActivityLib(r: Raw): ActivityLibraryItem {
  return {
    id: s(r.id), title: s(r.title), startTime: s(r.startTime), endTime: s(r.endTime), labelName: s(r.labelName),
    type: oneOf(r.type, ["PLANNED", "NEW"] as const, "PLANNED"), description: s(r.description), nameKey: s(r.nameKey),
    createdAt: n(r.createdAt, Date.now()),
  };
}

export function normPreset(r: Raw): PresetRecord {
  return {
    category: oneOf(r.category, ["Workout", "Food", "Routine"] as const, "Food"),
    displayName: sn(r.displayName), content: s(r.content), updatedAt: n(r.updatedAt, Date.now()),
  };
}

export function normSettings(r: Raw | undefined): SharedSettings {
  const x = r ?? {};
  const intervals = Array.isArray(x.learnReviewIntervals)
    ? (x.learnReviewIntervals as unknown[]).filter((v): v is number => typeof v === "number" && v >= 1)
    : DEFAULT_SHARED_SETTINGS.learnReviewIntervals;
  return {
    sleepTargetBedtimeMinutes: nn(x.sleepTargetBedtimeMinutes),
    sleepTargetWakeupMinutes: nn(x.sleepTargetWakeupMinutes),
    filloutReminderMinutes: nn(x.filloutReminderMinutes),
    learnOnHoldDays: n(x.learnOnHoldDays, 14),
    learnReviewIntervals: intervals.length ? intervals : DEFAULT_SHARED_SETTINGS.learnReviewIntervals,
    learnQuizSize: n(x.learnQuizSize, 5),
    learnPassPercent: n(x.learnPassPercent, 80),
  };
}

export const asJson = <T extends object>(v: T): JsonObject => v as unknown as JsonObject;

export const habitLogId = (date: string, habitId: string) => `hlog_${date}_${habitId}`;
export const nameKey = (name: string) => name.trim().toLowerCase();
export const exerciseLibKey = (typeKey: string, nameKeyValue: string) => `${typeKey}|${nameKeyValue}`;
