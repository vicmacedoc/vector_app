// Calendar AI (GeminiActivity/Nutrition/Exercise/SleepClient): same prompts, schemas and normalization as the phone.
import {
  parseActivityType, type ActivityDraft,
} from "@/domain/daily/activity";
import { isPlanMode, type CalendarAiMode } from "@/domain/daily/clipboard";
import {
  EXERCISE_DEFAULTS, defaultSessionTitle, normalizeDistanceUnit, normalizeLoadUnit, normalizeMuscleTargets, normalizeRpe,
  normalizeShortTimeUnit, normalizeSport, normalizeStroke, normalizeSupersetGroup, normalizeTiming, normalizedForImport,
  parseExerciseKind, type ExerciseAiContext, type ExerciseDraft, type ExerciseKind, type ExerciseSessionDraft,
} from "@/domain/daily/exerciseDraft";
import type { MealDraft } from "@/domain/daily/food";
import type { SleepEstimate } from "@/domain/daily/sleep";
import { formatHhMm, normalizeActivityHhMm, normalizeMealTimeOrBlank, optionalValue, parseHhMm } from "@/domain/daily/time";
import { SECONDARY, type MuscleMap } from "@/domain/daily/workoutPlan";
import { prompt } from "./prompts";
import { S, generateJson, imagePart, type Part, type Schema } from "./gemini";

export interface DayContext {
  /** Lines describing what's already on the day. */
  existingItems: string;
  /** The weekday's preset as JSON, if any. */
  presetJson: string | null;
}

export interface AiRequest {
  mode: CalendarAiMode;
  date: string;
  text: string;
  images: Blob[];
  context: DayContext;
}

/** CalendarDayContext.promptSection. */
function daySection(ctx: DayContext, mode: CalendarAiMode, itemNoun: string): string {
  switch (mode) {
    case "SINGLE":
    case "MULTIPLE":
      return `\n\n${itemNoun} already on this day (do not repeat them; avoid time clashes):\n${ctx.existingItems.trim() || "(none)"}`;
    case "PLAN_SCRATCH":
      return "";
    case "PLAN_PRESET":
      return `\n\nThis weekday's preset (the starting plan):\n${ctx.presetJson?.trim() || "(no preset for this weekday)"}`;
  }
}

const batch = (key: string, item: Schema): Schema => S.obj({ [key]: S.arr(item) }, [key]);

type Raw = Record<string, unknown>;
const s = (v: unknown): string | null => (typeof v === "string" ? v : null);
const n = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);
const list = (v: unknown): Raw[] => (Array.isArray(v) ? v.filter((x): x is Raw => !!x && typeof x === "object") : []);
const items = (out: unknown, mode: CalendarAiMode, key: string): Raw[] =>
  mode === "SINGLE" ? list([out]) : list((out as Raw | null)?.[key]);

// ---- Routine ----

function activitySchema(labels: string[]): Schema {
  return S.obj(
    {
      title: S.str(),
      startTime: S.str(),
      endTime: S.str(),
      label: labels.length ? S.enumStr(labels) : S.str(),
      type: S.enumStr(["PLANNED", "NEW"]),
      description: S.str(),
    },
    ["title", "startTime", "endTime", "type"],
  );
}

export async function aiActivities(req: AiRequest, labelNames: string[]): Promise<ActivityDraft[]> {
  const text = req.text.trim();
  if (!text && req.mode !== "PLAN_PRESET") throw new Error("Describe the activities for this day.");
  const body =
    `${prompt("activity", "system")}\n\n${prompt("activity", `mode.${req.mode}`)}` +
    `\n\nCalendar date: ${req.date}` +
    `\nAvailable labels (use one of these exact names): ${labelNames.length ? labelNames.join(", ") : "(none yet — omit label)"}` +
    daySection(req.context, req.mode, "Activities") +
    `\n\nUser instruction:\n${text || "(none — reproduce the preset as is)"}`;
  const schema = activitySchema(labelNames);
  const out = await generateJson({
    parts: [{ text: body }, ...(await Promise.all(req.images.map(imagePart)))],
    schema: req.mode === "SINGLE" ? schema : batch("activities", schema),
    temperature: 0.2,
  });
  const defaultType = isPlanMode(req.mode) ? "PLANNED" : "NEW";
  const drafts = items(out, req.mode, "activities")
    .map<ActivityDraft>((a) => ({
      title: optionalValue(s(a.title)) ?? "",
      startTime: normalizeActivityHhMm(s(a.startTime), "09:00"),
      endTime: normalizeActivityHhMm(s(a.endTime), "10:00"),
      labelName: optionalValue(s(a.label)),
      type: parseActivityType(s(a.type), defaultType),
      description: optionalValue(s(a.description)) ?? "",
      source: "AI",
    }))
    .filter((d) => d.title.trim())
    .sort((a, b) => a.startTime.localeCompare(b.startTime));
  if (!drafts.length) throw new Error("Gemini returned no activities");
  return drafts;
}

// ---- Food ----

const MEAL_SCHEMA = S.obj(
  { name: S.str(), time: S.str(), amount: S.num(), unit: S.str(), kcal: S.num(), protein: S.num(), carbs: S.num(), fats: S.num() },
  ["name", "amount", "unit", "kcal", "protein", "carbs", "fats"],
);

export async function aiMeals(req: AiRequest): Promise<MealDraft[]> {
  const text = req.text.trim();
  if (!text && !req.images.length && req.mode !== "PLAN_PRESET") throw new Error("Provide a description and/or a photo.");
  const instruction = text
    ? `\n\nUser instruction:\n${text}`
    : req.images.length
      ? "\n\nThe user provided a photo only; estimate from the image."
      : "\n\nUser instruction: (none — reproduce the preset as is)";
  const body =
    `${prompt("nutrition", "system")}\n\n${prompt("nutrition", `mode.${req.mode}`)}` +
    `\n\nCalendar date: ${req.date}` +
    daySection(req.context, req.mode, "Food rows") +
    instruction;
  const out = await generateJson({
    parts: [{ text: body }, ...(await Promise.all(req.images.map(imagePart)))],
    schema: req.mode === "SINGLE" ? MEAL_SCHEMA : batch("meals", MEAL_SCHEMA),
    temperature: 0.2,
  });
  const meals = items(out, req.mode, "meals")
    .flatMap<MealDraft>((m) => {
      const name = s(m.name)?.trim() ?? "";
      const amount = n(m.amount) ?? 0;
      const kcal = n(m.kcal) ?? 0;
      if (!name || amount <= 0 || kcal < 0) return [];
      return [{
        name, time: normalizeMealTimeOrBlank(s(m.time)), amount, unit: s(m.unit)?.trim() || "g", kcal,
        protein: Math.max(0, n(m.protein) ?? 0), carbs: Math.max(0, n(m.carbs) ?? 0), fats: Math.max(0, n(m.fats) ?? 0),
        asPlanned: false,
      }];
    })
    .sort((a, b) => a.time.localeCompare(b.time));
  if (!meals.length) throw new Error("Gemini JSON missing required meal fields (name/amount/kcal)");
  return meals;
}

// ---- Exercise ----

const MAX = { REPS: 999, RIR: 10, REST: 3600, DURATION: 99_999, LOAD: 2000, DISTANCE: 99_999, CADENCE: 250 };
const intIn = (v: unknown, lo: number, hi: number) => {
  const x = n(v);
  if (x === null) return null;
  const r = Math.round(x);
  return r >= lo && r <= hi ? r : null;
};
const posIn = (v: unknown, lo: number, hi: number) => {
  const x = n(v);
  return x !== null && x >= lo && x <= hi && x > 0 ? x : null;
};

const EXERCISE_SCHEMA = (() => {
  const drop = S.obj({ description: S.str(), targetLoad: S.num(), unitLoad: S.str(), targetReps: S.int(), targetRir: S.int() });
  const set = S.obj({
    description: S.str(), targetLoad: S.num(), unitLoad: S.str(), targetReps: S.int(), targetRir: S.int(), rest: S.int(),
    unitRest: S.str(), targetDuration: S.int(), unitDuration: S.str(), drops: S.arr(drop),
  });
  const interval = S.obj({
    description: S.str(), targetDuration: S.int(), unitDuration: S.enumStr(["s", "min"]), targetDistance: S.num(),
    unitDistance: S.enumStr(EXERCISE_DEFAULTS.DISTANCE_UNITS), targetZone: S.int(), targetRpe: S.num(), targetCadence: S.int(),
    rest: S.int(), unitRest: S.enumStr(["s", "min"]),
  });
  const exercise = S.obj(
    {
      name: S.str(), description: S.str(), target: S.arr(S.obj({ muscle: S.str(), weight: S.num() }, ["muscle", "weight"])),
      supersetGroup: S.str(), timing: S.str(), totalDuration: S.num(), unitDuration: S.enumStr(["min", "s"]),
      stroke: S.enumStr(EXERCISE_DEFAULTS.STROKES), rest: S.int(), unitRest: S.str(), sets: S.arr(set), intervals: S.arr(interval),
    },
    ["name"],
  );
  return S.obj(
    {
      title: S.str(), description: S.str(), type: S.enumStr(["RESISTANCE", "ENDURANCE", "OTHER"]),
      sport: S.enumStr(EXERCISE_DEFAULTS.SPORTS), exercises: S.arr(exercise),
    },
    ["title", "type", "exercises"],
  );
})();

function toExercise(e: Raw, kind: ExerciseKind, loadUnit: string): ExerciseDraft {
  const targets: MuscleMap = {};
  for (const t of list(e.target)) {
    const muscle = s(t.muscle);
    if (muscle) targets[muscle] = n(t.weight) ?? SECONDARY;
  }
  return {
    name: optionalValue(s(e.name)) ?? "",
    description: optionalValue(s(e.description)) ?? "",
    targets: normalizeMuscleTargets(targets),
    supersetGroup: normalizeSupersetGroup(s(e.supersetGroup)),
    timing: normalizeTiming(s(e.timing)),
    totalDuration: posIn(e.totalDuration, 0, MAX.DURATION),
    unitDuration: normalizeShortTimeUnit(s(e.unitDuration), EXERCISE_DEFAULTS.EXERCISE_DURATION_UNIT),
    stroke: normalizeStroke(s(e.stroke)),
    sets: kind !== "RESISTANCE" ? [] : list(e.sets).map((st) => ({
      description: optionalValue(s(st.description)) ?? "",
      targetLoad: posIn(st.targetLoad, 0, MAX.LOAD),
      unitLoad: normalizeLoadUnit(s(st.unitLoad), loadUnit),
      targetReps: intIn(st.targetReps, 1, MAX.REPS),
      targetRir: intIn(st.targetRir, 0, MAX.RIR),
      rest: intIn(st.rest, 0, MAX.REST),
      restUnit: normalizeShortTimeUnit(s(st.unitRest), EXERCISE_DEFAULTS.REST_UNIT),
      targetDuration: intIn(st.targetDuration, 0, MAX.DURATION),
      unitDuration: normalizeShortTimeUnit(s(st.unitDuration), EXERCISE_DEFAULTS.SET_DURATION_UNIT),
      drops: list(st.drops).map((d) => ({
        description: optionalValue(s(d.description)) ?? "",
        targetLoad: posIn(d.targetLoad, 0, MAX.LOAD),
        unitLoad: optionalValue(s(d.unitLoad)) ? normalizeLoadUnit(s(d.unitLoad), loadUnit) : null,
        targetReps: intIn(d.targetReps, 1, MAX.REPS),
        targetRir: intIn(d.targetRir, 0, MAX.RIR),
      })),
    })),
    intervals: kind !== "ENDURANCE" ? [] : list(e.intervals).map((i) => ({
      description: optionalValue(s(i.description)) ?? "",
      targetDuration: intIn(i.targetDuration, 1, MAX.DURATION),
      unitDuration: normalizeShortTimeUnit(s(i.unitDuration), EXERCISE_DEFAULTS.INTERVAL_DURATION_UNIT),
      targetDistance: posIn(i.targetDistance, 0, MAX.DISTANCE),
      unitDistance: normalizeDistanceUnit(s(i.unitDistance)),
      targetZone: intIn(i.targetZone, 1, EXERCISE_DEFAULTS.MAX_ZONE),
      targetRpe: normalizeRpe(n(i.targetRpe)),
      targetCadence: intIn(i.targetCadence, 1, MAX.CADENCE),
      unitCadence: EXERCISE_DEFAULTS.CADENCE_UNIT,
      rest: intIn(i.rest, 0, MAX.REST),
      restUnit: normalizeShortTimeUnit(s(i.unitRest), EXERCISE_DEFAULTS.REST_UNIT),
    })),
    rest: kind === "OTHER" ? intIn(e.rest, 0, MAX.REST) : null,
    restUnit: normalizeShortTimeUnit(s(e.unitRest), EXERCISE_DEFAULTS.REST_UNIT),
  };
}

export async function aiExercises(req: AiRequest, ctx: ExerciseAiContext): Promise<ExerciseSessionDraft[]> {
  const text = req.text.trim();
  if (!text && req.mode !== "PLAN_PRESET") throw new Error("Describe the workout you want to add.");
  const muscles = ctx.muscleVocabulary.length ? ctx.muscleVocabulary : [...EXERCISE_DEFAULTS.MUSCLES];
  const history = ctx.recentExercises.length
    ? ctx.recentExercises.map((e) => `- ${e}`).join("\n")
    : "(no logged history yet — use standard prescriptions)";
  const body =
    `${prompt("exercise", "system")}\n\n${prompt("exercise", `mode.${req.mode}`)}` +
    `\n\nCalendar date for this workout: ${req.date}` +
    `\nAllowed muscle tags (use these exact strings): ${muscles.join(", ")}` +
    `\nPreferred load unit (use it for every unitLoad): ${ctx.loadUnit}` +
    (isPlanMode(req.mode) ? "" : `\nSessions already on this day (do not repeat them): ${ctx.existingSessionTitles.join(", ") || "(none)"}`) +
    daySection(req.context, req.mode, "Sessions") +
    `\n\nThe user's recent exercises (name, tags, last prescription):\n${history}` +
    `\n\nUser instruction:\n${text || "(none — reproduce the preset as is)"}`;
  const out = await generateJson({
    parts: [{ text: body }, ...(await Promise.all(req.images.map(imagePart)))],
    schema: req.mode === "SINGLE" ? EXERCISE_SCHEMA : batch("sessions", EXERCISE_SCHEMA),
    temperature: 0.2,
  });
  const sessions = items(out, req.mode, "sessions")
    .map<ExerciseSessionDraft>((x) => {
      const kind = parseExerciseKind(s(x.type));
      return {
        title: optionalValue(s(x.title)) ?? defaultSessionTitle(kind),
        description: optionalValue(s(x.description)) ?? "",
        kind,
        sport: kind === "ENDURANCE" ? normalizeSport(s(x.sport)) : null,
        exercises: list(x.exercises).map((e) => toExercise(e, kind, ctx.loadUnit)),
      };
    })
    .filter((x) => x.exercises.length);
  if (!sessions.length) throw new Error("Gemini returned no exercises");
  return normalizedForImport(sessions);
}

// ---- Sleep ----

const SLEEP_SCHEMA = S.obj(
  Object.fromEntries([
    ...["plannedBedtime", "plannedWakeUp", "actualBedtime", "actualWakeUp", "fellAsleepAt"].map((k) => [k, S.str()]),
    ...["latencyMinutes", "deepMinutes", "lightMinutes", "remMinutes", "awakeMinutes", "deepPercent", "lightPercent", "remPercent", "awakePercent"].map((k) => [k, S.int()]),
  ]),
);

export async function aiSleep(image: Blob | null, text: string): Promise<SleepEstimate> {
  const t = text.trim();
  if (!t && !image) throw new Error("Provide a description and/or a photo.");
  const parts: Part[] = [{
    text: `${prompt("sleep", "system")}${t ? `\n\nUser description:\n${t}` : "\n\nUser provided a screenshot only; extract sleep data from the image."}`,
  }];
  if (image) parts.push(await imagePart(image));
  const r = await generateJson<Raw>({ parts, schema: SLEEP_SCHEMA, temperature: 0.1 });
  const hhmm = (v: unknown) => {
    const m = parseHhMm(s(v));
    return m === null ? null : formatHhMm(m);
  };
  const minutes = (v: unknown) => {
    const x = n(v);
    return x !== null && x >= 0 ? Math.round(x) : null;
  };
  const pct = (v: unknown) => {
    const x = n(v);
    return x !== null && x >= 0 && x <= 100 ? Math.round(x) : null;
  };
  return {
    plannedBedtime: hhmm(r.plannedBedtime), plannedWakeUp: hhmm(r.plannedWakeUp),
    actualBedtime: hhmm(r.actualBedtime), actualWakeUp: hhmm(r.actualWakeUp), fellAsleepAt: hhmm(r.fellAsleepAt),
    latencyMinutes: minutes(r.latencyMinutes), deepMinutes: minutes(r.deepMinutes), lightMinutes: minutes(r.lightMinutes),
    remMinutes: minutes(r.remMinutes), awakeMinutes: minutes(r.awakeMinutes), deepPercent: pct(r.deepPercent),
    lightPercent: pct(r.lightPercent), remPercent: pct(r.remPercent), awakePercent: pct(r.awakePercent),
  };
}
