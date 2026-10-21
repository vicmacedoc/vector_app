// New/edit activity form (ActivityEditDialog) and the actual-time adjust dialog.
import { useState } from "react";
import { cn } from "@/lib/cn";
import {
  adjustActivity, effectiveEnd, effectiveStart, isAdjusted, resolveDraftLabelId, type ActivityDraft,
} from "@/domain/daily/activity";
import { COMPLETIONS, normActivity, type ActivityCompletion, type ActivityEntry, type ActivityLabel, type ActivityType } from "@/domain/daily/models";
import { Button, Dialog, Input, Textarea } from "@/ui/primitives";
import { ErrorLine, Field, LabelChip, TimeField } from "./ui";

export const COMPLETION_COLOR: Record<ActivityCompletion, string> = {
  DONE: "#4caf50", NOT_DONE: "#d32f2f", CANCELLED: "#9e9e9e", NA: "#78909c", NONE: "#f8f9fa",
};

export const completionText = (c: ActivityCompletion) => c.replace("_", " ");

export function CompletionPicker({ value, onChange, disabled }: { value: ActivityCompletion; onChange: (c: ActivityCompletion) => void; disabled?: boolean }) {
  return (
    <select
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value as ActivityCompletion)}
      className={cn(
        "h-7 cursor-pointer appearance-none rounded-full px-3 text-[11px] font-semibold outline-none disabled:cursor-default",
        value === "NONE" ? "border border-black/25 text-black" : "text-white",
      )}
      style={{ background: COMPLETION_COLOR[value] }}
    >
      {COMPLETIONS.map((c) => (
        <option key={c} value={c} className="bg-white text-black">{completionText(c)}</option>
      ))}
    </select>
  );
}

export function TypeSwitch({ value, onChange, disabled }: { value: ActivityType; onChange: (t: ActivityType) => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onChange(value === "PLANNED" ? "NEW" : "PLANNED")}
      className={cn(
        "rounded-full border border-black/20 px-2.5 py-0.5 text-[11px] font-medium",
        value === "PLANNED" ? "bg-navy text-white" : "bg-offwhite text-black",
      )}
      title="Toggle planned / new"
    >
      {value === "PLANNED" ? "Planned" : "New"}
    </button>
  );
}

export function ActivityDialog({
  entry, prefill, labels, date, onClose, onSave, onDelete,
}: {
  entry: ActivityEntry | null;
  prefill?: ActivityDraft | null;
  labels: ActivityLabel[];
  date: string;
  onClose: () => void;
  onSave: (a: ActivityEntry) => void;
  onDelete?: () => void;
}) {
  const seed = entry ? null : prefill ?? null;
  const pickable = labels.filter((l) => !l.isSystem);
  const [title, setTitle] = useState(entry?.title ?? seed?.title ?? "");
  const [start, setStart] = useState(entry?.startTime ?? seed?.startTime ?? "09:00");
  const [end, setEnd] = useState(entry?.endTime ?? seed?.endTime ?? "10:00");
  const [labelId, setLabelId] = useState(
    entry?.labelId || (seed ? resolveDraftLabelId(seed, labels, "") : "") || pickable[0]?.id || "",
  );
  const [type, setType] = useState<ActivityType>(entry?.type ?? seed?.type ?? "NEW");
  const [completion, setCompletion] = useState<ActivityCompletion>(entry?.completion ?? "NONE");
  const [description, setDescription] = useState(entry?.description ?? seed?.description ?? "");
  const [error, setError] = useState<string | null>(null);
  const unknownLabel = seed?.labelName && !labels.some((l) => l.name.toLowerCase() === seed.labelName!.toLowerCase()) ? seed.labelName : null;

  const save = () => {
    const t = title.trim();
    if (!t) return setError("Title is required");
    const label = labelId || pickable[0]?.id || "";
    if (!label) return setError("Select a label");
    if (!entry) {
      onSave(normActivity({
        id: `act_${crypto.randomUUID()}`, date, title: t, startTime: start, endTime: end, labelId: label,
        completion: "NONE", type, description: description.trim(), timestamp: Date.now(),
      }));
      return;
    }
    const keep = isAdjusted(entry) && !(effectiveStart(entry) === start && effectiveEnd(entry) === end);
    onSave({
      ...entry, title: t, startTime: start, endTime: end,
      adjustedStartTime: keep ? effectiveStart(entry) : null, adjustedEndTime: keep ? effectiveEnd(entry) : null,
      labelId: label, completion, type, description: description.trim(), timestamp: Date.now(),
    });
  };

  return (
    <Dialog
      open
      onClose={onClose}
      title={entry ? "Edit activity" : "New activity"}
      footer={
        <>
          {onDelete && <Button variant="ghost" className="mr-auto text-danger" onClick={onDelete}>Remove</Button>}
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={save}>{entry ? "Save" : "Add"}</Button>
        </>
      }
    >
      <div className="space-y-4" onKeyDown={(e) => e.key === "Enter" && e.ctrlKey && save()}>
        {unknownLabel && <ErrorLine>Unknown label "{unknownLabel}". Pick one below or create it in Settings.</ErrorLine>}
        <Field label="Title">
          <Input autoFocus value={title} onChange={(e) => setTitle(e.target.value)} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Start"><TimeField value={start} onChange={setStart} /></Field>
          <Field label="End"><TimeField value={end} onChange={setEnd} /></Field>
        </div>
        {entry && isAdjusted(entry) && <div className="text-xs text-item">Adjusted: {effectiveStart(entry)} – {effectiveEnd(entry)}</div>}
        <Field label="Label">
          <div className="flex flex-wrap gap-2">
            {pickable.map((l) => (
              <LabelChip key={l.id} label={l} selected={l.id === labelId} onClick={() => setLabelId(l.id)} />
            ))}
          </div>
        </Field>
        <div className="flex gap-6">
          <Field label="Type"><TypeSwitch value={type} onChange={setType} /></Field>
          {entry && <Field label="Status"><CompletionPicker value={completion} onChange={setCompletion} /></Field>}
        </div>
        <Field label="Description">
          <Textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <ErrorLine>{error}</ErrorLine>
      </div>
    </Dialog>
  );
}

export function AdjustDialog({ entry, onClose, onSave }: { entry: ActivityEntry; onClose: () => void; onSave: (a: ActivityEntry) => void }) {
  const [start, setStart] = useState(effectiveStart(entry));
  const [end, setEnd] = useState(effectiveEnd(entry));
  return (
    <Dialog
      open
      onClose={onClose}
      title="Adjust actual time"
      width="max-w-sm"
      footer={
        <>
          {isAdjusted(entry) && (
            <Button variant="ghost" className="mr-auto" onClick={() => onSave(adjustActivity(entry, entry.startTime, entry.endTime))}>
              Reset to plan
            </Button>
          )}
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={() => onSave(adjustActivity(entry, start, end))}>Apply</Button>
        </>
      }
    >
      <div className="space-y-3">
        <div className="text-sm text-muted">Planned {entry.startTime} – {entry.endTime}</div>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Actual start"><TimeField value={start} onChange={setStart} /></Field>
          <Field label="Actual end"><TimeField value={end} onChange={setEnd} /></Field>
        </div>
      </div>
    </Dialog>
  );
}
