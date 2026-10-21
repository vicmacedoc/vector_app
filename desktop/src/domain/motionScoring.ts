// Port of core/.../MotionScoring.kt. Points freeze at check time (a MotionScoreEvent).
import { addDays, dateKey, daysBetween, nextOrSame } from "./dates";
import { addMonthsClamped } from "./dates";
import type { DayOfWeek, MotionRecurrence, MotionTask } from "./models";

export const MAX_EARLY_BONUS = 7;
export const AGING_POINTS_PER_WEEK = 5;
export const AGING_CAP_MULTIPLIER = 2;
export const SUPERMARKET_POINTS = 20;
export const SUPERMARKET_LATE_PENALTY = 5;

/**
 * With a deadline: +1 per calendar day to spare (max 7), measured against the earlier of the first and
 * current deadline; -1 per day late, never below half of base. Without one: +5 per full week since
 * creation, capped at 2 x base.
 */
export function pointsAt(
  base: number,
  createdAt: number,
  dueAt: number | null,
  firstDueAt: number | null,
  completedAt: number,
  utc = false,
): number {
  const doneOn = dateKey(completedAt, utc);
  if (dueAt !== null) {
    const anchor = dateKey(Math.min(dueAt, firstDueAt ?? dueAt), utc);
    const spare = daysBetween(doneOn, anchor);
    if (spare > 0) return base + Math.min(MAX_EARLY_BONUS, spare);
    const late = daysBetween(dateKey(dueAt, utc), doneOn);
    if (late > 0) return Math.max(Math.floor((base + 1) / 2), base - late);
    return base;
  }
  const weeks = Math.floor(Math.max(0, daysBetween(dateKey(createdAt, utc), doneOn)) / 7);
  return base + Math.min(weeks * AGING_POINTS_PER_WEEK, AGING_CAP_MULTIPLIER * base);
}

export const taskPointsAt = (task: MotionTask, completedAt: number) =>
  pointsAt(task.points, task.createdAt, task.dueAt, task.firstDueAt, completedAt);

export interface SupermarketCycle {
  dueDate: string;
  open: boolean;
}

export function supermarketCycle(lastCycleDue: string | null, today: string, dueDay: DayOfWeek): SupermarketCycle {
  let due = lastCycleDue === null ? nextOrSame(today, dueDay) : nextOrSame(addDays(lastCycleDue, 1), dueDay);
  while (daysBetween(due, today) >= 7) due = addDays(due, 7);
  const open = daysBetween(addDays(due, -6), today) >= 0;
  return { dueDate: due, open };
}

export function supermarketPoints(dueDate: string, tripDate: string): number {
  const late = Math.max(0, daysBetween(dueDate, tripDate));
  return Math.max(0, SUPERMARKET_POINTS - late * SUPERMARKET_LATE_PENALTY);
}

/** Consecutive days meeting target, ending today (today counts only once met). */
export function streak(pointsByDate: Map<string, number>, today: string, target: number): number {
  let day = (pointsByDate.get(today) ?? 0) >= target ? today : addDays(today, -1);
  let count = 0;
  while ((pointsByDate.get(day) ?? 0) >= target) {
    count++;
    day = addDays(day, -1);
  }
  return count;
}

export function nextOccurrence(from: number, recurrence: MotionRecurrence): number {
  const d = new Date(from);
  switch (recurrence) {
    case "DAILY":
      d.setDate(d.getDate() + 1);
      return d.getTime();
    case "WEEKLY":
      d.setDate(d.getDate() + 7);
      return d.getTime();
    case "MONTHLY":
      return addMonthsClamped(from, 1);
    default:
      return from;
  }
}
