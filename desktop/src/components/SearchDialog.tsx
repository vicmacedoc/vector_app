import { useEffect, useMemo, useState } from "react";
import { Brain, CheckSquare, FolderOpen, Search } from "lucide-react";
import { useBrain } from "@/store/brain";
import { useNav } from "@/store/nav";
import { openLink } from "@/ui/files";
import { Dialog } from "@/ui/primitives";
import { NoteKindIcon } from "./NoteKindIcon";

interface Hit {
  key: string;
  icon: React.ReactNode;
  title: string;
  subtitle: string;
  run: () => void;
}

/** In-memory search over tiles, sources and Motion items (Ctrl+K). */
export function SearchDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const brain = useBrain((s) => s.brain);
  const openAsset = useBrain((s) => s.openAsset);
  const go = useNav((s) => s.go);
  const [query, setQuery] = useState("");
  const [index, setIndex] = useState(0);

  useEffect(() => {
    if (open) {
      setQuery("");
      setIndex(0);
    }
  }, [open]);

  const hits = useMemo<Hit[]>(() => {
    const terms = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
    if (!terms.length) return [];
    const match = (...fields: (string | null | undefined)[]) => {
      const hay = fields.filter(Boolean).join(" ").toLowerCase();
      return terms.every((t) => hay.includes(t));
    };
    const nodeName = new Map(brain.nodes.map((n) => [n.id, n]));
    const out: Hit[] = [];
    for (const n of brain.nodes) {
      if (!match(n.name, n.tags.join(" "))) continue;
      const view = n.section === "MEMORY" ? "memory" : n.section === "MODEL" ? "model" : "motion";
      out.push({
        key: n.id, icon: n.section === "MODEL" ? <Brain size={16} /> : <FolderOpen size={16} />, title: n.name,
        subtitle: n.section === "MEMORY" ? "Memory tile" : n.section === "MODEL" ? "Learn tile" : "Motion list",
        run: () => go(view, n.id),
      });
    }
    for (const note of brain.notes) {
      if (!match(note.title, note.body, note.description, note.url, note.tags.join(" "))) continue;
      const node = note.nodeId ? nodeName.get(note.nodeId) : undefined;
      out.push({
        key: note.id, icon: <NoteKindIcon kind={note.kind} />, title: note.title || "Untitled",
        subtitle: node ? node.name : "Inbox",
        run: () => {
          if (note.kind === "LINK" && note.url) openLink(note.url);
          else if (note.assetId && note.kind !== "TEXT") void openAsset(note.assetId);
          if (!node) go("inbox");
          else go(node.section === "MODEL" ? "model" : "memory", node.id);
        },
      });
    }
    for (const task of brain.tasks) {
      if (task.status !== "OPEN" || !match(task.title, task.notes)) continue;
      const node = task.nodeId ? nodeName.get(task.nodeId) : undefined;
      out.push({
        key: task.id, icon: <CheckSquare size={16} />, title: task.title, subtitle: node?.name ?? "Inbox to-do",
        run: () => (node ? go("motion", node.id) : go("inbox")),
      });
    }
    return out.slice(0, 50);
  }, [brain, query, go, openAsset]);

  const pick = (hit: Hit | undefined) => {
    if (!hit) return;
    hit.run();
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} title={<span className="flex items-center gap-2"><Search size={16} /> Search Brain</span>} width="max-w-xl">
      <input
        autoFocus
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setIndex(0);
        }}
        onKeyDown={(e) => {
          if (e.key === "ArrowDown") setIndex((i) => Math.min(i + 1, hits.length - 1));
          if (e.key === "ArrowUp") setIndex((i) => Math.max(i - 1, 0));
          if (e.key === "Enter") pick(hits[index]);
        }}
        placeholder="Tiles, notes, links, files, to-dos…"
        className="mb-3 h-11 w-full rounded-lg border border-hairline px-3 text-sm outline-none focus:border-electric"
      />
      <div className="max-h-96 space-y-0.5 overflow-y-auto">
        {hits.map((h, i) => (
          <button
            key={h.key}
            onMouseEnter={() => setIndex(i)}
            onClick={() => pick(h)}
            className={`flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left ${i === index ? "bg-electric/10" : ""}`}
          >
            <span className="text-navy">{h.icon}</span>
            <span className="min-w-0 flex-1 truncate text-sm font-medium">{h.title}</span>
            <span className="truncate text-xs text-item">{h.subtitle}</span>
          </button>
        ))}
        {query.trim() && hits.length === 0 && <div className="py-6 text-center text-sm text-muted">Nothing found</div>}
      </div>
    </Dialog>
  );
}
