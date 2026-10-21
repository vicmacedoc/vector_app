import { useMemo, useState } from "react";
import { ArrowRightLeft, CheckSquare, Plus } from "lucide-react";
import { SourceList } from "@/components/SourceList";
import { TaskEditor, type TaskDraft } from "@/components/TaskEditor";
import { TaskRow } from "@/components/TaskRow";
import * as ops from "@/domain/ops";
import { useBrain } from "@/store/brain";
import { Button, Card, Dialog } from "@/ui/primitives";

/** Unsorted captures: sources without a tile and to-dos without a list. */
export function InboxScreen() {
  const brain = useBrain((s) => s.brain);
  const commit = useBrain((s) => s.commit);
  const [taskDraft, setTaskDraft] = useState<TaskDraft | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [moving, setMoving] = useState(false);
  const todos = useMemo(
    () => brain.tasks.filter((t) => t.nodeId === null && t.status === "OPEN").sort((a, b) => a.createdAt - b.createdAt),
    [brain.tasks],
  );
  const lists = brain.nodes.filter((n) => n.section === "MOTION").sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));
  const toggle = (id: string) => setSelected((s) => {
    const next = new Set(s);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    return next;
  });

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold">Inbox</h1>
        <div className="text-sm text-muted">Quick captures from the phone, the watch and this computer, waiting for a home.</div>
      </div>

      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="flex items-center gap-2 font-semibold"><CheckSquare size={17} /> To-dos <span className="text-sm font-normal text-item">{todos.length}</span></h2>
          <div className="flex gap-2">
            {selected.size > 0 && (
              <Button size="sm" variant="accent" onClick={() => setMoving(true)}><ArrowRightLeft size={14} /> Move {selected.size} to list</Button>
            )}
            <Button size="sm" variant="secondary" onClick={() => setTaskDraft({ mode: "new", nodeId: null })}><Plus size={14} /> To-do</Button>
          </div>
        </div>
        {todos.length === 0 ? (
          <Card className="px-4 py-6 text-center text-sm text-muted">No loose to-dos</Card>
        ) : (
          <Card className="divide-y divide-hairline">
            {todos.map((t) => (
              <div key={t.id} className="flex items-center">
                <input type="checkbox" className="ml-4 accent-[#0074d9]" checked={selected.has(t.id)} onChange={() => toggle(t.id)} aria-label="Select" />
                <div className="min-w-0 flex-1"><TaskRow task={t} onEdit={(task) => setTaskDraft({ mode: "edit", task })} /></div>
              </div>
            ))}
          </Card>
        )}
      </section>

      <section>
        <h2 className="mb-3 font-semibold">Sources</h2>
        <SourceList nodeId={null} section="MEMORY" />
      </section>

      <TaskEditor draft={taskDraft} onClose={() => setTaskDraft(null)} />
      <Dialog open={moving} onClose={() => setMoving(false)} title="Move to list" width="max-w-sm">
        {lists.length === 0 ? <div className="text-sm text-muted">Create a list in Motion first.</div> : (
          <div className="space-y-1">
            {lists.map((l) => (
              <button
                key={l.id}
                className="w-full rounded-lg px-3 py-2 text-left text-sm hover:bg-offwhite"
                onClick={() => {
                  commit((s) => ops.moveTasks(s, [...selected], l.id));
                  setSelected(new Set());
                  setMoving(false);
                }}
              >
                {l.name}
              </button>
            ))}
          </div>
        )}
      </Dialog>
    </div>
  );
}
