// Add/edit meal form (AddMealDialog) shared by the Food tab and the add flow.
import { useState } from "react";
import { emptyMealDraft, mealDraftError, type MealDraft } from "@/domain/daily/food";
import { Button, Dialog, Input } from "@/ui/primitives";
import { ErrorLine, Field, NumField, TimeField } from "./ui";

export function MealForm({ draft, onChange, showPlanned = true }: { draft: MealDraft; onChange: (d: MealDraft) => void; showPlanned?: boolean }) {
  const set = <K extends keyof MealDraft>(k: K, v: MealDraft[K]) => onChange({ ...draft, [k]: v });
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-[1fr_140px] gap-3">
        <Field label="Name"><Input value={draft.name} onChange={(e) => set("name", e.target.value)} /></Field>
        <Field label="Time">
          <TimeField value={draft.time || null} onChange={(v) => set("time", v)} placeholder="now" />
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Amount"><NumField value={draft.amount} min={0} step={0.1} onChange={(v) => set("amount", v ?? 0)} /></Field>
        <Field label="Unit"><Input className="h-9" value={draft.unit} onChange={(e) => set("unit", e.target.value)} /></Field>
      </div>
      <div className="grid grid-cols-4 gap-3">
        <Field label="kcal"><NumField value={draft.kcal} min={0} step={1} onChange={(v) => set("kcal", v ?? 0)} /></Field>
        <Field label="Protein g"><NumField value={draft.protein} min={0} step={0.1} onChange={(v) => set("protein", v ?? 0)} /></Field>
        <Field label="Carbs g"><NumField value={draft.carbs} min={0} step={0.1} onChange={(v) => set("carbs", v ?? 0)} /></Field>
        <Field label="Fats g"><NumField value={draft.fats} min={0} step={0.1} onChange={(v) => set("fats", v ?? 0)} /></Field>
      </div>
      {showPlanned && (
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={draft.asPlanned} onChange={(e) => set("asPlanned", e.target.checked)} className="h-4 w-4 accent-navy" />
          Count as planned
        </label>
      )}
    </div>
  );
}

export function MealDialog({
  title, initial, onClose, onSave, confirmLabel = "Add",
}: {
  title: string;
  initial?: MealDraft | null;
  onClose: () => void;
  onSave: (d: MealDraft) => void;
  confirmLabel?: string;
}) {
  const [draft, setDraft] = useState<MealDraft>(initial ?? emptyMealDraft());
  const [error, setError] = useState<string | null>(null);
  const save = () => {
    const d = { ...draft, time: draft.time.trim() };
    const err = mealDraftError(d.time ? d : { ...d, time: "12:00" });
    if (err) return setError(err);
    onSave(d);
  };
  return (
    <Dialog
      open
      onClose={onClose}
      title={title}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={save}>{confirmLabel}</Button>
        </>
      }
    >
      <div className="space-y-3">
        <MealForm draft={draft} onChange={setDraft} />
        <ErrorLine>{error}</ErrorLine>
      </div>
    </Dialog>
  );
}
