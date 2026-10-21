import { useMemo, useState } from "react";
import { ChevronDown, Inbox, ListChecks, MoreHorizontal, Pencil, Plus, Sparkles, Trash2 } from "lucide-react";
import { AiTodosDialog, MergeTodosDialog } from "@/components/MotionImport";
import { TaskEditor, type TaskDraft } from "@/components/TaskEditor";
import { TaskRow } from "@/components/TaskRow";
import { startOfDay } from "@/domain/dates";
import type { MotionTask } from "@/domain/models";
import * as ops from "@/domain/ops";
import { cn } from "@/lib/cn";
import { useBrain } from "@/store/brain";
import { useNav } from "@/store/nav";
import { Button, Card, Confirm, Dialog, Empty, Input, Menu } from "@/ui/primitives";

const ALL = "__all";
const INBOX = "__inbox";

/** Phone order: due date ascending (no date first, as SQLite sorts nulls), then sortOrder. */
const byDue = (a: MotionTask, b: MotionTask) => (a.dueAt ?? -Infinity) - (b.dueAt ?? -Infinity) || a.sortOrder - b.sortOrder;

export function MotionScreen() {
  const brain = useBrain((s) => s.brain);
  const commit = useBrain((s) => s.commit);
  const { selected, go } = useNav();
  const [draft, setDraft] = useState<TaskDraft | null>(null);
  const [quick, setQuick] = useState("");
  const [naming, setNaming] = useState<{ id: string | null; name: string } | null>(null);
  const [deleting, setDeleting] = useState<string | null>(null);
  const [importing, setImporting] = useState<"merge" | "ai" | null>(null);

  const lists = brain.nodes.filter((n) => n.section === "MOTION").sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));
  const current = selected ?? ALL;
  const list = lists.find((l) => l.id === current);
  const todayStart = startOfDay();

  const counts = useMemo(() => {
    const m = new Map<string, number>();
    for (const t of brain.tasks) if (t.status === "OPEN") m.set(t.nodeId ?? INBOX, (m.get(t.nodeId ?? INBOX) ?? 0) + 1);
    return m;
  }, [brain.tasks]);
  const inboxCount = counts.get(INBOX) ?? 0;

  const tasks = brain.tasks
    .filter((t) => {
      if (current === ALL) return t.status === "OPEN";
      const inList = current === INBOX ? t.nodeId === null : t.nodeId === current;
      return inList && (t.status === "OPEN" || (t.status === "DONE" && (t.completedAt ?? 0) >= todayStart));
    })
    .sort((a, b) => Number(a.status === "DONE") - Number(b.status === "DONE") || byDue(a, b));

  const addQuick = () => {
    if (!quick.trim()) return;
    const nodeId = list ? list.id : null;
    commit((s) => ops.createTask(s, { title: quick }, nodeId)[0]);
    setQuick("");
  };

  const saveName = () => {
    if (!naming || !naming.name.trim()) return;
    if (naming.id) commit((s) => ops.renameNode(s, naming.id!, naming.name));
    else {
      let id = "";
      commit((s) => {
        const [next, created] = ops.createNode(s, "MOTION", naming.name);
        id = created;
        return next;
      });
      go("motion", id);
    }
    setNaming(null);
  };

  const navItem = (id: string, label: string, icon: React.ReactNode) => (
    <button
      key={id}
      onClick={() => go("motion", id === ALL ? null : id)}
      className={cn(
        "flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm transition",
        current === id ? "bg-navy text-white" : "text-black hover:bg-offwhite",
      )}
    >
      {icon}
      <span className="min-w-0 flex-1 truncate">{label}</span>
      <span className={cn("text-xs tabular-nums", current === id ? "text-white/70" : "text-item")}>{id === ALL ? [...counts.values()].reduce((a, b) => a + b, 0) : counts.get(id) ?? ""}</span>
    </button>
  );

  return (
    <div className="flex gap-6">
      <div className="w-60 shrink-0">
        <h1 className="mb-4 text-2xl font-semibold">Motion</h1>
        <div className="space-y-0.5">
          {navItem(ALL, "All open", <ListChecks size={16} />)}
          {navItem(INBOX, "Inbox", <Inbox size={16} />)}
        </div>
        <div className="mt-5 mb-1.5 flex items-center justify-between px-3 text-xs font-semibold tracking-wide text-muted uppercase">
          Lists
          <button onClick={() => setNaming({ id: null, name: "" })} className="text-electric"><Plus size={15} /></button>
        </div>
        <div className="space-y-0.5">
          {lists.map((l) => navItem(l.id, l.name, <span className="h-2 w-2 rounded-full bg-electric" />))}
          {lists.length === 0 && <div className="px-3 py-2 text-xs text-item">No lists yet</div>}
        </div>
      </div>

      <div className="min-w-0 flex-1">
        <div className="mb-4 flex items-center gap-2">
          <h2 className="flex-1 truncate text-lg font-semibold">{list?.name ?? (current === INBOX ? "Inbox" : "All open")}</h2>
          <Menu
            trigger={<Button size="sm"><Plus size={14} /> Add<ChevronDown size={13} /></Button>}
            items={[
              { label: "New item", icon: <Plus size={14} />, onSelect: () => setDraft({ mode: "new", nodeId: list?.id ?? null }) },
              ...(list ? [{ label: `Merge to-dos${inboxCount ? ` (${inboxCount})` : ""}`, icon: <Inbox size={14} />, onSelect: () => setImporting("merge") }] : []),
              { label: "From text (AI)", icon: <Sparkles size={14} />, onSelect: () => setImporting("ai") },
            ]}
          />
          {list && (
            <Menu
              trigger={<Button variant="secondary" size="icon"><MoreHorizontal size={16} /></Button>}
              items={[
                { label: "Rename list", icon: <Pencil size={14} />, onSelect: () => setNaming({ id: list.id, name: list.name }) },
                { label: "Delete list", icon: <Trash2 size={14} />, danger: true, onSelect: () => setDeleting(list.id) },
              ]}
            />
          )}
        </div>
        {current !== ALL && (
          <div className="mb-3">
            <Input value={quick} onChange={(e) => setQuick(e.target.value)} onKeyDown={(e) => e.key === "Enter" && addQuick()} placeholder="Add an item and press Enter" />
          </div>
        )}
        {tasks.length === 0 ? (
          <Empty icon={<ListChecks size={28} />} title="All clear" hint="Checked items stay here until tomorrow so you can undo them." />
        ) : (
          <Card className="divide-y divide-hairline">
            {tasks.map((t) => <TaskRow key={t.id} task={t} showList={current === ALL} onEdit={(task) => setDraft({ mode: "edit", task })} />)}
          </Card>
        )}
      </div>

      <TaskEditor draft={draft} onClose={() => setDraft(null)} />
      {list && <MergeTodosDialog open={importing === "merge"} listId={list.id} listName={list.name} onClose={() => setImporting(null)} />}
      <AiTodosDialog
        open={importing === "ai"}
        listId={list?.id ?? null}
        listName={list?.name ?? "Inbox"}
        onClose={() => setImporting(null)}
      />
      <Dialog open={naming !== null} onClose={() => setNaming(null)} title={naming?.id ? "Rename list" : "New list"} width="max-w-sm"
        footer={<><Button variant="secondary" onClick={() => setNaming(null)}>Cancel</Button><Button onClick={saveName} disabled={!naming?.name.trim()}>Save</Button></>}>
        <Input autoFocus value={naming?.name ?? ""} onChange={(e) => naming && setNaming({ ...naming, name: e.target.value })} onKeyDown={(e) => e.key === "Enter" && saveName()} placeholder="List name" />
      </Dialog>
      <Confirm
        open={deleting !== null}
        title="Delete list?"
        message="Its items are deleted too. Points already earned stay."
        onConfirm={() => {
          if (deleting) commit((s) => ops.deleteMotionList(s, deleting));
          go("motion");
        }}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}
