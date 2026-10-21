import { useEffect, useState } from "react";
import { ArrowLeft, MoreHorizontal, Pencil, Trash2 } from "lucide-react";
import type { BrainNode } from "@/domain/models";
import * as ops from "@/domain/ops";
import { useBrain } from "@/store/brain";
import { Button, Confirm, Menu } from "@/ui/primitives";
import { TagEditor } from "./TagEditor";

/** Back button, inline rename, tags and delete for a Memory or Learn tile. */
export function TileHeader({ node, onBack, children, deleteMessage }: { node: BrainNode; onBack: () => void; children?: React.ReactNode; deleteMessage: string }) {
  const commit = useBrain((s) => s.commit);
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(node.name);
  const [confirm, setConfirm] = useState(false);
  useEffect(() => setName(node.name), [node.name]);

  const rename = () => {
    setEditing(false);
    if (name.trim() && name.trim() !== node.name) commit((s) => ops.renameNode(s, node.id, name));
    else setName(node.name);
  };

  return (
    <div className="mb-6">
      <button onClick={onBack} className="mb-3 flex items-center gap-1 text-sm text-muted hover:text-black"><ArrowLeft size={15} /> Back</button>
      <div className="flex items-start gap-3">
        <div className="min-w-0 flex-1">
          {editing ? (
            <input
              autoFocus
              value={name}
              onChange={(e) => setName(e.target.value)}
              onBlur={rename}
              onKeyDown={(e) => {
                if (e.key === "Enter") rename();
                if (e.key === "Escape") {
                  setName(node.name);
                  setEditing(false);
                }
              }}
              className="w-full border-b-2 border-electric bg-transparent text-2xl font-semibold outline-none"
            />
          ) : (
            <h1 onDoubleClick={() => setEditing(true)} className="truncate text-2xl font-semibold">{node.name}</h1>
          )}
          <div className="mt-3">
            <TagEditor section={node.section} value={node.tags} onChange={(tags) => commit((s) => ops.setNodeTags(s, node.id, tags))} />
          </div>
        </div>
        {children}
        <Menu
          trigger={<Button variant="secondary" size="icon"><MoreHorizontal size={16} /></Button>}
          items={[
            { label: "Rename", icon: <Pencil size={14} />, onSelect: () => setEditing(true) },
            { label: "Delete tile", icon: <Trash2 size={14} />, danger: true, onSelect: () => setConfirm(true) },
          ]}
        />
      </div>
      <Confirm
        open={confirm}
        title={`Delete “${node.name}”?`}
        message={deleteMessage}
        onConfirm={() => {
          commit((s) => ops.deleteTile(s, node.id));
          onBack();
        }}
        onClose={() => setConfirm(false)}
      />
    </div>
  );
}
