import { useMemo, useState } from "react";
import { Library, Plus } from "lucide-react";
import { NoteKindIcon } from "@/components/NoteKindIcon";
import { SourceList } from "@/components/SourceList";
import { TagEditor } from "@/components/TagEditor";
import { TileHeader } from "@/components/TileHeader";
import type { BrainSection, MemoryNoteKind } from "@/domain/models";
import * as ops from "@/domain/ops";
import { cn } from "@/lib/cn";
import { useBrain } from "@/store/brain";
import { useNav } from "@/store/nav";
import { Badge, Button, Card, Dialog, Empty, Input, Label, SearchInput } from "@/ui/primitives";

export function NewTileDialog({ open, section, onClose, onCreated }: { open: boolean; section: BrainSection; onClose: () => void; onCreated: (id: string) => void }) {
  const commit = useBrain((s) => s.commit);
  const [name, setName] = useState("");
  const [tags, setTags] = useState<string[]>([]);
  const create = () => {
    if (!name.trim()) return;
    let id = "";
    commit((s) => {
      const [next, created] = ops.createNode(s, section, name, tags);
      id = created;
      return next;
    });
    setName("");
    setTags([]);
    onClose();
    onCreated(id);
  };
  return (
    <Dialog open={open} onClose={onClose} title={section === "MODEL" ? "New Learn tile" : "New Memory tile"}
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button onClick={create} disabled={!name.trim()}>Create</Button></>}>
      <div className="space-y-4">
        <div>
          <Label>Name</Label>
          <Input autoFocus value={name} onChange={(e) => setName(e.target.value)} onKeyDown={(e) => e.key === "Enter" && create()} placeholder={section === "MODEL" ? "e.g. Linear algebra" : "e.g. Books"} />
        </div>
        <div>
          <Label>Tags</Label>
          <TagEditor section={section} value={tags} onChange={setTags} />
        </div>
      </div>
    </Dialog>
  );
}

export function TagFilter({ section, value, onChange }: { section: BrainSection; value: string | null; onChange: (t: string | null) => void }) {
  const nodes = useBrain((s) => s.brain.nodes);
  const used = useMemo(() => [...new Set(nodes.filter((n) => n.section === section).flatMap((n) => n.tags))].sort((a, b) => a.localeCompare(b)), [nodes, section]);
  if (!used.length) return null;
  return (
    <div className="flex flex-wrap gap-1.5">
      {[null, ...used].map((t) => (
        <button
          key={t ?? "__all"}
          onClick={() => onChange(t === value ? null : t)}
          className={cn(
            "rounded-full border px-3 py-1 text-xs transition",
            t === value ? "border-navy bg-navy text-white" : "border-hairline text-muted hover:border-electric/50 hover:text-black",
          )}
        >
          {t ?? "All"}
        </button>
      ))}
    </div>
  );
}

export function MemoryScreen() {
  const brain = useBrain((s) => s.brain);
  const { selected, go } = useNav();
  const [query, setQuery] = useState("");
  const [tag, setTag] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const stats = useMemo(() => {
    const m = new Map<string, { kinds: Map<MemoryNoteKind, number>; updated: number }>();
    for (const n of brain.notes) {
      if (!n.nodeId) continue;
      const s = m.get(n.nodeId) ?? { kinds: new Map(), updated: 0 };
      s.kinds.set(n.kind, (s.kinds.get(n.kind) ?? 0) + 1);
      s.updated = Math.max(s.updated, n.updatedAt);
      m.set(n.nodeId, s);
    }
    return m;
  }, [brain.notes]);

  const node = selected ? brain.nodes.find((n) => n.id === selected && n.section === "MEMORY") : undefined;
  if (node) {
    return (
      <div>
        <TileHeader node={node} onBack={() => go("memory")} deleteMessage="Its sources are deleted and their files moved to the Drive trash." />
        <SourceList nodeId={node.id} section="MEMORY" />
      </div>
    );
  }

  const q = query.trim().toLowerCase();
  const tiles = brain.nodes
    .filter((n) => n.section === "MEMORY")
    .filter((n) => !tag || n.tags.includes(tag))
    .filter((n) => !q || n.name.toLowerCase().includes(q) || n.tags.some((t) => t.toLowerCase().includes(q)))
    .sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Memory</h1>
          <div className="text-sm text-muted">Everything worth keeping, grouped into tiles.</div>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-64"><SearchInput value={query} onChange={setQuery} placeholder="Search tiles" /></div>
          <Button onClick={() => setCreating(true)}><Plus size={16} /> New tile</Button>
        </div>
      </div>
      <TagFilter section="MEMORY" value={tag} onChange={setTag} />
      {tiles.length === 0 ? (
        <Empty icon={<Library size={28} />} title={q || tag ? "No matching tiles" : "No Memory tiles yet"} hint="Tiles hold notes, links and files. They sync with the phone's Memory." action={<Button onClick={() => setCreating(true)}><Plus size={16} /> New tile</Button>} />
      ) : (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-4">
          {tiles.map((t) => {
            const s = stats.get(t.id);
            const total = s ? [...s.kinds.values()].reduce((a, b) => a + b, 0) : 0;
            return (
              <Card key={t.id} onClick={() => go("memory", t.id)} className="flex min-h-36 flex-col p-4">
                <div className="flex items-start justify-between gap-2">
                  <div className="line-clamp-2 font-semibold">{t.name}</div>
                  <span className="text-xs text-item tabular-nums">{total}</span>
                </div>
                <div className="mt-2 flex flex-wrap gap-1">{t.tags.slice(0, 4).map((x) => <Badge key={x}>{x}</Badge>)}</div>
                <div className="mt-auto flex gap-3 pt-3 text-xs text-muted">
                  {s && [...s.kinds].map(([kind, n]) => (
                    <span key={kind} className="flex items-center gap-1"><NoteKindIcon kind={kind} size={13} />{n}</span>
                  ))}
                </div>
              </Card>
            );
          })}
        </div>
      )}
      <NewTileDialog open={creating} section="MEMORY" onClose={() => setCreating(false)} onCreated={(id) => go("memory", id)} />
    </div>
  );
}
