// Exercise import review (ExerciseImportWizardDialog): edit sessions → exercises → sets/intervals, then confirm.
import { useState } from "react";
import { Copy, Plus, Trash2 } from "lucide-react";
import { cn } from "@/lib/cn";
import {
  EXERCISE_DEFAULTS, EXERCISE_KINDS, emptyDrop, emptyExercise, emptyInterval, emptySet, exerciseDraftError,
  normalizedForImport, sessionsError, type ExerciseDraft, type ExerciseKind, type ExerciseSessionDraft,
} from "@/domain/daily/exerciseDraft";
import { PRIMARY, SECONDARY } from "@/domain/daily/workoutPlan";
import { Button, Dialog, Input, Select, Textarea } from "@/ui/primitives";
import { ErrorLine, Field, NumField, ReplacesDayBanner } from "./ui";

const KIND_LABEL: Record<ExerciseKind, string> = { RESISTANCE: "Resistance", ENDURANCE: "Endurance", OTHER: "Other" };

function UnitSelect({ value, options, onChange }: { value: string; options: readonly string[]; onChange: (v: string) => void }) {
  return (
    <select value={value} onChange={(e) => onChange(e.target.value)} className="h-9 rounded-lg border border-hairline bg-white px-1.5 text-xs outline-none focus:border-electric">
      {options.map((o) => <option key={o} value={o}>{o}</option>)}
    </select>
  );
}

function MuscleTargets({ value, vocabulary, onChange }: { value: Record<string, number>; vocabulary: readonly string[]; onChange: (v: Record<string, number>) => void }) {
  const all = [...new Set([...vocabulary, ...Object.keys(value)])];
  const cycle = (m: string) => {
    const cur = value[m];
    const next = { ...value };
    if (cur === undefined) next[m] = PRIMARY;
    else if (cur >= PRIMARY) next[m] = SECONDARY;
    else delete next[m];
    onChange(next);
  };
  return (
    <div className="flex flex-wrap gap-1.5">
      {all.map((m) => {
        const w = value[m];
        return (
          <button
            key={m}
            type="button"
            onClick={() => cycle(m)}
            title="Click: primary → secondary → off"
            className={cn(
              "rounded-full border px-2 py-0.5 text-[11px] capitalize",
              w === undefined ? "border-hairline text-muted" : w >= PRIMARY ? "border-navy bg-navy text-white" : "border-navy/40 bg-navy/10 text-navy",
            )}
          >
            {m.replace(/_/g, " ")}{w !== undefined && w < PRIMARY ? " ½" : ""}
          </button>
        );
      })}
    </div>
  );
}

function ExerciseEditor({ ex, kind, sport, vocabulary, onChange }: { ex: ExerciseDraft; kind: ExerciseKind; sport: string | null; vocabulary: readonly string[]; onChange: (e: ExerciseDraft) => void }) {
  const set = <K extends keyof ExerciseDraft>(k: K, v: ExerciseDraft[K]) => onChange({ ...ex, [k]: v });
  const err = exerciseDraftError(ex, kind);
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-[1fr_auto] gap-3">
        <Field label="Exercise"><Input className="h-9" value={ex.name} onChange={(e) => set("name", e.target.value)} /></Field>
        {kind === "RESISTANCE" && (
          <Field label="Superset">
            <select value={ex.supersetGroup ?? ""} onChange={(e) => set("supersetGroup", e.target.value || null)} className="h-9 rounded-lg border border-hairline bg-white px-2 text-sm">
              <option value="">—</option>
              {EXERCISE_DEFAULTS.SUPERSET_GROUPS.map((g) => <option key={g} value={g}>{g}</option>)}
            </select>
          </Field>
        )}
      </div>
      <Field label="Description"><Textarea rows={2} value={ex.description} onChange={(e) => set("description", e.target.value)} /></Field>

      {kind === "RESISTANCE" && (
        <>
          <div className="grid grid-cols-[160px_1fr] gap-3">
            <Field label="Tempo"><Input className="h-9" placeholder="3-1-1-0" value={ex.timing ?? ""} onChange={(e) => set("timing", e.target.value || null)} /></Field>
            <Field label="Muscles"><MuscleTargets value={ex.targets} vocabulary={vocabulary} onChange={(t) => set("targets", t)} /></Field>
          </div>
          <div>
            <div className="mb-1 flex items-center justify-between">
              <span className="text-[11px] font-semibold tracking-wide text-muted uppercase">Sets</span>
              <Button size="sm" variant="ghost" onClick={() => set("sets", [...ex.sets, ex.sets.length ? { ...ex.sets[ex.sets.length - 1], drops: [] } : emptySet()])}><Plus size={13} /> Set</Button>
            </div>
            <table className="w-full text-xs">
              <thead className="text-left text-[10px] text-item uppercase">
                <tr><th className="w-8">#</th><th>Load</th><th className="w-16">Unit</th><th>Reps</th><th>RIR</th><th>Duration</th><th className="w-16" /><th>Rest</th><th className="w-16" /><th className="w-20" /></tr>
              </thead>
              <tbody>
                {ex.sets.map((s, i) => {
                  const up = (patch: Partial<typeof s>) => set("sets", ex.sets.map((x, j) => (j === i ? { ...x, ...patch } : x)));
                  return [
                    <tr key={`s${i}`} className="[&>td]:py-0.5 [&>td]:pr-1">
                      <td className="font-mono">{i + 1}</td>
                      <td><NumField value={s.targetLoad} min={0} step={0.5} onChange={(v) => up({ targetLoad: v })} /></td>
                      <td><UnitSelect value={s.unitLoad} options={EXERCISE_DEFAULTS.LOAD_UNITS} onChange={(v) => up({ unitLoad: v })} /></td>
                      <td><NumField value={s.targetReps} integer min={0} onChange={(v) => up({ targetReps: v })} /></td>
                      <td><NumField value={s.targetRir} integer min={0} max={10} onChange={(v) => up({ targetRir: v })} /></td>
                      <td><NumField value={s.targetDuration} integer min={0} onChange={(v) => up({ targetDuration: v })} /></td>
                      <td><UnitSelect value={s.unitDuration} options={["s", "min"]} onChange={(v) => up({ unitDuration: v })} /></td>
                      <td><NumField value={s.rest} integer min={0} onChange={(v) => up({ rest: v })} /></td>
                      <td><UnitSelect value={s.restUnit} options={EXERCISE_DEFAULTS.REST_UNITS} onChange={(v) => up({ restUnit: v })} /></td>
                      <td className="whitespace-nowrap">
                        <Button size="icon" variant="ghost" className="h-7 w-7" title="Add drop" onClick={() => up({ drops: [...s.drops, emptyDrop()] })}><Plus size={12} /></Button>
                        <Button size="icon" variant="ghost" className="h-7 w-7 text-danger" title="Remove set" onClick={() => set("sets", ex.sets.filter((_, j) => j !== i))}><Trash2 size={12} /></Button>
                      </td>
                    </tr>,
                    ...s.drops.map((d, k) => {
                      const upd = (patch: Partial<typeof d>) => up({ drops: s.drops.map((x, j) => (j === k ? { ...x, ...patch } : x)) });
                      return (
                        <tr key={`s${i}d${k}`} className="text-prio-mid [&>td]:py-0.5 [&>td]:pr-1">
                          <td className="text-[10px]">↳{k + 1}</td>
                          <td><NumField value={d.targetLoad} min={0} step={0.5} onChange={(v) => upd({ targetLoad: v })} /></td>
                          <td><UnitSelect value={d.unitLoad ?? s.unitLoad} options={EXERCISE_DEFAULTS.LOAD_UNITS} onChange={(v) => upd({ unitLoad: v })} /></td>
                          <td><NumField value={d.targetReps} integer min={0} onChange={(v) => upd({ targetReps: v })} /></td>
                          <td><NumField value={d.targetRir} integer min={0} max={10} onChange={(v) => upd({ targetRir: v })} /></td>
                          <td colSpan={4} className="text-[10px] text-item">drop set</td>
                          <td><Button size="icon" variant="ghost" className="h-7 w-7 text-danger" onClick={() => up({ drops: s.drops.filter((_, j) => j !== k) })}><Trash2 size={12} /></Button></td>
                        </tr>
                      );
                    }),
                  ];
                })}
              </tbody>
            </table>
          </div>
        </>
      )}

      {kind === "ENDURANCE" && (
        <>
          {sport === "SWIM" && (
            <Field label="Stroke" className="w-40">
              <select value={ex.stroke ?? ""} onChange={(e) => set("stroke", e.target.value || null)} className="h-9 rounded-lg border border-hairline bg-white px-2 text-sm">
                <option value="">—</option>
                {EXERCISE_DEFAULTS.STROKES.map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
            </Field>
          )}
          <div>
            <div className="mb-1 flex items-center justify-between">
              <span className="text-[11px] font-semibold tracking-wide text-muted uppercase">Intervals</span>
              <Button size="sm" variant="ghost" onClick={() => set("intervals", [...ex.intervals, ex.intervals.length ? { ...ex.intervals[ex.intervals.length - 1] } : emptyInterval()])}><Plus size={13} /> Interval</Button>
            </div>
            <table className="w-full text-xs">
              <thead className="text-left text-[10px] text-item uppercase">
                <tr><th className="w-8">#</th><th>Duration</th><th className="w-16" /><th>Distance</th><th className="w-16" /><th>Zone</th><th>RPE</th><th>Rest</th><th className="w-16" /><th className="w-8" /></tr>
              </thead>
              <tbody>
                {ex.intervals.map((iv, i) => {
                  const up = (patch: Partial<typeof iv>) => set("intervals", ex.intervals.map((x, j) => (j === i ? { ...x, ...patch } : x)));
                  return (
                    <tr key={i} className="[&>td]:py-0.5 [&>td]:pr-1">
                      <td className="font-mono">{i + 1}</td>
                      <td><NumField value={iv.targetDuration} integer min={0} onChange={(v) => up({ targetDuration: v })} /></td>
                      <td><UnitSelect value={iv.unitDuration} options={EXERCISE_DEFAULTS.DURATION_UNITS} onChange={(v) => up({ unitDuration: v })} /></td>
                      <td><NumField value={iv.targetDistance} min={0} step={0.1} onChange={(v) => up({ targetDistance: v })} /></td>
                      <td><UnitSelect value={iv.unitDistance} options={EXERCISE_DEFAULTS.DISTANCE_UNITS} onChange={(v) => up({ unitDistance: v })} /></td>
                      <td><NumField value={iv.targetZone} integer min={1} max={5} onChange={(v) => up({ targetZone: v })} /></td>
                      <td><NumField value={iv.targetRpe} min={0} max={10} step={0.5} onChange={(v) => up({ targetRpe: v })} /></td>
                      <td><NumField value={iv.rest} integer min={0} onChange={(v) => up({ rest: v })} /></td>
                      <td><UnitSelect value={iv.restUnit} options={EXERCISE_DEFAULTS.REST_UNITS} onChange={(v) => up({ restUnit: v })} /></td>
                      <td><Button size="icon" variant="ghost" className="h-7 w-7 text-danger" onClick={() => set("intervals", ex.intervals.filter((_, j) => j !== i))}><Trash2 size={12} /></Button></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </>
      )}

      {kind === "OTHER" && (
        <div className="grid grid-cols-4 gap-3">
          <Field label="Total duration"><NumField value={ex.totalDuration} min={0} onChange={(v) => set("totalDuration", v)} /></Field>
          <Field label="Unit"><UnitSelect value={ex.unitDuration} options={EXERCISE_DEFAULTS.DURATION_UNITS} onChange={(v) => set("unitDuration", v)} /></Field>
          <Field label="Rest"><NumField value={ex.rest} integer min={0} onChange={(v) => set("rest", v)} /></Field>
          <Field label="Rest unit"><UnitSelect value={ex.restUnit} options={EXERCISE_DEFAULTS.REST_UNITS} onChange={(v) => set("restUnit", v)} /></Field>
        </div>
      )}
      {err && <div className="text-xs text-prio-mid">{err}</div>}
    </div>
  );
}

export function ExerciseReview({
  initial, sourceLabel, replacesDay, vocabulary = EXERCISE_DEFAULTS.MUSCLES, onClose, onConfirm,
}: {
  initial: ExerciseSessionDraft[];
  sourceLabel: string;
  replacesDay: boolean;
  vocabulary?: readonly string[];
  onClose: () => void;
  onConfirm: (sessions: ExerciseSessionDraft[]) => string | null;
}) {
  const [sessions, setSessions] = useState(initial);
  const [sel, setSel] = useState<{ s: number; e: number | null }>({ s: 0, e: initial[0]?.exercises.length ? 0 : null });
  const [error, setError] = useState<string | null>(null);
  const session = sessions[sel.s];
  const exercise = session && sel.e !== null ? session.exercises[sel.e] : null;
  const upSession = (i: number, patch: Partial<ExerciseSessionDraft>) => setSessions((all) => all.map((s, j) => (j === i ? { ...s, ...patch } : s)));
  const upExercise = (si: number, ei: number, ex: ExerciseDraft) =>
    setSessions((all) => all.map((s, j) => (j === si ? { ...s, exercises: s.exercises.map((x, k) => (k === ei ? ex : x)) } : s)));

  const confirm = () => {
    const normalized = normalizedForImport(sessions);
    const err = sessionsError(normalized);
    if (err) return setError(err);
    const result = onConfirm(normalized);
    if (result) setError(result);
  };

  return (
    <Dialog
      open
      onClose={onClose}
      title={`Review sessions · ${sourceLabel}`}
      width="max-w-6xl"
      dismissable={false}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={confirm}>{replacesDay ? "Replace day" : `Add ${sessions.length} session(s)`}</Button>
        </>
      }
    >
      <div className="space-y-3">
        {replacesDay && <ReplacesDayBanner>Confirming replaces every session on this day, preset sessions included.</ReplacesDayBanner>}
        <div className="grid h-[62vh] grid-cols-[260px_1fr] gap-4">
          <div className="space-y-2 overflow-y-auto border-r border-hairline pr-3">
            {sessions.map((s, si) => (
              <div key={si}>
                <button
                  onClick={() => setSel({ s: si, e: null })}
                  className={cn("w-full rounded-lg px-2 py-1.5 text-left text-sm font-semibold", sel.s === si && sel.e === null ? "bg-navy text-white" : "hover:bg-offwhite")}
                >
                  {s.title || "Untitled session"}
                  <span className="ml-1 text-[10px] font-normal opacity-70">{KIND_LABEL[s.kind]}</span>
                </button>
                <div className="ml-3 space-y-0.5 border-l border-hairline pl-2">
                  {s.exercises.map((e, ei) => (
                    <button
                      key={ei}
                      onClick={() => setSel({ s: si, e: ei })}
                      className={cn(
                        "flex w-full items-center gap-1 rounded px-2 py-1 text-left text-[13px]",
                        sel.s === si && sel.e === ei ? "bg-electric/10 text-electric" : "hover:bg-offwhite",
                      )}
                    >
                      <span className="flex-1 truncate">{e.name || "Unnamed"}</span>
                      {exerciseDraftError(e, s.kind) && <span className="h-1.5 w-1.5 rounded-full bg-prio-mid" />}
                    </button>
                  ))}
                  <button
                    className="flex items-center gap-1 px-2 py-1 text-xs text-electric"
                    onClick={() => {
                      const ex = { ...emptyExercise(), sets: s.kind === "RESISTANCE" ? [emptySet()] : [], intervals: s.kind === "ENDURANCE" ? [emptyInterval()] : [] };
                      upSession(si, { exercises: [...s.exercises, ex] });
                      setSel({ s: si, e: s.exercises.length });
                    }}
                  >
                    <Plus size={12} /> Exercise
                  </button>
                </div>
              </div>
            ))}
            <button
              className="flex items-center gap-1 px-2 py-1 text-xs text-electric"
              onClick={() => {
                setSessions((all) => [...all, { title: "", description: "", kind: "RESISTANCE", sport: null, exercises: [{ ...emptyExercise(), sets: [emptySet()] }] }]);
                setSel({ s: sessions.length, e: null });
              }}
            >
              <Plus size={12} /> Session
            </button>
          </div>

          <div className="min-w-0 overflow-y-auto pr-1">
            {session && sel.e === null && (
              <div className="space-y-4">
                <div className="grid grid-cols-[1fr_160px_140px] gap-3">
                  <Field label="Session title"><Input className="h-9" value={session.title} onChange={(e) => upSession(sel.s, { title: e.target.value })} /></Field>
                  <Field label="Kind">
                    <Select value={session.kind} className="h-9" options={EXERCISE_KINDS.map((k) => ({ value: k, label: KIND_LABEL[k] }))} onChange={(kind) => upSession(sel.s, { kind })} />
                  </Field>
                  {session.kind === "ENDURANCE" && (
                    <Field label="Sport">
                      <select value={session.sport ?? ""} onChange={(e) => upSession(sel.s, { sport: e.target.value || null })} className="h-9 rounded-lg border border-hairline bg-white px-2 text-sm">
                        <option value="">Other</option>
                        {EXERCISE_DEFAULTS.SPORTS.map((s) => <option key={s} value={s}>{s}</option>)}
                      </select>
                    </Field>
                  )}
                </div>
                <Field label="Description"><Textarea rows={3} value={session.description} onChange={(e) => upSession(sel.s, { description: e.target.value })} /></Field>
                <div className="flex gap-2">
                  <Button size="sm" variant="secondary" onClick={() => {
                    setSessions((all) => [...all.slice(0, sel.s + 1), structuredClone(session), ...all.slice(sel.s + 1)]);
                  }}><Copy size={13} /> Duplicate</Button>
                  <Button size="sm" variant="ghost" className="text-danger" disabled={sessions.length <= 1} onClick={() => {
                    setSessions((all) => all.filter((_, j) => j !== sel.s));
                    setSel({ s: Math.max(0, sel.s - 1), e: null });
                  }}><Trash2 size={13} /> Remove session</Button>
                </div>
              </div>
            )}
            {session && exercise && sel.e !== null && (
              <div className="space-y-3">
                <ExerciseEditor ex={exercise} kind={session.kind} sport={session.sport} vocabulary={vocabulary} onChange={(ex) => upExercise(sel.s, sel.e!, ex)} />
                <Button size="sm" variant="ghost" className="text-danger" onClick={() => {
                  upSession(sel.s, { exercises: session.exercises.filter((_, k) => k !== sel.e) });
                  setSel({ s: sel.s, e: null });
                }}><Trash2 size={13} /> Remove exercise</Button>
              </div>
            )}
          </div>
        </div>
        <ErrorLine>{error}</ErrorLine>
      </div>
    </Dialog>
  );
}
