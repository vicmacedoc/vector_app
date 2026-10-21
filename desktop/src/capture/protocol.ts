import type { MotionPriority } from "@/domain/models";

export const CAPTURE_EVENT = "capture-submit";

export type CapturePayload =
  | { kind: "note"; title: string; body: string }
  | { kind: "link"; url: string; title: string }
  | { kind: "todo"; title: string; priority: MotionPriority; dueAt: number | null }
  | { kind: "learn"; name: string }
  | { kind: "files"; paths: string[] };
