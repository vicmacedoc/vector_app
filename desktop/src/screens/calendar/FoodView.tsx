// Food day: rows grouped by time (sticky headers), eaten cycle, multiplier slider, audit badges, totals.
import { useMemo, useState } from "react";
import { Copy, Pencil, Trash2, Utensils } from "lucide-react";
import { cn } from "@/lib/cn";
import {
  currentMacros, effectiveAmount, effectiveTime, foodDayTotals, foodEntryToClipboard, groupByTime,
  hasPresetBaseline, resolveAuditStatus, type MealDraft,
} from "@/domain/daily/food";
import type { FoodDraft } from "@/domain/daily/drafts";
import type { AuditStatus, FoodEntry } from "@/domain/daily/models";
import { clamp, normalizeMealTimeOrBlank, nowHhMm } from "@/domain/daily/time";
import { useBrain } from "@/store/brain";
import { useCalendar } from "@/store/calendar";
import { Button, Empty, Slider } from "@/ui/primitives";
import { MealDialog } from "./MealDialog";
import { Section, TimeField, copyText, fmtNum } from "./ui";

const AUDIT_STYLE: Record<AuditStatus, string> = {
  PLANNED: "bg-navy text-white",
  ADJUSTED: "bg-prio-mid/15 text-prio-mid",
  UNPLANNED: "bg-electric/10 text-electric",
};

export function AuditBadge({ status, onClick }: { status: AuditStatus; onClick?: () => void }) {
  return (
    <button
      type="button"
      disabled={!onClick}
      onClick={onClick}
      title={onClick ? "Mark as planned" : undefined}
      className={cn("rounded-full px-2 py-0.5 text-[10px] font-semibold tracking-wide", AUDIT_STYLE[status], onClick && "hover:ring-2 hover:ring-navy/30")}
    >
      {status}
    </button>
  );
}

const fmt = (v: number) => fmtNum(Math.round(v * 10) / 10);

export function FoodView() {
  const slot = useCalendar((s) => s.FOOD);
  const editing = useCalendar((s) => s.editing);
  const update = useCalendar((s) => s.update);
  const toast = useBrain((s) => s.toast);
  const [editingEntry, setEditingEntry] = useState<FoodEntry | null>(null);
  const groups = useMemo(() => groupByTime(slot?.current.entries ?? []), [slot]);
  const totals = useMemo(() => foodDayTotals(slot?.current.entries ?? []), [slot]);
  if (!slot) return null;

  const edit = (fn: (d: FoodDraft) => FoodDraft) => update("FOOD", fn);
  const patch = (id: string, fn: (e: FoodEntry) => FoodEntry) =>
    edit((d) => ({ entries: d.entries.map((e) => (e.id === id ? fn(e) : e)) }));

  const cycle = (e: FoodEntry): FoodEntry =>
    e.notEaten ? { ...e, notEaten: false } : !e.isChecked ? { ...e, isChecked: true } : { ...e, isChecked: false, notEaten: true };

  const setMultiplier = (e: FoodEntry, m: number) => {
    const q = clamp(m, 0, 3);
    return { ...e, quantityMultiplier: q, status: resolveAuditStatus(e, q) };
  };

  const setTime = (e: FoodEntry, raw: string) => {
    const t = normalizeMealTimeOrBlank(raw);
    if (!t || t === effectiveTime(e)) return e;
    const next = { ...e, time: t };
    return { ...next, status: resolveAuditStatus(next) };
  };

  const markPlanned = (e: FoodEntry) => {
    if (hasPresetBaseline(e)) return e;
    const next = { ...e, plannedTime: effectiveTime(e), plannedAmount: effectiveAmount(e) };
    return { ...next, status: resolveAuditStatus(next) };
  };

  const saveManual = (e: FoodEntry, d: MealDraft) => {
    const time = normalizeMealTimeOrBlank(d.time) || nowHhMm();
    const base: FoodEntry = {
      ...e, name: d.name.trim(), time, amount: d.amount, unit: d.unit || "g", kcal: d.kcal, protein: d.protein, carbs: d.carbs, fats: d.fats,
      plannedTime: d.asPlanned ? e.plannedTime ?? time : null, plannedAmount: d.asPlanned ? e.plannedAmount ?? d.amount : null,
    };
    return { ...base, status: resolveAuditStatus(base) };
  };

  return (
    <div className="grid grid-cols-[minmax(0,1fr)_320px] gap-6 2xl:grid-cols-[minmax(0,1fr)_360px]">
      <div className="min-w-0">
        {slot.presetError && <div className="mb-3 rounded-lg bg-danger/10 px-3 py-2 text-xs text-danger">{slot.presetError}</div>}
        {groups.length === 0 ? (
          <Empty
            icon={<Utensils size={22} />}
            title="No meals"
            hint={slot.noPreset ? "No food preset yet. Load one in Settings, or add meals with the Add button." : "Use Add to log a meal."}
          />
        ) : (
          <div className="rounded-xl border border-hairline">
            <table className="w-full border-separate border-spacing-0 text-sm">
              <thead className="bg-offwhite text-left text-[10px] uppercase tracking-wide text-item">
                <tr>
                  <th className="w-10 px-3 py-2" />
                  <th className="px-2 py-2 font-semibold">Meal</th>
                  <th className="w-28 px-2 py-2 text-right font-semibold">Amount</th>
                  <th className="w-56 px-2 py-2 font-semibold">Multiplier</th>
                  <th className="w-40 px-2 py-2 text-right font-semibold">kcal · P · C · F</th>
                  <th className="w-36 px-2 py-2" />
                </tr>
              </thead>
              {groups.map((g) => (
                <tbody key={g.time}>
                  <tr className="sticky top-0 z-10">
                    <td colSpan={6} className="border-b border-hairline bg-[#eef2f6] px-3 py-1.5 font-mono text-xs font-semibold text-navy backdrop-blur">
                      {g.time}
                    </td>
                  </tr>
                  {g.entries.map((e) => {
                    const m = currentMacros(e);
                    const done = e.isChecked && !e.notEaten;
                    return (
                      <tr key={e.id} className={cn("group [&>td]:border-b [&>td]:border-hairline", done && "bg-prio-low/5", e.notEaten && "bg-danger/5")}>
                        <td className="px-3 py-2">
                          <button
                            disabled={!editing}
                            onClick={() => patch(e.id, cycle)}
                            title="Blank → eaten → not eaten"
                            className={cn(
                              "flex h-5 w-5 items-center justify-center rounded border-2",
                              e.notEaten ? "border-danger" : done ? "border-navy" : "border-black/40",
                            )}
                          >
                            {(done || e.notEaten) && <span className={cn("h-3 w-3 rounded-sm", e.notEaten ? "bg-danger" : "bg-navy")} />}
                          </button>
                        </td>
                        <td className="px-2 py-2">
                          <div className={cn("font-medium", done && "text-muted line-through")}>{e.name}</div>
                          {e.notEaten && <div className="text-[11px] font-semibold text-danger">NOT EATEN</div>}
                        </td>
                        <td className="px-2 py-2 text-right font-mono text-[13px]">
                          {fmt(effectiveAmount(e) * e.quantityMultiplier)} {e.unit}
                        </td>
                        <td className="px-2 py-2">
                          <div className="flex items-center gap-2">
                            <Slider
                              value={e.quantityMultiplier}
                              min={0}
                              max={3}
                              step={0.1}
                              disabled={!editing || e.notEaten}
                              onChange={(v) => patch(e.id, (x) => setMultiplier(x, v))}
                            />
                            <span className="w-9 text-right font-mono text-xs">{e.quantityMultiplier.toFixed(1)}</span>
                          </div>
                        </td>
                        <td className="px-2 py-2 text-right font-mono text-xs text-muted">
                          <span className="font-semibold text-black">{fmt(m.kcal)}</span> · {fmt(m.protein)} · {fmt(m.carbs)} · {fmt(m.fats)}
                        </td>
                        <td className="px-2 py-2">
                          <div className="flex items-center justify-end gap-1">
                            <div className="hidden gap-0.5 group-hover:flex">
                              {editing && (
                                <TimeField
                                  value={effectiveTime(e)}
                                  onChange={(v) => patch(e.id, (x) => setTime(x, v))}
                                  className="h-7 w-[84px] text-xs"
                                />
                              )}
                              <Button size="icon" variant="ghost" className="h-7 w-7" title="Copy as JSON" onClick={async () => {
                                if (await copyText(JSON.stringify(foodEntryToClipboard(e), null, 2))) toast("Copied meal");
                              }}>
                                <Copy size={13} />
                              </Button>
                              {editing && !hasPresetBaseline(e) && (
                                <Button size="icon" variant="ghost" className="h-7 w-7" title="Edit" onClick={() => setEditingEntry(e)}>
                                  <Pencil size={13} />
                                </Button>
                              )}
                              {editing && (
                                <Button size="icon" variant="ghost" className="h-7 w-7 text-danger" title="Remove" onClick={() => edit((d) => ({ entries: d.entries.filter((x) => x.id !== e.id) }))}>
                                  <Trash2 size={13} />
                                </Button>
                              )}
                            </div>
                            <AuditBadge
                              status={e.status}
                              onClick={editing && !hasPresetBaseline(e) ? () => patch(e.id, markPlanned) : undefined}
                            />
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              ))}
            </table>
          </div>
        )}
      </div>

      <div className="space-y-6">
        <Section title="Day totals">
          <MacroBars totals={totals} />
        </Section>
        <Section title="Legend">
          <div className="space-y-2 text-xs text-muted">
            <div className="flex items-center gap-2"><AuditBadge status="PLANNED" /> matches the preset</div>
            <div className="flex items-center gap-2"><AuditBadge status="ADJUSTED" /> multiplier, time or amount changed</div>
            <div className="flex items-center gap-2"><AuditBadge status="UNPLANNED" /> added by hand (click to plan it)</div>
          </div>
        </Section>
      </div>

      {editingEntry && (
        <MealDialog
          title="Edit meal"
          confirmLabel="Save"
          initial={{
            name: editingEntry.name, time: effectiveTime(editingEntry), amount: effectiveAmount(editingEntry), unit: editingEntry.unit,
            kcal: editingEntry.kcal, protein: editingEntry.protein, carbs: editingEntry.carbs, fats: editingEntry.fats,
            asPlanned: hasPresetBaseline(editingEntry),
          }}
          onClose={() => setEditingEntry(null)}
          onSave={(d) => {
            patch(editingEntry.id, (x) => saveManual(x, d));
            setEditingEntry(null);
          }}
        />
      )}
    </div>
  );
}

function MacroBars({ totals }: { totals: ReturnType<typeof foodDayTotals> }) {
  const rows: { key: keyof typeof totals.got; label: string; unit: string }[] = [
    { key: "kcal", label: "Calories", unit: "kcal" },
    { key: "protein", label: "Protein", unit: "g" },
    { key: "carbs", label: "Carbs", unit: "g" },
    { key: "fats", label: "Fats", unit: "g" },
  ];
  return (
    <div className="space-y-4">
      {rows.map((r) => {
        const got = totals.got[r.key];
        const plan = totals.planned[r.key];
        const pct = plan > 0 ? Math.min(150, (got / plan) * 100) : got > 0 ? 100 : 0;
        return (
          <div key={r.key}>
            <div className="mb-1 flex items-baseline justify-between text-sm">
              <span className="font-medium">{r.label}</span>
              <span className="font-mono text-xs">
                <span className="font-semibold">{fmt(got)}</span>
                <span className="text-item"> / {fmt(plan)} {r.unit}</span>
              </span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-offwhite ring-1 ring-hairline">
              <div
                className={cn("h-full rounded-full", pct > 110 ? "bg-prio-mid" : "bg-navy")}
                style={{ width: `${Math.min(100, pct)}%` }}
              />
            </div>
          </div>
        );
      })}
      <div className="text-[11px] text-item">Eaten rows vs. the planned baseline.</div>
    </div>
  );
}
