import type { ReactNode } from "react";
import { Card } from "@/ui/primitives";

export function SettingsSection({ id, title, hint, children }: { id: string; title: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <section id={`settings-${id}`} data-section={id} className="scroll-mt-6">
      <Card className="p-5">
        <div className="mb-4">
          <div className="text-base font-semibold">{title}</div>
          {hint && <div className="text-sm text-muted">{hint}</div>}
        </div>
        {children}
      </Card>
    </section>
  );
}

export function Row({ label, hint, children }: { label: ReactNode; hint?: ReactNode; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[minmax(0,14rem)_minmax(0,1fr)] items-start gap-4 border-t border-hairline py-4 first:border-t-0 first:pt-0 last:pb-0">
      <div>
        <div className="text-sm font-medium">{label}</div>
        {hint && <div className="mt-0.5 text-xs text-item">{hint}</div>}
      </div>
      <div className="min-w-0">{children}</div>
    </div>
  );
}

export function Kbd({ children }: { children: ReactNode }) {
  return <kbd className="rounded bg-offwhite px-1.5 py-0.5 font-mono text-xs">{children}</kbd>;
}
