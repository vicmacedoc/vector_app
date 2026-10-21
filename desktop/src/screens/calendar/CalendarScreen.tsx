// Daily inputs Calendar: date strip, category tabs, Day/Week views, lock, Add, Save/Discard.
import { useEffect, useMemo, useState } from "react";
import { ChevronLeft, ChevronRight, Fingerprint, Lock, LockOpen, Plus, RotateCcw, Save } from "lucide-react";
import { cn } from "@/lib/cn";
import { addDays, daysBetween, formatDay, todayKey } from "@/domain/dates";
import { draftKey, type DraftCategory } from "@/domain/daily/drafts";
import { dayCompletion } from "@/domain/daily/home";
import { mondayOf } from "@/domain/daily/stats";
import { useCalendar } from "@/store/calendar";
import { useDaily } from "@/store/daily";
import { useNav } from "@/store/nav";
import { usePrivacy } from "@/store/privacy";
import { Button, Confirm, Segmented } from "@/ui/primitives";
import { AddFlow } from "./AddFlow";
import { ExerciseView } from "./ExerciseView";
import { FoodView } from "./FoodView";
import { RoutineView } from "./RoutineView";
import { SelfView } from "./SelfView";
import { WeekView } from "./WeekView";

const TABS: { c: DraftCategory; label: string }[] = [
  { c: "ROUTINE", label: "Routine" },
  { c: "FOOD", label: "Food" },
  { c: "EXERCISE", label: "Exercise" },
  { c: "SELF", label: "Self" },
];

function SelfGate({ children }: { children: React.ReactNode }) {
  const { requireHello, unlocked, error, unlock } = usePrivacy();
  if (!requireHello || unlocked) return <>{children}</>;
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-24 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-offwhite text-navy"><Fingerprint size={28} /></div>
      <div className="text-lg font-semibold">Self is locked</div>
      <div className="max-w-sm text-sm text-muted">Your journal is protected with Windows Hello. You can turn this off in Settings.</div>
      <Button onClick={() => void unlock()}><Fingerprint size={16} /> Unlock</Button>
      {error && <div className="text-sm text-danger">{error}</div>}
    </div>
  );
}

const typingTarget = (t: EventTarget | null) =>
  t instanceof HTMLElement && (t.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(t.tagName));

export function CalendarScreen() {
  const date = useNav((s) => s.date);
  const category = useNav((s) => s.category);
  const mode = useNav((s) => s.calendarMode);
  const replace = useNav((s) => s.replace);
  const ready = useDaily((s) => s.ready);
  const shards = useDaily((s) => s.shards);
  const drafts = useDaily((s) => s.drafts);
  const loadedDate = useCalendar((s) => s.date);
  const loaded = useCalendar((s) => s[category] !== null);
  const editing = useCalendar((s) => s.editing);
  const saving = useCalendar((s) => s.saving);
  const cal = useCalendar();
  const [adding, setAdding] = useState(false);
  const [discarding, setDiscarding] = useState(false);
  const today = todayKey();

  useEffect(() => {
    if (ready && (loadedDate !== date || !loaded)) useCalendar.getState().load(date);
  }, [ready, date, loadedDate, loaded]);

  const dirty = (c: DraftCategory) => loadedDate === date && cal.isDirty(c);
  const step = (n: number) => replace({ date: addDays(date, n) });

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.key.toLowerCase() === "s") {
        e.preventDefault();
        if (mode === "day" && useCalendar.getState().isDirty(category)) void useCalendar.getState().save(category);
        return;
      }
      if (typingTarget(e.target) || e.altKey || e.ctrlKey || e.metaKey) return;
      if (e.key === "PageUp" || e.key === "[") step(-1);
      else if (e.key === "PageDown" || e.key === "]") step(1);
      else if (e.key === "t") replace({ date: todayKey() });
      else if (["1", "2", "3", "4"].includes(e.key)) replace({ category: TABS[Number(e.key) - 1].c });
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  const strip = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(mondayOf(date), i)), [date]);
  const draftKeys = useMemo(() => new Set(Object.keys(drafts)), [drafts]);

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="shrink-0 border-b border-hairline bg-white px-8 pt-5">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1">
            <Button size="icon" variant="ghost" title="Previous day ([)" onClick={() => step(mode === "week" ? -7 : -1)}><ChevronLeft size={18} /></Button>
            <Button size="icon" variant="ghost" title="Next day (])" onClick={() => step(mode === "week" ? 7 : 1)}><ChevronRight size={18} /></Button>
          </div>
          <div>
            <h1 className="text-xl font-semibold">{mode === "week" ? `Week of ${formatDay(mondayOf(date))}` : formatDay(date)}</h1>
            {mode === "day" && date !== today && <div className="text-xs text-item">{date < today ? `${daysBetween(date, today)} day(s) ago` : `In ${daysBetween(today, date)} day(s)`}</div>}
          </div>
          <input
            type="date"
            value={date}
            onChange={(e) => e.target.value && replace({ date: e.target.value })}
            className="h-9 rounded-lg border border-hairline px-2 text-sm outline-none focus:border-electric"
          />
          {date !== today && <Button size="sm" variant="secondary" onClick={() => replace({ date: today })}>Today</Button>}
          <div className="ml-auto flex items-center gap-2">
            <Segmented value={mode} onChange={(m) => replace({ calendarMode: m })} options={[{ value: "day", label: "Day" }, { value: "week", label: "Week" }]} />
            {mode === "day" && (
              <>
                <Button
                  size="icon"
                  variant={editing ? "ghost" : "secondary"}
                  title={editing ? "Editing enabled — click to lock" : "Locked — click to edit this day"}
                  onClick={() => cal.setEditing(!editing)}
                >
                  {editing ? <LockOpen size={16} /> : <Lock size={16} className="text-prio-mid" />}
                </Button>
                {category !== "SELF" && (
                  <Button variant="secondary" disabled={!editing} onClick={() => setAdding(true)}><Plus size={16} /> Add</Button>
                )}
                <Button variant="ghost" disabled={!dirty(category)} onClick={() => setDiscarding(true)}><RotateCcw size={15} /> Discard</Button>
                <Button disabled={!dirty(category) || saving !== null} onClick={() => void cal.save(category)}>
                  <Save size={15} /> {saving === category ? "Saving…" : "Save"}
                </Button>
              </>
            )}
          </div>
        </div>

        {mode === "day" && (
          <>
            <div className="mt-4 grid grid-cols-7 gap-2">
              {strip.map((d) => {
                const dt = new Date(`${d}T00:00:00`);
                const comp = dayCompletion(shards, category, d, draftKeys);
                return (
                  <button
                    key={d}
                    onClick={() => replace({ date: d })}
                    className={cn(
                      "flex items-center justify-center gap-2 rounded-lg border py-1.5 text-sm transition",
                      d === date ? "border-navy bg-navy text-white" : "border-hairline hover:border-navy/40",
                      d === today && d !== date && "border-electric text-electric",
                    )}
                  >
                    <span className="text-xs opacity-75">{dt.toLocaleDateString(undefined, { weekday: "short" })}</span>
                    <span className="font-semibold">{dt.getDate()}</span>
                    {comp.hasDraft ? <span className="h-1.5 w-1.5 rounded-full bg-prio-mid" /> : comp.hasData ? <span className={cn("h-1.5 w-1.5 rounded-full", d === date ? "bg-white" : "bg-prio-low")} /> : null}
                  </button>
                );
              })}
            </div>
            <div className="mt-3 flex gap-1">
              {TABS.map((t) => (
                <button
                  key={t.c}
                  onClick={() => replace({ category: t.c })}
                  className={cn(
                    "relative -mb-px flex items-center gap-2 border-b-2 px-4 py-2 text-sm font-medium transition",
                    category === t.c ? "border-navy text-navy" : "border-transparent text-muted hover:text-black",
                  )}
                >
                  {t.label}
                  {(dirty(t.c) || drafts[draftKey(t.c, date)]) && <span className="h-1.5 w-1.5 rounded-full bg-prio-mid" title="Unsaved changes" />}
                </button>
              ))}
              {!editing && <span className="ml-auto self-center text-xs text-item">Locked. Use the lock to edit past or future days.</span>}
            </div>
          </>
        )}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-8 py-6">
        {!ready || (mode === "day" && (loadedDate !== date || !loaded)) ? (
          <div className="text-sm text-muted">Loading…</div>
        ) : mode === "week" ? (
          <WeekView date={date} />
        ) : category === "ROUTINE" ? (
          <RoutineView />
        ) : category === "FOOD" ? (
          <FoodView />
        ) : category === "EXERCISE" ? (
          <ExerciseView />
        ) : (
          <SelfGate><SelfView /></SelfGate>
        )}
      </div>

      {adding && category !== "SELF" && <AddFlow category={category} onClose={() => setAdding(false)} />}
      <Confirm
        open={discarding}
        title="Discard changes?"
        message="Your unsaved edits for this category and day will be lost."
        confirmLabel="Discard"
        onClose={() => setDiscarding(false)}
        onConfirm={() => cal.discard(category)}
      />
    </div>
  );
}
