// Calendar working copies per category: baseline (saved or preset), current edits, drafts and Save.
import { create } from "zustand";
import { todayKey } from "@/domain/dates";
import {
  decodeDraft, draftKey, sameExercise, sameFood, sameRoutine, sameSelf,
  type DraftCategory, type DraftPayloads,
} from "@/domain/daily/drafts";
import {
  exerciseBaseline, foodBaseline, routineBaseline, saveExerciseEdits, saveFoodEdits, saveRoutineEdits, saveSelfEdits,
  selfBaseline, type Baseline, type ShardEdit,
} from "@/domain/daily/day";
import { clearDayEdits } from "@/domain/daily/home";
import { errorMessage } from "@/platform/native";
import { useBrain } from "./brain";
import { useDaily } from "./daily";
import { uploadDiaryAudio } from "@/drive/self";
import type { ShardRecords } from "@/sync/shardEngine";
import { CONFIG_SHARD } from "@/sync/shards";

export interface Slot<T> {
  date: string;
  base: T;
  current: T;
  noPreset: boolean;
  presetError: string | null;
}

type Slots = { [C in DraftCategory]: Slot<DraftPayloads[C]> | null };

const SAME: { [C in DraftCategory]: (a: DraftPayloads[C], b: DraftPayloads[C]) => boolean } = {
  ROUTINE: sameRoutine,
  FOOD: sameFood,
  EXERCISE: sameExercise,
  SELF: sameSelf,
};

const BASELINE: { [C in DraftCategory]: (shards: ReturnType<typeof useDaily.getState>["shards"], date: string) => Baseline<DraftPayloads[C]> } = {
  ROUTINE: routineBaseline,
  FOOD: foodBaseline,
  EXERCISE: exerciseBaseline,
  SELF: selfBaseline,
};

const CATEGORIES: DraftCategory[] = ["ROUTINE", "FOOD", "EXERCISE", "SELF"];

const LABEL: Record<DraftCategory, string> = { ROUTINE: "Routine", FOOD: "Food", EXERCISE: "Exercise", SELF: "Self" };

interface CalendarStore extends Slots {
  date: string;
  editing: boolean;
  saving: DraftCategory | null;
  /** Journal recording waiting to upload on Save. */
  selfAudio: Blob | null;
  load(date: string): void;
  isDirty(c: DraftCategory): boolean;
  update<C extends DraftCategory>(c: C, fn: (cur: DraftPayloads[C]) => DraftPayloads[C]): void;
  discard(c: DraftCategory): void;
  save(c: DraftCategory): Promise<void>;
  setEditing(on: boolean): void;
  setSelfAudio(blob: Blob | null): void;
  /** Deletes saved data and the draft for [c] on [date]; the day falls back to the preset. */
  resetDay(c: DraftCategory, date: string): void;
}

let subscribed = false;

function applyEdits(edits: ShardEdit[]) {
  const daily = useDaily.getState();
  for (const e of edits) daily.mutate(e.shard, e.fn);
}

export const useCalendar = create<CalendarStore>((set, get) => {
  const slotFor = <C extends DraftCategory>(c: C, date: string, keepDraft = true): Slot<DraftPayloads[C]> => {
    const daily = useDaily.getState();
    const b = BASELINE[c](daily.shards, date);
    const draft = keepDraft ? decodeDraft<C>(daily.drafts[draftKey(c, date)]?.payloadJson) : null;
    return { date, base: b.payload, current: draft ?? b.payload, noPreset: b.noPreset, presetError: b.presetError };
  };

  const subscribe = () => {
    if (subscribed) return;
    subscribed = true;
    useDaily.subscribe((s, prev) => {
      if (s.ready && !prev.ready) {
        get().load(get().date);
        return;
      }
      if (s.shards === prev.shards) return;
      const st = get();
      const patch: Partial<Slots> = {};
      for (const c of CATEGORIES) {
        const slot = st[c];
        if (!slot) continue;
        const b = BASELINE[c](s.shards, slot.date);
        if (st.isDirty(c)) {
          (patch as Record<string, unknown>)[c] = { ...slot, noPreset: b.noPreset, presetError: b.presetError };
        } else {
          (patch as Record<string, unknown>)[c] = { ...slot, base: b.payload, current: b.payload, noPreset: b.noPreset, presetError: b.presetError };
        }
      }
      set(patch);
    });
  };

  return {
    date: todayKey(),
    editing: true,
    saving: null,
    selfAudio: null,
    ROUTINE: null,
    FOOD: null,
    EXERCISE: null,
    SELF: null,

    load(date) {
      subscribe();
      set({
        date,
        editing: date === todayKey(),
        selfAudio: null,
        ROUTINE: slotFor("ROUTINE", date),
        FOOD: slotFor("FOOD", date),
        EXERCISE: slotFor("EXERCISE", date),
        SELF: slotFor("SELF", date),
      });
    },

    isDirty(c) {
      const slot = get()[c];
      if (!slot) return false;
      if (c === "SELF" && get().selfAudio) return true;
      return !(SAME[c] as (a: unknown, b: unknown) => boolean)(slot.base, slot.current);
    },

    update(c, fn) {
      const slot = get()[c] as Slot<DraftPayloads[typeof c]> | null;
      if (!slot || !get().editing) return;
      const current = fn(slot.current);
      set({ [c]: { ...slot, current } } as Partial<Slots>);
      const dirty = !(SAME[c] as (a: unknown, b: unknown) => boolean)(slot.base, current);
      useDaily.getState().setDraft(draftKey(c, slot.date), dirty ? JSON.stringify(current) : null);
    },

    discard(c) {
      const slot = get()[c];
      if (!slot) return;
      useDaily.getState().setDraft(draftKey(c, slot.date), null);
      set({ [c]: slotFor(c, slot.date, false), ...(c === "SELF" ? { selfAudio: null } : {}) } as Partial<CalendarStore>);
    },

    async save(c) {
      const st = get();
      const slot = st[c];
      if (!slot || st.saving) return;
      const shards = useDaily.getState().shards;
      set({ saving: c });
      try {
        switch (c) {
          case "ROUTINE": {
            const s = slot as Slot<DraftPayloads["ROUTINE"]>;
            applyEdits(saveRoutineEdits(shards, s.date, s.current, s.base));
            break;
          }
          case "FOOD":
            applyEdits(saveFoodEdits(slot.date, (slot as Slot<DraftPayloads["FOOD"]>).current));
            break;
          case "EXERCISE":
            applyEdits(saveExerciseEdits(slot.date, (slot as Slot<DraftPayloads["EXERCISE"]>).current));
            break;
          case "SELF": {
            let current = (slot as Slot<DraftPayloads["SELF"]>).current;
            if (st.selfAudio) {
              const id = await uploadDiaryAudio(slot.date, st.selfAudio);
              current = { ...current, audioDriveFileId: id };
              set({ selfAudio: null });
            }
            applyEdits(saveSelfEdits(slot.date, current));
            break;
          }
        }
        useDaily.getState().setDraft(draftKey(c, slot.date), null);
        set({ [c]: slotFor(c, slot.date, false) } as Partial<Slots>);
        useBrain.getState().toast(`${LABEL[c]} saved`);
      } catch (e) {
        useBrain.getState().toast(`Failed to save ${LABEL[c].toLowerCase()}: ${errorMessage(e)}`, "error");
      } finally {
        set({ saving: null });
      }
    },

    setEditing: (on) => set({ editing: on }),
    setSelfAudio: (blob) => set({ selfAudio: blob }),

    resetDay(c, date) {
      useDaily.getState().setDraft(draftKey(c, date), null);
      applyEdits(clearDayEdits(c, date));
      if (get()[c]?.date === date) {
        set({ [c]: slotFor(c, date, false), ...(c === "SELF" ? { selfAudio: null } : {}) } as Partial<CalendarStore>);
      }
      useBrain.getState().toast(`${LABEL[c]} cleared for ${date}`);
    },
  };
});

/** Library writes go straight to the config shard, like the phone's immediate upserts. */
export const editLibrary = (fn: (r: ShardRecords) => ShardRecords) => useDaily.getState().mutate(CONFIG_SHARD, fn);
