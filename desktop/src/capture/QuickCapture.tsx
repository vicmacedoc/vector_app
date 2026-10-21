import { useEffect, useRef, useState } from "react";
import { emitTo, listen } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { Brain, CheckSquare, FilePlus2, Link2, NotebookPen, Upload } from "lucide-react";
import type { MotionPriority } from "@/domain/models";
import { cn } from "@/lib/cn";
import { looksLikeUrl, pickFiles, useFileDrop } from "@/ui/files";
import { Segmented } from "@/ui/primitives";
import { CAPTURE_EVENT, type CapturePayload } from "./protocol";

type Mode = "note" | "link" | "todo" | "learn";

const MODES: { value: Mode; label: React.ReactNode }[] = [
  { value: "note", label: <span className="flex items-center gap-1.5"><NotebookPen size={13} /> Note</span> },
  { value: "link", label: <span className="flex items-center gap-1.5"><Link2 size={13} /> Link</span> },
  { value: "todo", label: <span className="flex items-center gap-1.5"><CheckSquare size={13} /> To-do</span> },
  { value: "learn", label: <span className="flex items-center gap-1.5"><Brain size={13} /> Learn tile</span> },
];

const PLACEHOLDER: Record<Mode, string> = {
  note: "Capture a thought… (first line is the title)",
  link: "Paste a link",
  todo: "What needs doing?",
  learn: "What do you want to learn?",
};

/** The always-on-top window opened by Ctrl+Shift+Space. */
export function QuickCapture() {
  const [mode, setMode] = useState<Mode>("note");
  const [text, setText] = useState("");
  const [title, setTitle] = useState("");
  const [priority, setPriority] = useState<MotionPriority>("NONE");
  const [today, setToday] = useState(false);
  const input = useRef<HTMLTextAreaElement>(null);

  const reset = () => {
    setText("");
    setTitle("");
    setPriority("NONE");
    setToday(false);
    setTimeout(() => input.current?.focus(), 30);
  };

  useEffect(() => {
    const un = listen("capture-opened", reset);
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && void getCurrentWindow().hide();
    window.addEventListener("keydown", onKey);
    reset();
    return () => {
      void un.then((f) => f());
      window.removeEventListener("keydown", onKey);
    };
  }, []);

  const send = async (payload: CapturePayload) => {
    await emitTo("main", CAPTURE_EVENT, payload);
    reset();
    await getCurrentWindow().hide();
  };

  const hovering = useFileDrop((paths) => void send({ kind: "files", paths }));

  const effectiveMode: Mode = mode === "note" && looksLikeUrl(text) ? "link" : mode;

  const submit = () => {
    const value = text.trim();
    if (!value) return;
    if (effectiveMode === "link") void send({ kind: "link", url: value, title: title.trim() });
    else if (effectiveMode === "todo") {
      const due = today ? new Date(new Date().setHours(18, 0, 0, 0)).getTime() : null;
      void send({ kind: "todo", title: value, priority, dueAt: due });
    } else if (effectiveMode === "learn") void send({ kind: "learn", name: value });
    else {
      const [first, ...rest] = value.split("\n");
      void send({ kind: "note", title: first.slice(0, 120), body: rest.length ? rest.join("\n").trim() : first.length > 120 ? value : "" });
    }
  };

  return (
    <div className={cn("flex h-full flex-col gap-3 bg-white p-4", hovering && "ring-4 ring-inset ring-electric")}>
      <div className="flex items-center justify-between">
        <Segmented value={effectiveMode} options={MODES} onChange={setMode} />
        <button
          onClick={async () => {
            const paths = await pickFiles();
            if (paths.length) void send({ kind: "files", paths });
          }}
          className="flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-[13px] text-muted hover:bg-offwhite hover:text-black"
        >
          <FilePlus2 size={14} /> Files
        </button>
      </div>
      <textarea
        ref={input}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && (effectiveMode !== "note" || e.ctrlKey)) {
            e.preventDefault();
            submit();
          }
        }}
        placeholder={PLACEHOLDER[effectiveMode]}
        className="min-h-0 flex-1 resize-none rounded-xl border border-hairline p-3 text-[15px] outline-none focus:border-electric focus:ring-2 focus:ring-electric/20"
      />
      {effectiveMode === "link" && (
        <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Title (optional)" className="h-9 rounded-lg border border-hairline px-3 text-sm outline-none focus:border-electric" />
      )}
      {effectiveMode === "todo" && (
        <div className="flex items-center gap-3 text-[13px]">
          <Segmented value={priority} onChange={setPriority} options={[
            { value: "NONE", label: "—" }, { value: "LOW", label: "Low" }, { value: "MEDIUM", label: "Med" }, { value: "HIGH", label: "High" },
          ]} />
          <label className="flex items-center gap-1.5 text-muted"><input type="checkbox" checked={today} onChange={(e) => setToday(e.target.checked)} /> Due today 18:00</label>
        </div>
      )}
      <div className="flex items-center justify-between text-[11px] text-item">
        <span className="flex items-center gap-1"><Upload size={11} /> Drop files to send them to the Inbox</span>
        <span>{effectiveMode === "note" ? "Ctrl+Enter" : "Enter"} to save · Esc to close</span>
      </div>
    </div>
  );
}
