import { X } from "lucide-react";
import { cn } from "@/lib/cn";
import { useBrain } from "@/store/brain";

export function Toasts() {
  const toasts = useBrain((s) => s.toasts);
  const dismiss = useBrain((s) => s.dismissToast);
  return (
    <div className="pointer-events-none fixed right-6 bottom-6 z-[60] flex flex-col items-end gap-2">
      {toasts.map((t) => (
        <div
          key={t.id}
          className={cn(
            "animate-fade-up pointer-events-auto flex items-center gap-3 rounded-xl px-4 py-3 text-sm text-white shadow-xl",
            t.tone === "error" ? "bg-danger" : "bg-navy",
          )}
        >
          <span className="max-w-md">{t.message}</span>
          <button onClick={() => dismiss(t.id)} className="opacity-70 hover:opacity-100"><X size={14} /></button>
        </div>
      ))}
    </div>
  );
}
