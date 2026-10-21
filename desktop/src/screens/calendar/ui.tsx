// Small Calendar widgets: time/number fields, status pills, label chips and clipboard access.
import { useEffect, useState, type ReactNode } from "react";
import { AlertTriangle } from "lucide-react";
import { cn } from "@/lib/cn";
import type { ActivityLabel } from "@/domain/daily/models";

export const argbCss = (argb: number) => {
  const v = argb >>> 0;
  return `rgb(${(v >> 16) & 255}, ${(v >> 8) & 255}, ${v & 255})`;
};

export const fmtNum = (v: number | null | undefined, digits = 1) =>
  v === null || v === undefined || !Number.isFinite(v) ? "–" : Number.isInteger(v) ? String(v) : v.toFixed(digits).replace(/\.0+$/, "");

export function TimeField({
  value, onChange, disabled, className, placeholder,
}: {
  value: string | null;
  onChange: (v: string) => void;
  disabled?: boolean;
  className?: string;
  placeholder?: string;
}) {
  return (
    <input
      type="time"
      value={value ?? ""}
      placeholder={placeholder}
      disabled={disabled}
      onChange={(e) => e.target.value && onChange(e.target.value)}
      className={cn(
        "h-9 rounded-lg border border-hairline bg-white px-2 font-mono text-sm outline-none focus:border-electric disabled:opacity-50",
        className,
      )}
    />
  );
}

/** Numeric input that keeps its own text while typing; blank means null. */
export function NumField({
  value, onChange, step = 1, min, max, disabled, className, placeholder, integer,
}: {
  value: number | null;
  onChange: (v: number | null) => void;
  step?: number;
  min?: number;
  max?: number;
  disabled?: boolean;
  className?: string;
  placeholder?: string;
  integer?: boolean;
}) {
  const [text, setText] = useState(value === null ? "" : String(value));
  useEffect(() => {
    const parsed = text.trim() === "" ? null : Number(text);
    if (parsed !== value) setText(value === null ? "" : String(value));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);
  return (
    <input
      type="number"
      inputMode="decimal"
      value={text}
      step={step}
      min={min}
      max={max}
      disabled={disabled}
      placeholder={placeholder}
      onChange={(e) => {
        setText(e.target.value);
        if (e.target.value.trim() === "") return onChange(null);
        let n = Number(e.target.value);
        if (!Number.isFinite(n)) return;
        if (integer) n = Math.round(n);
        if (min !== undefined) n = Math.max(min, n);
        if (max !== undefined) n = Math.min(max, n);
        onChange(n);
      }}
      className={cn(
        "h-9 w-full rounded-lg border border-hairline bg-white px-2 font-mono text-sm outline-none focus:border-electric disabled:opacity-50",
        className,
      )}
    />
  );
}

export function Field({ label, children, className }: { label: ReactNode; children: ReactNode; className?: string }) {
  return (
    <label className={cn("flex min-w-0 flex-col gap-1", className)}>
      <span className="text-[11px] font-semibold uppercase tracking-wide text-muted">{label}</span>
      {children}
    </label>
  );
}

export function LabelChip({ label, onClick, selected = true, small }: { label: ActivityLabel | undefined; onClick?: () => void; selected?: boolean; small?: boolean }) {
  const color = label ? argbCss(label.colorArgb) : "#90a4ae";
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={!onClick}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border font-medium transition",
        small ? "px-2 py-0.5 text-[11px]" : "px-2.5 py-1 text-xs",
        selected ? "border-transparent text-white" : "border-hairline bg-offwhite text-black hover:border-black/30",
      )}
      style={selected ? { background: color } : undefined}
    >
      {!selected && <span className="h-2 w-2 rounded-full" style={{ background: color }} />}
      {label?.name ?? "?"}
    </button>
  );
}

export function ReplacesDayBanner({ children }: { children: ReactNode }) {
  return (
    <div className="flex items-center gap-2 rounded-lg bg-prio-mid/10 px-3 py-2 text-xs text-black">
      <AlertTriangle size={16} className="shrink-0 text-prio-mid" />
      {children}
    </div>
  );
}

export function ErrorLine({ children }: { children: ReactNode }) {
  if (!children) return null;
  return <div className="rounded-lg bg-danger/10 px-3 py-2 text-xs text-danger">{children}</div>;
}

export async function readClipboardText(): Promise<string> {
  try {
    return await navigator.clipboard.readText();
  } catch {
    return "";
  }
}

export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

export function Section({ title, actions, children, className }: { title: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={cn("rounded-xl border border-hairline bg-white", className)}>
      <header className="flex items-center gap-2 border-b border-hairline px-4 py-2.5">
        <h3 className="flex-1 text-sm font-semibold">{title}</h3>
        {actions}
      </header>
      <div className="p-4">{children}</div>
    </section>
  );
}
