// Learn tile rules from BrainRepository / LearningSettings; the settings come from the synced config shard.
import type { LearningTile, LearningTileStatus } from "./models";

const DAY_MS = 86_400_000;

export interface LearnRules {
  onHoldDays: number;
  /** Expanding review ladder; its length is the number of reviews before a tile is mastered. */
  reviewIntervals: number[];
}

export const DEFAULT_LEARN_RULES: LearnRules = { onHoldDays: 14, reviewIntervals: [3, 7, 21, 60, 180] };

/** Days until the review at [step], or null once the ladder is finished (mastered). */
export const intervalDays = (rules: LearnRules, step: number): number | null => rules.reviewIntervals[step] ?? null;

export type LearnGroup = "BACKLOG" | "IN_PROGRESS" | "LEARNED";

export const GROUP_OF: Record<LearningTileStatus, LearnGroup> = {
  PRIORITY: "BACKLOG",
  BACKLOG: "BACKLOG",
  IN_PROGRESS: "IN_PROGRESS",
  ON_HOLD: "IN_PROGRESS",
  TO_REVIEW: "LEARNED",
  LEARNED: "LEARNED",
};

export const STATUS_LABEL: Record<LearningTileStatus, string> = {
  PRIORITY: "Priority",
  BACKLOG: "General",
  IN_PROGRESS: "Active",
  ON_HOLD: "On hold",
  TO_REVIEW: "Review",
  LEARNED: "Done",
};

/**
 * Status as the phone would show it after its periodic refresh. Display only: writing it here would race
 * the phone's own refresh and turn every flip into a conflict.
 */
export function effectiveStatus(tile: LearningTile, rules: LearnRules = DEFAULT_LEARN_RULES, now = Date.now()): LearningTileStatus {
  if (tile.status === "LEARNED" && tile.nextReviewAt !== null && tile.nextReviewAt <= now) return "TO_REVIEW";
  if (tile.status === "IN_PROGRESS" && tile.lastActivityAt < now - rules.onHoldDays * DAY_MS) return "ON_HOLD";
  return tile.status;
}

/** Manual status change (BrainRepository.setLearnStatus). */
export function withStatus(tile: LearningTile, status: LearningTileStatus, rules: LearnRules = DEFAULT_LEARN_RULES, now = Date.now()): LearningTile {
  if (tile.status === status) return tile;
  let t = tile;
  switch (status) {
    case "LEARNED": {
      const days = intervalDays(rules, 0);
      t = { ...t, learnedAt: now, reviewStep: 0, nextReviewAt: days !== null ? now + days * DAY_MS : null };
      break;
    }
    case "IN_PROGRESS":
      t = { ...t, lastActivityAt: now, reviewStep: 0, nextReviewAt: null };
      break;
    case "BACKLOG":
    case "PRIORITY":
      t = { ...t, reviewStep: 0, nextReviewAt: null };
      break;
    default:
      break;
  }
  return { ...t, status, updatedAt: now };
}

/**
 * Review outcome (BrainRepository.completeReview): [solid] climbs one step of the ladder (mastered after the
 * last one); otherwise the tile goes back to In Progress and the ladder restarts.
 */
export function withReview(tile: LearningTile, solid: boolean, score: number | null, rules: LearnRules = DEFAULT_LEARN_RULES, now = Date.now()): LearningTile {
  let t: LearningTile;
  if (solid) {
    const step = tile.reviewStep + 1;
    const days = intervalDays(rules, step);
    t = { ...tile, status: "LEARNED", reviewStep: step, nextReviewAt: days !== null ? now + days * DAY_MS : null };
  } else {
    t = { ...tile, status: "IN_PROGRESS", reviewStep: 0, nextReviewAt: null, lastActivityAt: now };
  }
  return { ...t, lastReviewScore: score ?? tile.lastReviewScore, lastReviewedAt: now, updatedAt: now };
}

/** A note added or edited on a Learn tile counts as study (touchLearnActivity). */
export function touched(tile: LearningTile, now = Date.now()): LearningTile {
  const status: LearningTileStatus =
    tile.status === "BACKLOG" || tile.status === "PRIORITY" || tile.status === "ON_HOLD" ? "IN_PROGRESS" : tile.status;
  return { ...tile, status, lastActivityAt: now, updatedAt: now };
}

/** True when adding "from depends on to" would close a cycle (or is a self link). */
export function wouldCycle(links: { fromNodeId: string; toNodeId: string }[], from: string, to: string): boolean {
  if (from === to) return true;
  const dependsOn = new Map<string, string[]>();
  for (const l of links) dependsOn.set(l.fromNodeId, [...(dependsOn.get(l.fromNodeId) ?? []), l.toNodeId]);
  const seen = new Set<string>();
  const stack = [to];
  while (stack.length) {
    const current = stack.pop()!;
    if (current === from) return true;
    if (!seen.has(current)) {
      seen.add(current);
      stack.push(...(dependsOn.get(current) ?? []));
    }
  }
  return false;
}
