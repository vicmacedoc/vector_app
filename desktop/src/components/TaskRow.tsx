import { AlarmClock, Check, Link2, MoreHorizontal, Pencil, Repeat, Trash2 } from "lucide-react";
import { relativeDue } from "@/domain/dates";
import type { MotionTask } from "@/domain/models";
import { taskPointsAt } from "@/domain/motionScoring";
import * as ops from "@/domain/ops";
import { cn } from "@/lib/cn";
import { useBrain } from "@/store/brain";
import { Menu } from "@/ui/primitives";

const PRIORITY_RING: Record<MotionTask["priority"], string> = {
  HIGH: "border-prio-high",
  MEDIUM: "border-prio-mid",
  LOW: "border-prio-low",
  NONE: "border-item",
};

function snoozeTimes(): { label: string; at: number }[] {
  const now = new Date();
  const inHour = now.getTime() + 3_600_000;
  const tonight = new Date(now);
  tonight.setHours(19, 0, 0, 0);
  const tomorrow = new Date(now);
  tomorrow.setDate(tomorrow.getDate() + 1);
  tomorrow.setHours(9, 0, 0, 0);
  const nextWeek = new Date(tomorrow);
  nextWeek.setDate(nextWeek.getDate() + 6);
  const out = [{ label: "In 1 hour", at: inHour }];
  if (tonight.getTime() > inHour) out.push({ label: "This evening", at: tonight.getTime() });
  out.push({ label: "Tomorrow 9:00", at: tomorrow.getTime() }, { label: "Next week", at: nextWeek.getTime() });
  return out;
}

export function TaskRow({ task, onEdit, showList = false }: { task: MotionTask; onEdit: (t: MotionTask) => void; showList?: boolean }) {
  const commit = useBrain((s) => s.commit);
  const nodes = useBrain((s) => s.brain.nodes);
  const done = task.status === "DONE";
  const overdue = !done && task.dueAt !== null && task.dueAt < Date.now();
  const points = done ? task.earnedPoints ?? task.points : taskPointsAt(task, Date.now());
  const list = showList && task.nodeId ? nodes.find((n) => n.id === task.nodeId) : undefined;
  const tile = task.linkedTileId ? nodes.find((n) => n.id === task.linkedTileId) : undefined;
  const toggle = () => commit((s) => (done ? ops.uncheckTask(s, task.id) : ops.checkTask(s, task.id)));

  return (
    <div className={cn("group flex items-center gap-3 px-4 py-2.5 hover:bg-offwhite/70", done && "opacity-55")}>
      <button
        onClick={toggle}
        aria-label={done ? "Uncheck" : "Check"}
        className={cn(
          "flex h-5 w-5 shrink-0 items-center justify-center rounded-md border-2 transition",
          done ? "border-navy bg-navy text-white" : cn(PRIORITY_RING[task.priority], "hover:bg-offwhite"),
        )}
      >
        {done && <Check size={13} strokeWidth={3} />}
      </button>
      <button onClick={() => onEdit(task)} className="min-w-0 flex-1 text-left">
        <div className={cn("truncate text-sm", done ? "text-item line-through" : "text-black")}>{task.title}</div>
        <div className="mt-0.5 flex items-center gap-2 text-xs text-muted">
          {task.dueAt !== null && <span className={cn(overdue && "font-medium text-danger")}>{relativeDue(task.dueAt)}</span>}
          {task.recurrence !== "NONE" && <span className="flex items-center gap-0.5"><Repeat size={11} />{task.recurrence.toLowerCase()}</span>}
          {list && <span className="truncate">· {list.name}</span>}
          {tile && <span className="flex items-center gap-0.5 truncate text-electric"><Link2 size={11} />{tile.name}</span>}
          {task.notes && <span className="truncate text-item">{task.notes}</span>}
        </div>
      </button>
      {task.target !== null && (
        <div className="flex items-center gap-1.5 text-xs">
          <button className="h-6 w-6 rounded-md border border-hairline hover:bg-white" onClick={() => commit((s) => ops.setProgress(s, task.id, task.progress - 1))}>−</button>
          <span className="w-16 text-center tabular-nums">{task.progress}/{task.target}{task.unit ? ` ${task.unit}` : ""}</span>
          <button className="h-6 w-6 rounded-md border border-hairline hover:bg-white" onClick={() => commit((s) => ops.setProgress(s, task.id, task.progress + 1))}>+</button>
        </div>
      )}
      <span className={cn("w-12 text-right text-xs font-semibold tabular-nums", done ? "text-navy" : "text-electric")}>+{points}</span>
      <Menu
        trigger={<button className="rounded-md p-1.5 text-muted opacity-50 group-hover:opacity-100 hover:bg-white"><MoreHorizontal size={16} /></button>}
        items={[
          { label: "Edit", icon: <Pencil size={14} />, onSelect: () => onEdit(task) },
          ...(!done ? snoozeTimes().map((t) => ({ label: `Snooze · ${t.label}`, icon: <AlarmClock size={14} />, onSelect: () => commit((s) => ops.snoozeTask(s, task.id, t.at)) })) : []),
          { label: "Delete", icon: <Trash2 size={14} />, danger: true, onSelect: () => commit((s) => ops.deleteTask(s, task.id)) },
        ]}
      />
    </div>
  );
}
