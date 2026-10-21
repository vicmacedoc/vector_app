import { useEffect, useRef, useState } from "react";
import { Mic, Square } from "lucide-react";
import { cn } from "@/lib/cn";
import { errorMessage } from "@/platform/native";
import { useBrain } from "@/store/brain";

const pad = (n: number) => String(n).padStart(2, "0");

function fileStamp(d = new Date()) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}.${pad(d.getMinutes())}`;
}

export function pickAudioMime(): string {
  for (const m of ["audio/webm;codecs=opus", "audio/ogg;codecs=opus", "audio/mp4", "audio/webm"]) {
    if (typeof MediaRecorder !== "undefined" && MediaRecorder.isTypeSupported(m)) return m;
  }
  return "";
}

export const audioExt = (mime: string) => (mime.includes("ogg") ? "ogg" : mime.includes("mp4") ? "m4a" : "webm");

/** Records from the microphone; resolves the blob on stop. */
export function useRecorder() {
  const [recording, setRecording] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const rec = useRef<MediaRecorder | null>(null);
  const chunks = useRef<Blob[]>([]);
  const timer = useRef<ReturnType<typeof setInterval> | undefined>(undefined);
  const resolver = useRef<((b: Blob | null) => void) | null>(null);

  useEffect(() => () => {
    clearInterval(timer.current);
    rec.current?.stream.getTracks().forEach((t) => t.stop());
  }, []);

  async function start() {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const mime = pickAudioMime();
    const r = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined);
    chunks.current = [];
    r.ondataavailable = (e) => e.data.size && chunks.current.push(e.data);
    r.onstop = () => {
      stream.getTracks().forEach((t) => t.stop());
      const blob = chunks.current.length ? new Blob(chunks.current, { type: r.mimeType || mime || "audio/webm" }) : null;
      resolver.current?.(blob);
      resolver.current = null;
    };
    r.start(1000);
    rec.current = r;
    setElapsed(0);
    const started = Date.now();
    timer.current = setInterval(() => setElapsed(Math.floor((Date.now() - started) / 1000)), 500);
    setRecording(true);
  }

  function stop(): Promise<Blob | null> {
    clearInterval(timer.current);
    setRecording(false);
    const r = rec.current;
    rec.current = null;
    if (!r || r.state === "inactive") return Promise.resolve(null);
    return new Promise((resolve) => {
      resolver.current = resolve;
      r.stop();
    });
  }

  return { recording, elapsed, start, stop };
}

/** Header mic: records a voice note straight into the Inbox. */
export function VoiceCapture() {
  const { recording, elapsed, start, stop } = useRecorder();
  const addBlob = useBrain((s) => s.addBlob);
  const toast = useBrain((s) => s.toast);

  async function toggle() {
    if (!recording) {
      try {
        await start();
      } catch (e) {
        toast(`Microphone unavailable: ${errorMessage(e)}`, "error");
      }
      return;
    }
    const blob = await stop();
    if (!blob) return;
    const id = await addBlob(blob, `Voice note ${fileStamp()}.${audioExt(blob.type)}`, null);
    if (id) toast("Voice note saved to Inbox");
  }

  return (
    <button
      onClick={() => void toggle()}
      title={recording ? "Stop and save to Inbox" : "Record a voice note"}
      className={cn(
        "flex h-9 items-center gap-2 rounded-lg border px-3 text-sm transition",
        recording ? "border-danger bg-danger/10 text-danger" : "border-hairline text-item hover:border-electric/40 hover:text-black",
      )}
    >
      {recording ? <Square size={14} className="fill-current" /> : <Mic size={15} />}
      {recording ? `${Math.floor(elapsed / 60)}:${pad(elapsed % 60)}` : "Voice"}
    </button>
  );
}
