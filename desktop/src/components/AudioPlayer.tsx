// Recording player (AudioPlayerScreen): speed, seek, bookmarks, sleep timer, notes, AI transcript and summary.
import { useEffect, useRef, useState } from "react";
import { Bookmark, Moon, Pause, Play, RotateCcw, RotateCw, Sparkles, Trash2 } from "lucide-react";
import { summarizeAudio, summarizeText, transcribeAudio } from "@/ai/brain";
import type { MemoryNote } from "@/domain/models";
import * as ops from "@/domain/ops";
import { cn } from "@/lib/cn";
import { errorMessage } from "@/platform/native";
import { useBrain } from "@/store/brain";
import { Button, Dialog, Input, Label, Menu, Segmented, Slider, Textarea } from "@/ui/primitives";
import { Markdown } from "./Markdown";

interface AudioBookmark {
  id: string;
  positionMs: number;
  label: string;
}

/** Local playback state, like the phone's AudioProgress row (device-only, never synced). */
interface Progress {
  positionMs: number;
  speed: number;
  bookmarks: AudioBookmark[];
}

const SPEEDS = [0.75, 1, 1.25, 1.5, 1.75, 2];
const SLEEP_OPTIONS = [5, 10, 15, 30, 45, 60];
const SEEK_BACK = 15;
const SEEK_FORWARD = 30;
const progressKey = (assetId: string) => `vector:audio:${assetId}`;

function readProgress(assetId: string): Progress {
  try {
    const raw = localStorage.getItem(progressKey(assetId));
    if (raw) return { positionMs: 0, speed: 1, bookmarks: [], ...JSON.parse(raw) };
  } catch {
    // Corrupt entry: start fresh.
  }
  return { positionMs: 0, speed: 1, bookmarks: [] };
}

const clock = (sec: number) => {
  if (!Number.isFinite(sec)) return "0:00";
  const s = Math.max(0, Math.floor(sec));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = String(s % 60).padStart(2, "0");
  return h ? `${h}:${String(m).padStart(2, "0")}:${r}` : `${m}:${r}`;
};

export function AudioPlayer({ note, onClose }: { note: MemoryNote | null; onClose: () => void }) {
  return <Dialog open={note !== null} onClose={onClose} title="Recording" width="max-w-5xl">{note && <PlayerBody key={note.id} noteId={note.id} />}</Dialog>;
}

function PlayerBody({ noteId }: { noteId: string }) {
  const note = useBrain((s) => s.brain.notes.find((n) => n.id === noteId));
  const asset = useBrain((s) => (note?.assetId ? s.brain.assets.find((a) => a.id === note.assetId) : undefined));
  const commit = useBrain((s) => s.commit);
  const preview = useBrain((s) => s.assetPreview);
  const audio = useRef<HTMLAudioElement>(null);
  const assetId = note?.assetId ?? "";
  const [src, setSrc] = useState<string | null>(null);
  const [missing, setMissing] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [time, setTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [progress, setProgress] = useState<Progress>(() => readProgress(assetId));
  const [sleepAt, setSleepAt] = useState<number | null>(null);
  const [now, setNow] = useState(Date.now());
  const [title, setTitle] = useState(note?.title ?? "");
  const [notes, setNotes] = useState(asset?.description ?? "");
  const [transcript, setTranscript] = useState(note?.body ?? "");
  const [tab, setTab] = useState<"notes" | "transcript">(note?.body ? "transcript" : "notes");
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState<"transcribe" | "summary" | null>(null);
  const [aiError, setAiError] = useState<string | null>(null);

  useEffect(() => {
    if (!assetId) return;
    let alive = true;
    void preview(assetId).then((p) => {
      if (!alive) return;
      setSrc(p);
      setMissing(!p);
    });
    return () => {
      alive = false;
    };
  }, [assetId, preview]);

  const save = (p: Progress) => {
    setProgress(p);
    localStorage.setItem(progressKey(assetId), JSON.stringify(p));
  };

  useEffect(() => () => {
    const a = audio.current;
    if (a && assetId && !a.ended) localStorage.setItem(progressKey(assetId), JSON.stringify({ ...readProgress(assetId), positionMs: Math.round(a.currentTime * 1000) }));
  }, [assetId]);

  useEffect(() => {
    if (sleepAt === null) return;
    const t = setInterval(() => {
      setNow(Date.now());
      if (Date.now() >= sleepAt) {
        audio.current?.pause();
        setSleepAt(null);
      }
    }, 1000);
    return () => clearInterval(t);
  }, [sleepAt]);

  const toggle = () => {
    const a = audio.current;
    if (!a) return;
    if (a.paused) void a.play();
    else a.pause();
  };
  const seek = (delta: number) => {
    const a = audio.current;
    if (a) a.currentTime = Math.min(Math.max(0, a.currentTime + delta), a.duration || Infinity);
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      if (target.closest("input, textarea, select")) return;
      if (e.key === " ") {
        e.preventDefault();
        toggle();
      } else if (e.key === "ArrowLeft") seek(-SEEK_BACK);
      else if (e.key === "ArrowRight") seek(SEEK_FORWARD);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  if (!note || !asset) return <div className="text-sm text-muted">Recording not found.</div>;

  const el = () => audio.current;
  const jump = (ms: number) => {
    const a = el();
    if (a) a.currentTime = ms / 1000;
  };
  const setSpeed = (speed: number) => {
    const a = el();
    if (a) a.playbackRate = speed;
    save({ ...progress, speed });
  };
  const addBookmark = () => {
    const positionMs = Math.round((el()?.currentTime ?? 0) * 1000);
    save({ ...progress, bookmarks: [...progress.bookmarks, { id: crypto.randomUUID(), positionMs, label: "" }].sort((a, b) => a.positionMs - b.positionMs) });
  };
  const patchBookmark = (id: string, label: string) => save({ ...progress, bookmarks: progress.bookmarks.map((b) => (b.id === id ? { ...b, label } : b)) });
  const removeBookmark = (id: string) => save({ ...progress, bookmarks: progress.bookmarks.filter((b) => b.id !== id) });

  const commitTitle = () => {
    const t = title.trim();
    if (t && t !== note.title) commit((s) => ops.saveNote(s, { ...note, title: t }));
  };
  const commitNotes = (text = notes) => commit((s) => ops.setAssetDescription(s, asset.id, text));
  const commitTranscript = (text = transcript) => {
    if (text !== note.body) commit((s) => ops.saveNote(s, { ...note, body: text }));
  };

  const audioBlob = async () => {
    if (!src) throw new Error("Audio file missing");
    return (await fetch(src)).blob();
  };

  const transcribe = async () => {
    setBusy("transcribe");
    setAiError(null);
    try {
      const result = await transcribeAudio(await audioBlob(), asset.mimeType);
      commit((s) => ops.applyTranscription(s, note.id, result.title, result.transcript));
      const updated = useBrain.getState().brain.notes.find((n) => n.id === note.id);
      setTranscript(result.transcript);
      if (updated) setTitle(updated.title);
      setTab("transcript");
    } catch (e) {
      setAiError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  const summarize = async () => {
    setBusy("summary");
    setAiError(null);
    try {
      const text = transcript.trim() ? await summarizeText(transcript) : await summarizeAudio(await audioBlob(), asset.mimeType);
      setNotes(text);
      commitNotes(text);
      setTab("notes");
    } catch (e) {
      setAiError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  const total = duration || (progress.positionMs ? progress.positionMs / 1000 : 0);
  const sleepLeft = sleepAt ? Math.max(0, Math.round((sleepAt - now) / 1000)) : 0;
  const body = tab === "notes" ? notes : transcript;

  return (
    <div className="grid grid-cols-[minmax(0,1fr)_18rem] gap-6">
      <div className="min-w-0 space-y-5">
        <Input value={title} onChange={(e) => setTitle(e.target.value)} onBlur={commitTitle} className="text-base font-semibold" />
        {src && (
          <audio
            ref={audio}
            src={src}
            preload="metadata"
            onLoadedMetadata={(e) => {
              const a = e.currentTarget;
              setDuration(a.duration);
              a.playbackRate = progress.speed;
              if (progress.positionMs && progress.positionMs / 1000 < a.duration - 1) a.currentTime = progress.positionMs / 1000;
            }}
            onTimeUpdate={(e) => setTime(e.currentTarget.currentTime)}
            onPlay={() => setPlaying(true)}
            onPause={() => {
              setPlaying(false);
              save({ ...readProgress(assetId), positionMs: Math.round((audio.current?.currentTime ?? 0) * 1000) });
            }}
            onEnded={() => save({ ...readProgress(assetId), positionMs: 0 })}
          />
        )}
        {missing && <div className="rounded-lg bg-offwhite px-3 py-2 text-sm text-muted">This recording hasn't reached Drive yet, so it can't play here.</div>}

        <div className="rounded-xl border border-hairline p-4">
          <div className="relative">
            <Slider value={time} min={0} max={total || 1} step={0.1} onChange={(v) => jump(v * 1000)} disabled={!src} />
            {total > 0 && progress.bookmarks.map((b) => (
              <button
                key={b.id}
                title={b.label || clock(b.positionMs / 1000)}
                onClick={() => jump(b.positionMs)}
                className="absolute -top-1.5 h-2 w-2 -translate-x-1/2 rounded-full bg-electric"
                style={{ left: `${Math.min(100, (b.positionMs / 1000 / total) * 100)}%` }}
              />
            ))}
          </div>
          <div className="mt-1 flex justify-between text-xs tabular-nums text-item">
            <span>{clock(time)}</span>
            <span>{clock(total)}</span>
          </div>
          <div className="mt-3 flex items-center justify-center gap-3">
            <Button variant="ghost" size="icon" disabled={!src} onClick={() => seek(-SEEK_BACK)} title={`Back ${SEEK_BACK}s (←)`}>
              <RotateCcw size={18} />
            </Button>
            <button
              disabled={!src}
              onClick={toggle}
              className="flex h-14 w-14 items-center justify-center rounded-full bg-navy text-white transition hover:bg-navy-light disabled:opacity-40"
              title="Play / pause (Space)"
            >
              {playing ? <Pause size={24} /> : <Play size={24} className="translate-x-0.5" />}
            </button>
            <Button variant="ghost" size="icon" disabled={!src} onClick={() => seek(SEEK_FORWARD)} title={`Forward ${SEEK_FORWARD}s (→)`}>
              <RotateCw size={18} />
            </Button>
          </div>
          <div className="mt-4 flex flex-wrap items-center justify-center gap-2">
            <Segmented<string> value={String(progress.speed)} onChange={(v) => setSpeed(Number(v))} options={SPEEDS.map((s) => ({ value: String(s), label: `${s}×` }))} />
            <Menu
              trigger={
                <Button size="sm" variant={sleepAt ? "accent" : "secondary"}>
                  <Moon size={14} /> {sleepAt ? clock(sleepLeft) : "Sleep timer"}
                </Button>
              }
              items={[
                ...SLEEP_OPTIONS.map((m) => ({ label: `${m} minutes`, onSelect: () => setSleepAt(Date.now() + m * 60_000) })),
                ...(total > 0 ? [{ label: "End of recording", onSelect: () => setSleepAt(Date.now() + ((total - time) / progress.speed) * 1000) }] : []),
                ...(sleepAt ? [{ label: "Turn off", onSelect: () => setSleepAt(null) }] : []),
              ]}
            />
            <Button size="sm" variant="secondary" disabled={!src} onClick={addBookmark}><Bookmark size={14} /> Bookmark</Button>
          </div>
        </div>

        <div>
          <div className="mb-2 flex items-center gap-2">
            <Segmented<"notes" | "transcript">
              value={tab}
              onChange={(t) => { setTab(t); setEditing(false); }}
              options={[{ value: "notes", label: "Notes" }, { value: "transcript", label: "Transcript" }]}
            />
            <Button size="sm" variant="ghost" onClick={() => setEditing((e) => !e)}>{editing ? "Done" : "Edit"}</Button>
            <div className="ml-auto flex gap-2">
              <Button size="sm" variant="secondary" disabled={busy !== null || !src} onClick={() => void transcribe()}>
                <Sparkles size={14} /> {busy === "transcribe" ? "Transcribing…" : transcript ? "Re-transcribe" : "Transcribe"}
              </Button>
              <Button size="sm" variant="secondary" disabled={busy !== null || (!src && !transcript.trim())} onClick={() => void summarize()}>
                <Sparkles size={14} /> {busy === "summary" ? "Summarizing…" : "Summarize"}
              </Button>
            </div>
          </div>
          {aiError && <div className="mb-2 text-sm text-danger">{aiError}</div>}
          {editing ? (
            <Textarea
              autoFocus
              rows={14}
              value={body}
              onChange={(e) => (tab === "notes" ? setNotes(e.target.value) : setTranscript(e.target.value))}
              onBlur={() => (tab === "notes" ? commitNotes() : commitTranscript())}
              className="font-mono text-[13px] leading-relaxed"
              placeholder={tab === "notes" ? "Your notes, or let Gemini summarize the recording" : "Transcript"}
            />
          ) : (
            <div className="max-h-[22rem] min-h-[8rem] overflow-y-auto rounded-lg border border-hairline px-4 py-3">
              {body.trim() ? <Markdown>{body}</Markdown> : <div className="text-sm text-item">{tab === "notes" ? "No notes yet." : "No transcript yet."}</div>}
            </div>
          )}
        </div>
      </div>

      <div>
        <Label>Bookmarks</Label>
        {progress.bookmarks.length === 0 ? (
          <div className="text-sm text-item">Bookmark a moment to jump back to it later.</div>
        ) : (
          <div className="space-y-1.5">
            {progress.bookmarks.map((b) => (
              <div key={b.id} className={cn("group flex items-center gap-2 rounded-lg border border-hairline p-1.5", Math.abs(time * 1000 - b.positionMs) < 1500 && "border-electric")}>
                <button onClick={() => jump(b.positionMs)} className="rounded-md bg-offwhite px-2 py-1 font-mono text-xs tabular-nums hover:bg-electric/10">
                  {clock(b.positionMs / 1000)}
                </button>
                <input
                  defaultValue={b.label}
                  onBlur={(e) => e.target.value !== b.label && patchBookmark(b.id, e.target.value)}
                  placeholder="Label"
                  className="min-w-0 flex-1 bg-transparent text-sm outline-none"
                />
                <button onClick={() => removeBookmark(b.id)} className="text-item opacity-0 group-hover:opacity-100 hover:text-danger"><Trash2 size={14} /></button>
              </div>
            ))}
          </div>
        )}
        <div className="mt-6 text-xs text-item">Space plays or pauses; ← and → skip {SEEK_BACK}s back and {SEEK_FORWARD}s forward.</div>
      </div>
    </div>
  );
}
