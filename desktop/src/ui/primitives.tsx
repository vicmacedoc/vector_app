import { forwardRef, useEffect, useRef, useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";
import { cn } from "@/lib/cn";

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger" | "accent";
type ButtonSize = "sm" | "md" | "icon";

const VARIANTS: Record<ButtonVariant, string> = {
  primary: "bg-navy text-white hover:bg-navy-light",
  accent: "bg-electric text-white hover:brightness-110",
  secondary: "bg-white text-black border border-hairline hover:bg-offwhite",
  ghost: "text-black hover:bg-offwhite",
  danger: "bg-danger text-white hover:brightness-110",
};

const SIZES: Record<ButtonSize, string> = {
  sm: "h-8 px-3 text-[13px] gap-1.5",
  md: "h-10 px-4 text-sm gap-2",
  icon: "h-9 w-9 justify-center",
};

export const Button = forwardRef<HTMLButtonElement, ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; size?: ButtonSize }>(
  ({ variant = "primary", size = "md", className, ...props }, ref) => (
    <button
      ref={ref}
      className={cn(
        "inline-flex shrink-0 items-center rounded-lg font-medium transition-colors outline-none",
        "focus-visible:ring-2 focus-visible:ring-electric/50 disabled:pointer-events-none disabled:opacity-40",
        VARIANTS[variant],
        SIZES[size],
        className,
      )}
      {...props}
    />
  ),
);

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(({ className, ...props }, ref) => (
  <input
    ref={ref}
    className={cn(
      "h-10 w-full rounded-lg border border-hairline bg-white px-3 text-sm text-black outline-none transition",
      "placeholder:text-item focus:border-electric focus:ring-2 focus:ring-electric/20",
      className,
    )}
    {...props}
  />
));

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(({ className, ...props }, ref) => (
  <textarea
    ref={ref}
    className={cn(
      "w-full resize-none rounded-lg border border-hairline bg-white px-3 py-2 text-sm text-black outline-none transition",
      "placeholder:text-item focus:border-electric focus:ring-2 focus:ring-electric/20",
      className,
    )}
    {...props}
  />
));

export function Label({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("mb-1.5 text-xs font-semibold uppercase tracking-wide text-muted", className)}>{children}</div>;
}

export function Card({ children, className, onClick }: { children: ReactNode; className?: string; onClick?: () => void }) {
  return (
    <div
      onClick={onClick}
      className={cn(
        "rounded-xl border border-hairline bg-white",
        onClick && "cursor-pointer transition hover:border-electric/50 hover:shadow-[0_2px_12px_rgba(0,31,63,0.08)]",
        className,
      )}
    >
      {children}
    </div>
  );
}

export function Badge({ children, className, tone = "neutral" }: { children: ReactNode; className?: string; tone?: "neutral" | "navy" | "electric" | "danger" }) {
  const tones = {
    neutral: "bg-offwhite text-muted border border-hairline",
    navy: "bg-navy text-white",
    electric: "bg-electric/10 text-electric",
    danger: "bg-danger/10 text-danger",
  };
  return <span className={cn("inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium", tones[tone], className)}>{children}</span>;
}

export function Empty({ icon, title, hint, action }: { icon: ReactNode; title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
      <div className="mb-1 rounded-2xl bg-offwhite p-4 text-navy">{icon}</div>
      <div className="font-semibold">{title}</div>
      {hint && <div className="max-w-sm text-sm text-muted">{hint}</div>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

export function Dialog({
  open, onClose, title, children, footer, width = "max-w-lg", dismissable = true,
}: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  width?: string;
  dismissable?: boolean;
}) {
  useEffect(() => {
    if (!open || !dismissable) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose, dismissable]);
  if (!open) return null;
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-navy/30 p-6 backdrop-blur-[2px]" onMouseDown={() => dismissable && onClose()}>
      <div
        className={cn("animate-fade-up flex max-h-full w-full flex-col overflow-hidden rounded-2xl bg-white shadow-2xl", width)}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-hairline px-5 py-4">
          <div className="text-base font-semibold">{title}</div>
          {dismissable && (
            <button className="rounded-md p-1 text-muted hover:bg-offwhite" onClick={onClose} aria-label="Close">
              <X size={18} />
            </button>
          )}
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">{children}</div>
        {footer && <div className="flex justify-end gap-2 border-t border-hairline bg-offwhite/60 px-5 py-3">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}

export function Confirm({
  open, title, message, confirmLabel = "Delete", danger = true, onConfirm, onClose,
}: {
  open: boolean;
  title: string;
  message: ReactNode;
  confirmLabel?: string;
  danger?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={title}
      width="max-w-sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant={danger ? "danger" : "primary"} onClick={() => { onConfirm(); onClose(); }}>{confirmLabel}</Button>
        </>
      }
    >
      <div className="text-sm text-muted">{message}</div>
    </Dialog>
  );
}

export interface MenuItem {
  label: string;
  icon?: ReactNode;
  onSelect: () => void;
  danger?: boolean;
  active?: boolean;
}

export function Menu({ trigger, items, align = "right" }: { trigger: ReactNode; items: MenuItem[]; align?: "left" | "right" }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    window.addEventListener("mousedown", close);
    return () => window.removeEventListener("mousedown", close);
  }, [open]);
  return (
    <div className="relative" ref={ref} onClick={(e) => e.stopPropagation()}>
      <div onClick={() => setOpen((o) => !o)}>{trigger}</div>
      {open && (
        <div className={cn("animate-fade-up absolute z-40 mt-1 min-w-44 rounded-xl border border-hairline bg-white p-1 shadow-xl", align === "right" ? "right-0" : "left-0")}>
          {items.map((item) => (
            <button
              key={item.label}
              className={cn(
                "flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm hover:bg-offwhite",
                item.danger && "text-danger",
                item.active && "font-semibold text-electric",
              )}
              onClick={() => {
                setOpen(false);
                item.onSelect();
              }}
            >
              {item.icon}
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export function Select<T extends string>({
  value, options, onChange, className,
}: {
  value: T;
  options: { value: T; label: string }[];
  onChange: (v: T) => void;
  className?: string;
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value as T)}
      className={cn("h-10 rounded-lg border border-hairline bg-white px-3 text-sm outline-none focus:border-electric", className)}
    >
      {options.map((o) => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  );
}

export function Slider({
  value, min = 0, max, step = 1, onChange, disabled, className,
}: {
  value: number;
  min?: number;
  max: number;
  step?: number;
  onChange: (v: number) => void;
  disabled?: boolean;
  className?: string;
}) {
  const fill = max > min ? ((value - min) / (max - min)) * 100 : 0;
  return (
    <input
      type="range"
      className={cn("vector-slider w-full", disabled && "opacity-40", className)}
      style={{ ["--fill" as string]: `${fill}%` }}
      value={value}
      min={min}
      max={max}
      step={step}
      disabled={disabled}
      onChange={(e) => onChange(Number(e.target.value))}
    />
  );
}

export function Segmented<T extends string>({
  value, options, onChange,
}: {
  value: T;
  options: { value: T; label: ReactNode }[];
  onChange: (v: T) => void;
}) {
  return (
    <div className="inline-flex rounded-lg border border-hairline bg-offwhite p-0.5">
      {options.map((o) => (
        <button
          key={o.value}
          onClick={() => onChange(o.value)}
          className={cn(
            "rounded-md px-3 py-1.5 text-[13px] font-medium transition",
            value === o.value ? "bg-white text-navy shadow-sm" : "text-muted hover:text-black",
          )}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function SearchInput({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  return (
    <div className="relative">
      <Input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} className="h-9 pl-3 pr-8" />
      {value && (
        <button className="absolute top-1/2 right-2 -translate-y-1/2 text-item hover:text-black" onClick={() => onChange("")}>
          <X size={14} />
        </button>
      )}
    </div>
  );
}
