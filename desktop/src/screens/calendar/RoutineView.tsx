// Routine day: sleep strip, activities (list or 24h timeline), behaviour donut and habits.
import { useMemo, useState } from "react";
import { Clock, Copy, List, Pencil, Rows3, SlidersHorizontal } from "lucide-react";
import { cn } from "@/lib/cn";
import { activityToClipboard, effectiveEnd, effectiveStart, isAdjusted, sortActivities } from "@/domain/daily/activity";
import { labelsWithPreset, sharedSettings } from "@/domain/daily/day";
import type { RoutineDraft } from "@/domain/daily/drafts";
import { HABIT_STATUS_CYCLE, dayHabitStatuses, setHabitStatusEdit } from "@/domain/daily/habits";
import type { ActivityEntry, HabitStatus } from "@/domain/daily/models";
import { MINUTES_PER_DAY, parseActivityHhMm } from "@/domain/daily/time";
import { useBrain } from "@/store/brain";
import { useCalendar } from "@/store/calendar";
import { useDaily } from "@/store/daily";
import { useNav } from "@/store/nav";
import { Button, Empty, Segmented } from "@/ui/primitives";
import { ActivityDialog, AdjustDialog, CompletionPicker, TypeSwitch } from "./ActivityDialog";
import { BehaviorDonut, sleepArcs } from "./Donut";
import { SleepPanel } from "./SleepPanel";
import { LabelChip, Section, argbCss, copyText } from "./ui";

export function RoutineView() {
  const slot = useCalendar((s) => s.ROUTINE);
  const editing = useCalendar((s) => s.editing);
  const update = useCalendar((s) => s.update);
  const shards = useDaily((s) => s.shards);
  const labels = useMemo(() => labelsWithPreset(shards), [shards]);
  const settings = useMemo(() => sharedSettings(shards), [shards]);
  const [mode, setMode] = useState<"list" | "timeline">("list");
  const [editingEntry, setEditingEntry] = useState<ActivityEntry | null>(null);
  const [adjusting, setAdjusting] = useState<ActivityEntry | null>(null);

  if (!slot) return null;
  const cur = slot.current;
  const bed = settings.sleepTargetBedtimeMinutes ?? 22 * 60;
  const wake = settings.sleepTargetWakeupMinutes ?? 6 * 60;
  const edit = (fn: (d: RoutineDraft) => RoutineDraft) => update("ROUTINE", fn);
  const replace = (a: ActivityEntry) => edit((d) => ({ ...d, activities: sortActivities(d.activities.map((x) => (x.id === a.id ? a : x))) }));
  const remove = (id: string) => edit((d) => ({ ...d, activities: d.activities.filter((x) => x.id !== id) }));

  return (
    <div className="grid grid-cols-[minmax(0,1fr)_340px] gap-6 2xl:grid-cols-[minmax(0,1fr)_380px]">
      <div className="min-w-0 space-y-6">
        <Section title="Sleep">
          <SleepPanel
            previousNight={cur.previousNight}
            tonight={cur.tonight}
            bedDefault={bed}
            wakeDefault={wake}
            enabled={editing}
            onChange={(which, next) => edit((d) => ({ ...d, [which]: next }))}
          />
        </Section>

        <Section
          title={`Activities · ${cur.activities.length}`}
          actions={
            <Segmented
              value={mode}
              onChange={setMode}
              options={[
                { value: "list", label: <span className="inline-flex items-center gap-1"><List size={14} /> List</span> },
                { value: "timeline", label: <span className="inline-flex items-center gap-1"><Rows3 size={14} /> Timeline</span> },
              ]}
            />
          }
        >
          {slot.presetError && <div className="mb-3 rounded-lg bg-danger/10 px-3 py-2 text-xs text-danger">{slot.presetError}</div>}
          {cur.activities.length === 0 ? (
            <Empty
              icon={<Clock size={22} />}
              title="No activities"
              hint={slot.noPreset ? "No routine preset yet. Load one in Settings, or add activities with the Add button." : "Use Add to plan this day."}
            />
          ) : mode === "list" ? (
            <div className="space-y-2">
              {cur.activities.map((a) => (
                <ActivityRow
                  key={a.id}
                  entry={a}
                  labels={labels}
                  editing={editing}
                  onChange={replace}
                  onEdit={() => setEditingEntry(a)}
                  onAdjust={() => setAdjusting(a)}
                />
              ))}
            </div>
          ) : (
            <Timeline activities={cur.activities} labels={labels} onOpen={(a) => editing && setEditingEntry(a)} />
          )}
        </Section>
      </div>

      <div className="space-y-6">
        <Section title="24h activities">
          <BehaviorDonut
            activities={cur.activities}
            labels={labels}
            plannedSleep={sleepArcs(cur.previousNight, cur.tonight, bed, wake, false)}
            doneSleep={sleepArcs(cur.previousNight, cur.tonight, bed, wake, true)}
          />
        </Section>
        <HabitsCard date={slot.date} editing={editing} />
      </div>

      {editingEntry && (
        <ActivityDialog
          entry={editingEntry}
          labels={labels}
          date={slot.date}
          onClose={() => setEditingEntry(null)}
          onSave={(a) => {
            replace(a);
            setEditingEntry(null);
          }}
          onDelete={() => {
            remove(editingEntry.id);
            setEditingEntry(null);
          }}
        />
      )}
      {adjusting && (
        <AdjustDialog
          entry={adjusting}
          onClose={() => setAdjusting(null)}
          onSave={(a) => {
            replace(a);
            setAdjusting(null);
          }}
        />
      )}
    </div>
  );
}

function ActivityRow({
  entry, labels, editing, onChange, onEdit, onAdjust,
}: {
  entry: ActivityEntry;
  labels: ReturnType<typeof labelsWithPreset>;
  editing: boolean;
  onChange: (a: ActivityEntry) => void;
  onEdit: () => void;
  onAdjust: () => void;
}) {
  const [open, setOpen] = useState(false);
  const label = labels.find((l) => l.id === entry.labelId);
  const toast = useBrain((s) => s.toast);
  return (
    <div
      className="group flex items-stretch gap-3 rounded-lg border border-hairline bg-white px-3 py-2.5 hover:border-black/20"
      onDoubleClick={() => editing && onEdit()}
    >
      <div className="w-1 shrink-0 rounded-full" style={{ background: label ? argbCss(label.colorArgb) : "#90a4ae" }} />
      <div className="w-28 shrink-0 font-mono text-[13px]">
        {isAdjusted(entry) ? (
          <>
            <div className="text-item line-through">{entry.startTime}–{entry.endTime}</div>
            <div className="font-semibold">{effectiveStart(entry)}–{effectiveEnd(entry)}</div>
          </>
        ) : (
          <div>{entry.startTime}–{entry.endTime}</div>
        )}
      </div>
      <div className="min-w-0 flex-1 cursor-pointer" onClick={() => setOpen((o) => !o)}>
        <div className="truncate text-sm font-semibold">{entry.title || "(Untitled)"}</div>
        {open && entry.description && <div className="mt-1 text-[13px] whitespace-pre-wrap text-muted">{entry.description}</div>}
        {!open && entry.description && <div className="truncate text-xs text-item">{entry.description}</div>}
      </div>
      <div className="flex shrink-0 items-center gap-2">
        <TypeSwitch value={entry.type} disabled={!editing} onChange={(type) => onChange({ ...entry, type, timestamp: Date.now() })} />
        <LabelChip label={label} small />
        <CompletionPicker value={entry.completion} disabled={!editing} onChange={(completion) => onChange({ ...entry, completion, timestamp: Date.now() })} />
        <div className="flex opacity-0 transition group-hover:opacity-100">
          <Button size="icon" variant="ghost" className="h-7 w-7" title="Copy as JSON" onClick={async () => {
            if (await copyText(JSON.stringify(activityToClipboard(entry, labels), null, 2))) toast("Copied activity");
          }}>
            <Copy size={14} />
          </Button>
          {editing && (
            <>
              <Button size="icon" variant="ghost" className="h-7 w-7" title="Adjust actual time" onClick={onAdjust}><SlidersHorizontal size={14} /></Button>
              <Button size="icon" variant="ghost" className="h-7 w-7" title="Edit" onClick={onEdit}><Pencil size={14} /></Button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

const HOUR_PX = 44;

function Timeline({ activities, labels, onOpen }: { activities: ActivityEntry[]; labels: ReturnType<typeof labelsWithPreset>; onOpen: (a: ActivityEntry) => void }) {
  const blocks = useMemo(() => {
    const items: { a: ActivityEntry; s: number; e: number; lane: number }[] = [];
    for (const a of activities) {
      const s = parseActivityHhMm(effectiveStart(a));
      const e = parseActivityHhMm(effectiveEnd(a));
      if (s === null || e === null) continue;
      if (e > s) items.push({ a, s, e, lane: 0 });
      else if (e < s) {
        items.push({ a, s, e: MINUTES_PER_DAY, lane: 0 });
        if (e > 0) items.push({ a, s: 0, e, lane: 0 });
      }
    }
    items.sort((x, y) => x.s - y.s || y.e - x.e);
    const laneEnds: number[] = [];
    for (const it of items) {
      let lane = laneEnds.findIndex((end) => end <= it.s);
      if (lane < 0) lane = laneEnds.length;
      laneEnds[lane] = it.e;
      it.lane = lane;
    }
    return { items, lanes: Math.max(1, laneEnds.length) };
  }, [activities]);

  return (
    <div className="relative overflow-hidden rounded-lg border border-hairline" style={{ height: 24 * HOUR_PX }}>
      {Array.from({ length: 24 }, (_, h) => (
        <div key={h} className="absolute right-0 left-0 border-t border-hairline text-[10px] text-item" style={{ top: h * HOUR_PX }}>
          <span className="ml-1">{String(h).padStart(2, "0")}:00</span>
        </div>
      ))}
      <div className="absolute top-0 right-2 bottom-0 left-12">
        {blocks.items.map(({ a, s, e, lane }, i) => {
          const l = labels.find((x) => x.id === a.labelId);
          const w = 100 / blocks.lanes;
          return (
            <button
              key={`${a.id}_${i}`}
              onClick={() => onOpen(a)}
              className={cn(
                "absolute overflow-hidden rounded-md border border-white px-2 py-1 text-left text-[11px] text-white shadow-sm",
                a.completion === "DONE" && "ring-2 ring-prio-low ring-inset",
                a.completion === "NOT_DONE" && "opacity-60",
              )}
              style={{
                top: (s / 60) * HOUR_PX,
                height: Math.max(16, ((e - s) / 60) * HOUR_PX - 1),
                left: `${lane * w}%`,
                width: `${w}%`,
                background: l ? argbCss(l.colorArgb) : "#90a4ae",
              }}
              title={`${effectiveStart(a)}–${effectiveEnd(a)} ${a.title}`}
            >
              <div className="truncate font-semibold">{a.title}</div>
              {e - s >= 45 && <div className="truncate opacity-85">{effectiveStart(a)}–{effectiveEnd(a)}</div>}
            </button>
          );
        })}
      </div>
    </div>
  );
}

const HABIT_COLOR: Record<HabitStatus, string> = {
  NONE: "#f8f9fa", NOT_DONE: "#d32f2f", PARTIAL: "#ff9800", DONE: "#4caf50", EXCEEDED: "#0074d9", NA: "#9e9e9e",
};
const HABIT_TEXT: Record<HabitStatus, string> = {
  NONE: "None", NOT_DONE: "Not done", PARTIAL: "Partial", DONE: "Done", EXCEEDED: "Exceeded", NA: "N/A",
};

function HabitsCard({ date, editing }: { date: string; editing: boolean }) {
  const shards = useDaily((s) => s.shards);
  const rows = useMemo(() => dayHabitStatuses(shards, date), [shards, date]);
  const set = (habitId: string, status: HabitStatus) => {
    const e = setHabitStatusEdit(useDaily.getState().shards, date, habitId, status);
    useDaily.getState().mutate(e.shard, e.fn);
  };
  return (
    <Section
      title="Habits"
      actions={<Button size="sm" variant="ghost" onClick={() => useNav.getState().go("home")}>Manage</Button>}
    >
      {rows.length === 0 ? (
        <div className="text-sm text-muted">No habits selected for this day. Pick them on Home.</div>
      ) : (
        <div className="space-y-1.5">
          {rows.map(({ habit, status }) => (
            <div key={habit.id} className="flex items-center gap-2">
              <span className="flex-1 truncate text-sm">{habit.name}</span>
              <select
                value={status}
                disabled={!editing}
                onChange={(e) => set(habit.id, e.target.value as HabitStatus)}
                className={cn("h-7 rounded-full px-3 text-[11px] font-semibold outline-none", status === "NONE" ? "border border-black/25" : "text-white")}
                style={{ background: HABIT_COLOR[status] }}
              >
                {HABIT_STATUS_CYCLE.map((s) => (
                  <option key={s} value={s} className="bg-white text-black">{HABIT_TEXT[s]}</option>
                ))}
              </select>
            </div>
          ))}
        </div>
      )}
      <div className="mt-2 text-[11px] text-item">Habit changes save immediately.</div>
    </Section>
  );
}

export { HABIT_COLOR, HABIT_TEXT };
