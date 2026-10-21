// Motion list add-flows from the phone: "Merge to-dos" (Inbox captures into a list) and "From text" (Gemini).
import { useEffect, useState } from "react";
import { Sparkles } from "lucide-react";
import { parseTodos, type MotionTodoDraft } from "@/ai/motion";
import { formatDateTime } from "@/domain/dates";
import type { MotionPriority } from "@/domain/models";
import * as ops from "@/domain/ops";
import { cn } from "@/lib/cn";
import { errorMessage } from "@/platform/native";
import { useBrain } from "@/store/brain";
import { Badge, Button, Dialog, Input, Textarea } from "@/ui/primitives";

const PRIORITY_TONE: Record<MotionPriority, "neutral" | "navy" | "electric" | "danger"> = {
  NONE: "neutral", LOW: "neutral", MEDIUM: "electric", HIGH: "danger",
};

export function MergeTodosDialog({ open, listId, listName, onClose }: { open: boolean; listId: string; listName: string; onClose: () => void }) {
  const tasks = useBrain((s) => s.brain.tasks);
  const commit = useBrain((s) => s.commit);
  const toast = useBrain((s) => s.toast);
  const inbox = tasks.filter((t) => t.nodeId === null && t.status === "OPEN").sort((a, b) => a.createdAt - b.createdAt);
  const [picked, setPicked] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (open) setPicked(new Set(useBrain.getState().brain.tasks.filter((t) => t.nodeId === null && t.status === "OPEN").map((t) => t.id)));
  }, [open]);

  const merge = () => {
    commit((s) => ops.moveTasks(s, [...picked], listId));
    toast(`Merged ${picked.size} into ${listName}`);
    onClose();
  };

  const toggle = (id: string) => setPicked((p) => {
    const n = new Set(p);
    if (n.has(id)) n.delete(id);
    else n.add(id);
    return n;
  });

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Merge captured to-dos"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button disabled={picked.size === 0} onClick={merge}>{picked.size === 1 ? "Merge 1" : `Merge ${picked.size}`}</Button>
        </>
      }
    >
      {inbox.length === 0 ? (
        <div className="text-sm text-muted">To-dos from Quick capture wait in the Inbox until you merge them into a list. None waiting right now.</div>
      ) : (
        <div className="space-y-1">
          <div className="mb-2 text-sm text-muted">Pick the Inbox to-dos to move into <b>{listName}</b>.</div>
          {inbox.map((t) => (
            <label key={t.id} className="flex cursor-pointer items-start gap-3 rounded-lg px-2 py-2 hover:bg-offwhite">
              <input type="checkbox" className="mt-0.5 h-4 w-4 accent-navy" checked={picked.has(t.id)} onChange={() => toggle(t.id)} />
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium">{t.title}</div>
                <div className="flex gap-2 text-xs text-item">
                  {t.dueAt !== null && <span>Due {formatDateTime(t.dueAt)}</span>}
                  {t.notes && <span className="truncate">{t.notes}</span>}
                </div>
              </div>
              {t.priority !== "NONE" && <Badge tone={PRIORITY_TONE[t.priority]}>{t.priority.toLowerCase()}</Badge>}
            </label>
          ))}
        </div>
      )}
    </Dialog>
  );
}

const toLocalInput = (ms: number | null) => {
  if (ms === null) return "";
  const d = new Date(ms);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
};

export function AiTodosDialog({ open, listId, listName, onClose }: { open: boolean; listId: string | null; listName: string; onClose: () => void }) {
  const commit = useBrain((s) => s.commit);
  const toast = useBrain((s) => s.toast);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<(MotionTodoDraft & { keep: boolean })[] | null>(null);

  useEffect(() => {
    if (!open) {
      setDrafts(null);
      setError(null);
    }
  }, [open]);

  const read = async () => {
    setBusy(true);
    setError(null);
    try {
      const out = await parseTodos(text, listName);
      if (!out.length) setError("Gemini found no to-dos in that text");
      else setDrafts(out.map((d) => ({ ...d, keep: true })));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const patch = (i: number, p: Partial<MotionTodoDraft & { keep: boolean }>) => setDrafts((ds) => ds && ds.map((d, j) => (j === i ? { ...d, ...p } : d)));
  const kept = drafts?.filter((d) => d.keep && d.title.trim()) ?? [];

  const add = () => {
    commit((s) => kept.reduce((acc, d) => ops.createTask(acc, { title: d.title, notes: d.notes, priority: d.priority, dueAt: d.dueAt, target: d.target, unit: d.unit }, listId)[0], s));
    toast(`Added ${kept.length} item${kept.length === 1 ? "" : "s"}`);
    setText("");
    onClose();
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      width="max-w-2xl"
      dismissable={!busy}
      title={drafts ? "Suggested items" : `From text · ${listName}`}
      footer={
        drafts ? (
          <>
            <Button variant="secondary" className="mr-auto" onClick={() => setDrafts(null)}>Back</Button>
            <Button variant="secondary" onClick={onClose}>Cancel</Button>
            <Button disabled={kept.length === 0} onClick={add}>{kept.length === 1 ? "Add 1" : `Add ${kept.length}`}</Button>
          </>
        ) : (
          <>
            <Button variant="secondary" onClick={onClose} disabled={busy}>Cancel</Button>
            <Button onClick={() => void read()} disabled={busy || !text.trim()}><Sparkles size={14} /> {busy ? "Reading…" : "Read with Gemini"}</Button>
          </>
        )
      }
    >
      {drafts ? (
        <div className="space-y-2">
          <div className="text-xs text-item">Generated by Gemini, verify. Untick anything you don't want.</div>
          {drafts.map((d, i) => (
            <div key={i} className={cn("rounded-lg border border-hairline p-3", !d.keep && "opacity-50")}>
              <div className="flex items-center gap-2">
                <input type="checkbox" className="h-4 w-4 accent-navy" checked={d.keep} onChange={(e) => patch(i, { keep: e.target.checked })} />
                <Input value={d.title} onChange={(e) => patch(i, { title: e.target.value })} className="h-9" />
                <select
                  value={d.priority}
                  onChange={(e) => patch(i, { priority: e.target.value as MotionPriority })}
                  className="h-9 rounded-lg border border-hairline bg-white px-2 text-sm"
                >
                  {(["NONE", "LOW", "MEDIUM", "HIGH"] as const).map((p) => <option key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</option>)}
                </select>
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-2 pl-6 text-xs text-muted">
                <input
                  type="datetime-local"
                  value={toLocalInput(d.dueAt)}
                  onChange={(e) => patch(i, { dueAt: e.target.value ? new Date(e.target.value).getTime() : null })}
                  className="h-8 rounded-md border border-hairline px-2"
                />
                {d.target !== null && <span>Target {d.target}{d.unit ? ` ${d.unit}` : ""}</span>}
                {d.notes && <span className="truncate">{d.notes}</span>}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="space-y-2">
          <Textarea
            autoFocus
            rows={12}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && (e.ctrlKey || e.metaKey) && text.trim() && void read()}
            placeholder="Paste an email, meeting notes or a brain dump. Gemini turns it into to-dos with deadlines and priorities."
          />
          {error && <div className="text-sm text-danger">{error}</div>}
        </div>
      )}
    </Dialog>
  );
}
