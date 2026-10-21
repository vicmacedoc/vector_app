// Clock-time helpers shared by Food, Routine, Sleep and Analysis (SleepMath, ActivityImportDraft, MealImportDraft).

export const MINUTES_PER_DAY = 24 * 60;

const pad = (n: number) => String(n).padStart(2, "0");

/** "HH:mm" → minutes of day, wrapping out-of-range hours (SleepMath.parseHhMm). */
export function parseHhMm(s: string | null | undefined): number | null {
  if (!s || !s.trim()) return null;
  const p = s.trim().split(":");
  if (p.length < 2) return null;
  const h = toInt(p[0]);
  if (h === null) return null;
  const m = toInt(p[1]) ?? 0;
  return (((h * 60 + m) % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
}

export function formatHhMm(minutes: number): string {
  const norm = ((Math.round(minutes) % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
  return `${pad(Math.floor(norm / 60))}:${pad(norm % 60)}`;
}

/** "7h05m"; "--" for null. */
export function formatDuration(totalMinutes: number | null | undefined): string {
  if (totalMinutes == null) return "--";
  const abs = Math.abs(Math.round(totalMinutes));
  return `${Math.floor(abs / 60)}h${pad(abs % 60)}m`;
}

/** Minutes from start to end, wrapping past midnight when end <= start. */
export function durationMinutes(start: string | null | undefined, end: string | null | undefined): number | null {
  const s = parseHhMm(start);
  const e = parseHhMm(end);
  if (s === null || e === null) return null;
  return (e <= s ? e + MINUTES_PER_DAY : e) - s;
}

/** Activity clocks clamp instead of wrapping (ActivityEntry.parseActivityHhMm). */
export function parseActivityHhMm(time: string | null | undefined): number | null {
  if (!time || !time.trim()) return null;
  const p = time.trim().split(":");
  if (p.length < 2) return null;
  const h = toInt(p[0]);
  const m = toInt(p[1]);
  if (h === null || m === null) return null;
  return clamp(h, 0, 23) * 60 + clamp(m, 0, 59);
}

/** Sort key used by the Routine list: plain h*60+m with missing parts as 0. */
export function sortMinutes(time: string): number {
  const p = time.trim().split(":");
  return (toInt(p[0] ?? "") ?? 0) * 60 + (toInt(p[1] ?? "") ?? 0);
}

/** Blank, "null" and "none" read as missing (asOptionalImportValue). */
export function optionalValue(v: string | null | undefined): string | null {
  const t = (v ?? "").trim();
  if (!t) return null;
  const lower = t.toLowerCase();
  if (lower === "null" || lower === "none") return null;
  return t;
}

export function isValidHhMm(time: string | null | undefined): boolean {
  const p = time?.trim().split(":");
  if (!p || p.length < 2) return false;
  const h = toInt(p[0]);
  const m = toInt(p[1]);
  return h !== null && m !== null && h >= 0 && h <= 23 && m >= 0 && m <= 59;
}

export function normalizeActivityHhMm(raw: string | null | undefined, fallback: string): string {
  const v = optionalValue(raw);
  if (v === null || !isValidHhMm(v)) return fallback;
  const [h, m] = v.split(":").map((x) => Number.parseInt(x, 10));
  return `${pad(h)}:${pad(m)}`;
}

/** "7:5" → "07:05"; invalid or missing → "". */
export function normalizeMealTimeOrBlank(raw: string | null | undefined): string {
  const p = raw?.trim().split(":");
  if (!p || p.length < 2) return "";
  const h = toInt(p[0]);
  const m = toInt(p[1].slice(0, 2));
  if (h === null || h < 0 || h > 23 || m === null || m < 0 || m > 59) return "";
  return `${pad(h)}:${pad(m)}`;
}

export function nowHhMm(): string {
  const d = new Date();
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function toInt(s: string): number | null {
  const t = s.trim();
  return /^[+-]?\d+$/.test(t) ? Number.parseInt(t, 10) : null;
}

export const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));
