import { useEffect, useMemo, useState } from "react";
import {
  ArrowRightLeft, ExternalLink, FilePlus2, Link2, MoreHorizontal, NotebookPen, Pencil, Pin, PinOff, Trash2, Upload,
} from "lucide-react";
import { formatDateTime } from "@/domain/dates";
import type { BrainSection, MemoryNote } from "@/domain/models";
import * as ops from "@/domain/ops";
import { cn } from "@/lib/cn";
import { useBrain } from "@/store/brain";
import { formatBytes, openLink, pickFiles, useFileDrop } from "@/ui/files";
import { Badge, Button, Confirm, Dialog, Empty, Menu, SearchInput } from "@/ui/primitives";
import { AudioPlayer } from "./AudioPlayer";
import { Markdown } from "./Markdown";
import { KIND_LABEL, NoteKindIcon } from "./NoteKindIcon";
import { NoteEditor, type NoteDraft } from "./NoteEditor";

function ImageThumb({ assetId }: { assetId: string }) {
  const preview = useBrain((s) => s.assetPreview);
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    void preview(assetId).then((p) => alive && setSrc(p));
    return () => {
      alive = false;
    };
  }, [assetId, preview]);
  return src ? <img src={src} className="h-12 w-12 rounded-lg object-cover" /> : <div className="h-12 w-12 rounded-lg bg-offwhite" />;
}

/** Sources of a tile (or the Inbox when [nodeId] is null): notes, links and files, with drag-and-drop. */
export function SourceList({ nodeId, section, focusNoteId }: { nodeId: string | null; section: BrainSection; focusNoteId?: string | null }) {
  const brain = useBrain((s) => s.brain);
  const commit = useBrain((s) => s.commit);
  const addFiles = useBrain((s) => s.addFiles);
  const openAsset = useBrain((s) => s.openAsset);
  const toast = useBrain((s) => s.toast);
  const [draft, setDraft] = useState<NoteDraft | null>(null);
  const [query, setQuery] = useState("");
  const [deleting, setDeleting] = useState<MemoryNote | null>(null);
  const [moving, setMoving] = useState<MemoryNote | null>(null);
  const [viewing, setViewing] = useState<MemoryNote | null>(null);
  const [listening, setListening] = useState<MemoryNote | null>(null);

  useEffect(() => {
    const note = focusNoteId ? useBrain.getState().brain.notes.find((n) => n.id === focusNoteId) : undefined;
    if (note) setViewing(note);
  }, [focusNoteId]);

  const assets = useMemo(() => new Map(brain.assets.map((a) => [a.id, a])), [brain.assets]);
  const notes = useMemo(() => {
    const q = query.trim().toLowerCase();
    return brain.notes
      .filter((n) => n.nodeId === nodeId)
      .filter((n) => !q || [n.title, n.body, n.description, n.url ?? "", n.tags.join(" ")].some((f) => f.toLowerCase().includes(q)))
      .sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.updatedAt - a.updatedAt);
  }, [brain.notes, nodeId, query]);

  const add = async (paths: string[]) => {
    const n = await addFiles(paths, nodeId);
    if (n) toast(`Added ${n} file${n === 1 ? "" : "s"} · uploading`);
  };
  const hovering = useFileDrop((paths) => void add(paths), draft === null);

  const open = (note: MemoryNote) => {
    if (note.kind === "LINK" && note.url) openLink(note.url);
    else if (note.kind === "AUDIO" && note.assetId) setListening(note);
    else if (note.assetId && note.kind !== "TEXT") void openAsset(note.assetId);
    else setViewing(note);
  };

  return (
    <div className={cn("relative rounded-xl transition", hovering && "ring-2 ring-electric ring-offset-4")}>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <Button size="sm" onClick={() => setDraft({ mode: "new-text" })}><NotebookPen size={15} /> Note</Button>
        <Button size="sm" variant="secondary" onClick={() => setDraft({ mode: "new-link" })}><Link2 size={15} /> Link</Button>
        <Button size="sm" variant="secondary" onClick={async () => void add(await pickFiles())}><FilePlus2 size={15} /> Files</Button>
        <div className="ml-auto w-64"><SearchInput value={query} onChange={setQuery} placeholder="Filter sources" /></div>
      </div>

      {hovering && (
        <div className="pointer-events-none absolute inset-0 z-10 flex items-center justify-center rounded-xl bg-electric/5">
          <div className="flex items-center gap-2 rounded-full bg-electric px-4 py-2 text-sm font-medium text-white"><Upload size={16} /> Drop to add</div>
        </div>
      )}

      {notes.length === 0 ? (
        <Empty icon={<Upload size={28} />} title={query ? "No matching sources" : "No sources yet"} hint="Write a note, paste a link, or drop files anywhere on this page." />
      ) : (
        <div className="divide-y divide-hairline rounded-xl border border-hairline">
          {notes.map((note) => {
            const asset = note.assetId ? assets.get(note.assetId) : undefined;
            return (
              <div key={note.id} className="group flex items-center gap-3 px-4 py-3 hover:bg-offwhite/70">
                <button onClick={() => open(note)} className="flex min-w-0 flex-1 items-center gap-3 text-left">
                  {note.kind === "IMAGE" && asset ? (
                    <ImageThumb assetId={asset.id} />
                  ) : (
                    <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-offwhite text-navy"><NoteKindIcon kind={note.kind} size={20} /></div>
                  )}
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      {note.pinned && <Pin size={12} className="shrink-0 text-electric" />}
                      <span className="truncate font-medium">{note.title || KIND_LABEL[note.kind]}</span>
                    </div>
                    <div className="mt-0.5 flex items-center gap-2 truncate text-xs text-muted">
                      <span>{KIND_LABEL[note.kind]}</span>
                      <span>·</span>
                      <span>{formatDateTime(note.updatedAt)}</span>
                      {asset && <><span>·</span><span>{formatBytes(asset.sizeBytes)}</span></>}
                      {asset && !asset.driveFileId && <Badge tone="electric">uploading</Badge>}
                      {note.kind === "LINK" && note.url && <span className="truncate text-electric">{note.url}</span>}
                      {note.kind === "TEXT" && note.body && <span className="truncate">{note.body.slice(0, 120)}</span>}
                    </div>
                  </div>
                </button>
                <div className="hidden gap-1 md:flex">
                  {note.tags.slice(0, 3).map((t) => <Badge key={t}>{t}</Badge>)}
                </div>
                <Menu
                  trigger={<Button variant="ghost" size="icon" className="opacity-60 group-hover:opacity-100"><MoreHorizontal size={16} /></Button>}
                  items={[
                    { label: "Open", icon: <ExternalLink size={14} />, onSelect: () => open(note) },
                    { label: "Edit", icon: <Pencil size={14} />, onSelect: () => setDraft({ mode: "edit", note }) },
                    {
                      label: note.pinned ? "Unpin" : "Pin",
                      icon: note.pinned ? <PinOff size={14} /> : <Pin size={14} />,
                      onSelect: () => commit((s) => ops.saveNote(s, { ...note, pinned: !note.pinned })),
                    },
                    { label: "Move to…", icon: <ArrowRightLeft size={14} />, onSelect: () => setMoving(note) },
                    { label: "Delete", icon: <Trash2 size={14} />, danger: true, onSelect: () => setDeleting(note) },
                  ]}
                />
              </div>
            );
          })}
        </div>
      )}

      <NoteEditor draft={draft} nodeId={nodeId} section={section} onClose={() => setDraft(null)} />
      <MoveDialog note={moving} onClose={() => setMoving(null)} />
      <Confirm
        open={deleting !== null}
        title="Delete source?"
        message={deleting?.assetId ? "The file is also moved to the Drive trash." : "This can't be undone."}
        onConfirm={() => deleting && commit((s) => ops.deleteNote(s, deleting.id))}
        onClose={() => setDeleting(null)}
      />
      <Dialog open={viewing !== null} onClose={() => setViewing(null)} title={viewing?.title ?? ""} width="max-w-2xl"
        footer={<Button variant="secondary" onClick={() => { const n = viewing; setViewing(null); if (n) setDraft({ mode: "edit", note: n }); }}><Pencil size={14} /> Edit</Button>}>
        {viewing?.description && viewing.body && <div className="mb-3 text-sm text-muted">{viewing.description}</div>}
        {viewing?.body || viewing?.description ? <Markdown>{viewing.body || viewing.description}</Markdown> : <div className="text-sm text-item">Empty note</div>}
      </Dialog>
      <AudioPlayer note={listening} onClose={() => setListening(null)} />
    </div>
  );
}

function MoveDialog({ note, onClose }: { note: MemoryNote | null; onClose: () => void }) {
  const nodes = useBrain((s) => s.brain.nodes);
  const commit = useBrain((s) => s.commit);
  const targets = nodes.filter((n) => n.section !== "MOTION").sort((a, b) => a.section.localeCompare(b.section) || a.name.localeCompare(b.name));
  const move = (nodeId: string | null) => {
    if (note) commit((s) => ops.moveNotes(s, [note.id], nodeId));
    onClose();
  };
  return (
    <Dialog open={note !== null} onClose={onClose} title="Move to" width="max-w-sm">
      <div className="space-y-1">
        <button onClick={() => move(null)} className="w-full rounded-lg px-3 py-2 text-left text-sm hover:bg-offwhite">Inbox</button>
        {targets.map((n) => (
          <button key={n.id} disabled={n.id === note?.nodeId} onClick={() => move(n.id)} className="flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm hover:bg-offwhite disabled:opacity-40">
            <span>{n.name}</span>
            <span className="text-xs text-item">{n.section === "MODEL" ? "Model" : "Memory"}</span>
          </button>
        ))}
      </div>
    </Dialog>
  );
}
