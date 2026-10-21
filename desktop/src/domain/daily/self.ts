// SelfScale + SelfEntry content checks. Every scale runs worst (1) to best (5).
import { normSelf, type SelfEntry } from "./models";

type ScaleField =
  | "mood" | "energy" | "stress" | "cognition" | "motivation" | "physical" | "sleepQuality"
  | "social" | "selfEsteem" | "actions" | "external" | "future";

export interface SelfScale {
  id: string;
  label: string;
  lowLabel: string;
  highLabel: string;
  field: ScaleField;
}

export const SELF_SCALES: readonly SelfScale[] = [
  { id: "mood", label: "Mood", lowLabel: "Sad", highLabel: "Happy", field: "mood" },
  { id: "energy", label: "Energy", lowLabel: "Lethargic", highLabel: "Wired", field: "energy" },
  { id: "stress", label: "Stress", lowLabel: "Overwhelmed", highLabel: "Relaxed", field: "stress" },
  { id: "cognition", label: "Cognition", lowLabel: "Scattered", highLabel: "Focused", field: "cognition" },
  { id: "motivation", label: "Motivation", lowLabel: "Apathetic", highLabel: "Driven", field: "motivation" },
  { id: "physical", label: "Physical", lowLabel: "Exhausted", highLabel: "Fresh", field: "physical" },
  { id: "sleep", label: "Sleep", lowLabel: "Restless", highLabel: "Restorative", field: "sleepQuality" },
  { id: "social", label: "Social", lowLabel: "Withdrawn", highLabel: "Connected", field: "social" },
  { id: "self_esteem", label: "Self-esteem", lowLabel: "Insecure", highLabel: "Confident", field: "selfEsteem" },
  { id: "actions", label: "Actions", lowLabel: "Shameful", highLabel: "Proud", field: "actions" },
  { id: "external", label: "External", lowLabel: "Resentful", highLabel: "Grateful", field: "external" },
  { id: "future", label: "Future", lowLabel: "Pessimistic", highLabel: "Optimistic", field: "future" },
];

export const SECONDARY_SCALES = SELF_SCALES.filter((s) => s.id !== "mood");

export const MOOD_EMOJI = ["😞", "🙁", "😐", "🙂", "😄"] as const;

export const scaleValue = (s: SelfScale, e: SelfEntry): number | null => e[s.field];

export function applyScale(s: SelfScale, e: SelfEntry, value: number | null): SelfEntry {
  return { ...e, [s.field]: value === null ? null : Math.min(5, Math.max(1, Math.round(value))) };
}

export const scaleFromId = (id: string | null | undefined) =>
  id ? SELF_SCALES.find((s) => s.id.toLowerCase() === id.trim().toLowerCase()) ?? null : null;

const filled = (v: string | null) => !!v && v.trim().length > 0;

export const hasReflection = (e: SelfEntry) =>
  filled(e.journalText) || filled(e.journalAudioDriveFileId) || filled(e.highlight) || filled(e.lowlight) || filled(e.gratitude);

export const hasAnyContent = (e: SelfEntry | null | undefined) =>
  !!e && (SELF_SCALES.some((s) => scaleValue(s, e) !== null) || filled(e.intention) || hasReflection(e));

export function writtenWordCount(e: SelfEntry): number {
  return [e.journalText, e.highlight, e.lowlight, e.gratitude]
    .filter((t): t is string => t !== null)
    .reduce((sum, t) => sum + t.trim().split(/\s+/).filter((w) => w.trim()).length, 0);
}

export const emptySelf = (date: string): SelfEntry => normSelf({ date, timestamp: 0 });

/** Blank strings stored as null so an untouched field round-trips with the phone. */
export function cleanSelf(e: SelfEntry): SelfEntry {
  const t = (v: string | null) => (v && v.trim() ? v : null);
  return {
    ...e, intention: t(e.intention), highlight: t(e.highlight), lowlight: t(e.lowlight),
    gratitude: t(e.gratitude), journalText: t(e.journalText),
  };
}
