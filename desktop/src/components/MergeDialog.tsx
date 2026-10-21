import { useEffect, useState } from "react";
import { ArrowLeft, GitMerge, Laptop, Smartphone } from "lucide-react";
import { formatDateTime } from "@/domain/dates";
import { cn } from "@/lib/cn";
import type { MergeChoice } from "@/sync/engine";
import { C, type Json, type JsonObject, type MergeAnalysis, type MergeConflict, type Resolution } from "@/sync/merge";
import { D, K, shardLabel } from "@/sync/shards";
import type { SyncDevice } from "@/domain/models";
import { useBrain } from "@/store/brain";
import { useDaily } from "@/store/daily";
import { Button, Confirm, Dialog } from "@/ui/primitives";

const COLLECTION_LABEL: Record<string, string> = {
  [C.NODES]: "Tile",
  [C.NOTES]: "Note",
  [C.ASSETS]: "File",
  [C.LEARNING_TILES]: "Learn progress",
  [C.LEARNING_LINKS]: "Learn link",
  [C.TAGS]: "Tag",
  [C.TASKS]: "Task",
  [C.SCORE_EVENTS]: "Points",
  [C.SUPERMARKET_ITEMS]: "Supermarket item",
  [C.META]: "Settings",
  [D.FOOD]: "Meal",
  [D.ACTIVITIES]: "Activity",
  [D.SLEEP]: "Sleep",
  [D.WORKOUT_SETS]: "Workout set",
  [D.SELF]: "Self",
  [D.HOME]: "Body",
  [D.HABIT_LOGS]: "Habit",
  [D.HABIT_CONFIGS]: "Habit selection",
  [K.LABELS]: "Label",
  [K.HABITS]: "Habit",
  [K.PRESETS]: "Preset",
  [K.SETTINGS]: "Settings",
};

interface MergeView {
  analysis: MergeAnalysis;
  remoteDevice: SyncDevice | null;
  remoteWrittenAt: number;
}

function otherName(view: MergeView): string {
  const d = view.remoteDevice;
  if (!d) return "Other device";
  if (d.platform === "android") return d.name || "Phone";
  return d.name || "Other device";
}

function kindLabel(c: MergeConflict, other: string): string {
  switch (c.kind) {
    case "EDIT_EDIT": return "Edited on both devices";
    case "ADD_ADD": return "Added on both devices with different values";
    case "DELETE_EDIT": return `Deleted on this computer, edited on ${other}`;
    case "EDIT_DELETE": return `Edited on this computer, deleted on ${other}`;
  }
}

const str = (o: JsonObject | null | undefined, f: string) => {
  const v = o?.[f];
  return typeof v === "string" && v.trim() ? v : null;
};

function recordTitle(c: MergeConflict, view: MergeView): string {
  if (c.collection === C.LEARNING_TILES) {
    const node = view.analysis.local.get(C.NODES)?.get(c.key) ?? view.analysis.remote.get(C.NODES)?.get(c.key);
    return str(node, "name") ?? c.key;
  }
  if (c.collection === C.META) return c.key === "settings" ? "Motion settings" : "Supermarket aisles";
  const record = c.local ?? c.remote;
  const title = str(record, "title") ?? str(record, "name") ?? str(record, "exerciseName") ?? str(record, "fileName");
  const date = str(record, "date");
  return [title, date].filter(Boolean).join(" · ") || c.key;
}

function display(field: string, value: Json | undefined): string {
  if (value === null || value === undefined) return "—";
  if (Array.isArray(value)) return value.length ? value.map((v) => display(field, v)).join(", ") : "—";
  if (typeof value === "object") return JSON.stringify(value).slice(0, 200);
  if (typeof value === "number" && field.endsWith("At") && value > 1e12) return formatDateTime(value);
  const s = String(value);
  return s.trim() ? s.slice(0, 400) : "—";
}

const summaryFields = (r: JsonObject) =>
  ["title", "name", "fileName", "body", "notes", "status", "journalText", "completion"].filter((f) => f in r).slice(0, 3);

function SidePick({ label, icon, record, fields, selected, newer, onSelect }: {
  label: string;
  icon: React.ReactNode;
  record: JsonObject | null;
  fields: string[];
  selected: boolean;
  newer: boolean;
  onSelect: () => void;
}) {
  const shown = record ? (fields.length ? fields : summaryFields(record)) : [];
  return (
    <button
      onClick={onSelect}
      className={cn(
        "flex-1 rounded-xl border-2 p-3 text-left transition",
        selected ? "border-electric bg-electric/5" : "border-hairline hover:border-electric/40",
      )}
    >
      <div className="mb-2 flex items-center gap-1.5 text-xs font-semibold text-muted">
        {icon}{label}
        {newer && <span className="ml-auto rounded-full bg-electric/10 px-1.5 text-[10px] text-electric">newer</span>}
      </div>
      {record ? (
        <div className="space-y-1.5">
          {shown.map((f) => (
            <div key={f}>
              <div className="text-[11px] uppercase tracking-wide text-item">{f}</div>
              <div className="selectable line-clamp-4 text-sm break-words whitespace-pre-wrap">{display(f, record[f])}</div>
            </div>
          ))}
        </div>
      ) : (
        <div className="text-sm font-medium text-danger">Deleted</div>
      )}
    </button>
  );
}

/** Same choices as the phone's merge sheet: Smart merge (with per-item review), keep one side, or later. */
function MergeDialogView({ open, title, view, onClose, onResolve }: {
  open: boolean;
  title: string;
  view: MergeView | null;
  onClose: () => void;
  onResolve: (choice: MergeChoice, resolutions?: Record<string, Resolution>) => Promise<void>;
}) {
  const [step, setStep] = useState<"overview" | "review">("overview");
  const [choices, setChoices] = useState<Record<string, Resolution>>({});
  const [confirm, setConfirm] = useState<MergeChoice | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open && view) {
      setStep("overview");
      setChoices(Object.fromEntries(view.analysis.conflicts.map((c) => [c.id, c.newer as Resolution])));
    }
  }, [open, view]);

  if (!open || !view) return null;
  const other = otherName(view);
  const { analysis } = view;
  const OtherIcon = view.remoteDevice?.platform === "android" ? Smartphone : Laptop;

  const run = async (choice: MergeChoice, resolutions?: Record<string, Resolution>) => {
    setBusy(true);
    try {
      await onResolve(choice, resolutions);
    } finally {
      setBusy(false);
    }
  };

  const smart = () => (analysis.conflicts.length === 0 ? run("SMART") : setStep("review"));

  return (
    <>
      <Dialog
        open
        onClose={onClose}
        width={step === "review" ? "max-w-3xl" : "max-w-lg"}
        title={
          <span className="flex items-center gap-2">
            {step === "review" && (
              <button onClick={() => setStep("overview")} className="rounded-md p-1 hover:bg-offwhite"><ArrowLeft size={16} /></button>
            )}
            <GitMerge size={18} className="text-electric" />
            {step === "review" ? `Review ${analysis.conflicts.length} item(s)` : title}
          </span>
        }
        footer={
          step === "review" ? (
            <Button disabled={busy} onClick={() => run("SMART", choices)}>{busy ? "Merging…" : "Apply merge"}</Button>
          ) : undefined
        }
      >
        {step === "overview" ? (
          <div className="space-y-4">
            <p className="text-sm">
              This computer: <b>{analysis.localChanges}</b> change(s). {other}: <b>{analysis.remoteChanges}</b> change(s)
              {view.remoteWrittenAt > 0 && <>, synced {formatDateTime(view.remoteWrittenAt)}</>}.
            </p>
            <p className="text-sm text-muted">
              {analysis.conflicts.length === 0
                ? "No item was edited on both sides, so Smart merge keeps every change."
                : `${analysis.conflicts.length} item(s) were edited on both sides. Smart merge keeps everything else and lets you pick a side for those (the newer one is preselected).`}
            </p>
            <Button className="w-full justify-center" disabled={busy} onClick={smart}>
              {busy ? "Merging…" : analysis.conflicts.length === 0 ? "Smart merge" : `Smart merge · review ${analysis.conflicts.length}`}
            </Button>
            <div className="grid grid-cols-2 gap-2">
              <Button variant="secondary" className="justify-center" disabled={busy} onClick={() => setConfirm("KEEP_LOCAL")}><Laptop size={15} /> Keep this computer</Button>
              <Button variant="secondary" className="justify-center" disabled={busy} onClick={() => setConfirm("KEEP_REMOTE")}><OtherIcon size={15} /> Keep {other}</Button>
            </div>
            <Button variant="ghost" className="w-full justify-center" onClick={onClose}>Later</Button>
          </div>
        ) : (
          <div className="space-y-4">
            {analysis.conflicts.map((c) => {
              const choice = choices[c.id] ?? "LOCAL";
              const pick = (r: Resolution) => setChoices((x) => ({ ...x, [c.id]: r }));
              return (
                <div key={c.id} className="rounded-xl border border-hairline p-4">
                  <div className="mb-1 flex items-center gap-2">
                    <span className="rounded-full bg-offwhite px-2 py-0.5 text-[11px] font-medium text-muted">{COLLECTION_LABEL[c.collection] ?? c.collection}</span>
                    <span className="truncate font-semibold">{recordTitle(c, view)}</span>
                  </div>
                  <div className="mb-3 text-xs text-muted">
                    {kindLabel(c, other)}
                    {c.fields.length > 0 && <> · {c.fields.join(", ")}</>}
                  </div>
                  <div className="flex gap-3">
                    <SidePick label="This computer" icon={<Laptop size={13} />} record={c.local} fields={c.fields} newer={c.newer === "LOCAL"} selected={choice !== "REMOTE"} onSelect={() => pick("LOCAL")} />
                    <SidePick label={other} icon={<OtherIcon size={13} />} record={c.remote} fields={c.fields} newer={c.newer === "REMOTE"} selected={choice === "REMOTE" || choice === "BOTH"} onSelect={() => pick("REMOTE")} />
                  </div>
                  {c.canKeepBoth && c.local && c.remote && (
                    <button
                      onClick={() => pick(choice === "BOTH" ? "LOCAL" : "BOTH")}
                      className={cn("mt-3 text-sm font-medium", choice === "BOTH" ? "text-electric" : "text-navy hover:text-electric")}
                    >
                      {choice === "BOTH" ? "✓ Keeping both (adds a copy)" : "Keep both (adds a copy)"}
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </Dialog>
      <Confirm
        open={confirm !== null}
        title={confirm === "KEEP_LOCAL" ? "Keep this computer?" : `Keep ${other}?`}
        message={
          confirm === "KEEP_LOCAL"
            ? `${analysis.remoteChanges} change(s) made on ${other} will be undone on every device.`
            : `${analysis.localChanges} change(s) made on this computer will be undone.`
        }
        confirmLabel="Discard them"
        onConfirm={() => confirm && void run(confirm)}
        onClose={() => setConfirm(null)}
      />
    </>
  );
}

export function MergeDialog() {
  const open = useBrain((s) => s.mergeOpen);
  const view = useBrain((s) => s.merge);
  const close = useBrain((s) => s.closeMerge);
  const resolveMerge = useBrain((s) => s.resolveMerge);
  return <MergeDialogView open={open} view={view} title="Brain changed on two devices" onClose={close} onResolve={resolveMerge} />;
}

export function ShardMergeDialog() {
  const open = useDaily((s) => s.mergeOpen);
  const view = useDaily((s) => s.merge);
  const close = useDaily((s) => s.closeMerge);
  const resolveMerge = useDaily((s) => s.resolveMerge);
  return (
    <MergeDialogView
      open={open}
      view={view}
      title={view ? `${shardLabel(view.shard)} changed on two devices` : ""}
      onClose={close}
      onResolve={resolveMerge}
    />
  );
}
