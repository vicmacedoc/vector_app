import { useEffect, useState } from "react";
import type { BrainSection, MemoryNote } from "@/domain/models";
import * as ops from "@/domain/ops";
import { cn } from "@/lib/cn";
import { useBrain } from "@/store/brain";
import { Button, Dialog, Input, Label, Segmented, Textarea } from "@/ui/primitives";
import { Markdown } from "./Markdown";
import { TagEditor } from "./TagEditor";

type BodyView = "write" | "split" | "preview";
const VIEW_KEY = "vector:noteEditorView";

export type NoteDraft = { mode: "new-text" } | { mode: "new-link" } | { mode: "edit"; note: MemoryNote };

/** Create/edit dialog for a source. Text bodies are Markdown, as on the phone. */
export function NoteEditor({
  draft, nodeId, section, onClose,
}: {
  draft: NoteDraft | null;
  nodeId: string | null;
  section: BrainSection;
  onClose: () => void;
}) {
  const commit = useBrain((s) => s.commit);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [url, setUrl] = useState("");
  const [description, setDescription] = useState("");
  const [tags, setTags] = useState<string[]>([]);
  const [view, setViewState] = useState<BodyView>(() => (localStorage.getItem(VIEW_KEY) as BodyView | null) ?? "split");
  const setView = (v: BodyView) => {
    localStorage.setItem(VIEW_KEY, v);
    setViewState(v);
  };

  useEffect(() => {
    const n = draft?.mode === "edit" ? draft.note : null;
    setTitle(n?.title ?? "");
    setBody(n?.body ?? "");
    setUrl(n?.url ?? "");
    setDescription(n?.description ?? "");
    setTags(n?.tags ?? []);
  }, [draft]);

  if (!draft) return null;
  const isLink = draft.mode === "new-link" || (draft.mode === "edit" && draft.note.kind === "LINK");
  const isText = draft.mode === "new-text" || (draft.mode === "edit" && draft.note.kind === "TEXT");
  const canSave = isLink ? url.trim().length > 0 : title.trim().length > 0 || body.trim().length > 0;

  const save = () => {
    if (!canSave) return;
    if (draft.mode === "new-text") {
      commit((s) => {
        const [next, id] = ops.createTextNote(s, title.trim() || body.trim().split("\n")[0].slice(0, 80), body, nodeId, tags);
        return description ? ops.saveNote(next, { ...next.notes.find((n) => n.id === id)!, description }) : next;
      });
    } else if (draft.mode === "new-link") {
      commit((s) => {
        const [next, id] = ops.createLinkNote(s, title, url.trim(), nodeId);
        const note = next.notes.find((n) => n.id === id)!;
        return tags.length || description ? ops.saveNote(next, { ...note, tags, description }) : next;
      });
    } else {
      commit((s) => ops.saveNote(s, { ...draft.note, title: title.trim() || draft.note.title, body, url: isLink ? url.trim() : draft.note.url, description, tags }));
    }
    onClose();
  };

  return (
    <Dialog
      open
      onClose={onClose}
      width={isText && view === "split" ? "max-w-6xl" : "max-w-3xl"}
      title={draft.mode === "edit" ? "Edit source" : isLink ? "New link" : "New note"}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={save} disabled={!canSave}>Save</Button>
        </>
      }
    >
      <div
        className="space-y-4"
        onKeyDown={(e) => {
          if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) save();
        }}
      >
        {isLink && (
          <div>
            <Label>URL</Label>
            <Input autoFocus value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://" />
          </div>
        )}
        <div>
          <Label>Title</Label>
          <Input autoFocus={!isLink} value={title} onChange={(e) => setTitle(e.target.value)} placeholder={isLink ? "Defaults to the URL" : "Title"} />
        </div>
        {(isText || (draft.mode === "edit" && draft.note.body)) && (
          <div>
            <div className="mb-1.5 flex items-center justify-between">
              <Label className="mb-0">Body · Markdown</Label>
              <Segmented<BodyView>
                value={view}
                onChange={setView}
                options={[{ value: "write", label: "Write" }, { value: "split", label: "Split" }, { value: "preview", label: "Preview" }]}
              />
            </div>
            <div className={cn("grid gap-3", view === "split" && "grid-cols-2")}>
              {view !== "preview" && (
                <Textarea value={body} onChange={(e) => setBody(e.target.value)} rows={18} className="font-mono text-[13px] leading-relaxed" placeholder="Write…" />
              )}
              {view !== "write" && (
                <div className="h-[27rem] overflow-y-auto rounded-lg border border-hairline px-4 py-3">
                  {body.trim() ? <Markdown>{body}</Markdown> : <div className="text-sm text-item">Nothing to preview</div>}
                </div>
              )}
            </div>
          </div>
        )}
        <div>
          <Label>Description</Label>
          <Input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Optional" />
        </div>
        <div>
          <Label>Tags</Label>
          <TagEditor section={section === "MODEL" ? "MODEL" : "MEMORY"} value={tags} onChange={setTags} />
        </div>
        <div className="text-xs text-item">Ctrl+Enter to save</div>
      </div>
    </Dialog>
  );
}
