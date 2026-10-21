// Four categories x 14 days. Orange: unsaved Calendar draft. Green: saved data. Grey: preset only.
import { useMemo, useState } from "react";
import { cn } from "@/lib/cn";
import { completionRow, type DayCompletion } from "@/domain/daily/home";
import type { DraftCategory } from "@/domain/daily/drafts";
import { useCalendar } from "@/store/calendar";
import { useDaily } from "@/store/daily";
import { openCalendar } from "@/store/nav";
import { Button, Card, Dialog } from "@/ui/primitives";

const ROWS: { c: DraftCategory; label: string }[] = [
  { c: "ROUTINE", label: "Routine" },
  { c: "FOOD", label: "Food" },
  { c: "EXERCISE", label: "Exercise" },
  { c: "SELF", label: "Self" },
];

const DAYS = 14;

export function DailyInputs({ endDate }: { endDate: string }) {
  const shards = useDaily((s) => s.shards);
  const drafts = useDaily((s) => s.drafts);
  const [pending, setPending] = useState<{ c: DraftCategory; label: string; day: DayCompletion } | null>(null);
  const rows = useMemo(() => {
    const keys = new Set(Object.keys(drafts));
    return ROWS.map((r) => ({ ...r, days: completionRow(shards, r.c, endDate, DAYS, keys) }));
  }, [shards, drafts, endDate]);
  const header = rows[0]?.days ?? [];

  return (
    <Card className="p-4">
      <div className="mb-3 flex items-center justify-between">
        <div className="font-semibold">Daily inputs</div>
        <div className="flex items-center gap-3 text-[11px] text-item">
          <Legend className="bg-prio-low" label="Saved" />
          <Legend className="bg-prio-mid" label="Unsaved" />
          <Legend className="bg-muted/25" label="Preset only" />
        </div>
      </div>
      <div className="grid items-center gap-x-1 gap-y-1.5" style={{ gridTemplateColumns: `5.5rem repeat(${DAYS}, minmax(0, 1fr))` }}>
        <div />
        {header.map((d) => {
          const dt = new Date(`${d.date}T00:00:00`);
          return (
            <div key={d.date} className={cn("text-center text-[10px] leading-tight text-item", d.date === endDate && "font-semibold text-navy")}>
              {dt.toLocaleDateString(undefined, { weekday: "narrow" })}
              <br />
              {dt.getDate()}
            </div>
          );
        })}
        {rows.map((r) => (
          <Row key={r.c} label={r.label} days={r.days} endDate={endDate} onOpen={(d) => openCalendar(d, r.c)} onReset={(day) => setPending({ c: r.c, label: r.label, day })} />
        ))}
      </div>
      <div className="mt-3 text-[11px] text-item">Click a day to open it in the Calendar. Right-click to reset it to the preset.</div>
      {pending && <ResetDialog pending={pending} onClose={() => setPending(null)} />}
    </Card>
  );
}

function Legend({ className, label }: { className: string; label: string }) {
  return (
    <span className="flex items-center gap-1">
      <span className={cn("h-2.5 w-2.5 rounded-sm", className)} /> {label}
    </span>
  );
}

function Row({ label, days, endDate, onOpen, onReset }: {
  label: string;
  days: DayCompletion[];
  endDate: string;
  onOpen: (date: string) => void;
  onReset: (d: DayCompletion) => void;
}) {
  return (
    <>
      <div className="text-sm font-semibold text-navy">{label}</div>
      {days.map((d) => (
        <button
          key={d.date}
          title={`${label} · ${d.date}${d.hasData ? ` · ${d.percent}%` : ""}${d.hasDraft ? " · unsaved" : ""}`}
          onClick={() => onOpen(d.date)}
          onContextMenu={(e) => {
            e.preventDefault();
            onReset(d);
          }}
          className={cn(
            "aspect-square w-full rounded-md border transition hover:scale-110",
            d.hasDraft ? "bg-prio-mid" : d.hasData ? "bg-prio-low" : "bg-muted/25",
            d.date === endDate ? "border-2 border-navy" : "border-navy/40",
          )}
        />
      ))}
    </>
  );
}

function ResetDialog({ pending, onClose }: { pending: { c: DraftCategory; label: string; day: DayCompletion }; onClose: () => void }) {
  const { c, label, day } = pending;
  const hasAnything = day.hasData || day.hasDraft;
  return (
    <Dialog
      open
      onClose={onClose}
      title={`Reset ${label} for ${day.date}?`}
      width="max-w-sm"
      footer={
        hasAnything ? (
          <>
            <Button variant="secondary" onClick={onClose}>Cancel</Button>
            <Button variant="danger" onClick={() => { useCalendar.getState().resetDay(c, day.date); onClose(); }}>Delete</Button>
          </>
        ) : (
          <Button onClick={onClose}>OK</Button>
        )
      }
    >
      <div className="text-sm text-muted">
        {hasAnything
          ? `This deletes the saved ${label} data${day.hasDraft ? " and unsaved changes" : ""} for this day. The Calendar will show the preset again.${c === "ROUTINE" ? " Last night's sleep log is deleted too." : ""}`
          : "Nothing is saved for this day yet. It already shows the preset."}
      </div>
    </Dialog>
  );
}
