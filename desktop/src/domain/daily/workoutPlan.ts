// Workout preset template v2 (WeeklyWorkoutPlan.kt), MuscleTargets and WorkoutPlanRows.
import { int, isObj, num, str, type Obj } from "./clipboard";
import { weekdayOf, WEEKDAYS, type Weekday } from "./food";
import { normWorkoutSet, REST_DAY_ACK_ID, type WorkoutSet } from "./models";

// ---- MuscleTargets ----

export type MuscleMap = Record<string, number>;
export const PRIMARY = 1.0;
export const SECONDARY = 0.5;

export const normalizeMuscle = (raw: string) => raw.trim().toLowerCase().replace(/ /g, "_");

export function fromOrderedTags(tags: string[]): MuscleMap {
  const out: MuscleMap = {};
  tags.map(normalizeMuscle).filter(Boolean).forEach((tag, i) => {
    if (!(tag in out)) out[tag] = i === 0 ? PRIMARY : SECONDARY;
  });
  return out;
}

export function parseMuscles(csv: string): MuscleMap {
  const parts = csv.split(",").map((p) => p.trim()).filter(Boolean);
  if (!parts.length) return {};
  if (!parts.some((p) => p.includes(":"))) return fromOrderedTags(parts);
  const out: MuscleMap = {};
  for (const part of parts) {
    const idx = part.indexOf(":");
    const name = normalizeMuscle(idx < 0 ? part : part.slice(0, idx));
    const rawWeight = idx < 0 ? "" : part.slice(idx + 1).trim();
    const parsed = rawWeight && Number.isFinite(Number(rawWeight)) ? Number(rawWeight) : PRIMARY;
    if (name && parsed > 0) out[name] = (out[name] ?? 0) + parsed;
  }
  return out;
}

const formatWeight = (w: number) => (Number.isInteger(w) ? `${w}.0` : String(w));

export function formatMuscles(targets: MuscleMap): string {
  return Object.entries(targets)
    .filter(([k, v]) => k.trim() && v > 0)
    .map(([k, v]) => `${normalizeMuscle(k)}:${formatWeight(v)}`)
    .join(",");
}

/** Accepts `{muscle: weight}`, an ordered list, or a CSV string (MuscleTargetSerializer). */
export function decodeMuscleTarget(v: unknown): MuscleMap {
  if (v == null) return {};
  if (Array.isArray(v)) return fromOrderedTags(v.filter((x) => typeof x === "string" || typeof x === "number").map(String));
  if (typeof v === "string") return fromOrderedTags(v.split(","));
  if (isObj(v)) {
    const out: MuscleMap = {};
    for (const [name, value] of Object.entries(v)) {
      const w = typeof value === "number" ? value : typeof value === "string" && Number.isFinite(Number(value)) ? Number(value) : PRIMARY;
      const key = normalizeMuscle(name);
      if (key && w > 0) out[key] = w;
    }
    return out;
  }
  return {};
}

// ---- plan types ----

export interface WorkoutDropPlan {
  dropNumber: number;
  description: string;
  targetLoad: number | null;
  unitLoad: string | null;
  targetReps: number | null;
  targetRir: number | null;
}

export interface WorkoutSetPlan {
  setNumber: number;
  description: string;
  cadence: string | null;
  setType: string;
  targetLoad: number | null;
  unitLoad: string;
  targetReps: number | null;
  targetVelocity: number | null;
  unitVelocity: string;
  targetDuration: number | null;
  unitDuration: string;
  targetDistance: number | null;
  unitDistance: string;
  targetRir: number | null;
  drops: WorkoutDropPlan[];
  rest: number | null;
  restUnit: string;
}

export interface WorkoutIntervalPlan {
  intervalNumber: number;
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

export interface WorkoutExercisePlan {
  name: string;
  type: string;
  target: MuscleMap;
  supersetGroup: string | null;
  description: string;
  timing: string | null;
  totalDuration: number | null;
  unitDuration: string | null;
  stroke: string | null;
  targetDistance: number | null;
  unitDistance: string;
  targetDuration: number | null;
  rest: number | null;
  restUnit: string;
  targetRpe: number | null;
  sets: WorkoutSetPlan[];
  intervals: WorkoutIntervalPlan[];
}

export interface WorkoutDaySession {
  title: string;
  description: string;
  type: string;
  sport: string | null;
  exercises: WorkoutExercisePlan[];
}

export interface WeeklyWorkoutPlan {
  id: string;
  name: string;
  weekly_plan: Record<Weekday, WorkoutDaySession[]>;
}

const sn = (v: unknown): string | null => (typeof v === "string" ? v : null);

export function decodeDrop(o: Obj): WorkoutDropPlan {
  return {
    dropNumber: int(o.dropNumber) ?? 1, description: str(o.description), targetLoad: num(o.targetLoad),
    unitLoad: sn(o.unitLoad), targetReps: int(o.targetReps), targetRir: int(o.targetRir),
  };
}

export function decodeSetPlan(o: Obj, defaultUnitLoad = "kg"): WorkoutSetPlan {
  return {
    setNumber: int(o.setNumber) ?? 1, description: str(o.description), cadence: sn(o.cadence), setType: str(o.setType, "NORMAL"),
    targetLoad: num(o.targetLoad), unitLoad: str(o.unitLoad, defaultUnitLoad), targetReps: int(o.targetReps),
    targetVelocity: num(o.targetVelocity), unitVelocity: str(o.unitVelocity, "m/s"), targetDuration: int(o.targetDuration),
    unitDuration: str(o.unitDuration, "s"), targetDistance: num(o.targetDistance), unitDistance: str(o.unitDistance, "km"),
    targetRir: int(o.targetRir), drops: arr(o.drops).map(decodeDrop), rest: int(o.rest), restUnit: str(o.unitRest, "s"),
  };
}

export function decodeInterval(o: Obj): WorkoutIntervalPlan {
  return {
    intervalNumber: int(o.intervalNumber) ?? 1, description: str(o.description), targetDuration: int(o.targetDuration),
    unitDuration: str(o.unitDuration, "s"), targetDistance: num(o.targetDistance), unitDistance: str(o.unitDistance, "km"),
    targetZone: int(o.targetZone), targetRpe: num(o.targetRpe), targetCadence: int(o.targetCadence),
    unitCadence: str(o.unitCadence, "rpm"), rest: int(o.rest), restUnit: str(o.unitRest, "s"),
  };
}

export function decodeExercise(o: Obj, defaultUnitLoad = "kg"): WorkoutExercisePlan {
  return {
    name: str(o.name), type: str(o.type, "RESISTANCE"), target: decodeMuscleTarget(o.target),
    supersetGroup: sn(o.supersetGroup) ?? sn(o.groupingId), description: str(o.description), timing: sn(o.timing),
    totalDuration: num(o.totalDuration), unitDuration: sn(o.unitDuration), stroke: sn(o.stroke),
    targetDistance: num(o.targetDistance), unitDistance: str(o.unitDistance, "km"), targetDuration: int(o.targetDuration),
    rest: int(o.rest), restUnit: str(o.unitRest, "s"), targetRpe: num(o.targetRpe),
    sets: arr(o.sets).map((s) => decodeSetPlan(s, defaultUnitLoad)), intervals: arr(o.intervals).map(decodeInterval),
  };
}

export function decodeSession(o: Obj, defaultUnitLoad = "kg"): WorkoutDaySession {
  return {
    title: str(o.title), description: str(o.description), type: str(o.type, "RESISTANCE"), sport: sn(o.sport),
    exercises: arr(o.exercises).map((e) => decodeExercise(e, defaultUnitLoad)),
  };
}

export function decodeWorkoutPlan(content: string): WeeklyWorkoutPlan {
  const root = JSON.parse(content) as unknown;
  if (!isObj(root)) throw new Error("Workout preset must be a JSON object");
  const days = isObj(root.weekly_plan) ? root.weekly_plan : {};
  const weekly = {} as Record<Weekday, WorkoutDaySession[]>;
  for (const d of WEEKDAYS) weekly[d] = arr(days[d]).map((s) => decodeSession(s));
  return { id: str(root.id), name: str(root.name), weekly_plan: weekly };
}

function arr(v: unknown): Obj[] {
  return Array.isArray(v) ? v.filter(isObj) : [];
}

/** JSON with nulls and empty lists dropped, like the phone's explicitNulls = false encoder. */
export function encodeCompact(v: unknown): unknown {
  if (Array.isArray(v)) return v.map(encodeCompact);
  if (isObj(v)) {
    const out: Obj = {};
    for (const [k, x] of Object.entries(v)) {
      if (x === null || x === undefined) continue;
      const key = k === "restUnit" ? "unitRest" : k;
      out[key] = encodeCompact(x);
    }
    return out;
  }
  return v;
}

// ---- durations ----

export function durationToMinutes(value: number, unit: string | null | undefined): number {
  switch (unit?.toLowerCase()) {
    case "s": case "sec": case "secs": case "second": case "seconds": return value / 60;
    case "h": case "hr": case "hrs": case "hour": case "hours": return value * 60;
    default: return value;
  }
}

export const exerciseDurationUnit = (e: WorkoutExercisePlan) => e.unitDuration ?? (e.totalDuration !== null ? "min" : "s");

export function plannedTotalMinutes(e: WorkoutExercisePlan): number | null {
  if (e.totalDuration !== null) return durationToMinutes(e.totalDuration, exerciseDurationUnit(e));
  if (e.targetDuration !== null) return durationToMinutes(e.targetDuration, exerciseDurationUnit(e));
  if (!e.intervals.length) return null;
  const sum = e.intervals.reduce(
    (acc, i) =>
      acc + (i.targetDuration !== null ? durationToMinutes(i.targetDuration, i.unitDuration) : 0) +
      (i.rest !== null ? durationToMinutes(i.rest, i.restUnit) : 0),
    0,
  );
  return sum > 0 ? sum : null;
}

// ---- rows ----

export const isDropRow = (s: WorkoutSet) => s.dropNumber > 0;
export const isIntervalRow = (s: WorkoutSet) => s.setType === "INTERVAL";
export const isRestDayAck = (s: WorkoutSet) => s.exerciseId === REST_DAY_ACK_ID;

const ADDED_EXERCISE_ID = /^ex_added_(\d+)_\d+$/;
export const isAddedExercise = (exerciseId: string) => ADDED_EXERCISE_ID.test(exerciseId);

export const sessionsForDate = (plan: WeeklyWorkoutPlan, date: string) => plan.weekly_plan[weekdayOf(date)];

export function planToRows(plan: WeeklyWorkoutPlan, date: string): WorkoutSet[] {
  return sessionsForDate(plan, date).flatMap((s, i) => sessionToRows(date, s, `ex_${i}`));
}

export function sessionToRows(date: string, session: WorkoutDaySession, idPrefix: string, typeOverride?: string): WorkoutSet[] {
  const sessionType = (typeOverride ?? session.type) || "RESISTANCE";
  return session.exercises.flatMap((exercise, i) =>
    exerciseToRows(date, `${idPrefix}_${i}`, session.title, session.description, sessionType, session.sport, exercise),
  );
}

const upperOrNull = (v: string | null) => {
  const t = v?.trim().toUpperCase();
  return t ? t : null;
};

export function exerciseToRows(
  date: string, exerciseId: string, sessionTitle: string, sessionDescription: string, sessionType: string,
  sport: string | null, exercise: WorkoutExercisePlan,
): WorkoutSet[] {
  const base = normWorkoutSet({
    id: `${date}_${exerciseId}_1`, exerciseId, setNumber: 1, date, sessionTitle, sessionDescription,
    exerciseName: exercise.name, exerciseDescription: exercise.description, timing: exercise.timing, exerciseType: sessionType,
    groupingId: upperOrNull(exercise.supersetGroup), sport: upperOrNull(sport), stroke: upperOrNull(exercise.stroke),
    totalDuration: plannedTotalMinutes(exercise), unitTotalDuration: "min", targetMuscles: formatMuscles(exercise.target),
  });
  if (exercise.intervals.length) {
    return exercise.intervals.map((iv) => ({
      ...base, id: `${date}_${exerciseId}_${iv.intervalNumber}`, setNumber: iv.intervalNumber, setType: "INTERVAL",
      description: iv.description, targetDuration: iv.targetDuration, unitDuration: iv.unitDuration,
      targetDistance: iv.targetDistance, unitDistance: iv.unitDistance, targetZone: iv.targetZone, targetRpe: iv.targetRpe,
      targetCadence: iv.targetCadence, unitCadence: iv.unitCadence, targetRest: iv.rest, restUnit: iv.restUnit,
    }));
  }
  if (exercise.sets.length) return exercise.sets.flatMap((sp) => setRows(base, date, exerciseId, sp));
  return [{
    ...base, description: exercise.description,
    unitDuration: exercise.targetDuration !== null ? exerciseDurationUnit(exercise) : "min",
    targetDuration: exercise.targetDuration, targetDistance: exercise.targetDistance, unitDistance: exercise.unitDistance,
    targetRpe: exercise.targetRpe, targetRest: exercise.rest, restUnit: exercise.restUnit,
  }];
}

function setRows(base: WorkoutSet, date: string, exerciseId: string, sp: WorkoutSetPlan): WorkoutSet[] {
  const isDrop = sp.drops.length > 0 || sp.setType.toUpperCase() === "DROP";
  const mainId = `${date}_${exerciseId}_${sp.setNumber}`;
  const main: WorkoutSet = {
    ...base, id: mainId, setNumber: sp.setNumber, setType: isDrop ? "DROP" : "NORMAL", description: sp.description,
    cadence: sp.cadence, unitLoad: sp.unitLoad, unitDuration: sp.unitDuration, unitDistance: sp.unitDistance,
    unitVelocity: sp.unitVelocity, targetLoad: sp.targetLoad, targetReps: sp.targetReps, targetDuration: sp.targetDuration,
    targetDistance: sp.targetDistance, targetVelocity: sp.targetVelocity, targetRir: sp.targetRir, targetRest: sp.rest,
    restUnit: sp.restUnit,
  };
  const drops = [...sp.drops].sort((a, b) => a.dropNumber - b.dropNumber).map((d, i) => {
    const dropNumber = d.dropNumber > 0 ? d.dropNumber : i + 1;
    return {
      ...main, id: `${mainId}_d${dropNumber}`, dropNumber, description: d.description, cadence: null,
      unitLoad: d.unitLoad ?? sp.unitLoad, targetLoad: d.targetLoad, targetReps: d.targetReps, targetDuration: null,
      targetDistance: null, targetVelocity: null, targetRir: d.targetRir, targetRest: null,
    };
  });
  return [main, ...drops];
}

const bySetThenDrop = (a: WorkoutSet, b: WorkoutSet) => a.setNumber - b.setNumber || a.dropNumber - b.dropNumber;

export function rowsToExercisePlan(rows: WorkoutSet[]): WorkoutExercisePlan {
  const sorted = [...rows].sort(bySetThenDrop);
  const r0 = sorted[0];
  const mains = sorted.filter((r) => !isDropRow(r));
  const isIntervals = sorted.some(isIntervalRow);
  const isResistance = r0.exerciseType.toUpperCase() === "RESISTANCE";
  const intervals: WorkoutIntervalPlan[] = isIntervals
    ? mains.map((r) => ({
        intervalNumber: r.setNumber, description: r.description, targetDuration: r.targetDuration, unitDuration: r.unitDuration,
        targetDistance: r.targetDistance, unitDistance: r.unitDistance, targetZone: r.targetZone, targetRpe: r.targetRpe,
        targetCadence: r.targetCadence, unitCadence: r.unitCadence, rest: r.targetRest, restUnit: r.restUnit,
      }))
    : [];
  const sets: WorkoutSetPlan[] = !isIntervals && (isResistance || mains.length > 1)
    ? mains.map((m) => {
        const drops = sorted.filter((r) => isDropRow(r) && r.setNumber === m.setNumber);
        return {
          setNumber: m.setNumber, description: m.description, cadence: m.cadence,
          setType: drops.length || m.setType === "DROP" ? "DROP" : "NORMAL", targetLoad: m.targetLoad, unitLoad: m.unitLoad,
          targetReps: m.targetReps, targetVelocity: m.targetVelocity, unitVelocity: m.unitVelocity,
          targetDuration: m.targetDuration, unitDuration: m.unitDuration, targetDistance: m.targetDistance,
          unitDistance: m.unitDistance, targetRir: m.targetRir,
          drops: drops.map((d) => ({
            dropNumber: d.dropNumber, description: d.description, targetLoad: d.targetLoad, unitLoad: d.unitLoad,
            targetReps: d.targetReps, targetRir: d.targetRir,
          })),
          rest: m.targetRest, restUnit: m.restUnit,
        };
      })
    : [];
  const single = !isIntervals && !sets.length;
  const totalMinutes = r0.totalDuration !== null ? durationToMinutes(r0.totalDuration, r0.unitTotalDuration) : null;
  return {
    name: r0.exerciseName, type: r0.exerciseType || "RESISTANCE", target: parseMuscles(r0.targetMuscles),
    supersetGroup: r0.groupingId, description: r0.exerciseDescription || (single ? r0.description : ""), timing: r0.timing,
    totalDuration: totalMinutes, unitDuration: totalMinutes !== null ? "min" : single ? r0.unitDuration : null,
    stroke: r0.stroke, targetDistance: single ? r0.targetDistance : null, unitDistance: r0.unitDistance,
    targetDuration: single && totalMinutes === null ? r0.targetDuration : null, rest: single ? r0.targetRest : null,
    restUnit: r0.restUnit, targetRpe: single ? r0.targetRpe : null, sets, intervals,
  };
}

export function rowsToSession(rows: WorkoutSet[]): WorkoutDaySession {
  const first = rows[0];
  const ids = [...new Set(rows.map((r) => r.exerciseId))];
  const exercises = ids.map((id) => rowsToExercisePlan(rows.filter((r) => r.exerciseId === id)));
  return {
    title: first.sessionTitle || "Workout", description: first.sessionDescription,
    type: first.exerciseType || exercises[0]?.type || "RESISTANCE", sport: first.sport, exercises,
  };
}

/** Groups day rows into sessions (by title + description) in first-appearance order. */
export function groupSessions(rows: WorkoutSet[]): { key: string; title: string; rows: WorkoutSet[] }[] {
  const out: { key: string; title: string; rows: WorkoutSet[] }[] = [];
  for (const r of rows) {
    if (isRestDayAck(r)) continue;
    const prefix = r.exerciseId.replace(/_\d+$/, "");
    const key = `${prefix}|${r.sessionTitle}`;
    let g = out.find((x) => x.key === key);
    if (!g) out.push((g = { key, title: r.sessionTitle || "Workout", rows: [] }));
    g.rows.push(r);
  }
  return out;
}

/** A saved day shows its saved sets; the preset only backfills blank metadata on matching ids. */
export function backfillFrom(saved: WorkoutSet, blue: WorkoutSet | undefined): WorkoutSet {
  if (!blue) return saved;
  return {
    ...saved,
    sessionTitle: saved.sessionTitle || blue.sessionTitle,
    sessionDescription: saved.sessionDescription || blue.sessionDescription,
    exerciseName: saved.exerciseName || blue.exerciseName,
    exerciseDescription: saved.exerciseDescription || blue.exerciseDescription,
    timing: saved.timing ?? blue.timing,
    targetMuscles: saved.targetMuscles || blue.targetMuscles,
  };
}

/** Calendar display rows: saved rows (minus the rest acknowledgement) or else the preset blueprint. */
export function displayRows(saved: WorkoutSet[], blueprint: WorkoutSet[]): WorkoutSet[] {
  if (saved.length) {
    const byId = new Map(blueprint.map((b) => [b.id, b]));
    return saved.filter((s) => !isRestDayAck(s)).map((s) => backfillFrom(s, byId.get(s.id)));
  }
  return blueprint;
}

export const restDayAckSet = (date: string): WorkoutSet =>
  normWorkoutSet({
    id: `${date}_${REST_DAY_ACK_ID}_1`, exerciseId: REST_DAY_ACK_ID, setNumber: 1, date, exerciseType: "REST_ACK",
    workoutStatus: "Done", isCompleted: true,
  });

/** Rows to persist for a day: empty days store one acknowledgement row. */
export const rowsToSave = (date: string, rows: WorkoutSet[]) => (rows.length ? rows : [restDayAckSet(date)]);

export function nextAddedSessionIndex(existing: WorkoutSet[]): number {
  const used = existing.map((s) => ADDED_EXERCISE_ID.exec(s.exerciseId)?.[1]).filter(Boolean).map(Number);
  return (used.length ? Math.max(...used) : -1) + 1;
}

/** Rows for sessions added on top of the day; ids continue after earlier additions. */
export function addedSessionRows(date: string, sessions: WorkoutDaySession[], existing: WorkoutSet[]): WorkoutSet[] {
  const base = nextAddedSessionIndex(existing);
  const rows = sessions.flatMap((s, i) => sessionToRows(date, s, `ex_added_${base + i}`));
  if (!rows.length) throw new Error("No exercises or sets to add");
  return rows;
}

/** Rows for a re-planned day; `ex_plan_*` ids count toward targets without colliding with preset ids. */
export function plannedDayRows(date: string, sessions: WorkoutDaySession[]): WorkoutSet[] {
  const rows = sessions.flatMap((s, i) => sessionToRows(date, s, `ex_plan_${i}`));
  if (!rows.length) throw new Error("No exercises or sets to add");
  return rows;
}

export function exercisesAsSession(exercises: WorkoutExercisePlan[], title?: string): WorkoutDaySession {
  const type = exercises[0]?.type || "RESISTANCE";
  return {
    title: title ?? (exercises.length === 1 && exercises[0].name ? exercises[0].name : "Added"),
    description: "", type, sport: null, exercises,
  };
}

/** Clipboard sessions: SINGLE takes one session / exercise / exercise array; other modes a session list. */
export function parseClipboardSessions(text: string, mode: "SINGLE" | "MULTIPLE" | "PLAN_SCRATCH" | "PLAN_PRESET", defaultUnitLoad: string): WorkoutDaySession[] {
  const trimmed = text.trim();
  if (!trimmed) throw new Error("Clipboard is empty");
  let element: unknown;
  try {
    element = JSON.parse(trimmed);
  } catch {
    throw new Error("Clipboard is not valid JSON");
  }
  const isSession = (v: unknown) => isObj(v) && "exercises" in v;
  const wrapped = isObj(element) && Array.isArray(element.sessions) ? element.sessions : null;
  let sessions: WorkoutDaySession[];
  if (mode === "SINGLE") {
    if (wrapped || (Array.isArray(element) && element.some(isSession))) {
      throw new Error("Clipboard holds several sessions. Use Add multiple instead.");
    }
    if (isSession(element)) sessions = [decodeSession(element as Obj, defaultUnitLoad)];
    else if (Array.isArray(element)) sessions = [exercisesAsSession(arr(element).map((e) => decodeExercise(e, defaultUnitLoad)))];
    else if (isObj(element)) sessions = [exercisesAsSession([decodeExercise(element, defaultUnitLoad)])];
    else throw new Error("Expected one workout session object");
  } else {
    let items: unknown[];
    if (wrapped) items = wrapped;
    else if (Array.isArray(element) && element.every(isSession)) items = element;
    else if (Array.isArray(element)) {
      throw new Error("This looks like one session's exercises. Use Add a single workout, or wrap sessions in an array.");
    } else throw new Error('Clipboard holds a single workout. Use a JSON array of sessions or { "sessions": [...] }.');
    sessions = arr(items).map((s) => decodeSession(s, defaultUnitLoad));
  }
  if (!sessions.length || sessions.some((s) => !s.exercises.length)) throw new Error("Every session needs at least one exercise");
  return sessions;
}

export const sessionToClipboard = (rows: WorkoutSet[]): string => {
  const real = rows.filter((r) => !isRestDayAck(r));
  return real.length ? JSON.stringify(encodeCompact(rowsToSession(real)), null, 2) : "{}";
};

/** Library item fields for a session (LibraryRepository.upsertExerciseSession). */
export function sessionLibraryFields(session: WorkoutDaySession): { type: string; name: string; payloadJson: string } | null {
  const type = (session.type || "RESISTANCE").toUpperCase();
  const name = session.title.trim() || session.exercises[0]?.name.trim() || "";
  if (!name) return null;
  return { type, name, payloadJson: JSON.stringify(encodeCompact({ ...session, title: name, type })) };
}

export function decodeLibrarySession(payloadJson: string): WorkoutDaySession | null {
  try {
    const v = JSON.parse(payloadJson) as unknown;
    return isObj(v) ? decodeSession(v) : null;
  } catch {
    return null;
  }
}
