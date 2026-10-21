// Home's left column: Brain counters, Motion score and the next open to-dos.
import { useMemo, useState } from "react";
import { AlarmClock, ArrowRight, Brain, CalendarClock, Flame, Inbox, Plus, Zap } from "lucide-react";
import { TaskEditor, type TaskDraft } from "@/components/TaskEditor";
import { TaskRow } from "@/components/TaskRow";
import { addDays, startOfDay, todayKey } from "@/domain/dates";
import { effectiveStatus } from "@/domain/learn";
import type { MotionPriority } from "@/domain/models";
import { streak } from "@/domain/motionScoring";
import { cn } from "@/lib/cn";
import { native } from "@/platform/native";
import { useBrain } from "@/store/brain";
import { useLearnSettings } from "@/store/learnSettings";
import { useNav } from "@/store/nav";
import { Button, Card } from "@/ui/primitives";

const UP_NEXT = 6;
const PRIORITY_RANK: readonly MotionPriority[] = ["NONE", "LOW", "MEDIUM", "HIGH"];

function Tile({ icon, label, value, tone, onClick }: { icon: React.ReactNode; label: string; value: number; tone?: string; onClick: () => void }) {
  return (
    <Card onClick={onClick} className="p-3">
      <div className="flex items-center gap-1.5 text-[11px] font-medium text-muted">{icon}{label}</div>
      <div className={cn("mt-1 text-2xl font-semibold tabular-nums", value > 0 && tone)}>{value}</div>
    </Card>
  );
}

export function BrainColumn() {
  const brain = useBrain((s) => s.brain);
  const go = useNav((s) => s.go);
  const rules = useLearnSettings();
  const [taskDraft, setTaskDraft] = useState<TaskDraft | null>(null);
  const today = todayKey();
  const target = brain.settings.motionDailyTarget;

  const byDate = useMemo(() => {
    const m = new Map<string, number>();
    for (const e of brain.scoreEvents) m.set(e.dateKey, (m.get(e.dateKey) ?? 0) + e.points);
    return m;
  }, [brain.scoreEvents]);
  const todayPoints = byDate.get(today) ?? 0;
  const week = Array.from({ length: 7 }, (_, i) => addDays(today, i - 6));
  const maxBar = Math.max(target, ...week.map((d) => byDate.get(d) ?? 0));

  const sod = startOfDay();
  const eod = sod + 86_400_000;
  const open = brain.tasks.filter((t) => t.status === "OPEN");
  const overdue = open.filter((t) => t.dueAt !== null && t.dueAt < sod).length;
  const dueToday = open.filter((t) => t.dueAt !== null && t.dueAt >= sod && t.dueAt < eod).length;
  const inbox = brain.notes.filter((n) => n.nodeId === null).length + open.filter((t) => t.nodeId === null).length;
  const activeTiles = brain.learningTiles.filter((t) => ["IN_PROGRESS", "TO_REVIEW"].includes(effectiveStatus(t, rules))).length;
  const upNext = open
    .filter((t) => t.nodeId !== null)
    .sort(
      (a, b) =>
        (a.dueAt ?? Number.MAX_SAFE_INTEGER) - (b.dueAt ?? Number.MAX_SAFE_INTEGER) ||
        PRIORITY_RANK.indexOf(b.priority) - PRIORITY_RANK.indexOf(a.priority),
    )
    .slice(0, UP_NEXT);

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3">
        <Tile icon={<Inbox size={13} />} label="Inbox" value={inbox} tone="text-electric" onClick={() => go("inbox")} />
        <Tile icon={<Brain size={13} />} label="Active tiles" value={activeTiles} onClick={() => go("model")} />
        <Tile icon={<AlarmClock size={13} />} label="Overdue" value={overdue} tone="text-danger" onClick={() => go("motion")} />
        <Tile icon={<CalendarClock size={13} />} label="Due today" value={dueToday} tone="text-navy" onClick={() => go("motion")} />
      </div>

      <Card className="p-4">
        <div className="flex items-center justify-between text-xs font-medium text-muted">
          <span>Motion score</span>
          <span className="flex items-center gap-1 text-navy"><Flame size={13} className="text-electric" /> {streak(byDate, today, target)} day streak</span>
        </div>
        <div className="mt-1 flex items-baseline gap-1.5">
          <span className="text-3xl font-semibold tabular-nums">{todayPoints}</span>
          <span className="text-sm text-item">/ {target} pts</span>
        </div>
        <div className="mt-3 flex h-14 items-end gap-1.5">
          {week.map((d) => {
            const pts = byDate.get(d) ?? 0;
            return (
              <div key={d} className="relative h-full flex-1 overflow-hidden rounded bg-offwhite" title={`${d}: ${pts} pts`}>
                <div className={cn("absolute bottom-0 w-full rounded", pts >= target ? "bg-navy" : "bg-electric/60")} style={{ height: `${(pts / maxBar) * 100}%` }} />
              </div>
            );
          })}
        </div>
      </Card>

      <Card>
        <div className="flex items-center justify-between border-b border-hairline px-4 py-3">
          <div className="font-semibold">Up next</div>
          <button onClick={() => go("motion")} className="flex items-center gap-1 text-xs text-electric">All lists <ArrowRight size={12} /></button>
        </div>
        {upNext.length === 0 ? (
          <div className="px-4 py-8 text-center text-sm text-muted">No open to-dos</div>
        ) : (
          <div className="divide-y divide-hairline">
            {upNext.map((t) => <TaskRow key={t.id} task={t} showList onEdit={(task) => setTaskDraft({ mode: "edit", task })} />)}
          </div>
        )}
        <div className="flex gap-2 border-t border-hairline p-3">
          <Button size="sm" variant="secondary" className="flex-1" onClick={() => setTaskDraft({ mode: "new", nodeId: null })}><Plus size={14} /> To-do</Button>
          <Button size="sm" variant="secondary" className="flex-1" title="Ctrl+Shift+Space from anywhere" onClick={() => void native.openCapture()}><Zap size={14} /> Capture</Button>
        </div>
      </Card>

      <TaskEditor draft={taskDraft} onClose={() => setTaskDraft(null)} />
    </div>
  );
}
