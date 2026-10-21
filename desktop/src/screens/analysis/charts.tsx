// Analysis widgets: recharts for series, plain SVG/CSS for heatmaps, dot strips and rank bars.
import type { ReactNode } from "react";
import {
  Area, Bar, CartesianGrid, Cell, ComposedChart, Line, Pie, PieChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { cn } from "@/lib/cn";

export const THEME = {
  text: "#001f3f",
  muted: "#4a4a4a",
  accent: "#0074d9",
  navy: "#001f3f",
  grid: "rgba(117,117,117,0.22)",
  empty: "rgba(117,117,117,0.12)",
  positive: "#388e3c",
  negative: "#d32f2f",
  warning: "#ff9800",
};

/** Intensity ramp for heatmap cells; [fraction] is clamped to 0..1. */
export function heat(fraction: number): string {
  const f = Math.min(1, Math.max(0, fraction));
  if (f <= 0) return THEME.empty;
  return `rgba(0,116,217,${(0.16 + 0.84 * f).toFixed(3)})`;
}

const MOOD_PALETTE = ["#e53935", "#ff9800", "#ffeb3b", "#8bc34a", "#4caf50"];
export const moodColor = (level: number) => MOOD_PALETTE[Math.trunc(level) - 1] ?? "#757575";

// ---- formatting (mirrors the phone's AnalysisWidgets) ----

export function formatStat(v: number): string {
  const a = Math.abs(v);
  if (a >= 1000) return Math.round(v).toLocaleString("en-US");
  if (a >= 100) return v.toFixed(0);
  return v.toFixed(1);
}
export const statOrDash = (v: number | null | undefined) => (v == null ? "—" : formatStat(v));
export const percentOrDash = (v: number | null | undefined) => (v == null ? "—" : `${v.toFixed(0)}%`);
export const signed = (v: number | null | undefined, unit = "") => (v == null ? "—" : `${v >= 0 ? "+" : "−"}${formatStat(Math.abs(v))}${unit}`);
export const clock = (m: number | null | undefined) => {
  if (m == null) return "—";
  const n = ((Math.round(m) % 1440) + 1440) % 1440;
  return `${String(Math.floor(n / 60)).padStart(2, "0")}:${String(n % 60).padStart(2, "0")}`;
};

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
/** "2026-04-06" → "Apr 6". */
export function shortDate(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  return m ? `${MONTHS[+m[2] - 1]} ${+m[3]}` : iso;
}

function axisValue(v: number): string {
  const a = Math.abs(v);
  if (a >= 10_000) return `${(v / 1000).toFixed(0)}k`;
  if (a >= 1_000) return `${(v / 1000).toFixed(1)}k`;
  if (Number.isInteger(v)) return String(v);
  return a >= 100 ? v.toFixed(0) : v.toFixed(1);
}

// ---- layout widgets ----

export function Section({ title, subtitle, trailing, children, className }: {
  title: string; subtitle?: string; trailing?: ReactNode; children: ReactNode; className?: string;
}) {
  return (
    <section className={cn("rounded-xl border border-hairline bg-white p-5", className)}>
      <div className="mb-4 flex items-start gap-3">
        <div className="min-w-0 flex-1">
          <h3 className="font-semibold text-navy">{title}</h3>
          {subtitle && <p className="mt-0.5 text-xs text-item">{subtitle}</p>}
        </div>
        {trailing != null && <div className="shrink-0 font-mono text-sm font-semibold text-navy">{trailing}</div>}
      </div>
      {children}
    </section>
  );
}

export function StatRow({ stats, className }: { stats: [string, string][]; className?: string }) {
  return (
    <div className={cn("grid gap-3", className)} style={{ gridTemplateColumns: `repeat(${stats.length}, minmax(0, 1fr))` }}>
      {stats.map(([label, value]) => (
        <div key={label} className="rounded-lg bg-offwhite px-3 py-2">
          <div className="truncate text-[11px] text-item">{label}</div>
          <div className="font-mono text-base font-semibold text-navy">{value}</div>
        </div>
      ))}
    </div>
  );
}

export function DeltaPill({ delta, unit = "", higherIsBetter = true }: { delta: number; unit?: string; higherIsBetter?: boolean }) {
  const improving = higherIsBetter ? delta >= 0 : delta <= 0;
  const color = Math.abs(delta) < 1e-6 ? THEME.muted : improving ? THEME.positive : THEME.negative;
  return (
    <span className="rounded-full px-2 py-0.5 font-mono text-xs font-semibold" style={{ color, background: `${color}1a` }}>
      {signed(delta, unit)}
    </span>
  );
}

export function HeroStat({ value, unit, caption, delta, deltaUnit = "", higherIsBetter = true }: {
  value: string; unit?: string; caption?: string | null; delta?: number | null; deltaUnit?: string; higherIsBetter?: boolean;
}) {
  return (
    <div>
      <div className="flex items-end gap-2">
        <span className="font-mono text-3xl font-semibold text-navy">{value}</span>
        {unit && <span className="pb-1 text-sm text-item">{unit}</span>}
        {delta != null && <span className="pb-1"><DeltaPill delta={delta} unit={deltaUnit} higherIsBetter={higherIsBetter} /></span>}
      </div>
      {caption && <div className="mt-0.5 text-xs text-item">{caption}</div>}
    </div>
  );
}

export function Legend({ entries, className }: { entries: [string, string][]; className?: string }) {
  return (
    <div className={cn("flex flex-wrap gap-x-4 gap-y-1.5", className)}>
      {entries.map(([label, color]) => (
        <span key={label} className="inline-flex items-center gap-1.5 text-xs text-muted">
          <span className="size-2.5 rounded-sm" style={{ background: color }} />
          {label}
        </span>
      ))}
    </div>
  );
}

export function EmptyState({ message }: { message: string }) {
  return <div className="rounded-xl border border-dashed border-hairline bg-offwhite/50 py-20 text-center text-sm text-item">{message}</div>;
}

// ---- recharts ----

const tooltipStyle = {
  contentStyle: { borderRadius: 8, border: "1px solid #e5e7eb", fontSize: 12, padding: "6px 10px" },
  labelStyle: { color: THEME.text, fontWeight: 600 },
};
const tick = { fontSize: 11, fill: "#757575" };
const fmtTooltip = (v: unknown) => (typeof v === "number" ? formatStat(v) : String(v ?? "—"));

export interface LineLayer {
  name: string;
  values: (number | null)[];
  color: string;
  filled?: boolean;
  dashed?: boolean;
}

function rows(labels: string[], series: { name: string; values: (number | null)[] }[]) {
  return labels.map((l, i) => {
    const r: Record<string, string | number | null> = { x: l };
    for (const s of series) r[s.name] = s.values[i] ?? null;
    return r;
  });
}

function Axes({ yLabel, zero }: { yLabel?: string; zero?: boolean }) {
  return (
    <>
      <CartesianGrid stroke={THEME.grid} vertical={false} />
      <XAxis dataKey="x" tick={tick} tickFormatter={shortDate} minTickGap={24} axisLine={false} tickLine={false} />
      <YAxis
        tick={tick} width={44} axisLine={false} tickLine={false} tickFormatter={axisValue}
        domain={zero ? [0, "auto"] : ["auto", "auto"]}
        label={yLabel ? { value: yLabel, angle: -90, position: "insideLeft", style: { fontSize: 10, fill: "#757575" }, offset: 10 } : undefined}
      />
    </>
  );
}

export function LineChart({ dates, layers, yLabel, height = 220 }: { dates: string[]; layers: LineLayer[]; yLabel?: string; height?: number }) {
  const data = rows(dates, layers);
  return (
    <ResponsiveContainer width="100%" height={height}>
      <ComposedChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
        <Axes yLabel={yLabel} />
        <Tooltip {...tooltipStyle} labelFormatter={(l) => shortDate(String(l))} formatter={fmtTooltip} />
        {layers.map((l) =>
          l.filled ? (
            <Area
              key={l.name} type="monotone" dataKey={l.name} stroke={l.color} strokeWidth={2} fill={l.color} fillOpacity={0.12}
              connectNulls dot={dates.length <= 31 ? { r: 2.5, fill: l.color } : false} isAnimationActive={false}
            />
          ) : (
            <Line
              key={l.name} type="monotone" dataKey={l.name} stroke={l.color} strokeWidth={l.dashed ? 1.5 : 2}
              strokeDasharray={l.dashed ? "5 4" : undefined} connectNulls dot={false} isAnimationActive={false}
            />
          ),
        )}
      </ComposedChart>
    </ResponsiveContainer>
  );
}

export function BarChart({ labels, values, yLabel, overlay, target, height = 220 }: {
  labels: string[]; values: (number | null)[]; yLabel?: string; overlay?: (number | null)[]; target?: number | null; height?: number;
}) {
  const data = labels.map((x, i) => ({ x, value: values[i] ?? null, overlay: overlay?.[i] ?? null }));
  return (
    <ResponsiveContainer width="100%" height={height}>
      <ComposedChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
        <Axes yLabel={yLabel} zero />
        <Tooltip {...tooltipStyle} labelFormatter={(l) => shortDate(String(l))} formatter={fmtTooltip} cursor={{ fill: "rgba(0,116,217,0.06)" }} />
        <Bar dataKey="value" name={yLabel ?? "value"} fill={THEME.accent} radius={[3, 3, 0, 0]} isAnimationActive={false} />
        {overlay && <Line dataKey="overlay" name="7-day avg" stroke={THEME.navy} strokeWidth={2} dot={false} connectNulls isAnimationActive={false} />}
        {target != null && <ReferenceLine y={target} stroke={THEME.warning} strokeDasharray="5 4" label={{ value: `target ${formatStat(target)}`, fontSize: 10, fill: THEME.warning, position: "insideTopRight" }} />}
      </ComposedChart>
    </ResponsiveContainer>
  );
}

export function StackedBarChart({ labels, segments, yLabel, height = 220 }: {
  labels: string[]; segments: { name: string; color: string; values: number[] }[]; yLabel?: string; height?: number;
}) {
  const data = rows(labels, segments);
  return (
    <ResponsiveContainer width="100%" height={height}>
      <ComposedChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
        <Axes yLabel={yLabel} zero />
        <Tooltip {...tooltipStyle} labelFormatter={(l) => shortDate(String(l))} formatter={fmtTooltip} cursor={{ fill: "rgba(0,116,217,0.06)" }} />
        {segments.map((s, i) => (
          <Bar key={s.name} dataKey={s.name} stackId="a" fill={s.color} isAnimationActive={false} radius={i === segments.length - 1 ? [3, 3, 0, 0] : undefined} />
        ))}
      </ComposedChart>
    </ResponsiveContainer>
  );
}

export interface Slice {
  name: string;
  value: number;
  color: string;
}

/** Donut with a caption in the hole; pass [outer] for a second ring (planned inside, done outside). */
export function Donut({ slices, outer, centerValue, centerCaption, total, size = 200 }: {
  slices: Slice[]; outer?: Slice[]; centerValue: string; centerCaption?: string; total?: number; size?: number;
}) {
  const pad = (s: Slice[]) => {
    if (total == null) return s;
    const sum = s.reduce((a, x) => a + x.value, 0);
    return sum < total ? [...s, { name: "", value: total - sum, color: THEME.empty }] : s;
  };
  const inner = pad(slices.filter((s) => s.value > 0));
  const ring = outer ? pad(outer.filter((s) => s.value > 0)) : null;
  const r = size / 2;
  return (
    <div className="relative mx-auto" style={{ width: size, height: size }}>
      <PieChart width={size} height={size}>
        <Pie data={inner} dataKey="value" innerRadius={ring ? r * 0.5 : r * 0.62} outerRadius={ring ? r * 0.72 : r * 0.95} startAngle={90} endAngle={-270} stroke="none" isAnimationActive={false}>
          {inner.map((s, i) => <Cell key={i} fill={s.color} />)}
        </Pie>
        {ring && (
          <Pie data={ring} dataKey="value" innerRadius={r * 0.77} outerRadius={r * 0.97} startAngle={90} endAngle={-270} stroke="none" isAnimationActive={false}>
            {ring.map((s, i) => <Cell key={i} fill={s.color} />)}
          </Pie>
        )}
      </PieChart>
      <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
        <span className="font-mono text-2xl font-semibold text-navy">{centerValue}</span>
        {centerCaption && <span className="text-[11px] text-item">{centerCaption}</span>}
      </div>
    </div>
  );
}

// ---- custom grids ----

export interface HeatmapRow {
  label: string;
  cells: string[];
  trailing?: string;
  titles?: string[];
}

/** One row per series, one column per period; reads better than a line for "which days did I miss". */
export function Heatmap({ rows, labelWidth = 96, cellHeight = 20, startCaption, endCaption }: {
  rows: HeatmapRow[]; labelWidth?: number; cellHeight?: number; startCaption?: string; endCaption?: string;
}) {
  if (!rows.length) return null;
  const hasTrailing = rows.some((r) => r.trailing != null);
  return (
    <div className="space-y-[3px]">
      {rows.map((row, ri) => (
        <div key={`${row.label}-${ri}`} className="flex items-center">
          <div className="shrink-0 truncate pr-2 text-[11px] text-muted" style={{ width: labelWidth }} title={row.label}>{row.label}</div>
          <div className="flex min-w-0 flex-1 gap-[2px]" style={{ height: cellHeight }}>
            {row.cells.map((c, i) => (
              <div key={i} className="min-w-[1px] flex-1 rounded-[3px]" style={{ background: c }} title={row.titles?.[i]} />
            ))}
          </div>
          {hasTrailing && <div className="w-14 shrink-0 text-right font-mono text-xs font-semibold text-navy">{row.trailing ?? ""}</div>}
        </div>
      ))}
      {(startCaption || endCaption) && (
        <div className="flex justify-between text-[10px] text-item" style={{ paddingLeft: labelWidth, paddingRight: hasTrailing ? 56 : 0 }}>
          <span>{startCaption}</span>
          <span>{endCaption}</span>
        </div>
      )}
    </div>
  );
}

/** Times-of-day as dots, one lane per day; shows drifting schedules that daily totals hide. */
export function DotStrip({ rows, startMinute = 4 * 60, endMinute = 24 * 60, color = THEME.accent, labelWidth = 64 }: {
  rows: { label: string; minutes: number[] }[]; startMinute?: number; endMinute?: number; color?: string; labelWidth?: number;
}) {
  if (!rows.length) return null;
  const span = Math.max(1, endMinute - startMinute);
  const pos = (m: number) => `${((Math.min(endMinute, Math.max(startMinute, m)) - startMinute) / span) * 100}%`;
  const ticks = [startMinute, (startMinute + endMinute) / 2, endMinute];
  return (
    <div className="space-y-[2px]">
      {rows.map((row, ri) => (
        <div key={`${row.label}-${ri}`} className="flex items-center">
          <div className="shrink-0 truncate pr-2 text-[10px] text-item" style={{ width: labelWidth }}>{row.label}</div>
          <div className="relative h-3.5 flex-1">
            <div className="absolute inset-x-0 top-1/2 h-px" style={{ background: THEME.grid }} />
            {row.minutes.map((m, i) => (
              <div
                key={i} title={clock(m)}
                className="absolute top-1/2 size-2 -translate-x-1/2 -translate-y-1/2 rounded-full"
                style={{ left: pos(m), background: color }}
              />
            ))}
          </div>
        </div>
      ))}
      <div className="flex justify-between pt-0.5 text-[10px] text-item" style={{ paddingLeft: labelWidth }}>
        {ticks.map((t) => <span key={t}>{clock(t)}</span>)}
      </div>
    </div>
  );
}

export interface RankItem {
  label: string;
  value: number;
  valueLabel: string;
}

export function RankBars({ items, selected, onSelect, maxRows = 12 }: {
  items: RankItem[]; selected?: string | null; onSelect?: (label: string) => void; maxRows?: number;
}) {
  const shown = items.slice(0, maxRows);
  const max = Math.max(1e-9, ...shown.map((i) => i.value));
  return (
    <div className="space-y-1.5">
      {shown.map((it) => {
        const active = selected === it.label;
        return (
          <button
            key={it.label}
            disabled={!onSelect}
            onClick={() => onSelect?.(it.label)}
            className={cn("flex w-full items-center gap-3 rounded-md px-1 py-0.5 text-left", onSelect && "hover:bg-offwhite", active && "bg-electric/5")}
          >
            <span className={cn("w-32 shrink-0 truncate text-xs capitalize", active ? "font-semibold text-navy" : "text-muted")}>{it.label}</span>
            <span className="relative h-3 flex-1 overflow-hidden rounded-full bg-offwhite">
              <span
                className="absolute inset-y-0 left-0 rounded-full"
                style={{ width: `${(it.value / max) * 100}%`, background: active || !selected ? THEME.accent : "rgba(0,116,217,0.45)" }}
              />
            </span>
            <span className="w-14 shrink-0 text-right font-mono text-xs font-semibold text-navy">{it.valueLabel}</span>
          </button>
        );
      })}
    </div>
  );
}
