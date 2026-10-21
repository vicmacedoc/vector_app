// 24h behaviour donut (BehaviorDonutChart): inner ring planned, outer ring done, sleep arcs included.
import { useMemo } from "react";
import { cappedDoneMinutes, effectiveEnd, effectiveStart } from "@/domain/daily/activity";
import { SLEEP_COLOR_ARGB, type ActivityEntry, type ActivityLabel, type SleepEntry } from "@/domain/daily/models";
import { MINUTES_PER_DAY, formatHhMm, parseHhMm } from "@/domain/daily/time";
import { argbCss } from "./ui";

export interface ClockArc {
  start: number;
  end: number;
}

/** Sleep arcs for one calendar day: midnight → wake (previous night) and bedtime → midnight (tonight). */
export function sleepArcs(prev: SleepEntry | null, tonight: SleepEntry | null, bedDefault: number, wakeDefault: number, useActual: boolean): ClockArc[] {
  const arcs: ClockArc[] = [];
  const defBed = formatHhMm(bedDefault);
  const defWake = formatHhMm(wakeDefault);
  const wake = parseHhMm(useActual ? prev?.actualWakeUp ?? prev?.plannedWakeUp ?? defWake : prev?.plannedWakeUp ?? defWake);
  if (wake !== null && wake > 0) arcs.push({ start: 0, end: wake });
  const bed = parseHhMm(useActual ? tonight?.actualBedtime ?? tonight?.plannedBedtime ?? defBed : tonight?.plannedBedtime ?? defBed);
  if (bed !== null && bed >= 12 * 60) arcs.push({ start: bed, end: MINUTES_PER_DAY });
  return arcs;
}

interface Segment {
  start: number;
  end: number;
  color: string;
  order: number;
}

const clockMinutes = (t: string) => {
  const p = t.trim().split(":");
  const h = Number(p[0]);
  const m = Number(p[1]);
  if (p.length < 2 || !Number.isFinite(h) || !Number.isFinite(m)) return null;
  return Math.min(MINUTES_PER_DAY - 1, Math.max(0, Math.min(23, Math.max(0, h)) * 60 + Math.min(59, Math.max(0, m))));
};

const split = (s: number, e: number): [number, number][] => (e > s ? [[s, e]] : e < s ? [[s, MINUTES_PER_DAY], [0, e]] : []);
const span = (s: number, e: number) => (e >= s ? e - s : MINUTES_PER_DAY - s + e);
const hours = (m: number) => Math.round((m / 60) * 10) / 10;

const SLEEP = argbCss(SLEEP_COLOR_ARGB);
const UNKNOWN = "#90a4ae";

function segments(acts: ActivityEntry[], labels: Map<string, ActivityLabel>, sleep: ClockArc[], effective: boolean): Segment[] {
  const out: Segment[] = [];
  for (const a of sleep) for (const [s, e] of split(a.start, a.end)) out.push({ start: s, end: e, color: SLEEP, order: -Infinity });
  for (const a of acts) {
    const s = clockMinutes(effective ? effectiveStart(a) : a.startTime);
    const e = clockMinutes(effective ? effectiveEnd(a) : a.endTime);
    if (s === null || e === null) continue;
    const l = labels.get(a.labelId);
    for (const [x, y] of split(s, e)) out.push({ start: x, end: y, color: l ? argbCss(l.colorArgb) : UNKNOWN, order: a.timestamp });
  }
  return out.sort((a, b) => a.order - b.order || a.start - b.start);
}

export interface LegendRow {
  name: string;
  color: string;
  done: number;
  planned: number;
}

function legend(acts: ActivityEntry[], labels: Map<string, ActivityLabel>, plannedSleep: ClockArc[], doneSleep: ClockArc[]): LegendRow[] {
  const planned = new Map<string, number>();
  const done = new Map<string, number>();
  const colors = new Map<string, string>();
  const add = (m: Map<string, number>, k: string, s: string, e: string) => {
    const a = clockMinutes(s);
    const b = clockMinutes(e);
    if (a !== null && b !== null) m.set(k, (m.get(k) ?? 0) + span(a, b));
  };
  for (const a of acts) {
    const l = labels.get(a.labelId);
    if (!l) continue;
    colors.set(l.name, argbCss(l.colorArgb));
    if (a.type === "PLANNED") add(planned, l.name, a.startTime, a.endTime);
    if (a.completion === "DONE") {
      if (a.type === "PLANNED") done.set(l.name, (done.get(l.name) ?? 0) + cappedDoneMinutes(a));
      else add(done, l.name, effectiveStart(a), effectiveEnd(a));
    }
  }
  if (plannedSleep.length) {
    colors.set("Sleep", SLEEP);
    planned.set("Sleep", (planned.get("Sleep") ?? 0) + plannedSleep.reduce((t, a) => t + span(a.start, a.end), 0));
  }
  if (doneSleep.length) {
    colors.set("Sleep", SLEEP);
    done.set("Sleep", (done.get("Sleep") ?? 0) + doneSleep.reduce((t, a) => t + span(a.start, a.end), 0));
  }
  const sum = (m: Map<string, number>) => Math.min(MINUTES_PER_DAY, [...m.values()].reduce((a, b) => a + b, 0));
  const names = [...new Set([...planned.keys(), ...done.keys()])].sort();
  return [
    ...names.map((n) => ({ name: n, color: colors.get(n) ?? "#9e9e9e", done: hours(done.get(n) ?? 0), planned: hours(planned.get(n) ?? 0) })),
    { name: "Not informed", color: "transparent", done: hours(MINUTES_PER_DAY - sum(done)), planned: hours(MINUTES_PER_DAY - sum(planned)) },
  ];
}

function arcPath(cx: number, cy: number, r: number, startMin: number, endMin: number) {
  const a0 = (startMin / MINUTES_PER_DAY) * 2 * Math.PI - Math.PI / 2;
  const a1 = (endMin / MINUTES_PER_DAY) * 2 * Math.PI - Math.PI / 2;
  const large = endMin - startMin > MINUTES_PER_DAY / 2 ? 1 : 0;
  if (endMin - startMin >= MINUTES_PER_DAY) {
    return `M ${cx} ${cy - r} A ${r} ${r} 0 1 1 ${cx - 0.01} ${cy - r}`;
  }
  return `M ${cx + r * Math.cos(a0)} ${cy + r * Math.sin(a0)} A ${r} ${r} 0 ${large} 1 ${cx + r * Math.cos(a1)} ${cy + r * Math.sin(a1)}`;
}

export function BehaviorDonut({
  activities, labels, plannedSleep, doneSleep,
}: {
  activities: ActivityEntry[];
  labels: ActivityLabel[];
  plannedSleep: ClockArc[];
  doneSleep: ClockArc[];
}) {
  const byId = useMemo(() => new Map(labels.map((l) => [l.id, l])), [labels]);
  const plannedSegs = useMemo(() => segments(activities.filter((a) => a.type === "PLANNED"), byId, plannedSleep, false), [activities, byId, plannedSleep]);
  const doneSegs = useMemo(() => segments(activities.filter((a) => a.completion === "DONE"), byId, doneSleep, true), [activities, byId, doneSleep]);
  const rows = useMemo(() => legend(activities, byId, plannedSleep, doneSleep), [activities, byId, plannedSleep, doneSleep]);
  const stats = useMemo(() => {
    const planned = activities.filter((a) => a.type === "PLANNED");
    const p = planned.reduce((t, a) => {
      const s = clockMinutes(a.startTime);
      const e = clockMinutes(a.endTime);
      return t + (s === null || e === null ? 0 : span(s, e));
    }, 0);
    const d = planned.filter((a) => a.completion === "DONE").reduce((t, a) => t + cappedDoneMinutes(a), 0);
    return { done: hours(d), pct: p <= 0 ? 0 : Math.min(100, Math.max(0, Math.floor((d / p) * 100))) };
  }, [activities]);

  const size = 220;
  const stroke = size * 0.12;
  const gap = size * 0.04;
  const outer = (size - stroke) / 2;
  const inner = outer - stroke / 2 - gap - stroke / 2;
  const c = size / 2;

  return (
    <div className="flex flex-col items-center gap-3">
      <div className="relative" style={{ width: size, height: size }}>
        <svg width={size} height={size}>
          <circle cx={c} cy={c} r={outer} fill="none" stroke="#f1f3f5" strokeWidth={stroke} />
          <circle cx={c} cy={c} r={inner} fill="none" stroke="#f1f3f5" strokeWidth={stroke} />
          {doneSegs.map((s, i) => (
            <path key={`d${i}`} d={arcPath(c, c, outer, s.start, s.end)} fill="none" stroke={s.color} strokeWidth={stroke} />
          ))}
          {plannedSegs.map((s, i) => (
            <path key={`p${i}`} d={arcPath(c, c, inner, s.start, s.end)} fill="none" stroke={s.color} strokeWidth={stroke} />
          ))}
          {[0, 6, 12, 18].map((h) => {
            const a = (h / 24) * 2 * Math.PI - Math.PI / 2;
            const r = inner - stroke / 2 - 10;
            return (
              <text key={h} x={c + r * Math.cos(a)} y={c + r * Math.sin(a) + 3} textAnchor="middle" className="fill-item text-[9px]">
                {String(h).padStart(2, "0")}
              </text>
            );
          })}
        </svg>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <div className="text-base font-bold">{Number.isInteger(stats.done) ? `${stats.done}h` : `${stats.done.toFixed(1)}h`}</div>
          <div className="text-xs text-muted">{stats.pct}%</div>
        </div>
      </div>
      <div className="text-[11px] text-item">Inner: planned · Outer: done</div>
      <table className="w-full text-xs">
        <thead>
          <tr className="text-left text-[10px] uppercase tracking-wide text-item">
            <th className="py-1 font-semibold">Label</th>
            <th className="py-1 text-right font-semibold">Done</th>
            <th className="py-1 text-right font-semibold">Planned</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.name} className="border-t border-hairline">
              <td className="py-1">
                <span className="inline-flex items-center gap-2">
                  <span className="h-2.5 w-2.5 rounded-full border border-black/15" style={{ background: r.color }} />
                  {r.name}
                </span>
              </td>
              <td className="py-1 text-right font-mono">{r.done.toFixed(1)}</td>
              <td className="py-1 text-right font-mono">{r.planned.toFixed(1)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
