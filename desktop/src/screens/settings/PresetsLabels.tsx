// Weekly presets (Routine / Food / Workout JSON) and activity labels, both stored in the config shard.
import { useMemo, useState } from "react";
import { Download, FileJson, Plus, Trash2, Upload } from "lucide-react";
import { cn } from "@/lib/cn";
import { allPlanActivities, decodeRoutinePlan, NEW_PRESET_LABEL_COLOR, unknownPresetLabels, validateRoutinePlan } from "@/domain/daily/activity";
import { preset, savedLabels, savePresetEdit, type ShardEdit } from "@/domain/daily/day";
import { parseFoodPlan, WEEKDAYS, type Weekday } from "@/domain/daily/food";
import { activitiesWithLabel, argbToHex, createLabelEdit, deleteLabelEdits, hexToArgb, updateLabelEdit } from "@/domain/daily/labels";
import { upsertActivitiesFromPlan, upsertExerciseSessions, upsertMealsFromPlan } from "@/domain/daily/library";
import type { ActivityLabel, PresetCategory } from "@/domain/daily/models";
import { decodeWorkoutPlan } from "@/domain/daily/workoutPlan";
import { errorMessage } from "@/platform/native";
import { useBrain } from "@/store/brain";
import { useDaily } from "@/store/daily";
import { CONFIG_SHARD } from "@/sync/shards";
import { pickTextFile, saveTextFile } from "@/ui/files";
import { Button, Confirm, Dialog, Input } from "@/ui/primitives";
import { SettingsSection } from "./common";

const apply = (edits: ShardEdit[]) => edits.forEach((e) => useDaily.getState().mutate(e.shard, e.fn));

interface Parsed {
  name: string;
  perDay: Record<Weekday, number>;
  unit: string;
  newLabels: string[];
  libraryEdit: ShardEdit;
}

function parsePreset(category: PresetCategory, text: string, labels: ActivityLabel[]): Parsed {
  if (category === "Routine") {
    const plan = decodeRoutinePlan(text);
    validateRoutinePlan(plan);
    return {
      name: plan.name,
      perDay: Object.fromEntries(WEEKDAYS.map((d) => [d, plan.weekly_plan[d].length])) as Record<Weekday, number>,
      unit: "activities",
      newLabels: unknownPresetLabels(plan, labels),
      libraryEdit: { shard: CONFIG_SHARD, fn: (r) => upsertActivitiesFromPlan(r, allPlanActivities(plan)) },
    };
  }
  if (category === "Food") {
    const plan = parseFoodPlan(text);
    return {
      name: plan.name,
      perDay: Object.fromEntries(WEEKDAYS.map((d) => [d, plan.weekly_plan[d].length])) as Record<Weekday, number>,
      unit: "meals",
      newLabels: [],
      libraryEdit: { shard: CONFIG_SHARD, fn: (r) => upsertMealsFromPlan(r, WEEKDAYS.flatMap((d) => plan.weekly_plan[d])) },
    };
  }
  const plan = decodeWorkoutPlan(text);
  return {
    name: plan.name,
    perDay: Object.fromEntries(WEEKDAYS.map((d) => [d, plan.weekly_plan[d].reduce((n, s) => n + s.exercises.length, 0)])) as Record<Weekday, number>,
    unit: "exercises",
    newLabels: [],
    libraryEdit: { shard: CONFIG_SHARD, fn: (r) => upsertExerciseSessions(r, WEEKDAYS.flatMap((d) => plan.weekly_plan[d])) },
  };
}

const CATEGORIES: { id: PresetCategory; hint: string }[] = [
  { id: "Routine", hint: "Planned activities per weekday" },
  { id: "Food", hint: "Meals per weekday" },
  { id: "Workout", hint: "Sessions per weekday" },
];

export function PresetsSection() {
  const shards = useDaily((s) => s.shards);
  const toast = useBrain((s) => s.toast);
  const [pending, setPending] = useState<{ category: PresetCategory; fileName: string; text: string; parsed: Parsed } | null>(null);
  const [removing, setRemoving] = useState<PresetCategory | null>(null);

  const browse = async (category: PresetCategory) => {
    try {
      const file = await pickTextFile(["json"]);
      if (!file) return;
      setPending({ category, fileName: file.name, text: file.text, parsed: parsePreset(category, file.text, savedLabels(shards)) });
    } catch (e) {
      toast(`Couldn't read preset: ${errorMessage(e)}`, "error");
    }
  };

  const confirmApply = () => {
    if (!pending) return;
    const { category, fileName, text, parsed } = pending;
    const edits: ShardEdit[] = [savePresetEdit(category, text, parsed.name || fileName.replace(/\.json$/i, ""))];
    for (const name of parsed.newLabels) {
      try {
        edits.push(createLabelEdit(shards, name, NEW_PRESET_LABEL_COLOR).edit);
      } catch {
        // Already exists under a different case; the Calendar matches labels case-insensitively.
      }
    }
    edits.push(parsed.libraryEdit);
    apply(edits);
    toast(`${category} preset applied — saved days are unchanged`);
    setPending(null);
  };

  const exportPreset = async (category: PresetCategory) => {
    const p = preset(shards, category);
    if (!p) return;
    try {
      const pretty = (() => {
        try {
          return JSON.stringify(JSON.parse(p.content), null, 2);
        } catch {
          return p.content;
        }
      })();
      if (await saveTextFile(`${category.toLowerCase()}_weekly.json`, pretty, ["json"])) toast("Preset exported");
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  return (
    <SettingsSection id="presets" title="Presets" hint="Weekly templates that pre-fill unsaved Calendar days. Applying one never changes days you already saved.">
      <div className="grid grid-cols-1 gap-3 xl:grid-cols-3">
        {CATEGORIES.map(({ id, hint }) => {
          const p = preset(shards, id);
          return (
            <div key={id} className="flex flex-col rounded-xl border border-hairline p-4">
              <div className="flex items-center gap-2">
                <FileJson size={16} className={p ? "text-electric" : "text-item"} />
                <div className="font-semibold">{id}</div>
              </div>
              <div className="mt-1 text-xs text-item">{hint}</div>
              <div className="mt-3 flex-1 text-sm">
                {p ? (
                  <>
                    <div className="truncate font-medium">{p.displayName || "Unnamed preset"}</div>
                    <div className="text-xs text-muted">Updated {new Date(p.updatedAt).toLocaleDateString()}</div>
                  </>
                ) : (
                  <div className="text-muted">No preset</div>
                )}
              </div>
              <div className="mt-3 flex flex-wrap gap-1.5">
                <Button size="sm" onClick={() => void browse(id)}><Upload size={13} /> {p ? "Replace" : "Load JSON"}</Button>
                {p && <Button size="sm" variant="secondary" onClick={() => void exportPreset(id)}><Download size={13} /> Export</Button>}
                {p && <Button size="sm" variant="ghost" onClick={() => setRemoving(id)} title="Remove"><Trash2 size={13} /></Button>}
              </div>
            </div>
          );
        })}
      </div>

      <Dialog
        open={!!pending}
        onClose={() => setPending(null)}
        title={`Apply ${pending?.category ?? ""} preset?`}
        footer={
          <>
            <Button variant="secondary" onClick={() => setPending(null)}>Cancel</Button>
            <Button onClick={confirmApply}>Apply</Button>
          </>
        }
      >
        {pending && (
          <div className="space-y-4 text-sm">
            <div>
              <div className="font-medium">{pending.parsed.name || pending.fileName}</div>
              <div className="text-xs text-item">{pending.fileName}</div>
            </div>
            <div className="grid grid-cols-7 gap-1 text-center">
              {WEEKDAYS.map((d) => (
                <div key={d} className="rounded-lg bg-offwhite py-2">
                  <div className="text-[10px] font-semibold uppercase text-muted">{d.slice(0, 3)}</div>
                  <div className="text-base font-semibold tabular-nums">{pending.parsed.perDay[d]}</div>
                </div>
              ))}
            </div>
            <div className="text-xs text-item">Counts are {pending.parsed.unit} per weekday. Items are also added to the library.</div>
            {pending.parsed.newLabels.length > 0 && (
              <div>
                <div className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-muted">New labels</div>
                <div className="flex flex-wrap gap-1.5">
                  {pending.parsed.newLabels.map((n) => (
                    <span key={n} className="inline-flex items-center gap-1.5 rounded-full border border-hairline px-2 py-0.5 text-xs">
                      <span className="h-2.5 w-2.5 rounded-full" style={{ background: argbToHex(NEW_PRESET_LABEL_COLOR) }} /> {n}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </Dialog>

      <Confirm
        open={!!removing}
        title={`Remove ${removing} preset?`}
        message="Unsaved days stop being pre-filled. Saved days and the library are kept."
        confirmLabel="Remove"
        onConfirm={() => removing && apply([savePresetEdit(removing, null, null)])}
        onClose={() => setRemoving(null)}
      />
    </SettingsSection>
  );
}

const SWATCHES = ["#001f3f", "#007bff", "#2e7d32", "#f9a825", "#ef6c00", "#c62828", "#6a1b9a", "#00838f", "#546e7a", "#8d6e63"];

export function LabelsSection() {
  const shards = useDaily((s) => s.shards);
  const toast = useBrain((s) => s.toast);
  const labels = useMemo(() => savedLabels(shards), [shards]);
  const [editing, setEditing] = useState<ActivityLabel | "new" | null>(null);
  const [name, setName] = useState("");
  const [color, setColor] = useState(SWATCHES[1]);
  const [deleting, setDeleting] = useState<ActivityLabel | null>(null);

  const open = (l: ActivityLabel | "new") => {
    setEditing(l);
    setName(l === "new" ? "" : l.name);
    setColor(l === "new" ? SWATCHES[1] : argbToHex(l.colorArgb));
  };

  const save = () => {
    if (!editing) return;
    try {
      apply([editing === "new" ? createLabelEdit(shards, name, hexToArgb(color)).edit : updateLabelEdit(shards, editing, { name, colorArgb: hexToArgb(color) })]);
      setEditing(null);
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const remove = (l: ActivityLabel) => {
    try {
      apply(deleteLabelEdits(shards, l));
      toast(`Deleted “${l.name}”`);
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const usage = deleting ? activitiesWithLabel(shards, deleting.id) : 0;
  const isSystem = editing !== null && editing !== "new" && editing.isSystem;

  return (
    <SettingsSection id="labels" title="Activity labels" hint="Colours used for Routine activities. Click a label to edit it.">
      <div className="flex flex-wrap gap-2">
        {labels.map((l) => (
          <button
            key={l.id}
            onClick={() => open(l)}
            className="group inline-flex items-center gap-2 rounded-full border border-hairline py-1 pr-3 pl-1.5 text-sm hover:border-electric"
          >
            <span className="h-5 w-5 rounded-full border border-black/10" style={{ background: argbToHex(l.colorArgb) }} />
            {l.name}
            {l.isSystem && <span className="text-[10px] uppercase text-item">system</span>}
          </button>
        ))}
        <Button size="sm" variant="secondary" onClick={() => open("new")}><Plus size={14} /> New label</Button>
      </div>

      <Dialog
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === "new" ? "New label" : "Edit label"}
        width="max-w-sm"
        footer={
          <>
            {editing !== "new" && editing && !editing.isSystem && (
              <Button variant="ghost" className="mr-auto text-danger" onClick={() => { setDeleting(editing); setEditing(null); }}>
                <Trash2 size={14} /> Delete
              </Button>
            )}
            <Button variant="secondary" onClick={() => setEditing(null)}>Cancel</Button>
            <Button disabled={!name.trim()} onClick={save}>Save</Button>
          </>
        }
      >
        <div className="space-y-4">
          <Input value={name} autoFocus disabled={isSystem} onChange={(e) => setName(e.target.value)} onKeyDown={(e) => e.key === "Enter" && save()} placeholder="Label name" />
          <div className="flex flex-wrap items-center gap-2">
            {SWATCHES.map((s) => (
              <button
                key={s}
                onClick={() => setColor(s)}
                className={cn("h-7 w-7 rounded-full border-2", color.toLowerCase() === s ? "border-black" : "border-transparent")}
                style={{ background: s }}
                aria-label={s}
              />
            ))}
            <label className="relative h-7 w-7 cursor-pointer overflow-hidden rounded-full border border-hairline" title="Custom colour">
              <input type="color" value={color} onChange={(e) => setColor(e.target.value)} className="absolute inset-0 h-full w-full cursor-pointer opacity-0" />
              <span className="flex h-full w-full items-center justify-center text-item"><Plus size={14} /></span>
            </label>
            <span className="ml-auto font-mono text-xs text-item">{color}</span>
          </div>
        </div>
      </Dialog>

      <Confirm
        open={!!deleting}
        title={`Delete “${deleting?.name ?? ""}”?`}
        message={usage > 0 ? `This also deletes ${usage} activit${usage === 1 ? "y" : "ies"} using this label, on every device after sync.` : "No activities use this label."}
        onConfirm={() => deleting && remove(deleting)}
        onClose={() => setDeleting(null)}
      />
    </SettingsSection>
  );
}
