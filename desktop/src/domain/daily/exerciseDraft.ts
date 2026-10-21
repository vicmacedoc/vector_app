// Exercise review drafts (ExerciseImportDraft.kt): normalizers, validation, plan conversion.
import { optionalValue } from "./time";
import type {
  MuscleMap, WorkoutDaySession, WorkoutDropPlan, WorkoutExercisePlan, WorkoutIntervalPlan, WorkoutSetPlan,
} from "./workoutPlan";
import { exerciseDurationUnit } from "./workoutPlan";

export type ExerciseKind = "RESISTANCE" | "ENDURANCE" | "OTHER";
export const EXERCISE_KINDS: readonly ExerciseKind[] = ["RESISTANCE", "ENDURANCE", "OTHER"];

export const EXERCISE_DEFAULTS = {
  LOAD_UNIT: "lbs",
  REST_UNIT: "s",
  SET_DURATION_UNIT: "s",
  EXERCISE_DURATION_UNIT: "min",
  INTERVAL_DURATION_UNIT: "min",
  DISTANCE_UNIT: "km",
  CADENCE_UNIT: "rpm",
  LOAD_UNITS: ["lbs", "kg"],
  REST_UNITS: ["s", "min"],
  DURATION_UNITS: ["min", "s"],
  DISTANCE_UNITS: ["km", "mi", "m", "yd"],
  SPORTS: ["RUN", "BIKE", "SWIM"],
  STROKES: ["FREE", "BACK", "BREAST", "FLY", "IM", "CHOICE", "DRILL", "KICK"],
  SUPERSET_GROUPS: ["A", "B", "C", "D"],
  MAX_ZONE: 5,
  MUSCLES: [
    "chest", "back", "shoulder", "triceps", "biceps", "forearms", "traps",
    "quads", "hamstrings", "glutes", "calves", "adductors", "core", "neck",
  ],
} as const;

export interface ExerciseDropDraft {
  description: string;
  targetLoad: number | null;
  unitLoad: string | null;
  targetReps: number | null;
  targetRir: number | null;
}

export interface ExerciseSetDraft {
  description: string;
  targetLoad: number | null;
  unitLoad: string;
  targetReps: number | null;
  targetRir: number | null;
  rest: number | null;
  restUnit: string;
  targetDuration: number | null;
  unitDuration: string;
  drops: ExerciseDropDraft[];
}

export interface ExerciseIntervalDraft {
  description: string;
  targetDuration: number | null;
  unitDuration: string;
  targetDistance: number | null;
  unitDistance: string;
  targetZone: number | null;
  targetRpe: number | null;
  targetCadence: number | null;
  unitCadence: string;
  rest: number | null;
  restUnit: string;
}

export interface ExerciseDraft {
  name: string;
  description: string;
  targets: MuscleMap;
  supersetGroup: string | null;
  timing: string | null;
  totalDuration: number | null;
  unitDuration: string;
  stroke: string | null;
  rest: number | null;
  restUnit: string;
  sets: ExerciseSetDraft[];
  intervals: ExerciseIntervalDraft[];
}

export interface ExerciseSessionDraft {
  title: string;
  description: string;
  kind: ExerciseKind;
  sport: string | null;
  exercises: ExerciseDraft[];
}

export interface ExerciseAiContext {
  muscleVocabulary: string[];
  loadUnit: string;
  recentExercises: string[];
  existingSessionTitles: string[];
}

export const emptyDrop = (): ExerciseDropDraft => ({ description: "", targetLoad: null, unitLoad: null, targetReps: null, targetRir: null });

export const emptySet = (unitLoad: string = EXERCISE_DEFAULTS.LOAD_UNIT): ExerciseSetDraft => ({
  description: "", targetLoad: null, unitLoad, targetReps: null, targetRir: null, rest: null,
  restUnit: EXERCISE_DEFAULTS.REST_UNIT, targetDuration: null, unitDuration: EXERCISE_DEFAULTS.SET_DURATION_UNIT, drops: [],
});

export const emptyInterval = (): ExerciseIntervalDraft => ({
  description: "", targetDuration: null, unitDuration: EXERCISE_DEFAULTS.INTERVAL_DURATION_UNIT, targetDistance: null,
  unitDistance: EXERCISE_DEFAULTS.DISTANCE_UNIT, targetZone: null, targetRpe: null, targetCadence: null,
  unitCadence: EXERCISE_DEFAULTS.CADENCE_UNIT, rest: null, restUnit: EXERCISE_DEFAULTS.REST_UNIT,
});

export const emptyExercise = (): ExerciseDraft => ({
  name: "", description: "", targets: {}, supersetGroup: null, timing: null, totalDuration: null,
  unitDuration: EXERCISE_DEFAULTS.EXERCISE_DURATION_UNIT, stroke: null, rest: null, restUnit: EXERCISE_DEFAULTS.REST_UNIT,
  sets: [], intervals: [],
});

export const emptySessionDraft = (kind: ExerciseKind = "RESISTANCE"): ExerciseSessionDraft => ({
  title: "", description: "", kind, sport: null, exercises: [],
});

// ---- normalizers ----

export function parseExerciseKind(raw: string | null | undefined, fallback: ExerciseKind = "RESISTANCE"): ExerciseKind {
  const v = optionalValue(raw)?.toUpperCase();
  return (EXERCISE_KINDS as readonly string[]).includes(v ?? "") ? (v as ExerciseKind) : fallback;
}

export const normalizeMuscleTag = (raw: string | null | undefined) => optionalValue(raw)?.toLowerCase().replace(/ /g, "_") ?? null;

const MAX_MUSCLE_WEIGHT = 2.0;

export function normalizeMuscleTargets(raw: MuscleMap): MuscleMap {
  const out: MuscleMap = {};
  for (const [tag, weight] of Object.entries(raw)) {
    const key = normalizeMuscleTag(tag);
    if (!key || !Number.isFinite(weight) || weight <= 0) continue;
    const rounded = Math.round(Math.min(weight, MAX_MUSCLE_WEIGHT) * 100) / 100;
    out[key] = Math.max(out[key] ?? 0, rounded);
  }
  return out;
}

export function normalizeSupersetGroup(raw: string | null | undefined): string | null {
  const v = optionalValue(raw)?.toUpperCase();
  return v ? [...v].find((c) => /\p{L}/u.test(c)) ?? null : null;
}

export function normalizeSport(raw: string | null | undefined): string | null {
  switch (optionalValue(raw)?.toLowerCase()) {
    case "run": case "running": case "jog": case "jogging": return "RUN";
    case "bike": case "biking": case "cycle": case "cycling": case "ride": case "spin": return "BIKE";
    case "swim": case "swimming": return "SWIM";
    default: return null;
  }
}

export function normalizeStroke(raw: string | null | undefined): string | null {
  const v = optionalValue(raw)?.toUpperCase();
  if (!v) return null;
  switch (v) {
    case "FREESTYLE": case "CRAWL": return "FREE";
    case "BACKSTROKE": return "BACK";
    case "BREASTSTROKE": return "BREAST";
    case "BUTTERFLY": return "FLY";
    case "MEDLEY": case "INDIVIDUAL MEDLEY": return "IM";
    case "ANY": return "CHOICE";
    default: return (EXERCISE_DEFAULTS.STROKES as readonly string[]).includes(v) ? v : null;
  }
}

export function normalizeLoadUnit(raw: string | null | undefined, fallback: string = EXERCISE_DEFAULTS.LOAD_UNIT): string {
  switch (optionalValue(raw)?.toLowerCase()) {
    case "lb": case "lbs": case "pound": case "pounds": return "lbs";
    case "kg": case "kgs": case "kilo": case "kilos": case "kilogram": case "kilograms": return "kg";
    default: return fallback;
  }
}

export function normalizeShortTimeUnit(raw: string | null | undefined, fallback: string): string {
  switch (optionalValue(raw)?.toLowerCase()) {
    case "s": case "sec": case "secs": case "second": case "seconds": return "s";
    case "m": case "min": case "mins": case "minute": case "minutes": return "min";
    default: return fallback;
  }
}

export function normalizeDistanceUnit(raw: string | null | undefined, fallback: string = EXERCISE_DEFAULTS.DISTANCE_UNIT): string {
  switch (optionalValue(raw)?.toLowerCase()) {
    case "km": case "kms": case "kilometer": case "kilometers": case "kilometre": case "kilometres": return "km";
    case "mi": case "mile": case "miles": return "mi";
    case "m": case "meter": case "meters": case "metre": case "metres": return "m";
    case "yd": case "yds": case "yard": case "yards": return "yd";
    default: return fallback;
  }
}

/** Tempo eccentric-pause-concentric-pause, e.g. "3-1-1-0". */
export function normalizeTiming(raw: string | null | undefined): string | null {
  const v = optionalValue(raw);
  if (!v) return null;
  const parts = v.split(/[-:/]/).map((p) => p.trim());
  if (parts.length < 3 || parts.length > 4 || parts.some((p) => !/^[+-]?\d+$/.test(p))) return null;
  return parts.join("-");
}

export function normalizeRpe(raw: number | null | undefined): number | null {
  if (raw == null || !Number.isFinite(raw)) return null;
  return Math.round(Math.min(10, Math.max(0, raw)) * 2) / 2;
}

const clampZone = (z: number | null) => (z === null ? null : Math.min(EXERCISE_DEFAULTS.MAX_ZONE, Math.max(1, z)));

export function defaultSessionTitle(kind: ExerciseKind): string {
  return kind === "RESISTANCE" ? "Resistance: Added session" : kind === "ENDURANCE" ? "Endurance: Added session" : "Other: Added session";
}

const toMinutes = (v: number, unit: string) => (unit === "s" ? v / 60 : v);

export function intervalMinutes(d: ExerciseDraft): number | null {
  const m = d.intervals.reduce(
    (acc, i) => acc + (i.targetDuration !== null ? toMinutes(i.targetDuration, i.unitDuration) : 0) + (i.rest !== null ? toMinutes(i.rest, i.restUnit) : 0),
    0,
  );
  return m > 0 ? m : null;
}

function normalizedFor(d: ExerciseDraft, kind: ExerciseKind): ExerciseDraft {
  switch (kind) {
    case "RESISTANCE":
      return { ...d, targets: normalizeMuscleTargets(d.targets), supersetGroup: normalizeSupersetGroup(d.supersetGroup), timing: normalizeTiming(d.timing), rest: null };
    case "ENDURANCE":
      return {
        ...d, targets: {}, supersetGroup: null, timing: null, stroke: normalizeStroke(d.stroke), rest: null,
        intervals: d.intervals.map((i) => ({
          ...i, unitDuration: normalizeShortTimeUnit(i.unitDuration, EXERCISE_DEFAULTS.INTERVAL_DURATION_UNIT),
          unitDistance: normalizeDistanceUnit(i.unitDistance), targetZone: clampZone(i.targetZone), targetRpe: normalizeRpe(i.targetRpe),
          restUnit: normalizeShortTimeUnit(i.restUnit, EXERCISE_DEFAULTS.REST_UNIT),
        })),
      };
    case "OTHER":
      return {
        ...d, targets: {}, supersetGroup: null, timing: null, totalDuration: d.totalDuration ?? intervalMinutes(d),
        unitDuration: d.totalDuration === null ? "min" : d.unitDuration, restUnit: normalizeShortTimeUnit(d.restUnit, EXERCISE_DEFAULTS.REST_UNIT),
      };
  }
}

export function normalizedForImport(sessions: ExerciseSessionDraft[]): ExerciseSessionDraft[] {
  return sessions.map((s) => ({
    ...s, title: s.title.trim() || defaultSessionTitle(s.kind), sport: s.kind === "ENDURANCE" ? normalizeSport(s.sport) : null,
    exercises: s.exercises.map((e) => normalizedFor(e, s.kind)),
  }));
}

export function sessionDraftError(s: ExerciseSessionDraft): string | null {
  if (!s.title.trim()) return "Session title is required";
  if (!s.exercises.length) return "Session has no exercises";
  return null;
}

export function exerciseDraftError(d: ExerciseDraft, kind: ExerciseKind): string | null {
  if (!d.name.trim()) return "Exercise name is required";
  switch (kind) {
    case "RESISTANCE":
      if (!d.sets.length) return "Add at least one set, or move this exercise to an Endurance/Other session";
      if (d.sets.some((s) => s.targetReps === null && s.targetDuration === null)) return "Every set needs reps or a duration";
      if (d.sets.some((s) => s.drops.some((x) => x.targetReps === null))) return "Every drop needs reps";
      return null;
    case "ENDURANCE":
      if (!d.intervals.length) return "Add at least one interval";
      if (d.intervals.some((i) => i.targetDuration === null && i.targetDistance === null)) return "Every interval needs a duration or a distance";
      return null;
    case "OTHER":
      return d.totalDuration === null || d.totalDuration <= 0 ? "Set a total duration" : null;
  }
}

/** First validation error across the whole review, or null. */
export function sessionsError(sessions: ExerciseSessionDraft[]): string | null {
  for (const s of sessions) {
    const err = sessionDraftError(s) ?? s.exercises.map((e) => exerciseDraftError(e, s.kind)).find(Boolean);
    if (err) return `${s.title || "Session"}: ${err}`;
  }
  return null;
}

// ---- conversion ----

export function sessionToDraft(s: WorkoutDaySession): ExerciseSessionDraft {
  const kind = parseExerciseKind(s.type);
  return { title: s.title, description: s.description, kind, sport: normalizeSport(s.sport), exercises: s.exercises.map((e) => exerciseToDraft(e, kind)) };
}

function exerciseToDraft(e: WorkoutExercisePlan, kind: ExerciseKind): ExerciseDraft {
  const total = e.totalDuration ?? e.targetDuration;
  const unit = exerciseDurationUnit(e);
  const legacy: ExerciseIntervalDraft | null =
    kind === "ENDURANCE" && !e.intervals.length && (e.targetDuration !== null || e.targetDistance !== null)
      ? {
          ...emptyInterval(), targetDuration: e.targetDuration ?? (e.totalDuration !== null ? Math.round(e.totalDuration) : null),
          unitDuration: normalizeShortTimeUnit(unit, EXERCISE_DEFAULTS.INTERVAL_DURATION_UNIT), targetDistance: e.targetDistance,
          unitDistance: normalizeDistanceUnit(e.unitDistance), targetRpe: normalizeRpe(e.targetRpe),
        }
      : null;
  return {
    name: e.name, description: e.description, targets: e.target, supersetGroup: normalizeSupersetGroup(e.supersetGroup),
    timing: e.timing, totalDuration: total, unitDuration: normalizeShortTimeUnit(unit, EXERCISE_DEFAULTS.EXERCISE_DURATION_UNIT),
    stroke: normalizeStroke(e.stroke), rest: e.rest, restUnit: e.restUnit,
    sets: e.sets.map((s) => ({
      description: s.description, targetLoad: s.targetLoad, unitLoad: s.unitLoad, targetReps: s.targetReps, targetRir: s.targetRir,
      rest: s.rest, restUnit: s.restUnit, targetDuration: s.targetDuration, unitDuration: s.unitDuration,
      drops: s.drops.map((d) => ({ description: d.description, targetLoad: d.targetLoad, unitLoad: d.unitLoad, targetReps: d.targetReps, targetRir: d.targetRir })),
    })),
    intervals: legacy
      ? [legacy]
      : e.intervals.map((i) => ({
          description: i.description, targetDuration: i.targetDuration, unitDuration: normalizeShortTimeUnit(i.unitDuration, "s"),
          targetDistance: i.targetDistance, unitDistance: normalizeDistanceUnit(i.unitDistance), targetZone: i.targetZone,
          targetRpe: i.targetRpe, targetCadence: i.targetCadence, unitCadence: i.unitCadence, rest: i.rest,
          restUnit: normalizeShortTimeUnit(i.restUnit, EXERCISE_DEFAULTS.REST_UNIT),
        })),
  };
}

export function draftToSession(s: ExerciseSessionDraft): WorkoutDaySession {
  const sport = s.kind === "ENDURANCE" ? normalizeSport(s.sport) : null;
  return {
    title: s.title.trim() || defaultSessionTitle(s.kind), description: s.description.trim(), type: s.kind, sport,
    exercises: s.exercises.map((e) => draftToExercise(e, s.kind, sport)),
  };
}

function draftToExercise(d: ExerciseDraft, kind: ExerciseKind, sport: string | null): WorkoutExercisePlan {
  const isRes = kind === "RESISTANCE";
  const isEnd = kind === "ENDURANCE";
  const total = d.totalDuration !== null && d.totalDuration > 0 ? d.totalDuration : null;
  return {
    name: d.name.trim() || "Exercise", type: kind, target: isRes ? normalizeMuscleTargets(d.targets) : {},
    supersetGroup: isRes ? normalizeSupersetGroup(d.supersetGroup) : null, description: d.description.trim(),
    timing: isRes ? normalizeTiming(d.timing) : null, totalDuration: total,
    unitDuration: total !== null ? normalizeShortTimeUnit(d.unitDuration, EXERCISE_DEFAULTS.EXERCISE_DURATION_UNIT) : null,
    stroke: isEnd && sport === "SWIM" ? normalizeStroke(d.stroke) : null, targetDistance: null, unitDistance: "km",
    targetDuration: null, rest: kind === "OTHER" ? d.rest : null, restUnit: normalizeShortTimeUnit(d.restUnit, EXERCISE_DEFAULTS.REST_UNIT),
    targetRpe: null, sets: isRes ? d.sets.map((s, i) => draftToSet(s, i + 1)) : [],
    intervals: isEnd ? d.intervals.map((iv, i) => draftToInterval(iv, i + 1)) : [],
  };
}

function draftToSet(s: ExerciseSetDraft, setNumber: number): WorkoutSetPlan {
  const unit = normalizeLoadUnit(s.unitLoad);
  const drops: WorkoutDropPlan[] = s.drops.map((d, i) => {
    const dropUnit = d.unitLoad !== null ? normalizeLoadUnit(d.unitLoad, unit) : null;
    return {
      dropNumber: i + 1, description: d.description.trim(), targetLoad: d.targetLoad,
      unitLoad: dropUnit !== null && dropUnit !== unit ? dropUnit : null, targetReps: d.targetReps, targetRir: d.targetRir,
    };
  });
  return {
    setNumber, description: s.description.trim(), cadence: null, setType: s.drops.length ? "DROP" : "NORMAL",
    targetLoad: s.targetLoad, unitLoad: unit, targetReps: s.targetReps, targetVelocity: null, unitVelocity: "m/s",
    targetDuration: s.targetDuration, unitDuration: normalizeShortTimeUnit(s.unitDuration, EXERCISE_DEFAULTS.SET_DURATION_UNIT),
    targetDistance: null, unitDistance: "km", targetRir: s.targetRir, drops, rest: s.rest,
    restUnit: normalizeShortTimeUnit(s.restUnit, EXERCISE_DEFAULTS.REST_UNIT),
  };
}

function draftToInterval(i: ExerciseIntervalDraft, intervalNumber: number): WorkoutIntervalPlan {
  return {
    intervalNumber, description: i.description.trim(), targetDuration: i.targetDuration,
    unitDuration: normalizeShortTimeUnit(i.unitDuration, EXERCISE_DEFAULTS.INTERVAL_DURATION_UNIT), targetDistance: i.targetDistance,
    unitDistance: normalizeDistanceUnit(i.unitDistance), targetZone: clampZone(i.targetZone), targetRpe: normalizeRpe(i.targetRpe),
    targetCadence: i.targetCadence, unitCadence: i.unitCadence || EXERCISE_DEFAULTS.CADENCE_UNIT, rest: i.rest,
    restUnit: normalizeShortTimeUnit(i.restUnit, EXERCISE_DEFAULTS.REST_UNIT),
  };
}
