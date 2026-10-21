// Weight / body fat for the selected day (saved on blur) with a 90-day sparkline, plus the body scan entry.
import { useEffect, useMemo, useState } from "react";
import { ScanLine } from "lucide-react";
import { addDays, todayKey } from "@/domain/dates";
import { allDayRecords, dayRecord, putRecord } from "@/domain/daily/repo";
import { asJson, normHome, type HomeEntry } from "@/domain/daily/models";
import { useDaily } from "@/store/daily";
import { D, monthShard } from "@/sync/shards";
import { Button, Card } from "@/ui/primitives";
import { fmtNum } from "@/screens/calendar/ui";
import { BodyScanDialog } from "./BodyScan";

const SPAN_DAYS = 90;

const parse = (v: string): number | null => {
  const n = Number(v.trim().replace(",", "."));
  return v.trim() && Number.isFinite(n) ? n : null;
};

export function BodyCard({ date }: { date: string }) {
  const shards = useDaily((s) => s.shards);
  const saved = useMemo(() => dayRecord(shards, D.HOME, date, normHome), [shards, date]);
  const [weight, setWeight] = useState("");
  const [fat, setFat] = useState("");
  const [scanning, setScanning] = useState(false);
  const editable = date <= todayKey();

  useEffect(() => {
    setWeight(saved?.weightKg?.toString() ?? "");
    setFat(saved?.bodyFatPercent?.toString() ?? "");
  }, [saved?.weightKg, saved?.bodyFatPercent, date]);

  const commit = () => {
    const w = parse(weight);
    const f = parse(fat);
    if (w === (saved?.weightKg ?? null) && f === (saved?.bodyFatPercent ?? null)) return;
    const next: HomeEntry = { date, weightKg: w, bodyFatPercent: f, dailyPlanText: saved?.dailyPlanText ?? null, timestamp: Date.now() };
    const empty = w === null && f === null && !next.dailyPlanText;
    useDaily.getState().mutate(monthShard(date), (r) => putRecord(r, D.HOME, date, empty ? null : asJson(next)));
  };

  const series = useMemo(() => {
    const from = addDays(date, -(SPAN_DAYS - 1));
    return allDayRecords(shards, D.HOME, normHome)
      .filter((h) => h.date >= from && h.date <= date)
      .sort((a, b) => a.date.localeCompare(b.date));
  }, [shards, date]);
  const lastWeight = [...series].reverse().find((h) => h.weightKg !== null);
  const firstWeight = series.find((h) => h.weightKg !== null);
  const delta = lastWeight && firstWeight && lastWeight !== firstWeight ? lastWeight.weightKg! - firstWeight.weightKg! : null;

  return (
    <Card className="p-4">
      <div className="mb-3 flex items-center justify-between">
        <div className="font-semibold">Body</div>
        <Button size="sm" variant="ghost" disabled={!editable} onClick={() => setScanning(true)} title="Full body scan (front, left, back, right)">
          <ScanLine size={15} /> Body scan
        </Button>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <BodyInput label="Weight" unit="kg" value={weight} onChange={setWeight} onCommit={commit} disabled={!editable} />
        <BodyInput label="Body fat" unit="%" value={fat} onChange={setFat} onCommit={commit} disabled={!editable} />
      </div>
      <div className="mt-4">
        <div className="mb-1 flex items-baseline justify-between text-[11px] text-item">
          <span>Last {SPAN_DAYS} days</span>
          {delta !== null && (
            <span className={delta <= 0 ? "text-prio-low" : "text-prio-mid"}>
              {delta > 0 ? "+" : ""}{fmtNum(delta)} kg
            </span>
          )}
        </div>
        <Sparkline from={addDays(date, -(SPAN_DAYS - 1))} to={date} points={series} />
        <div className="mt-1 flex gap-3 text-[11px] text-item">
          <span className="flex items-center gap-1"><span className="h-0.5 w-3 bg-navy" /> Weight</span>
          <span className="flex items-center gap-1"><span className="h-0.5 w-3 bg-electric" /> Body fat</span>
        </div>
      </div>
      {scanning && <BodyScanDialog onClose={() => setScanning(false)} />}
    </Card>
  );
}

function BodyInput({ label, unit, value, onChange, onCommit, disabled }: {
  label: string;
  unit: string;
  value: string;
  onChange: (v: string) => void;
  onCommit: () => void;
  disabled: boolean;
}) {
  return (
    <label className="block">
      <div className="mb-1 text-xs text-muted">{label}</div>
      <div className="flex h-10 items-center rounded-lg border border-hairline px-3 focus-within:border-electric">
        <input
          inputMode="decimal"
          disabled={disabled}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onBlur={onCommit}
          onKeyDown={(e) => e.key === "Enter" && (e.target as HTMLInputElement).blur()}
          className="w-full min-w-0 bg-transparent text-lg font-semibold tabular-nums outline-none disabled:opacity-50"
          placeholder="—"
        />
        <span className="text-sm text-item">{unit}</span>
      </div>
    </label>
  );
}

function Sparkline({ from, to, points }: { from: string; to: string; points: HomeEntry[] }) {
  const W = 300;
  const H = 70;
  const t0 = Date.parse(`${from}T00:00:00Z`);
  const span = Math.max(1, Date.parse(`${to}T00:00:00Z`) - t0);
  const line = (vals: { date: string; v: number }[]) => {
    if (vals.length === 0) return null;
    const lo = Math.min(...vals.map((p) => p.v));
    const hi = Math.max(...vals.map((p) => p.v));
    const range = hi - lo || 1;
    return vals
      .map((p) => `${((Date.parse(`${p.date}T00:00:00Z`) - t0) / span) * W},${H - 6 - ((p.v - lo) / range) * (H - 12)}`)
      .join(" ");
  };
  const w = line(points.filter((p) => p.weightKg !== null).map((p) => ({ date: p.date, v: p.weightKg! })));
  const f = line(points.filter((p) => p.bodyFatPercent !== null).map((p) => ({ date: p.date, v: p.bodyFatPercent! })));
  if (!w && !f) return <div className="flex h-[70px] items-center justify-center rounded-lg bg-offwhite text-xs text-item">No measurements yet</div>;
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="h-[70px] w-full rounded-lg bg-offwhite" preserveAspectRatio="none">
      {f && <polyline points={f} fill="none" stroke="var(--color-electric)" strokeWidth={1.5} vectorEffect="non-scaling-stroke" />}
      {w && <polyline points={w} fill="none" stroke="var(--color-navy)" strokeWidth={2} vectorEffect="non-scaling-stroke" />}
    </svg>
  );
}
