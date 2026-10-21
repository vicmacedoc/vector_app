// Self day: mood + scales, reflections, journal (text and voice) and Drive photo albums.
import { useEffect, useMemo, useRef, useState } from "react";
import { FolderPlus, ImagePlus, Images, Mic, RefreshCw, Square, Trash2 } from "lucide-react";
import { cn } from "@/lib/cn";
import { useRecorder } from "@/components/VoiceCapture";
import type { SelfDraft } from "@/domain/daily/drafts";
import { MOOD_EMOJI, SECONDARY_SCALES, SELF_SCALES } from "@/domain/daily/self";
import {
  createAlbum, deleteDriveFile, driveFileSrc, imageTakenAt, listAlbums, uploadAlbumImage, type Album,
} from "@/drive/self";
import { errorMessage } from "@/platform/native";
import { useBrain } from "@/store/brain";
import { useCalendar } from "@/store/calendar";
import { Button, Confirm, Dialog, Input, Textarea } from "@/ui/primitives";
import { Section } from "./ui";

const MOOD = SELF_SCALES.find((s) => s.id === "mood")!;

function ScaleRow({ label, low, high, value, onChange, disabled }: { label: string; low: string; high: string; value: number | null; onChange: (v: number | null) => void; disabled: boolean }) {
  return (
    <div className="grid grid-cols-[110px_1fr] items-center gap-3">
      <div className="text-sm font-medium">{label}</div>
      <div>
        <div className="flex gap-1">
          {[1, 2, 3, 4, 5].map((n) => (
            <button
              key={n}
              disabled={disabled}
              onClick={() => onChange(value === n ? null : n)}
              className={cn(
                "h-8 flex-1 rounded-md border text-xs font-semibold transition",
                value === n ? "border-navy bg-navy text-white" : value !== null && n < value ? "border-navy/20 bg-navy/10 text-navy" : "border-hairline bg-white text-muted hover:border-navy/40",
              )}
            >
              {n}
            </button>
          ))}
        </div>
        <div className="mt-0.5 flex justify-between text-[10px] text-item">
          <span>{low}</span>
          <span>{high}</span>
        </div>
      </div>
    </div>
  );
}

const words = (t: string) => t.trim().split(/\s+/).filter(Boolean).length;

export function SelfView() {
  const slot = useCalendar((s) => s.SELF);
  const editing = useCalendar((s) => s.editing);
  const update = useCalendar((s) => s.update);
  const selfAudio = useCalendar((s) => s.selfAudio);
  const setSelfAudio = useCalendar((s) => s.setSelfAudio);
  if (!slot) return null;
  const cur = slot.current;
  const edit = (fn: (d: SelfDraft) => SelfDraft) => update("SELF", fn);
  const setScale = (id: string, v: number | null) =>
    edit((d) => {
      const scales = { ...d.scales };
      if (v === null) delete scales[id];
      else scales[id] = v;
      return { ...d, scales };
    });
  const setText = (k: "intention" | "highlight" | "lowlight" | "gratitude" | "journalText", v: string) => edit((d) => ({ ...d, [k]: v }));
  const total = words(cur.journalText) + words(cur.highlight) + words(cur.lowlight) + words(cur.gratitude);

  return (
    <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-6">
      <div className="space-y-6">
        <Section title="How are you?">
          <div className="mb-5 flex justify-center gap-3">
            {MOOD_EMOJI.map((e, i) => {
              const v = i + 1;
              const active = cur.scales[MOOD.id] === v;
              return (
                <button
                  key={e}
                  disabled={!editing}
                  onClick={() => setScale(MOOD.id, active ? null : v)}
                  className={cn("flex h-14 w-14 items-center justify-center rounded-2xl border-2 text-3xl transition", active ? "border-navy bg-navy/10 scale-110" : "border-transparent opacity-60 hover:opacity-100")}
                  title={`${MOOD.label} ${v}`}
                >
                  {e}
                </button>
              );
            })}
          </div>
          <div className="space-y-3">
            {SECONDARY_SCALES.map((s) => (
              <ScaleRow key={s.id} label={s.label} low={s.lowLabel} high={s.highLabel} value={cur.scales[s.id] ?? null} disabled={!editing} onChange={(v) => setScale(s.id, v)} />
            ))}
          </div>
        </Section>
      </div>

      <div className="space-y-6">
        <Section title="Reflection" actions={<span className="text-xs text-item">{total} words</span>}>
          <div className="space-y-3">
            {([
              ["intention", "Intention", "What do you want from today?"],
              ["highlight", "Highlight", "Best moment"],
              ["lowlight", "Lowlight", "Hardest moment"],
              ["gratitude", "Gratitude", "Grateful for…"],
            ] as const).map(([k, label, ph]) => (
              <label key={k} className="block">
                <span className="mb-1 block text-[11px] font-semibold tracking-wide text-muted uppercase">{label}</span>
                <Textarea rows={2} value={cur[k]} disabled={!editing} placeholder={ph} onChange={(e) => setText(k, e.target.value)} />
              </label>
            ))}
            <label className="block">
              <span className="mb-1 block text-[11px] font-semibold tracking-wide text-muted uppercase">Journal</span>
              <Textarea rows={10} className="resize-y font-[450] leading-relaxed" value={cur.journalText} disabled={!editing} placeholder="Write freely…" onChange={(e) => setText("journalText", e.target.value)} />
            </label>
            <JournalAudio date={slot.date} fileId={cur.audioDriveFileId} pending={selfAudio} editing={editing} onRecord={setSelfAudio} onRemove={() => {
              setSelfAudio(null);
              edit((d) => ({ ...d, audioDriveFileId: null }));
            }} />
          </div>
        </Section>
        <AlbumsPanel />
      </div>
    </div>
  );
}

function JournalAudio({
  date, fileId, pending, editing, onRecord, onRemove,
}: {
  date: string;
  fileId: string | null;
  pending: Blob | null;
  editing: boolean;
  onRecord: (b: Blob | null) => void;
  onRemove: () => void;
}) {
  const { recording, elapsed, start, stop } = useRecorder();
  const toast = useBrain((s) => s.toast);
  const [src, setSrc] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const pendingUrl = useMemo(() => (pending ? URL.createObjectURL(pending) : null), [pending]);
  useEffect(() => () => void (pendingUrl && URL.revokeObjectURL(pendingUrl)), [pendingUrl]);
  useEffect(() => setSrc(null), [fileId]);

  const load = async () => {
    if (!fileId) return;
    setLoading(true);
    try {
      setSrc(await driveFileSrc(fileId, `${date}.audio`));
    } catch (e) {
      toast(`Couldn't load the recording: ${errorMessage(e)}`, "error");
    } finally {
      setLoading(false);
    }
  };

  const toggle = async () => {
    if (!recording) {
      try {
        await start();
      } catch (e) {
        toast(`Microphone unavailable: ${errorMessage(e)}`, "error");
      }
      return;
    }
    const blob = await stop();
    if (blob) onRecord(blob);
  };

  return (
    <div className="rounded-lg bg-offwhite p-3">
      <div className="flex items-center gap-2">
        <span className="flex-1 text-[11px] font-semibold tracking-wide text-muted uppercase">Voice journal</span>
        {editing && (
          <Button size="sm" variant={recording ? "danger" : "secondary"} onClick={() => void toggle()}>
            {recording ? <Square size={12} className="fill-current" /> : <Mic size={14} />}
            {recording ? `${Math.floor(elapsed / 60)}:${String(elapsed % 60).padStart(2, "0")}` : pending || fileId ? "Re-record" : "Record"}
          </Button>
        )}
        {editing && (pending || fileId) && (
          <Button size="icon" variant="ghost" className="h-8 w-8 text-danger" title="Remove recording" onClick={onRemove}><Trash2 size={14} /></Button>
        )}
      </div>
      {pendingUrl ? (
        <div className="mt-2">
          <audio controls src={pendingUrl} className="w-full" />
          <div className="mt-1 text-[11px] text-item">New recording — uploads to Drive when you save.</div>
        </div>
      ) : fileId ? (
        src ? (
          <audio controls src={src} className="mt-2 w-full" autoPlay />
        ) : (
          <Button size="sm" variant="ghost" className="mt-2" disabled={loading} onClick={() => void load()}>
            {loading ? "Loading…" : "Play saved recording"}
          </Button>
        )
      ) : (
        <div className="mt-1 text-xs text-item">No recording for this day.</div>
      )}
    </div>
  );
}

function Thumb({ fileId, name, onOpen }: { fileId: string; name: string; onOpen: (src: string) => void }) {
  const [src, setSrc] = useState<string | null>(null);
  const ref = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    let cancelled = false;
    const io = new IntersectionObserver(([e]) => {
      if (!e.isIntersecting) return;
      io.disconnect();
      driveFileSrc(fileId, name).then((s) => !cancelled && setSrc(s)).catch(() => {});
    });
    io.observe(el);
    return () => {
      cancelled = true;
      io.disconnect();
    };
  }, [fileId, name]);
  return (
    <button ref={ref} onClick={() => src && onOpen(src)} className="aspect-square overflow-hidden rounded-lg border border-hairline bg-offwhite">
      {src ? <img src={src} alt={name} className="h-full w-full object-cover" /> : <div className="h-full w-full animate-pulse" />}
    </button>
  );
}

function AlbumsPanel() {
  const toast = useBrain((s) => s.toast);
  const signedIn = useBrain((s) => s.signedIn);
  const [albums, setAlbums] = useState<Album[] | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [viewer, setViewer] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<{ id: string; name: string } | null>(null);
  const file = useRef<HTMLInputElement>(null);

  const refresh = async () => {
    setBusy(true);
    try {
      setAlbums(await listAlbums());
    } catch (e) {
      toast(`Albums: ${errorMessage(e)}`, "error");
    } finally {
      setBusy(false);
    }
  };
  useEffect(() => {
    if (signedIn) void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signedIn]);

  const album = albums?.find((a) => a.id === open) ?? null;

  const upload = async (files: FileList) => {
    if (!album) return;
    setBusy(true);
    try {
      for (const f of [...files]) await uploadAlbumImage(album.id, f, new Date(f.lastModified || Date.now()));
      toast(`Uploaded ${files.length} image(s)`);
      await refresh();
    } catch (e) {
      toast(`Upload failed: ${errorMessage(e)}`, "error");
      setBusy(false);
    }
  };

  return (
    <Section
      title={album ? <button className="hover:underline" onClick={() => setOpen(null)}>Albums</button> : "Albums"}
      actions={
        <div className="flex items-center gap-1">
          {album && <span className="mr-1 text-sm font-semibold">/ {album.name}</span>}
          <Button size="icon" variant="ghost" className="h-8 w-8" title="Refresh" disabled={busy} onClick={() => void refresh()}>
            <RefreshCw size={14} className={cn(busy && "animate-spin")} />
          </Button>
          {album ? (
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => file.current?.click()}><ImagePlus size={14} /> Add</Button>
          ) : (
            <Button size="sm" variant="secondary" onClick={() => setCreating(true)}><FolderPlus size={14} /> New</Button>
          )}
        </div>
      }
    >
      {!signedIn ? (
        <div className="text-sm text-muted">Sign in to Google Drive to see your albums.</div>
      ) : !albums ? (
        <div className="text-sm text-muted">Loading…</div>
      ) : album ? (
        album.images.length === 0 ? (
          <div className="text-sm text-muted">No images yet.</div>
        ) : (
          <div className="grid grid-cols-4 gap-2">
            {[...album.images].reverse().map((img) => (
              <div key={img.id} className="group relative">
                <Thumb fileId={img.id} name={img.name} onOpen={setViewer} />
                <div className="pointer-events-none absolute right-1 bottom-1 left-1 truncate rounded bg-black/50 px-1 text-[9px] text-white opacity-0 group-hover:opacity-100">
                  {imageTakenAt(img.name) ? new Date(imageTakenAt(img.name)!).toLocaleDateString() : img.name}
                </div>
                <button className="absolute top-1 right-1 hidden rounded-full bg-black/60 p-1 text-white group-hover:block" title="Delete" onClick={() => setDeleting({ id: img.id, name: img.name })}>
                  <Trash2 size={11} />
                </button>
              </div>
            ))}
          </div>
        )
      ) : albums.length === 0 ? (
        <div className="text-sm text-muted">No albums in Drive "collections" yet.</div>
      ) : (
        <div className="grid grid-cols-2 gap-2">
          {albums.map((a) => (
            <button key={a.id} onClick={() => setOpen(a.id)} className="flex items-center gap-3 rounded-lg border border-hairline px-3 py-2.5 text-left hover:border-electric/50">
              <Images size={18} className="text-navy" />
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium capitalize">{a.name}</div>
                <div className="text-[11px] text-item">{a.images.length} image(s){a.isBodyScan && " · body scan"}</div>
              </div>
            </button>
          ))}
        </div>
      )}
      <input ref={file} type="file" accept="image/*" multiple className="hidden" onChange={(e) => {
        if (e.target.files?.length) void upload(e.target.files);
        e.target.value = "";
      }} />

      <Dialog
        open={creating}
        onClose={() => setCreating(false)}
        title="New album"
        width="max-w-sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setCreating(false)}>Cancel</Button>
            <Button disabled={!name.trim()} onClick={async () => {
              try {
                await createAlbum(name);
                setCreating(false);
                setName("");
                await refresh();
              } catch (e) {
                toast(errorMessage(e), "error");
              }
            }}>Create</Button>
          </>
        }
      >
        <Input autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder="Album name" />
      </Dialog>

      {viewer && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-8" onClick={() => setViewer(null)}>
          <img src={viewer} alt="" className="max-h-full max-w-full rounded-lg" />
        </div>
      )}

      <Confirm
        open={!!deleting}
        title="Delete image?"
        message={`Move "${deleting?.name}" to the Drive trash?`}
        onClose={() => setDeleting(null)}
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await deleteDriveFile(deleting.id);
            await refresh();
          } catch (e) {
            toast(errorMessage(e), "error");
          }
        }}
      />
    </Section>
  );
}
