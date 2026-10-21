// Unsaved Calendar edits per category/day (CalendarDraftRepository). Device-local, never synced.
import type { ActivityEntry, FoodEntry, SleepEntry, WorkoutSet } from "./models";
import { isEmptySleep } from "./sleep";

export type DraftCategory = "ROUTINE" | "FOOD" | "EXERCISE" | "SELF";
export const DRAFT_CATEGORIES: readonly DraftCategory[] = ["ROUTINE", "FOOD", "EXERCISE", "SELF"];

export interface RoutineDraft {
  activities: ActivityEntry[];
  previousNight: SleepEntry | null;
  tonight: SleepEntry | null;
}

export interface FoodDraft {
  entries: FoodEntry[];
}

export interface ExerciseDraftPayload {
  sets: WorkoutSet[];
}

export interface SelfDraft {
  /** Scale id → level. */
  scales: Record<string, number>;
  intention: string;
  highlight: string;
  lowlight: string;
  gratitude: string;
  journalText: string;
  audioDriveFileId: string | null;
}

export interface DraftPayloads {
  ROUTINE: RoutineDraft;
  FOOD: FoodDraft;
  EXERCISE: ExerciseDraftPayload;
  SELF: SelfDraft;
}

const stable = (v: unknown): string =>
  JSON.stringify(v, (_k, x) =>
    x && typeof x === "object" && !Array.isArray(x)
      ? Object.fromEntries(Object.entries(x as Record<string, unknown>).sort(([a], [b]) => a.localeCompare(b)))
      : x,
  );

const byId = <T extends { id: string }>(list: T[]) => [...list].sort((a, b) => a.id.localeCompare(b.id));
const noStamp = <T extends { timestamp: number }>(x: T) => ({ ...x, timestamp: 0 });
const normSleep = (s: SleepEntry | null) => (s === null || isEmptySleep(s) ? null : noStamp(s));

export function sameRoutine(a: RoutineDraft, b: RoutineDraft): boolean {
  return (
    stable(byId(a.activities).map(noStamp)) === stable(byId(b.activities).map(noStamp)) &&
    stable(normSleep(a.previousNight)) === stable(normSleep(b.previousNight)) &&
    stable(normSleep(a.tonight)) === stable(normSleep(b.tonight))
  );
}

export const sameFood = (a: FoodDraft, b: FoodDraft) =>
  stable(byId(a.entries).map(noStamp)) === stable(byId(b.entries).map(noStamp));

export const sameExercise = (a: ExerciseDraftPayload, b: ExerciseDraftPayload) => stable(byId(a.sets)) === stable(byId(b.sets));

export const sameSelf = (a: SelfDraft, b: SelfDraft) => stable(a) === stable(b);

export const draftKey = (c: DraftCategory, date: string) => `${c}|${date}`;

export function parseDraftKey(key: string): { category: DraftCategory; date: string } {
  const [category, date] = key.split("|");
  return { category: category as DraftCategory, date };
}

export function decodeDraft<C extends DraftCategory>(payloadJson: string | undefined): DraftPayloads[C] | null {
  if (!payloadJson) return null;
  try {
    return JSON.parse(payloadJson) as DraftPayloads[C];
  } catch {
    return null;
  }
}
