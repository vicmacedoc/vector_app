// Calendar-date helpers on "yyyy-MM-dd" keys, like java.time.LocalDate.
import type { DayOfWeek } from "./models";

const DAY_MS = 86_400_000;
const DOW: DayOfWeek[] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

const pad = (n: number) => String(n).padStart(2, "0");

/** Local (or UTC) calendar day of an instant. */
export function dateKey(millis: number, utc = false): string {
  const d = new Date(millis);
  return utc
    ? `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`
    : `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export const todayKey = () => dateKey(Date.now());

function toUtcDays(key: string): number {
  const [y, m, d] = key.split("-").map(Number);
  return Math.round(Date.UTC(y, m - 1, d) / DAY_MS);
}

function fromUtcDays(days: number): string {
  return dateKey(days * DAY_MS, true);
}

/** `b - a` in whole days. */
export const daysBetween = (a: string, b: string) => toUtcDays(b) - toUtcDays(a);

export const addDays = (key: string, n: number) => fromUtcDays(toUtcDays(key) + n);

export function dayOfWeek(key: string): DayOfWeek {
  // 1970-01-01 was a Thursday.
  const idx = (((toUtcDays(key) + 3) % 7) + 7) % 7;
  return DOW[idx];
}

export function nextOrSame(key: string, day: DayOfWeek): string {
  const diff = (DOW.indexOf(day) - DOW.indexOf(dayOfWeek(key)) + 7) % 7;
  return addDays(key, diff);
}

/** Local midnight of the day containing [millis]. */
export function startOfDay(millis = Date.now()): number {
  const d = new Date(millis);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** Calendar.add semantics: months clamp to the last day instead of overflowing. */
export function addMonthsClamped(millis: number, months: number): number {
  const d = new Date(millis);
  const day = d.getDate();
  d.setDate(1);
  d.setMonth(d.getMonth() + months);
  const last = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
  d.setDate(Math.min(day, last));
  return d.getTime();
}

export function formatDay(key: string): string {
  const [y, m, d] = key.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" });
}

export function formatDateTime(millis: number): string {
  return new Date(millis).toLocaleString(undefined, {
    weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit",
  });
}

export function relativeDue(millis: number, now = Date.now()): string {
  const diff = daysBetween(dateKey(now), dateKey(millis));
  const time = new Date(millis).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
  if (diff === 0) return `Today ${time}`;
  if (diff === 1) return `Tomorrow ${time}`;
  if (diff === -1) return `Yesterday ${time}`;
  if (diff < 0) return `${-diff}d overdue`;
  if (diff < 7) return new Date(millis).toLocaleDateString(undefined, { weekday: "short" }) + ` ${time}`;
  return new Date(millis).toLocaleDateString(undefined, { day: "numeric", month: "short" });
}
