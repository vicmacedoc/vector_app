// Habit x day table with inline status selectors, plus the habit selection/definition editor.
import { useMemo, useState } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { cn } from "@/lib/cn";
import { todayKey } from "@/domain/dates";
import {
  HABIT_STATUS_CYCLE, createHabitEdit, dayHabitStatuses, definitions, deleteHabitEdits, formatScore, renameHabitEdit,
  scoreSummaries, selectedHabitIds, setHabitStatusEdit, setSelectedHabitsEdits, type ShardEdit,
} from "@/domain/daily/habits";
import { lastNDates } from "@/domain/daily/home";
import type { HabitDefinition, HabitStatus } from "@/domain/daily/models";
import { errorMessage } from "@/platform/native";
import { useBrain } from "@/store/brain";
import { useDaily } from "@/store/daily";
import { Button, Card, Confirm, Dialog, Input } from "@/ui/primitives";
import { HABIT_COLOR, HABIT_TEXT } from "@/screens/calendar/RoutineView";

const DAYS = 7;

const apply = (edits: ShardEdit[]) => {
  const daily = useDaily.getState();
  for (const e of edits) daily.mutate(e.shard, e.fn);
};

export function HabitsTracker({ endDate }: { endDate: string }) {
  const shards = useDaily((s) => s.shards);
  const [editing, setEditing] = useState(false);
  const today = todayKey();
  const dates = useMemo(() => lastNDates(endDate, DAYS), [endDate]);
  const scores = useMemo(() => scoreSummaries(shards), [shards]);
  const byDate = useMemo(
    () => new Map(dates.map((d) => [d, new Map(dayHabitStatuses(shards, d).map((r) => [r.habit.id, r.status]))])),
    [shards, dates],
  );
  const habits = useMemo(() => {
    const ids = new Set(dates.flatMap((d) => [...(byDate.get(d)?.keys() ?? [])]));
    return definitions(shards).filter((h) => ids.has(h.id));
  }, [shards, dates, byDate]);

  const setStatus = (date: string, habitId: string, status: HabitStatus) => apply([setHabitStatusEdit(useDaily.getState().shards, date, habitId, status)]);

  return (
    <Card className="p-4">
      <div className="mb-3 flex items-center justify-between">
        <div className="font-semibold">Habits tracker</div>
        <Button size="sm" variant="ghost" disabled={endDate > today} onClick={() => setEditing(true)}>
          <Pencil size={14} /> Edit habits
        </Button>
      </div>
      {habits.length === 0 ? (
        <div className="py-6 text-center text-sm text-muted">
          No habits tracked this week.{" "}
          {endDate <= today && <button className="text-electric hover:underline" onClick={() => setEditing(true)}>Choose habits</button>}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-separate border-spacing-1 text-sm">
            <thead>
              <tr className="text-[11px] text-item">
                <th className="text-left font-medium">Habit</th>
                {dates.map((d) => (
                  <th key={d} className={cn("font-medium", d === endDate && "text-navy")}>
                    {new Date(`${d}T00:00:00`).toLocaleDateString(undefined, { weekday: "short", day: "numeric" })}
                  </th>
                ))}
                <th className="text-right font-medium">Score</th>
              </tr>
            </thead>
            <tbody>
              {habits.map((h) => {
                const score = scores.get(h.id);
                return (
                  <tr key={h.id}>
                    <td className="max-w-40 truncate pr-2 font-medium" title={h.name}>{h.name}</td>
                    {dates.map((d) => {
                      const status = byDate.get(d)?.get(h.id);
                      if (status === undefined) return <td key={d} className="text-center text-item">·</td>;
                      return (
                        <td key={d}>
                          <select
                            value={status}
                            disabled={d > today}
                            onChange={(e) => setStatus(d, h.id, e.target.value as HabitStatus)}
                            title={`${h.name} · ${d}`}
                            className={cn(
                              "h-7 w-full min-w-16 cursor-pointer appearance-none rounded-md border border-hairline px-1 text-center text-[11px] font-medium outline-none disabled:cursor-default disabled:opacity-50",
                              status === "NONE" ? "text-muted" : "text-white",
                            )}
                            style={{ background: HABIT_COLOR[status] }}
                          >
                            {HABIT_STATUS_CYCLE.map((s) => (
                              <option key={s} value={s} className="bg-white text-black">{HABIT_TEXT[s]}</option>
                            ))}
                          </select>
                        </td>
                      );
                    })}
                    <td className="text-right text-xs whitespace-nowrap text-item tabular-nums">{score ? formatScore(score) : "—"}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      <div className="mt-2 text-[11px] text-item">Status changes save immediately. Partial counts 0.5, Exceeded 1.5, N/A is skipped.</div>
      {editing && <HabitsEditor date={endDate} onClose={() => setEditing(false)} />}
    </Card>
  );
}

function HabitsEditor({ date, onClose }: { date: string; onClose: () => void }) {
  const shards = useDaily((s) => s.shards);
  const toast = useBrain((s) => s.toast);
  const defs = useMemo(() => definitions(shards), [shards]);
  const [selected, setSelected] = useState<string[]>(() => selectedHabitIds(useDaily.getState().shards, date));
  const [name, setName] = useState("");
  const [renaming, setRenaming] = useState<{ habit: HabitDefinition; name: string } | null>(null);
  const [deleting, setDeleting] = useState<HabitDefinition | null>(null);

  const toggle = (id: string) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));

  const create = () => {
    try {
      const { edit, habit } = createHabitEdit(useDaily.getState().shards, name);
      apply([edit]);
      setSelected((s) => [...s, habit.id]);
      setName("");
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const save = () => {
    apply(setSelectedHabitsEdits(useDaily.getState().shards, date, selected));
    toast(`Habits updated from ${date} on`);
    onClose();
  };

  return (
    <Dialog
      open
      onClose={onClose}
      title="Edit habits"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={save}>Save selection</Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="text-xs text-muted">
          Checked habits are tracked from <b>{date}</b> onwards. Unchecking removes that habit's logs from this day on; earlier days keep their history.
        </div>
        <div className="flex gap-2">
          <Input value={name} onChange={(e) => setName(e.target.value)} onKeyDown={(e) => e.key === "Enter" && name.trim() && create()} placeholder="New habit name" />
          <Button variant="secondary" disabled={!name.trim()} onClick={create}><Plus size={15} /> Add</Button>
        </div>
        {defs.length === 0 ? (
          <div className="py-4 text-center text-sm text-muted">No habits yet.</div>
        ) : (
          <div className="divide-y divide-hairline rounded-xl border border-hairline">
            {defs.map((h) => (
              <div key={h.id} className="group flex items-center gap-3 px-3 py-2">
                <input type="checkbox" className="h-4 w-4 accent-navy" checked={selected.includes(h.id)} onChange={() => toggle(h.id)} />
                {renaming?.habit.id === h.id ? (
                  <Input
                    autoFocus
                    className="h-8"
                    value={renaming.name}
                    onChange={(e) => setRenaming({ ...renaming, name: e.target.value })}
                    onBlur={() => {
                      if (renaming.name.trim() && renaming.name.trim() !== h.name) apply([renameHabitEdit(h, renaming.name)]);
                      setRenaming(null);
                    }}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") (e.target as HTMLInputElement).blur();
                      if (e.key === "Escape") setRenaming(null);
                    }}
                  />
                ) : (
                  <button className="flex-1 text-left text-sm" onClick={() => toggle(h.id)}>{h.name}</button>
                )}
                <button title="Rename" className="rounded p-1 text-item opacity-0 group-hover:opacity-100 hover:text-black" onClick={() => setRenaming({ habit: h, name: h.name })}>
                  <Pencil size={14} />
                </button>
                <button title="Delete" className="rounded p-1 text-item opacity-0 group-hover:opacity-100 hover:text-danger" onClick={() => setDeleting(h)}>
                  <Trash2 size={14} />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
      <Confirm
        open={deleting !== null}
        title={`Delete "${deleting?.name}"?`}
        message="The habit and its entire history are deleted on both devices."
        onClose={() => setDeleting(null)}
        onConfirm={() => {
          if (!deleting) return;
          apply(deleteHabitEdits(useDaily.getState().shards, deleting.id));
          setSelected((s) => s.filter((id) => id !== deleting.id));
        }}
      />
    </Dialog>
  );
}
