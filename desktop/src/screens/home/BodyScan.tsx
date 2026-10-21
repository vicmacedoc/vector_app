// Full body scan: webcam countdown capture (front, left, back, right) or file upload, into Drive collections/{angle}.
import { useEffect, useRef, useState } from "react";
import { Camera, Upload } from "lucide-react";
import { cn } from "@/lib/cn";
import { BODY_SCAN_ANGLES, uploadBodyScan, type BodyScanAngle } from "@/drive/self";
import { errorMessage } from "@/platform/native";
import { useBrain } from "@/store/brain";
import { Button, Dialog, Segmented } from "@/ui/primitives";
import { NumField } from "@/screens/calendar/ui";

const LABEL: Record<BodyScanAngle, string> = { front: "Front", left: "Left", back: "Back", right: "Right" };
const DEFAULT_WAIT: Record<BodyScanAngle, number> = { front: 5, left: 3, back: 3, right: 3 };

type Shots = Partial<Record<BodyScanAngle, Blob>>;

class Beeper {
  private ctx = new AudioContext();
  private tone(freq: number, ms: number, at = 0) {
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.frequency.value = freq;
    gain.gain.value = 0.15;
    osc.connect(gain).connect(this.ctx.destination);
    const t = this.ctx.currentTime + at;
    osc.start(t);
    osc.stop(t + ms / 1000);
  }
  tick = () => this.tone(880, 80);
  doubleBeep = () => { this.tone(1320, 90); this.tone(1320, 90, 0.15); };
  finalBeep = () => this.tone(660, 400);
  close = () => void this.ctx.close();
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

function grab(video: HTMLVideoElement): Promise<Blob> {
  const canvas = document.createElement("canvas");
  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
  canvas.getContext("2d")!.drawImage(video, 0, 0);
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b && b.size > 0 ? resolve(b) : reject(new Error("Capture produced an empty photo"))), "image/jpeg", 0.92),
  );
}

export function BodyScanDialog({ onClose }: { onClose: () => void }) {
  const toast = useBrain((s) => s.toast);
  const [mode, setMode] = useState<"camera" | "files">("camera");
  const [waits, setWaits] = useState(DEFAULT_WAIT);
  const [cameras, setCameras] = useState<MediaDeviceInfo[]>([]);
  const [cameraId, setCameraId] = useState("");
  const [files, setFiles] = useState<Shots>({});
  const [running, setRunning] = useState(false);
  const [status, setStatus] = useState("");
  const [countdown, setCountdown] = useState(-1);
  const [uploading, setUploading] = useState(false);
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const cancelled = useRef(false);

  useEffect(() => {
    void navigator.mediaDevices?.enumerateDevices().then((all) => {
      const cams = all.filter((d) => d.kind === "videoinput");
      setCameras(cams);
      if (cams[0]) setCameraId((id) => id || cams[0].deviceId);
    });
    return () => {
      cancelled.current = true;
      streamRef.current?.getTracks().forEach((t) => t.stop());
    };
  }, []);

  const upload = async (shots: Shots) => {
    setUploading(true);
    setStatus("Saving photos…");
    try {
      for (const angle of BODY_SCAN_ANGLES) {
        const blob = shots[angle];
        if (blob) await uploadBodyScan(angle, blob);
      }
      toast("Body scan saved to Drive");
      onClose();
    } catch (e) {
      toast(`Upload failed: ${errorMessage(e)}`, "error");
      setUploading(false);
    }
  };

  const start = async () => {
    cancelled.current = false;
    setRunning(true);
    setStatus("Starting camera…");
    const beeper = new Beeper();
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { deviceId: cameraId ? { exact: cameraId } : undefined, width: { ideal: 1920 }, height: { ideal: 1080 } },
      });
      streamRef.current = stream;
      while (!videoRef.current) await sleep(16);
      const video = videoRef.current;
      video.srcObject = stream;
      await video.play();
      const shots: Shots = {};
      for (const angle of BODY_SCAN_ANGLES) {
        const wait = Math.min(600, Math.max(0, waits[angle]));
        setStatus(`${LABEL[angle]} — get ready`);
        for (let left = wait; left > 0; left--) {
          if (cancelled.current) return;
          setCountdown(left);
          beeper.tick();
          await sleep(1000);
        }
        setCountdown(-1);
        if (cancelled.current) return;
        setStatus(`Capturing ${LABEL[angle]}…`);
        beeper.doubleBeep();
        shots[angle] = await grab(video);
      }
      beeper.finalBeep();
      stream.getTracks().forEach((t) => t.stop());
      await upload(shots);
    } catch (e) {
      if (!cancelled.current) toast(errorMessage(e) || "Camera error", "error");
      setRunning(false);
    } finally {
      streamRef.current?.getTracks().forEach((t) => t.stop());
      setTimeout(beeper.close, 600);
    }
  };

  const cancel = () => {
    cancelled.current = true;
    streamRef.current?.getTracks().forEach((t) => t.stop());
    setRunning(false);
    setCountdown(-1);
  };

  if (running) {
    return (
      <div className="fixed inset-0 z-50 flex flex-col bg-black">
        <video ref={videoRef} muted playsInline className="min-h-0 flex-1 object-contain" style={{ transform: "scaleX(-1)" }} />
        <div className="flex flex-col items-center gap-2 bg-black/60 p-5 text-white">
          <div className="text-lg font-medium">{status}</div>
          {countdown >= 0 && <div className="text-4xl font-semibold tabular-nums">{countdown}s</div>}
          {!uploading && <Button variant="secondary" onClick={() => { cancel(); onClose(); }}>Cancel</Button>}
        </div>
      </div>
    );
  }

  const fileCount = Object.keys(files).length;
  return (
    <Dialog
      open
      onClose={onClose}
      title="Full body scan"
      dismissable={!uploading}
      footer={
        <>
          <Button variant="secondary" disabled={uploading} onClick={onClose}>Cancel</Button>
          {mode === "camera" ? (
            <Button disabled={!cameras.length} onClick={() => void start()}><Camera size={15} /> Start</Button>
          ) : (
            <Button disabled={!fileCount || uploading} onClick={() => void upload(files)}>
              <Upload size={15} /> {uploading ? "Uploading…" : `Upload ${fileCount}`}
            </Button>
          )}
        </>
      }
    >
      <div className="space-y-4">
        <div className="text-xs text-muted">Photos go to the Drive folders front, left, back and right under collections, same as the phone.</div>
        <Segmented value={mode} onChange={setMode} options={[{ value: "camera", label: "Webcam" }, { value: "files", label: "Upload files" }]} />
        {mode === "camera" ? (
          <>
            {cameras.length === 0 ? (
              <div className="rounded-lg bg-offwhite p-3 text-sm text-muted">No camera found. Use “Upload files” instead.</div>
            ) : cameras.length > 1 ? (
              <select value={cameraId} onChange={(e) => setCameraId(e.target.value)} className="h-10 w-full rounded-lg border border-hairline px-3 text-sm outline-none focus:border-electric">
                {cameras.map((c, i) => <option key={c.deviceId} value={c.deviceId}>{c.label || `Camera ${i + 1}`}</option>)}
              </select>
            ) : null}
            <div className="grid grid-cols-2 gap-3">
              {BODY_SCAN_ANGLES.map((a) => (
                <label key={a} className="block">
                  <div className="mb-1 text-xs text-muted">Seconds before {LABEL[a].toLowerCase()}</div>
                  <NumField value={waits[a]} integer min={0} max={600} onChange={(v) => setWaits((w) => ({ ...w, [a]: v ?? 0 }))} />
                </label>
              ))}
            </div>
          </>
        ) : (
          <div className="grid grid-cols-4 gap-3">
            {BODY_SCAN_ANGLES.map((a) => (
              <FileSlot key={a} label={LABEL[a]} blob={files[a]} onChange={(b) => setFiles((f) => {
                const next = { ...f };
                if (b) next[a] = b;
                else delete next[a];
                return next;
              })} />
            ))}
          </div>
        )}
        {uploading && <div className="text-sm text-muted">{status}</div>}
      </div>
    </Dialog>
  );
}

function FileSlot({ label, blob, onChange }: { label: string; blob: Blob | undefined; onChange: (b: Blob | null) => void }) {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!blob) return setUrl(null);
    const u = URL.createObjectURL(blob);
    setUrl(u);
    return () => URL.revokeObjectURL(u);
  }, [blob]);
  return (
    <label className={cn("flex aspect-[3/4] cursor-pointer flex-col items-center justify-center overflow-hidden rounded-lg border border-dashed border-hairline bg-offwhite text-xs text-item hover:border-electric", blob && "border-solid")}>
      {url ? <img src={url} alt={label} className="h-full w-full object-cover" /> : <><Upload size={16} /><span className="mt-1">{label}</span></>}
      <input type="file" accept="image/*" className="hidden" onChange={(e) => onChange(e.target.files?.[0] ?? null)} />
    </label>
  );
}
