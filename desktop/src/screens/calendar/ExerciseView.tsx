// Exercise day: sessions and exercises on the left, the selected exercise's set editor and day summary on the right.
import { useEffect, useMemo, useState } from "react";
import { Copy, Dumbbell, Trash2 } from "lucide-react";
import { cn } from "@/lib/cn";
import type { ExerciseDraftPayload } from "@/domain/daily/drafts";
import type { WorkoutSet } from "@/domain/daily/models";
import {
  BUCKET_LABEL, GROUP_LABEL, plannedMinutes, summarizeExerciseDay, type Progress,
} from "@/domain/daily/stats";
import { durationToMinutes, groupSessions, isDropRow, isIntervalRow, sessionToClipboard } from "@/domain/daily/workoutPlan";
import { useBrain } from "@/store/brain";
import { useCalendar } from "@/store/calendar";
import { Button, Confirm, Empty } from "@/ui/primitives";
import { NumField, Section, copyText, fmtNum } from "./ui";

export const WORKOUT_STATUSES = ["None", "Not Done", "Partial", "Done", "Exceeded", "N/A"] as const;
const STATUS_COLOR: Record<string, string> = {
  Done: "#4caf50", Partial: "#ff9800", "Not Done": "#d32f2f", Exceeded: "#0074d9", "N/A": "#757575",
};
const statusColor = (s: string | null) => (s ? STATUS_COLOR[s] ?? "#00000080" : "#00000080");

function sessionTint(blocks: WorkoutSet[][]): string {
  const relevant = blocks.map((b) => b[0]?.workoutStatus ?? null).filter((s) => s !== "N/A");
  if (!relevant.length) return "bg-white";
  if (relevant.every((s) => s === null || s === "None" || s === "Not Done")) return "bg-white";
  if (relevant.every((s) => s === "Exceeded")) return "bg-[#2196f3]/10";
  if (relevant.every((s) => s === "Done" || s === "Exceeded")) return "bg-prio-low/10";
  return "bg-prio-mid/10";
}

const strokeLabel = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

function exerciseSubtitle(rows: WorkoutSet[]): string {
  const mains = rows.filter((r) => !isDropRow(r));
  const head = mains[0];
  if (!head) return "";
  const parts: string[] = [];
  const planned = plannedMinutes(rows);
  if (isIntervalRow(head)) {
    const work = head.targetDistance !== null ? `${fmtNum(head.targetDistance)} ${head.unitDistance}` : head.targetDuration !== null ? `${head.targetDuration} ${head.unitDuration}` : null;
    parts.push(work ? `${mains.length} × ${work}` : `${mains.length} intervals`);
    const zones = [...new Set(mains.map((m) => m.targetZone).filter((z) => z !== null))];
    if (zones.length === 1) parts.push(`Z${zones[0]}`);
  } else if (head.exerciseType.toUpperCase() === "RESISTANCE") {
    parts.push(`${mains.length} set${mains.length !== 1 ? "s" : ""}`);
    if (rows.some(isDropRow)) parts.push("drop");
  }
  if (head.stroke) parts.push(strokeLabel(head.stroke));
  if (planned > 0) parts.push(`${fmtNum(planned)} min`);
  return parts.join(" · ");
}

function blocksOf(rows: WorkoutSet[]): WorkoutSet[][] {
  const order: string[] = [];
  const by = new Map<string, WorkoutSet[]>();
  for (const r of rows) {
    if (!by.has(r.exerciseId)) {
      order.push(r.exerciseId);
      by.set(r.exerciseId, []);
    }
    by.get(r.exerciseId)!.push(r);
  }
  return order.map((id) => by.get(id)!);
}

function supersetRuns(blocks: WorkoutSet[][]): WorkoutSet[][][] {
  const runs: WorkoutSet[][][] = [];
  for (const b of blocks) {
    const g = b[0]?.groupingId ?? null;
    const last = runs[runs.length - 1];
    if (g !== null && last && last[0][0]?.groupingId === g) last.push(b);
    else runs.push([b]);
  }
  return runs;
}

export function ExerciseView() {
  const slot = useCalendar((s) => s.EXERCISE);
  const editing = useCalendar((s) => s.editing);
  const update = useCalendar((s) => s.update);
  const toast = useBrain((s) => s.toast);
  const [selected, setSelected] = useState<string | null>(null);
  const [removing, setRemoving] = useState<{ title: string; ids: Set<string> } | null>(null);
  const sets = useMemo(() => slot?.current.sets ?? [], [slot]);
  const sessions = useMemo(() => groupSessions(sets), [sets]);
  const summary = useMemo(() => summarizeExerciseDay(sets), [sets]);
  const selectedRows = useMemo(() => sets.filter((s) => s.exerciseId === selected), [sets, selected]);
  useEffect(() => {
    if (selected && !sets.some((s) => s.exerciseId === selected)) setSelected(null);
  }, [sets, selected]);
  if (!slot) return null;

  const edit = (fn: (d: ExerciseDraftPayload) => ExerciseDraftPayload) => update("EXERCISE", fn);
  const setStatus = (exerciseId: string, status: string | null) =>
    edit((d) => ({ sets: d.sets.map((s) => (s.exerciseId === exerciseId ? { ...s, workoutStatus: status } : s)) }));
  const patchRow = (id: string, fn: (s: WorkoutSet) => WorkoutSet) => edit((d) => ({ sets: d.sets.map((s) => (s.id === id ? fn(s) : s)) }));

  return (
    <div className="grid grid-cols-[minmax(0,1fr)_400px] gap-6 2xl:grid-cols-[minmax(0,1fr)_460px]">
      <div className="min-w-0 space-y-4">
        {slot.presetError && <div className="rounded-lg bg-danger/10 px-3 py-2 text-xs text-danger">{slot.presetError}</div>}
        {sessions.length === 0 ? (
          <Empty
            icon={<Dumbbell size={22} />}
            title="Rest day"
            hint={slot.noPreset ? "No workout preset yet. Load one in Settings, or add sessions with the Add button." : "No workout planned. Save to log it as a rest day."}
          />
        ) : (
          sessions.map((s) => {
            const blocks = blocksOf(s.rows);
            const desc = s.rows[0]?.sessionDescription ?? "";
            return (
              <div key={s.key} className={cn("rounded-xl border border-navy/25 p-4", sessionTint(blocks))}>
                <div className="mb-3 flex items-start gap-2">
                  <div className="min-w-0 flex-1">
                    <div className="font-semibold text-navy">{s.title}</div>
                    {desc && <div className="mt-0.5 text-[13px] text-muted">{desc}</div>}
                  </div>
                  <Button size="icon" variant="ghost" className="h-8 w-8" title="Copy session as JSON" onClick={async () => {
                    if (await copyText(sessionToClipboard(s.rows))) toast("Copied session");
                  }}>
                    <Copy size={14} />
                  </Button>
                  {editing && (
                    <Button size="icon" variant="ghost" className="h-8 w-8 text-danger" title="Remove from day" onClick={() => setRemoving({ title: s.title, ids: new Set(s.rows.map((r) => r.id)) })}>
                      <Trash2 size={14} />
                    </Button>
                  )}
                </div>
                <div className="space-y-2">
                  {supersetRuns(blocks).map((run, i) => {
                    const letter = run[0][0]?.groupingId;
                    if (letter && run.length > 1) {
                      return (
                        <div key={i}>
                          <div className="my-1 flex items-center gap-2 text-[10px] font-semibold tracking-wider text-navy uppercase">
                            <span className="h-px flex-1 bg-navy/40" />
                            {run.length > 2 ? "Tri-set" : "Superset"} {letter}
                            <span className="h-px flex-1 bg-navy/40" />
                          </div>
                          <div className="space-y-2 border-l-[3px] border-navy/50 pl-2">
                            {run.map((b, j) => (
                              <ExerciseCard key={b[0].exerciseId} rows={b} badge={`${letter}${j + 1}`} selected={selected === b[0].exerciseId} editing={editing} onSelect={() => setSelected(b[0].exerciseId)} onStatus={(st) => setStatus(b[0].exerciseId, st)} />
                            ))}
                          </div>
                        </div>
                      );
                    }
                    return run.map((b) => (
                      <ExerciseCard key={b[0].exerciseId} rows={b} selected={selected === b[0].exerciseId} editing={editing} onSelect={() => setSelected(b[0].exerciseId)} onStatus={(st) => setStatus(b[0].exerciseId, st)} />
                    ));
                  })}
                </div>
              </div>
            );
          })
        )}
      </div>

      <div className="space-y-6">
        {selectedRows.length > 0 && <SetEditor rows={selectedRows} editing={editing} onPatch={patchRow} onClose={() => setSelected(null)} />}
        {sets.length > 0 && <DaySummary summary={summary} />}
      </div>

      <Confirm
        open={!!removing}
        title="Remove session?"
        message={`Remove "${removing?.title}" from this day? Save to keep the change.`}
        confirmLabel="Remove"
        onClose={() => setRemoving(null)}
        onConfirm={() => removing && edit((d) => ({ sets: d.sets.filter((s) => !removing.ids.has(s.id)) }))}
      />
    </div>
  );
}

function ExerciseCard({
  rows, badge, selected, editing, onSelect, onStatus,
}: {
  rows: WorkoutSet[];
  badge?: string;
  selected: boolean;
  editing: boolean;
  onSelect: () => void;
  onStatus: (s: string | null) => void;
}) {
  const head = rows[0];
  const status = head.workoutStatus;
  return (
    <div
      onClick={onSelect}
      className={cn(
        "flex cursor-pointer items-center gap-3 rounded-lg border bg-white px-3 py-2.5 transition",
        selected ? "border-electric ring-2 ring-electric/20" : "border-navy/20 hover:border-navy/50",
      )}
    >
      {badge && <span className="rounded bg-navy px-1.5 py-0.5 text-[10px] font-semibold text-white">{badge}</span>}
      <div className="min-w-0 flex-1">
        <div className="truncate text-sm font-semibold text-navy">{head.exerciseName || head.exerciseId}</div>
        <div className="truncate text-[11px] text-item">{exerciseSubtitle(rows)}</div>
      </div>
      <select
        value={status ?? "None"}
        disabled={!editing}
        onClick={(e) => e.stopPropagation()}
        onChange={(e) => onStatus(e.target.value === "None" ? null : e.target.value)}
        className="h-7 rounded px-2 text-[11px] font-semibold outline-none"
        style={{ color: statusColor(status), background: `color-mix(in srgb, ${statusColor(status)} 15%, white)` }}
      >
        {WORKOUT_STATUSES.map((s) => (
          <option key={s} value={s} className="text-black">{s}</option>
        ))}
      </select>
    </div>
  );
}

function SetEditor({ rows, editing, onPatch, onClose }: { rows: WorkoutSet[]; editing: boolean; onPatch: (id: string, fn: (s: WorkoutSet) => WorkoutSet) => void; onClose: () => void }) {
  const sorted = [...rows].sort((a, b) => a.setNumber - b.setNumber || a.dropNumber - b.dropNumber);
  const mains = sorted.filter((r) => !isDropRow(r));
  const first = sorted[0];
  const type = first.exerciseType || "RESISTANCE";
  const badges = [
    first.groupingId && `Superset ${first.groupingId}`,
    sorted.some(isDropRow) && "Drop set",
    first.stroke && strokeLabel(first.stroke),
    plannedMinutes(sorted) > 0 && `${fmtNum(plannedMinutes(sorted))} min`,
  ].filter(Boolean) as string[];
  return (
    <Section title={first.exerciseName || "Exercise"} actions={<Button size="sm" variant="ghost" onClick={onClose}>Close</Button>}>
      {badges.length > 0 && (
        <div className="mb-2 flex flex-wrap gap-1.5">
          {badges.map((b) => <span key={b} className="rounded bg-navy/10 px-1.5 py-0.5 text-[10px] font-medium text-navy">{b}</span>)}
        </div>
      )}
      {first.exerciseDescription && <div className="mb-2 text-[13px] text-muted">{first.exerciseDescription}</div>}
      {first.timing && <div className="mb-3 text-xs text-item">Timing: {first.timing}</div>}
      <div className="max-h-[60vh] space-y-3 overflow-y-auto pr-1">
        {mains.map((m) => {
          const label = isIntervalRow(m) ? `Interval ${m.setNumber}` : type.toUpperCase() === "RESISTANCE" ? `Set ${m.setNumber}${m.setType === "DROP" ? " · Drop set" : ""}` : null;
          const isOther = type.toUpperCase() === "OTHER";
          const showNote = m.description && !isOther && m.description !== first.exerciseDescription && !(m.setNumber === 1 && !isIntervalRow(m) && first.exerciseDescription);
          const totalTarget = mains.length === 1 && type.toUpperCase() !== "RESISTANCE" && !isIntervalRow(m) && m.targetDuration === null && m.totalDuration !== null
            ? durationToMinutes(m.totalDuration, m.unitTotalDuration)
            : null;
          return (
            <div key={m.id} className="rounded-lg border border-navy/15 bg-offwhite/60 p-3">
              {label && <div className="mb-1 text-sm font-semibold text-navy">{label}</div>}
              {showNote && <div className="mb-1 text-[13px]">{m.description}</div>}
              {m.cadence && <div className="mb-1 font-mono text-xs text-muted">Cadence: {m.cadence}</div>}
              <TargetLine row={m} totalTarget={totalTarget} />
              <Actuals row={m} totalTarget={totalTarget} editing={editing} onPatch={onPatch} />
              {sorted.filter((d) => isDropRow(d) && d.setNumber === m.setNumber).map((d) => (
                <div key={d.id} className="mt-3 border-t border-prio-mid/40 pt-2">
                  <div className="mb-1 text-sm font-semibold text-prio-mid">Drop {d.dropNumber}</div>
                  <TargetLine row={d} totalTarget={null} />
                  <Actuals row={d} totalTarget={null} editing={editing} onPatch={onPatch} />
                </div>
              ))}
            </div>
          );
        })}
      </div>
    </Section>
  );
}

function TargetLine({ row, totalTarget }: { row: WorkoutSet; totalTarget: number | null }) {
  const parts = [
    row.targetLoad !== null && `${fmtNum(row.targetLoad)} ${row.unitLoad}`,
    row.targetReps !== null && `${row.targetReps} r`,
    row.targetVelocity !== null && `${row.targetVelocity} ${row.unitVelocity}`,
    row.targetDuration !== null && `${row.targetDuration} ${row.unitDuration}`,
    totalTarget !== null && `${fmtNum(totalTarget)} min`,
    row.targetDistance !== null && `${fmtNum(row.targetDistance)} ${row.unitDistance}`,
    row.targetZone !== null && `Z${row.targetZone}`,
    row.targetRpe !== null && `RPE ${fmtNum(row.targetRpe)}`,
    row.targetCadence !== null && `${row.targetCadence} ${row.unitCadence}`,
    row.targetRir !== null && `RIR ${row.targetRir}`,
    row.targetRest !== null && row.targetRest > 0 && `Rest ${row.targetRest} ${row.restUnit}`,
  ].filter(Boolean);
  if (!parts.length) return null;
  return (
    <div className="mb-2">
      <div className="text-[10px] font-semibold tracking-wide text-item uppercase">Target</div>
      <div className="font-mono text-xs">{parts.join(" · ")}</div>
    </div>
  );
}

function Actuals({ row, totalTarget, editing, onPatch }: { row: WorkoutSet; totalTarget: number | null; editing: boolean; onPatch: (id: string, fn: (s: WorkoutSet) => WorkoutSet) => void }) {
  const unitIsMin = totalTarget !== null || ["min", "minutes", "minute"].includes(row.unitDuration.toLowerCase());
  const targetDur = row.targetDuration ?? (totalTarget !== null ? Math.round(totalTarget) : null);
  const showLoad = row.targetLoad !== null || row.actualLoad !== null;
  const showReps = row.targetReps !== null || row.actualReps !== null;
  const showDur = targetDur !== null || row.actualDuration !== null;
  const showDist = row.targetDistance !== null || row.actualDistance !== null;
  const showRpe = !isDropRow(row) && (row.actualRpe !== null || row.targetRpe !== null || row.targetRir !== null || showDur || showDist);
  const showRir = row.targetRir !== null || row.actualRir !== null;
  const showVel = row.targetVelocity !== null || row.actualVelocity !== null;
  const showCad = row.targetCadence !== null || row.actualCadence !== null;
  const showRest = (row.targetRest ?? 0) > 0 || row.actualRest !== null;
  if (!(showLoad || showReps || showDur || showDist || showRpe || showRir || showVel || showCad || showRest)) return null;
  const set = (fn: (s: WorkoutSet) => WorkoutSet) => onPatch(row.id, fn);
  const cell = (label: string, node: React.ReactNode) => (
    <label className="flex flex-col gap-0.5">
      <span className="text-[10px] text-item">{label}</span>
      {node}
    </label>
  );
  const durShown = row.actualDuration !== null ? (unitIsMin ? Math.floor(row.actualDuration / 60) : row.actualDuration) : null;
  return (
    <div>
      <div className="mb-1 text-[10px] font-semibold tracking-wide text-item uppercase">Actual</div>
      <div className="grid grid-cols-3 gap-2">
        {showLoad && cell(`Load (${row.unitLoad})`, <NumField value={row.actualLoad} placeholder={fmtNum(row.targetLoad)} min={0} max={999.9} step={0.5} disabled={!editing} onChange={(v) => set((s) => ({ ...s, actualLoad: v }))} />)}
        {showReps && cell("Reps", <NumField value={row.actualReps} placeholder={fmtNum(row.targetReps)} integer min={0} max={999} disabled={!editing} onChange={(v) => set((s) => ({ ...s, actualReps: v }))} />)}
        {showDur && cell(`Duration (${unitIsMin ? "min" : row.unitDuration})`, <NumField value={durShown} placeholder={fmtNum(targetDur)} integer min={0} disabled={!editing} onChange={(v) => set((s) => ({ ...s, actualDuration: v === null ? null : unitIsMin ? v * 60 : v }))} />)}
        {showDist && cell(`Distance (${row.unitDistance})`, <NumField value={row.actualDistance} placeholder={fmtNum(row.targetDistance)} min={0} step={["m", "yd"].includes(row.unitDistance.toLowerCase()) ? 25 : 0.1} disabled={!editing} onChange={(v) => set((s) => ({ ...s, actualDistance: v }))} />)}
        {showCad && cell(`Cadence (${row.unitCadence})`, <NumField value={row.actualCadence} placeholder={fmtNum(row.targetCadence)} integer min={0} max={250} disabled={!editing} onChange={(v) => set((s) => ({ ...s, actualCadence: v }))} />)}
        {showRpe && cell("RPE (0–10)", <NumField value={row.actualRpe} placeholder={fmtNum(row.targetRpe)} min={0} max={10} step={0.5} disabled={!editing} onChange={(v) => set((s) => ({ ...s, actualRpe: v === null ? null : Math.round(v * 2) / 2 }))} />)}
        {showRir && cell("RIR (0–10)", <NumField value={row.actualRir} placeholder={fmtNum(row.targetRir)} integer min={0} max={10} disabled={!editing} onChange={(v) => set((s) => ({ ...s, actualRir: v }))} />)}
        {showVel && cell(`Velocity (${row.unitVelocity})`, <NumField value={row.actualVelocity} placeholder={fmtNum(row.targetVelocity)} integer min={0} max={99} disabled={!editing} onChange={(v) => set((s) => ({ ...s, actualVelocity: v }))} />)}
        {showRest && cell(`Rest (${row.restUnit})`, <NumField value={row.actualRest} placeholder={fmtNum(row.targetRest)} integer min={0} disabled={!editing} onChange={(v) => set((s) => ({ ...s, actualRest: v }))} />)}
      </div>
    </div>
  );
}

function ProgressBar({ p, unit }: { p: Progress; unit: string }) {
  const pct = p.planned > 0 ? Math.min(100, (p.done / p.planned) * 100) : p.done > 0 ? 100 : 0;
  return (
    <div className="flex items-center gap-2">
      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-offwhite ring-1 ring-hairline">
        <div className="h-full rounded-full bg-navy" style={{ width: `${pct}%` }} />
      </div>
      <span className="w-24 text-right font-mono text-[11px]">
        {fmtNum(Math.round(p.done))}/{fmtNum(Math.round(p.planned))} {unit}
      </span>
    </div>
  );
}

function DaySummary({ summary }: { summary: ReturnType<typeof summarizeExerciseDay> }) {
  return (
    <Section title="Day summary">
      <div className="space-y-4 text-sm">
        <div>
          <div className="mb-1 flex justify-between font-medium"><span>Total</span></div>
          <ProgressBar p={summary.minutes} unit="min" />
        </div>
        <div className="space-y-1.5">
          {summary.byGroup.filter((g) => g.minutes.planned > 0 || g.minutes.done > 0).map((g) => (
            <div key={g.group} className="grid grid-cols-[90px_1fr] items-center gap-2">
              <span className="text-xs">{GROUP_LABEL[g.group]}</span>
              <ProgressBar p={g.minutes} unit="min" />
            </div>
          ))}
          {summary.byBucket.filter((b) => !["RESISTANCE", "OTHER", "ENDURANCE"].includes(b.bucket)).map((b) => (
            <div key={b.bucket} className="grid grid-cols-[90px_1fr] items-center gap-2 pl-3 text-item">
              <span className="text-xs">{BUCKET_LABEL[b.bucket]}</span>
              <ProgressBar p={b.minutes} unit="min" />
            </div>
          ))}
        </div>
        {summary.muscles.length > 0 && (
          <div>
            <div className="mb-1 text-[10px] font-semibold tracking-wide text-item uppercase">Sets per muscle</div>
            <div className="space-y-1">
              {summary.muscles.map((m) => (
                <div key={m.muscle} className="grid grid-cols-[90px_1fr] items-center gap-2">
                  <span className="truncate text-xs capitalize">{m.muscle.replace(/_/g, " ")}</span>
                  <ProgressBar p={m.sets} unit="sets" />
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </Section>
  );
}
