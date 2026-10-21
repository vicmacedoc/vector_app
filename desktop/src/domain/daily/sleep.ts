// SleepMath + SleepEdits. Nights are keyed by the evening's date.
import { normSleep, type SleepEntry } from "./models";
import { durationMinutes, formatHhMm, parseHhMm } from "./time";

export type SleepStage = "DEEP" | "LIGHT" | "REM" | "AWAKE";
export const SLEEP_STAGES: readonly SleepStage[] = ["DEEP", "LIGHT", "REM", "AWAKE"];

const STAGE_FIELD = {
  DEEP: "deepMinutes", LIGHT: "lightMinutes", REM: "remMinutes", AWAKE: "awakeMinutes",
} as const satisfies Record<SleepStage, keyof SleepEntry>;

export const stageMinutes = (e: SleepEntry, stage: SleepStage) => e[STAGE_FIELD[stage]];

export const effectiveBedtime = (e: SleepEntry) => e.actualBedtime ?? e.plannedBedtime;
export const effectiveWakeUp = (e: SleepEntry) => e.actualWakeUp ?? e.plannedWakeUp;

export const plannedDurationMinutes = (e: SleepEntry) => durationMinutes(e.plannedBedtime, e.plannedWakeUp);
export const actualDurationMinutes = (e: SleepEntry) => durationMinutes(e.actualBedtime, e.actualWakeUp);
export const timeInBedMinutes = (e: SleepEntry) => durationMinutes(effectiveBedtime(e), effectiveWakeUp(e));

export function latencyMinutes(e: SleepEntry): number | null {
  const bed = effectiveBedtime(e);
  return bed ? durationMinutes(bed, e.fellAsleepAt) : null;
}

export function fellAsleepFromLatency(bedtime: string | null, latency: number): string | null {
  const bed = parseHhMm(bedtime);
  return bed === null ? null : formatHhMm(bed + latency);
}

/** fellAsleepAt → actual/planned wake. Percentages use this as the denominator. */
export function totalSleepMinutes(e: SleepEntry): number | null {
  const wake = effectiveWakeUp(e);
  return wake ? durationMinutes(e.fellAsleepAt, wake) : null;
}

export function stagePercent(minutes: number | null, totalSleep: number | null): number | null {
  if (minutes === null || totalSleep === null || totalSleep <= 0) return null;
  return Math.round((minutes * 100) / totalSleep);
}

export function stageMinutesFromPercent(percent: number | null, totalSleep: number | null): number | null {
  if (percent === null || totalSleep === null || totalSleep <= 0) return null;
  return Math.round((percent / 100) * totalSleep);
}

export function timesMatch(actual: string | null, planned: string | null): boolean {
  return parseHhMm(actual) === parseHhMm(planned);
}

export const emptySleep = (date: string): SleepEntry => normSleep({ date, timestamp: 0 });

export function isEmptySleep(e: SleepEntry | null | undefined): boolean {
  if (!e) return true;
  return (
    e.plannedBedtime === null && e.plannedWakeUp === null && e.actualBedtime === null && e.actualWakeUp === null &&
    e.fellAsleepAt === null && e.deepMinutes === null && e.lightMinutes === null && e.remMinutes === null && e.awakeMinutes === null
  );
}

/** Home completion: actual times, fell-asleep or any stage minutes count as a logged night. */
export const hasLoggedSleep = (e: SleepEntry | null | undefined) =>
  !!e &&
  (e.actualBedtime !== null || e.actualWakeUp !== null || e.fellAsleepAt !== null ||
    SLEEP_STAGES.some((s) => stageMinutes(e, s) !== null));

// ---- edits ----

export const setStageMinutes = (e: SleepEntry, stage: SleepStage, minutes: number | null): SleepEntry => ({
  ...e, [STAGE_FIELD[stage]]: minutes,
});

export function setStagePercent(e: SleepEntry, stage: SleepStage, percent: number | null): SleepEntry {
  if (percent === null) return setStageMinutes(e, stage, null);
  const mins = stageMinutesFromPercent(percent, totalSleepMinutes(e));
  return mins === null ? e : setStageMinutes(e, stage, mins);
}

export function setLatency(e: SleepEntry, latency: number | null): SleepEntry {
  if (latency === null) return { ...e, fellAsleepAt: null };
  const bed = effectiveBedtime(e);
  if (!bed) return e;
  return { ...e, fellAsleepAt: fellAsleepFromLatency(bed, latency) };
}

export interface SleepEstimate {
  actualBedtime?: string | null;
  actualWakeUp?: string | null;
  plannedBedtime?: string | null;
  plannedWakeUp?: string | null;
  fellAsleepAt?: string | null;
  latencyMinutes?: number | null;
  deepMinutes?: number | null;
  lightMinutes?: number | null;
  remMinutes?: number | null;
  awakeMinutes?: number | null;
  deepPercent?: number | null;
  lightPercent?: number | null;
  remPercent?: number | null;
  awakePercent?: number | null;
}

export function applySleepEstimate(existing: SleepEntry, est: SleepEstimate): SleepEntry {
  let next = { ...existing };
  if (est.actualBedtime) next.actualBedtime = est.actualBedtime;
  if (est.actualWakeUp) next.actualWakeUp = est.actualWakeUp;
  if (est.plannedBedtime) next.plannedBedtime = est.plannedBedtime;
  if (est.plannedWakeUp) next.plannedWakeUp = est.plannedWakeUp;
  if (est.fellAsleepAt) next.fellAsleepAt = est.fellAsleepAt;
  else if (est.latencyMinutes != null) {
    const at = fellAsleepFromLatency(effectiveBedtime(next), est.latencyMinutes);
    if (at) next.fellAsleepAt = at;
  }
  const total = totalSleepMinutes(next);
  const resolve = (min: number | null | undefined, pct: number | null | undefined, cur: number | null) =>
    min != null ? min : pct == null ? cur : stageMinutesFromPercent(pct, total) ?? cur;
  next = {
    ...next,
    deepMinutes: resolve(est.deepMinutes, est.deepPercent, next.deepMinutes),
    lightMinutes: resolve(est.lightMinutes, est.lightPercent, next.lightMinutes),
    remMinutes: resolve(est.remMinutes, est.remPercent, next.remMinutes),
    awakeMinutes: resolve(est.awakeMinutes, est.awakePercent, next.awakeMinutes),
  };
  return next;
}
