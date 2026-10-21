// Week overview: one column per day, one row per category, saved data (or the preset) at a glance.
import { useMemo } from "react";
import { cn } from "@/lib/cn";
import { addDays, todayKey } from "@/domain/dates";
import { cappedDoneMinutes, effectiveEnd, effectiveStart, plannedDuration } from "@/domain/daily/activity";
import { exerciseBaseline, foodBaseline, labelsWithPreset, routineBaseline, selfBaseline } from "@/domain/daily/day";
import { decodeDraft, draftKey, type DraftCategory } from "@/domain/daily/drafts";
import { foodDayTotals } from "@/domain/daily/food";
import { dayCompletion } from "@/domain/daily/home";
import { MOOD_EMOJI } from "@/domain/daily/self";
import { summarizeExerciseDay } from "@/domain/daily/stats";
import { formatDuration } from "@/domain/daily/time";
import { mondayOf } from "@/domain/daily/stats";
import { useDaily } from "@/store/daily";
import { useNav } from "@/store/nav";
import { argbCss } from "./ui";

const CATS: { c: DraftCategory; label: string }[] = [
  { c: "ROUTINE", label: "Routine" },
  { c: "FOOD", label: "Food" },
  { c: "EXERCISE", label: "Exercise" },
  { c: "SELF", label: "Self" },
];

export function WeekView({ date }: { date: string }) {
  const shards = useDaily((s) => s.shards);
  const drafts = useDaily((s) => s.drafts);
  const draftKeys = useMemo(() => new Set(Object.keys(drafts)), [drafts]);
  const labels = useMemo(() => labelsWithPreset(shards), [shards]);
  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(mondayOf(date), i)), [date]);
  const today = todayKey();
  const open = (d: string, category: DraftCategory) => useNav.getState().open({ view: "calendar", date: d, category, calendarMode: "day" });

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[960px] table-fixed border-separate border-spacing-2">
        <thead>
          <tr>
            <th className="w-24" />
            {days.map((d) => {
              const dt = new Date(`${d}T00:00:00`);
              return (
                <th key={d} className={cn("rounded-lg py-2 text-center", d === today ? "bg-navy text-white" : "bg-offwhite")}>
                  <div className="text-[11px] font-medium uppercase opacity-80">{dt.toLocaleDateString(undefined, { weekday: "short" })}</div>
                  <div className="text-lg font-semibold">{dt.getDate()}</div>
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {CATS.map(({ c, label }) => (
            <tr key={c}>
              <td className="align-top text-sm font-semibold">{label}</td>
              {days.map((d) => {
                const comp = dayCompletion(shards, c, d, draftKeys);
                const draft = drafts[draftKey(c, d)];
                return (
                  <td key={d} className="align-top">
                    <button
                      onClick={() => open(d, c)}
                      className={cn(
                        "relative h-36 w-full overflow-hidden rounded-lg border p-2 text-left text-[11px] transition hover:border-electric/60",
                        comp.hasData ? "border-hairline bg-white" : "border-dashed border-hairline bg-offwhite/50",
                      )}
                    >
                      {draft && <span className="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-prio-mid" title="Unsaved draft" />}
                      <Cell category={c} date={d} labels={labels} draftJson={draft?.payloadJson} saved={comp.hasData} />
                    </button>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Cell({ category, date, labels, draftJson, saved }: { category: DraftCategory; date: string; labels: ReturnType<typeof labelsWithPreset>; draftJson?: string; saved: boolean }) {
  const shards = useDaily((s) => s.shards);
  const tag = !saved && <div className="mb-1 text-[10px] text-item italic">{draftJson ? "draft" : "plan"}</div>;
  switch (category) {
    case "ROUTINE": {
      const p = decodeDraft<"ROUTINE">(draftJson) ?? routineBaseline(shards, date).payload;
      const planned = p.activities.filter((a) => a.type === "PLANNED").reduce((t, a) => t + (plannedDuration(a) ?? 0), 0);
      const done = p.activities.filter((a) => a.type === "PLANNED" && a.completion === "DONE").reduce((t, a) => t + cappedDoneMinutes(a), 0);
      return (
        <div>
          {tag}
          {planned > 0 && <div className="mb-1 font-semibold">{Math.floor((done / planned) * 100)}% done</div>}
          <div className="space-y-0.5">
            {p.activities.slice(0, 6).map((a) => (
              <div key={a.id} className="flex items-center gap-1 truncate">
                <span className="h-1.5 w-1.5 shrink-0 rounded-full" style={{ background: argbCss(labels.find((l) => l.id === a.labelId)?.colorArgb ?? 0xff90a4ae) }} />
                <span className="font-mono text-item">{effectiveStart(a)}</span>
                <span className="truncate">{a.title}</span>
              </div>
            ))}
            {p.activities.length > 6 && <div className="text-item">+{p.activities.length - 6} more · {effectiveEnd(p.activities[p.activities.length - 1])}</div>}
          </div>
        </div>
      );
    }
    case "FOOD": {
      const p = decodeDraft<"FOOD">(draftJson) ?? foodBaseline(shards, date).payload;
      if (!p.entries.length) return <div className="text-item">—</div>;
      const t = foodDayTotals(p.entries);
      const pct = t.planned.kcal > 0 ? Math.min(100, (t.got.kcal / t.planned.kcal) * 100) : 0;
      return (
        <div>
          {tag}
          <div className="font-mono text-sm font-semibold">{Math.round(t.got.kcal)}</div>
          <div className="text-item">/ {Math.round(t.planned.kcal)} kcal</div>
          <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-offwhite ring-1 ring-hairline"><div className="h-full bg-navy" style={{ width: `${pct}%` }} /></div>
          <div className="mt-1 text-item">P {Math.round(t.got.protein)} · C {Math.round(t.got.carbs)} · F {Math.round(t.got.fats)}</div>
          <div className="text-item">{p.entries.filter((e) => e.isChecked && !e.notEaten).length}/{p.entries.length} eaten</div>
        </div>
      );
    }
    case "EXERCISE": {
      const p = decodeDraft<"EXERCISE">(draftJson) ?? exerciseBaseline(shards, date).payload;
      if (!p.sets.length) return <div className="text-item">Rest</div>;
      const s = summarizeExerciseDay(p.sets);
      const titles = [...new Set(p.sets.map((x) => x.sessionTitle || "Workout"))];
      return (
        <div>
          {tag}
          <div className="font-semibold">{formatDuration(Math.round(s.minutes.done))} / {formatDuration(Math.round(s.minutes.planned))}</div>
          <div className="mt-1 space-y-0.5">
            {titles.slice(0, 4).map((t) => <div key={t} className="truncate">{t}</div>)}
          </div>
        </div>
      );
    }
    case "SELF": {
      const p = decodeDraft<"SELF">(draftJson) ?? selfBaseline(shards, date).payload;
      const mood = p.scales.mood;
      const words = p.journalText.trim() ? p.journalText.trim().split(/\s+/).length : 0;
      if (!mood && !words && !Object.keys(p.scales).length) return <div className="text-item">—</div>;
      return (
        <div>
          {tag}
          {mood && <div className="text-2xl">{MOOD_EMOJI[mood - 1]}</div>}
          <div className="text-item">{Object.keys(p.scales).length} scales</div>
          {words > 0 && <div className="text-item">{words} words</div>}
          {p.intention && <div className="mt-1 line-clamp-2">{p.intention}</div>}
        </div>
      );
    }
  }
}
