import { useEffect, useState } from "react";
import { basePoints, type MotionPriority, type MotionRecurrence, type MotionTask } from "@/domain/models";
import * as ops from "@/domain/ops";
import { useBrain } from "@/store/brain";
import { Button, Dialog, Input, Label, Segmented, Select, Textarea } from "@/ui/primitives";

export type TaskDraft = { mode: "new"; nodeId: string | null } | { mode: "edit"; task: MotionTask };

const PRIORITIES: { value: MotionPriority; label: string }[] = [
  { value: "NONE", label: "None" },
  { value: "LOW", label: "Low" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HIGH", label: "High" },
];

const RECURRENCES: { value: MotionRecurrence; label: string }[] = [
  { value: "NONE", label: "Once" },
  { value: "DAILY", label: "Daily" },
  { value: "WEEKLY", label: "Weekly" },
  { value: "MONTHLY", label: "Monthly" },
];

const pad = (n: number) => String(n).padStart(2, "0");
function toLocalInput(ms: number | null): string {
  if (ms === null) return "";
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
const fromLocalInput = (s: string): number | null => (s ? new Date(s).getTime() : null);

export function TaskEditor({ draft, onClose }: { draft: TaskDraft | null; onClose: () => void }) {
  const nodes = useBrain((s) => s.brain.nodes);
  const commit = useBrain((s) => s.commit);
  const [title, setTitle] = useState("");
  const [notes, setNotes] = useState("");
  const [priority, setPriority] = useState<MotionPriority>("NONE");
  const [due, setDue] = useState("");
  const [recurrence, setRecurrence] = useState<MotionRecurrence>("NONE");
  const [points, setPoints] = useState("");
  const [target, setTarget] = useState("");
  const [unit, setUnit] = useState("");
  const [nodeId, setNodeId] = useState<string>("");
  const [linkedTileId, setLinkedTileId] = useState<string>("");

  useEffect(() => {
    if (!draft) return;
    const t = draft.mode === "edit" ? draft.task : null;
    setTitle(t?.title ?? "");
    setNotes(t?.notes ?? "");
    setPriority(t?.priority ?? "NONE");
    setDue(toLocalInput(t?.dueAt ?? null));
    setRecurrence(t?.recurrence ?? "NONE");
    setPoints(t?.pointsEdited ? String(t.points) : "");
    setTarget(t?.target != null ? String(t.target) : "");
    setUnit(t?.unit ?? "");
    setNodeId((draft.mode === "edit" ? draft.task.nodeId : draft.nodeId) ?? "");
    setLinkedTileId(t?.linkedTileId ?? "");
  }, [draft]);

  if (!draft) return null;
  const lists = nodes.filter((n) => n.section === "MOTION").sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));
  const tiles = nodes.filter((n) => n.section === "MODEL").sort((a, b) => a.name.localeCompare(b.name));
  const dueAt = fromLocalInput(due);
  const parsedPoints = points.trim() ? Math.max(0, Math.round(Number(points))) : null;
  const parsedTarget = target.trim() ? Number(target) : null;

  const save = () => {
    if (!title.trim()) return;
    if (draft.mode === "new") {
      commit((s) => ops.createTask(s, {
        title, notes, priority, dueAt, recurrence: dueAt === null ? "NONE" : recurrence,
        points: parsedPoints, target: parsedTarget, unit, linkedTileId: linkedTileId || null,
      }, nodeId || null)[0]);
    } else {
      const t = draft.task;
      const base = basePoints(priority);
      commit((s) => ops.saveTask(s, {
        ...t,
        title: title.trim(),
        notes: notes.trim(),
        priority,
        dueAt,
        recurrence: dueAt === null ? "NONE" : recurrence,
        pointsEdited: parsedPoints !== null && parsedPoints !== base,
        points: parsedPoints ?? base,
        target: parsedTarget,
        unit: unit.trim() || null,
        nodeId: nodeId || null,
        linkedTileId: linkedTileId || null,
      }));
    }
    onClose();
  };

  return (
    <Dialog
      open
      onClose={onClose}
      title={draft.mode === "new" ? "New item" : "Edit item"}
      width="max-w-xl"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={save} disabled={!title.trim()}>Save</Button>
        </>
      }
    >
      <div className="space-y-4" onKeyDown={(e) => e.key === "Enter" && (e.ctrlKey || e.metaKey) && save()}>
        <div>
          <Label>Title</Label>
          <Input autoFocus value={title} onChange={(e) => setTitle(e.target.value)} onKeyDown={(e) => e.key === "Enter" && save()} placeholder="What needs doing?" />
        </div>
        <div>
          <Label>Priority · {parsedPoints ?? basePoints(priority)} pts base</Label>
          <Segmented value={priority} options={PRIORITIES} onChange={setPriority} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label>Due</Label>
            <Input type="datetime-local" value={due} onChange={(e) => setDue(e.target.value)} />
          </div>
          <div>
            <Label>Repeat</Label>
            <Select value={dueAt === null ? "NONE" : recurrence} options={RECURRENCES} onChange={setRecurrence} className="w-full" />
          </div>
        </div>
        <div className="grid grid-cols-3 gap-3">
          <div>
            <Label>Points override</Label>
            <Input type="number" min={0} value={points} onChange={(e) => setPoints(e.target.value)} placeholder={String(basePoints(priority))} />
          </div>
          <div>
            <Label>Target</Label>
            <Input type="number" min={0} value={target} onChange={(e) => setTarget(e.target.value)} placeholder="e.g. 30" />
          </div>
          <div>
            <Label>Unit</Label>
            <Input value={unit} onChange={(e) => setUnit(e.target.value)} placeholder="pages" />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label>List</Label>
            <Select value={nodeId} options={[{ value: "", label: "Inbox" }, ...lists.map((l) => ({ value: l.id, label: l.name }))]} onChange={setNodeId} className="w-full" />
          </div>
          <div>
            <Label>Linked Learn tile</Label>
            <Select value={linkedTileId} options={[{ value: "", label: "None" }, ...tiles.map((t) => ({ value: t.id, label: t.name }))]} onChange={setLinkedTileId} className="w-full" />
          </div>
        </div>
        <div>
          <Label>Notes</Label>
          <Textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Optional" />
        </div>
      </div>
    </Dialog>
  );
}
