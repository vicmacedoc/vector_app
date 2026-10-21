// Everything behind the Calendar "Add" button for Routine / Food / Exercise (CalendarAddFlowHost).
import { useMemo, useState, type ReactNode } from "react";
import {
  CalendarClock, CalendarDays, ClipboardPaste, Library, ListPlus, PencilLine, Plus, Sparkles, Trash2,
} from "lucide-react";
import { cn } from "@/lib/cn";
import { aiActivities, aiExercises, aiMeals } from "@/ai/calendar";
import {
  activityDraftError, activityDraftToEntry, fallbackLabelId, libraryToActivityDraft, mergeActivityDraft,
  parseActivityClipboard, sortActivities, type ActivityDraft,
} from "@/domain/daily/activity";
import type { CalendarAiMode } from "@/domain/daily/clipboard";
import { isPlanMode } from "@/domain/daily/clipboard";
import { labelsWithPreset } from "@/domain/daily/day";
import type { DraftCategory } from "@/domain/daily/drafts";
import { EXERCISE_DEFAULTS, draftToSession, emptyExercise, emptySet, sessionToDraft, type ExerciseSessionDraft } from "@/domain/daily/exerciseDraft";
import { mealDraftError, mealDraftToEntry, parseMealClipboard, type MealDraft } from "@/domain/daily/food";
import { deleteLibraryItem, upsertActivity, upsertExerciseSessions, upsertMeal } from "@/domain/daily/library";
import { normActivityLib, normExerciseLib, normMealLib, type ActivityEntry, type ActivityLabel } from "@/domain/daily/models";
import { configRecords } from "@/domain/daily/repo";
import { normalizeMealTimeOrBlank, nowHhMm } from "@/domain/daily/time";
import { addedSessionRows, decodeLibrarySession, parseClipboardSessions, plannedDayRows } from "@/domain/daily/workoutPlan";
import { calendarDayDrafts } from "@/drive/gcal";
import { errorMessage } from "@/platform/native";
import { useBrain } from "@/store/brain";
import { editLibrary, useCalendar } from "@/store/calendar";
import { useDaily } from "@/store/daily";
import { K } from "@/sync/shards";
import { Button, Confirm, Dialog, SearchInput, Textarea } from "@/ui/primitives";
import { ActivityDialog, TypeSwitch } from "./ActivityDialog";
import { exerciseAiContext, exerciseContext, foodContext, routineContext } from "./context";
import { ExerciseReview } from "./ExerciseReview";
import { ImageDrop } from "./ImageDrop";
import { MealDialog, MealForm } from "./MealDialog";
import { ErrorLine, ReplacesDayBanner, TimeField, readClipboardText } from "./ui";

type AddCategory = Exclude<DraftCategory, "SELF">;

type Step =
  | { k: "menu" }
  | { k: "paste"; mode: CalendarAiMode; text: string; error: string | null }
  | { k: "ai"; mode: CalendarAiMode }
  | { k: "library" }
  | { k: "activityForm"; prefill: ActivityDraft | null }
  | { k: "activityReview"; drafts: ActivityDraft[]; replaces: boolean }
  | { k: "mealForm"; prefill: MealDraft | null }
  | { k: "mealReview"; drafts: MealDraft[]; source: string; replaces: boolean }
  | { k: "exerciseReview"; sessions: ExerciseSessionDraft[]; source: string; replaces: boolean }
  | { k: "error"; message: string };

const NOUN: Record<AddCategory, [string, string]> = {
  ROUTINE: ["activity", "activities"],
  FOOD: ["meal", "meals"],
  EXERCISE: ["session", "sessions"],
};

const AI_TITLE: Record<CalendarAiMode, string> = {
  SINGLE: "Add one with AI",
  MULTIPLE: "Add multiple with AI",
  PLAN_SCRATCH: "Plan day from scratch",
  PLAN_PRESET: "Plan day from preset",
};

function MenuRow({ icon, title, subtitle, onClick, disabled }: { icon: ReactNode; title: string; subtitle: string; onClick: () => void; disabled?: boolean }) {
  return (
    <button
      disabled={disabled}
      onClick={onClick}
      className="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left hover:bg-offwhite disabled:pointer-events-none disabled:opacity-40"
    >
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-navy text-white">{icon}</span>
      <span className="min-w-0">
        <span className="block text-sm font-medium">{title}</span>
        <span className="block text-xs text-item">{subtitle}</span>
      </span>
    </button>
  );
}

export function AddFlow({ category, onClose }: { category: AddCategory; onClose: () => void }) {
  const [step, setStep] = useState<Step>({ k: "menu" });
  const shards = useDaily((s) => s.shards);
  const slotDate = useCalendar((s) => s.date);
  const routine = useCalendar((s) => s.ROUTINE);
  const food = useCalendar((s) => s.FOOD);
  const exercise = useCalendar((s) => s.EXERCISE);
  const update = useCalendar((s) => s.update);
  const toast = useBrain((s) => s.toast);
  const labels = useMemo(() => labelsWithPreset(shards), [shards]);
  const [noun, nouns] = NOUN[category];
  const date = slotDate;
  const presetAvailable = category === "ROUTINE" ? !routine?.noPreset : category === "FOOD" ? !food?.noPreset : !exercise?.noPreset;

  // ---- apply ----

  const rememberActivities = (entries: ActivityEntry[]) => {
    const names = new Map(labels.map((l) => [l.id, l.name]));
    editLibrary((r) =>
      entries.reduce((acc, e) => upsertActivity(acc, {
        title: e.title, startTime: e.adjustedStartTime ?? e.startTime, endTime: e.adjustedEndTime ?? e.endTime,
        labelName: names.get(e.labelId) ?? "", type: e.type, description: e.description,
      }), r),
    );
  };

  const addActivities = (entries: ActivityEntry[], replaces: boolean) => {
    if (!entries.length) return;
    update("ROUTINE", (d) => {
      const ids = new Set(entries.map((e) => e.id));
      return { ...d, activities: sortActivities(replaces ? entries : [...d.activities.filter((a) => !ids.has(a.id)), ...entries]) };
    });
    rememberActivities(entries);
    toast(replaces ? `Day replaced with ${entries.length} activities. Save to keep it.` : `Added ${entries.length} ${entries.length === 1 ? "activity" : "activities"}. Save to keep it.`);
    onClose();
  };

  const addMeals = (drafts: MealDraft[], replaces: boolean) => {
    const entries = drafts.map((d) => mealDraftToEntry({ ...d, time: normalizeMealTimeOrBlank(d.time) || nowHhMm() }, date));
    update("FOOD", (d) => ({ entries: replaces ? entries : [...d.entries, ...entries] }));
    editLibrary((r) => drafts.reduce((acc, d) => upsertMeal(acc, d), r));
    toast(replaces ? `Day replaced with ${entries.length} food rows. Save to keep it.` : `Added ${entries.length} food row(s). Save to keep it.`);
    onClose();
  };

  const addSessions = (drafts: ExerciseSessionDraft[], replaces: boolean): string | null => {
    const sessions = drafts.map(draftToSession);
    try {
      const current = useCalendar.getState().EXERCISE?.current.sets ?? [];
      const rows = replaces ? plannedDayRows(date, sessions) : addedSessionRows(date, sessions, current);
      update("EXERCISE", (d) => ({ sets: replaces ? rows : [...d.sets, ...rows] }));
    } catch (e) {
      return errorMessage(e);
    }
    editLibrary((r) => upsertExerciseSessions(r, sessions));
    const n = sessions.reduce((t, s) => t + s.exercises.length, 0);
    toast(replaces ? `Day replaced with ${sessions.length} session(s). Save to keep it.` : `Added ${n} exercise(s). Save to keep it.`);
    onClose();
    return null;
  };

  // ---- sources ----

  const parseText = (text: string, mode: CalendarAiMode) => {
    try {
      if (category === "ROUTINE") {
        const drafts = parseActivityClipboard(text, mode).map((d) => mergeActivityDraft(d, labels));
        setStep(mode === "SINGLE" ? { k: "activityForm", prefill: drafts[0] } : { k: "activityReview", drafts, replaces: false });
      } else if (category === "FOOD") {
        const drafts = parseMealClipboard(text, mode);
        setStep(mode === "SINGLE" ? { k: "mealForm", prefill: drafts[0] } : { k: "mealReview", drafts, source: "Clipboard", replaces: false });
      } else {
        const sessions = parseClipboardSessions(text, mode, EXERCISE_DEFAULTS.LOAD_UNIT).map(sessionToDraft);
        setStep({ k: "exerciseReview", sessions, source: "Clipboard", replaces: false });
      }
    } catch (e) {
      setStep({ k: "paste", mode, text, error: errorMessage(e) });
    }
  };

  const fromClipboard = async (mode: CalendarAiMode) => {
    const text = await readClipboardText();
    if (!text.trim()) setStep({ k: "paste", mode, text: "", error: null });
    else parseText(text, mode);
  };

  const fromGoogleCalendar = async () => {
    try {
      const drafts = (await calendarDayDrafts(date)).map((d) => mergeActivityDraft(d, labels));
      if (!drafts.length) return setStep({ k: "error", message: "No events on this day in Google Calendar." });
      setStep({ k: "activityReview", drafts, replaces: false });
    } catch (e) {
      setStep({ k: "error", message: errorMessage(e) });
    }
  };

  const newSingle = () => {
    if (category === "ROUTINE") setStep({ k: "activityForm", prefill: null });
    else if (category === "FOOD") setStep({ k: "mealForm", prefill: null });
    else setStep({ k: "exerciseReview", sessions: [{ title: "", description: "", kind: "RESISTANCE", sport: null, exercises: [{ ...emptyExercise(), sets: [emptySet()] }] }], source: "New", replaces: false });
  };

  // ---- render ----

  switch (step.k) {
    case "menu":
      return (
        <Dialog open onClose={onClose} title={`Edit ${category === "ROUTINE" ? "routine" : category === "FOOD" ? "food" : "workout"}`} width="max-w-3xl">
          <div className="grid grid-cols-3 gap-5">
            <div>
              <div className="mb-2 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-muted uppercase"><Plus size={13} /> Add one {noun}</div>
              <MenuRow icon={<ClipboardPaste size={16} />} title="Clipboard" subtitle="Paste one JSON object" onClick={() => void fromClipboard("SINGLE")} />
              <MenuRow icon={<Sparkles size={16} />} title="AI" subtitle={`Describe one ${noun}`} onClick={() => setStep({ k: "ai", mode: "SINGLE" })} />
              <MenuRow icon={<Library size={16} />} title="Library" subtitle={`Search saved ${nouns}`} onClick={() => setStep({ k: "library" })} />
              <MenuRow icon={<PencilLine size={16} />} title="New" subtitle="Fill the form by hand" onClick={newSingle} />
            </div>
            <div>
              <div className="mb-2 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-muted uppercase"><ListPlus size={13} /> Add multiple</div>
              <MenuRow icon={<ClipboardPaste size={16} />} title="Clipboard" subtitle="A JSON array or wrapper object" onClick={() => void fromClipboard("MULTIPLE")} />
              <MenuRow icon={<Sparkles size={16} />} title="AI" subtitle={`Describe several ${nouns}`} onClick={() => setStep({ k: "ai", mode: "MULTIPLE" })} />
              {category === "ROUTINE" && (
                <MenuRow icon={<CalendarDays size={16} />} title="Google Calendar" subtitle="Import this day's events" onClick={() => void fromGoogleCalendar()} />
              )}
            </div>
            <div>
              <div className="mb-2 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-muted uppercase"><CalendarClock size={13} /> Plan the day</div>
              <div className="mb-1 px-2 text-[11px] text-item">Replaces every {noun} on this day, preset items included.</div>
              <MenuRow icon={<Sparkles size={16} />} title="From scratch" subtitle="AI builds the day from your description" onClick={() => setStep({ k: "ai", mode: "PLAN_SCRATCH" })} />
              <MenuRow
                icon={<CalendarClock size={16} />}
                title="From preset"
                subtitle={presetAvailable ? "AI adapts this weekday's preset" : "No active preset. Load one in Settings."}
                disabled={!presetAvailable}
                onClick={() => setStep({ k: "ai", mode: "PLAN_PRESET" })}
              />
            </div>
          </div>
        </Dialog>
      );

    case "paste":
      return (
        <Dialog
          open
          onClose={onClose}
          title={step.mode === "SINGLE" ? `Paste one ${noun}` : `Paste ${nouns}`}
          width="max-w-2xl"
          footer={
            <>
              <Button variant="secondary" onClick={() => setStep({ k: "menu" })}>Back</Button>
              <Button disabled={!step.text.trim()} onClick={() => parseText(step.text, step.mode)}>Continue</Button>
            </>
          }
        >
          <div className="space-y-3">
            <Textarea
              autoFocus
              rows={14}
              className="font-mono text-xs"
              placeholder="Paste JSON here (Ctrl+V)"
              value={step.text}
              onChange={(e) => setStep({ ...step, text: e.target.value, error: null })}
            />
            <ErrorLine>{step.error}</ErrorLine>
          </div>
        </Dialog>
      );

    case "ai":
      return (
        <AiDialog
          title={AI_TITLE[step.mode]}
          mode={step.mode}
          category={category}
          onBack={() => setStep({ k: "menu" })}
          onClose={onClose}
          run={async (text, images) => {
            const req = { mode: step.mode, date, text, images, context: { existingItems: "", presetJson: null as string | null } };
            const plan = isPlanMode(step.mode);
            const s = useCalendar.getState();
            const sh = useDaily.getState().shards;
            if (category === "ROUTINE") {
              req.context = routineContext(sh, date, s.ROUTINE?.current.activities ?? [], labels);
              const drafts = (await aiActivities(req, labels.filter((l) => !l.isSystem).map((l) => l.name))).map((d) => mergeActivityDraft(d, labels));
              setStep(step.mode === "SINGLE" ? { k: "activityForm", prefill: drafts[0] ?? null } : { k: "activityReview", drafts, replaces: plan });
            } else if (category === "FOOD") {
              req.context = foodContext(sh, date, s.FOOD?.current.entries ?? []);
              const drafts = await aiMeals(req);
              setStep(step.mode === "SINGLE" ? { k: "mealForm", prefill: drafts[0] ?? null } : { k: "mealReview", drafts: drafts.map((d) => ({ ...d, asPlanned: plan })), source: "AI", replaces: plan });
            } else {
              const sets = s.EXERCISE?.current.sets ?? [];
              req.context = exerciseContext(sh, date, sets);
              const sessions = await aiExercises(req, exerciseAiContext(sh, sets));
              setStep({ k: "exerciseReview", sessions, source: "AI", replaces: plan });
            }
          }}
        />
      );

    case "library":
      return (
        <LibraryPicker
          category={category}
          onClose={onClose}
          onPickActivity={(d) => setStep({ k: "activityForm", prefill: mergeActivityDraft(d, labels) })}
          onPickMeal={(d) => setStep({ k: "mealForm", prefill: d })}
          onPickSession={(s) => setStep({ k: "exerciseReview", sessions: [s], source: "Library", replaces: false })}
        />
      );

    case "activityForm":
      return (
        <ActivityDialog
          entry={null}
          prefill={step.prefill}
          labels={labels}
          date={date}
          onClose={onClose}
          onSave={(a) => addActivities([a], false)}
        />
      );

    case "activityReview":
      return <ActivityReview initial={step.drafts} labels={labels} date={date} replaces={step.replaces} onClose={onClose} onConfirm={(e) => addActivities(e, step.replaces)} />;

    case "mealForm":
      return <MealDialog title="Add meal" initial={step.prefill} onClose={onClose} onSave={(d) => addMeals([d], false)} />;

    case "mealReview":
      return <MealReview initial={step.drafts} source={step.source} replaces={step.replaces} onClose={onClose} onConfirm={(d) => addMeals(d, step.replaces)} />;

    case "exerciseReview":
      return (
        <ExerciseReview
          initial={step.sessions}
          sourceLabel={step.source}
          replacesDay={step.replaces}
          vocabulary={exerciseAiContext(shards, exercise?.current.sets ?? []).muscleVocabulary}
          onClose={onClose}
          onConfirm={(s) => addSessions(s, step.replaces)}
        />
      );

    case "error":
      return (
        <Dialog
          open
          onClose={onClose}
          title="Can't add that"
          width="max-w-md"
          footer={
            <>
              <Button variant="secondary" onClick={onClose}>Close</Button>
              <Button onClick={() => setStep({ k: "menu" })}>Back to menu</Button>
            </>
          }
        >
          <div className="text-sm">{step.message}</div>
        </Dialog>
      );
  }
}

function AiDialog({
  title, mode, category, onBack, onClose, run,
}: {
  title: string;
  mode: CalendarAiMode;
  category: AddCategory;
  onBack: () => void;
  onClose: () => void;
  run: (text: string, images: Blob[]) => Promise<void>;
}) {
  const [text, setText] = useState("");
  const [images, setImages] = useState<Blob[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const allowImages = category !== "EXERCISE";
  const go = async () => {
    setBusy(true);
    setError(null);
    try {
      await run(text, images);
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  };
  const placeholder =
    category === "ROUTINE" ? "e.g. Gym 7–8, deep work 9–12, lunch with Ana at 13:00…"
      : category === "FOOD" ? "e.g. 2 eggs and toast at 8, chicken salad for lunch…"
        : "e.g. Upper body push, 45 min, focus on chest…";
  return (
    <Dialog
      open
      onClose={onClose}
      title={title}
      width="max-w-2xl"
      footer={
        <>
          <Button variant="secondary" onClick={onBack}>Back</Button>
          <Button disabled={busy || (!text.trim() && !images.length && mode !== "PLAN_PRESET")} onClick={() => void go()}>
            <Sparkles size={14} /> {busy ? "Thinking…" : "Generate"}
          </Button>
        </>
      }
    >
      <div className="space-y-3" onKeyDown={(e) => e.key === "Enter" && e.ctrlKey && void go()}>
        {isPlanMode(mode) && <ReplacesDayBanner>The result replaces every item on this day after you review it.</ReplacesDayBanner>}
        <Textarea autoFocus rows={6} value={text} onChange={(e) => setText(e.target.value)} placeholder={placeholder} />
        {allowImages && <ImageDrop images={images} onChange={setImages} />}
        <ErrorLine>{error}</ErrorLine>
      </div>
    </Dialog>
  );
}

function LibraryPicker({
  category, onClose, onPickActivity, onPickMeal, onPickSession,
}: {
  category: AddCategory;
  onClose: () => void;
  onPickActivity: (d: ActivityDraft) => void;
  onPickMeal: (d: MealDraft) => void;
  onPickSession: (s: ExerciseSessionDraft) => void;
}) {
  const shards = useDaily((s) => s.shards);
  const [q, setQ] = useState("");
  const [deleting, setDeleting] = useState<{ collection: string; id: string; name: string } | null>(null);
  const needle = q.trim().toLowerCase();
  const items = useMemo(() => {
    if (category === "ROUTINE") {
      return configRecords(shards, K.ACTIVITY_LIBRARY, normActivityLib)
        .filter((i) => !needle || i.title.toLowerCase().includes(needle) || i.labelName.toLowerCase().includes(needle))
        .sort((a, b) => a.title.localeCompare(b.title))
        .map((i) => ({ id: i.id, collection: K.ACTIVITY_LIBRARY, title: i.title, sub: `${i.startTime}–${i.endTime}${i.labelName ? ` · ${i.labelName}` : ""}`, pick: () => onPickActivity(libraryToActivityDraft(i)) }));
    }
    if (category === "FOOD") {
      return configRecords(shards, K.MEAL_LIBRARY, normMealLib)
        .filter((i) => !needle || i.name.toLowerCase().includes(needle))
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((i) => ({
          id: i.id, collection: K.MEAL_LIBRARY, title: i.name, sub: `${i.amount} ${i.unit} · ${Math.round(i.kcal)} kcal · P ${i.protein} C ${i.carbs} F ${i.fats}`,
          pick: () => onPickMeal({ name: i.name, time: "", amount: i.amount, unit: i.unit, kcal: i.kcal, protein: i.protein, carbs: i.carbs, fats: i.fats, asPlanned: false }),
        }));
    }
    return configRecords(shards, K.EXERCISE_LIBRARY, normExerciseLib)
      .filter((i) => !needle || i.name.toLowerCase().includes(needle) || i.type.toLowerCase().includes(needle))
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((i) => {
        const s = decodeLibrarySession(i.payloadJson);
        return {
          id: i.id, collection: K.EXERCISE_LIBRARY, title: `${i.type}: ${i.name}`,
          sub: s ? s.exercises.map((e) => e.name).join(" · ") || `${s.exercises.length} exercises` : "Can't read this session",
          pick: s ? () => onPickSession(sessionToDraft(s)) : undefined,
        };
      });
  }, [category, shards, needle, onPickActivity, onPickMeal, onPickSession]);
  const empty = category === "ROUTINE" ? "No saved activities yet. Preset items and activities you add are saved here."
    : category === "FOOD" ? "No saved meals yet. Meals you add are saved here." : "No saved sessions yet. Sessions you add are saved here.";
  return (
    <Dialog open onClose={onClose} title={category === "ROUTINE" ? "Activity library" : category === "FOOD" ? "Meal library" : "Exercise library"} width="max-w-xl">
      <div className="space-y-3">
        <SearchInput value={q} onChange={setQ} placeholder="Search" />
        <div className="max-h-[55vh] space-y-1.5 overflow-y-auto">
          {items.length === 0 ? (
            <div className="rounded-lg bg-offwhite p-3 text-sm text-muted">{needle ? `Nothing matches "${q}".` : empty}</div>
          ) : (
            items.map((i) => (
              <div key={i.id} className="group flex items-center rounded-lg bg-offwhite">
                <button disabled={!i.pick} onClick={i.pick} className="min-w-0 flex-1 px-3 py-2 text-left disabled:opacity-50">
                  <div className="truncate text-sm font-medium">{i.title}</div>
                  <div className="truncate text-xs text-item">{i.sub}</div>
                </button>
                <Button size="icon" variant="ghost" className="mr-1 h-8 w-8 text-danger opacity-0 group-hover:opacity-100" title="Remove from library" onClick={() => setDeleting({ collection: i.collection, id: i.id, name: i.title })}>
                  <Trash2 size={14} />
                </Button>
              </div>
            ))
          )}
        </div>
      </div>
      <Confirm
        open={!!deleting}
        title="Remove from library?"
        message={`Delete "${deleting?.name}" from the library?`}
        onClose={() => setDeleting(null)}
        onConfirm={() => deleting && editLibrary((r) => deleteLibraryItem(r, deleting.collection, deleting.id))}
      />
    </Dialog>
  );
}

function ActivityReview({
  initial, labels, date, replaces, onClose, onConfirm,
}: {
  initial: ActivityDraft[];
  labels: ActivityLabel[];
  date: string;
  replaces: boolean;
  onClose: () => void;
  onConfirm: (entries: ActivityEntry[]) => void;
}) {
  const [rows, setRows] = useState(initial.map((d) => ({ d, include: true })));
  const [error, setError] = useState<string | null>(null);
  const pickable = labels.filter((l) => !l.isSystem);
  const defaultLabel = fallbackLabelId(labels);
  const up = (i: number, patch: Partial<ActivityDraft>) => setRows((all) => all.map((r, j) => (j === i ? { ...r, d: { ...r.d, ...patch } } : r)));
  const confirm = () => {
    const chosen = rows.filter((r) => r.include);
    for (const [i, r] of chosen.entries()) {
      const err = activityDraftError(r.d, labels, defaultLabel);
      if (err) return setError(`${r.d.title || `Activity ${i + 1}`}: ${err}`);
    }
    onConfirm(chosen.map((r) => activityDraftToEntry(r.d, date, labels, defaultLabel)).filter((e): e is ActivityEntry => !!e));
  };
  return (
    <Dialog
      open
      onClose={onClose}
      title={`Review ${rows.length} activities`}
      width="max-w-5xl"
      dismissable={false}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={confirm}>{replaces ? "Replace day" : `Add ${rows.filter((r) => r.include).length}`}</Button>
        </>
      }
    >
      <div className="space-y-3">
        {replaces && <ReplacesDayBanner>Confirming replaces every activity on this day.</ReplacesDayBanner>}
        <table className="w-full text-sm">
          <thead className="text-left text-[10px] text-item uppercase">
            <tr><th className="w-8" /><th>Title</th><th className="w-24">Start</th><th className="w-24">End</th><th className="w-40">Label</th><th className="w-24">Type</th></tr>
          </thead>
          <tbody>
            {rows.map((r, i) => {
              const labelId = r.d.labelName ? labels.find((l) => l.name.toLowerCase() === r.d.labelName!.toLowerCase())?.id ?? "" : defaultLabel;
              return (
                <tr key={i} className={cn("[&>td]:py-1 [&>td]:pr-2", !r.include && "opacity-40")}>
                  <td><input type="checkbox" className="h-4 w-4 accent-navy" checked={r.include} onChange={(e) => setRows((all) => all.map((x, j) => (j === i ? { ...x, include: e.target.checked } : x)))} /></td>
                  <td>
                    <input className="h-9 w-full rounded-lg border border-hairline px-2 outline-none focus:border-electric" value={r.d.title} onChange={(e) => up(i, { title: e.target.value })} />
                    {r.d.description && <div className="mt-0.5 truncate text-[11px] text-item" title={r.d.description}>{r.d.description}</div>}
                  </td>
                  <td><TimeField value={r.d.startTime} onChange={(v) => up(i, { startTime: v })} /></td>
                  <td><TimeField value={r.d.endTime} onChange={(v) => up(i, { endTime: v })} /></td>
                  <td>
                    <select value={labelId} onChange={(e) => up(i, { labelName: labels.find((l) => l.id === e.target.value)?.name ?? null })} className="h-9 w-full rounded-lg border border-hairline bg-white px-2 text-sm">
                      {pickable.map((l) => <option key={l.id} value={l.id}>{l.name}</option>)}
                    </select>
                  </td>
                  <td><TypeSwitch value={r.d.type} onChange={(type) => up(i, { type })} /></td>
                </tr>
              );
            })}
          </tbody>
        </table>
        <ErrorLine>{error}</ErrorLine>
      </div>
    </Dialog>
  );
}

function MealReview({
  initial, source, replaces, onClose, onConfirm,
}: {
  initial: MealDraft[];
  source: string;
  replaces: boolean;
  onClose: () => void;
  onConfirm: (d: MealDraft[]) => void;
}) {
  const [rows, setRows] = useState(initial.map((d) => ({ d, include: true })));
  const [open, setOpen] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const confirm = () => {
    const chosen = rows.filter((r) => r.include).map((r) => r.d);
    for (const d of chosen) {
      const err = mealDraftError(d.time ? d : { ...d, time: "12:00" });
      if (err) return setError(`${d.name || "Meal"}: ${err}`);
    }
    onConfirm(chosen);
  };
  return (
    <Dialog
      open
      onClose={onClose}
      title={`Review meals · ${source}`}
      width="max-w-4xl"
      dismissable={false}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={confirm}>{replaces ? "Replace day" : `Add ${rows.filter((r) => r.include).length}`}</Button>
        </>
      }
    >
      <div className="space-y-3">
        {replaces && <ReplacesDayBanner>Confirming replaces every food row on this day.</ReplacesDayBanner>}
        <div className="grid grid-cols-[240px_1fr] gap-4">
          <div className="space-y-1">
            {rows.map((r, i) => (
              <div key={i} className={cn("flex items-center gap-2 rounded-lg px-2 py-1.5", open === i ? "bg-navy/10" : "hover:bg-offwhite", !r.include && "opacity-40")}>
                <input type="checkbox" className="h-4 w-4 accent-navy" checked={r.include} onChange={(e) => setRows((all) => all.map((x, j) => (j === i ? { ...x, include: e.target.checked } : x)))} />
                <button className="min-w-0 flex-1 text-left" onClick={() => setOpen(i)}>
                  <div className="truncate text-sm font-medium">{r.d.name || "Unnamed"}</div>
                  <div className="text-[11px] text-item">{r.d.time || "now"} · {Math.round(r.d.kcal)} kcal</div>
                </button>
              </div>
            ))}
          </div>
          {rows[open] && (
            <MealForm draft={rows[open].d} onChange={(d) => setRows((all) => all.map((x, j) => (j === open ? { ...x, d } : x)))} />
          )}
        </div>
        <ErrorLine>{error}</ErrorLine>
      </div>
    </Dialog>
  );
}
