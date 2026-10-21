// Shared preferences: sleep targets, Learn review ladder, Motion, plus the keyboard reference.
import { useEffect, useState } from "react";
import { sharedSettings, saveSettingsEdit } from "@/domain/daily/day";
import { DEFAULT_SHARED_SETTINGS, type SharedSettings } from "@/domain/daily/models";
import { DAYS, type DayOfWeek } from "@/domain/models";
import { useBrain } from "@/store/brain";
import { useDaily } from "@/store/daily";
import { Button, Input, Select, Slider } from "@/ui/primitives";
import { TimeField } from "@/screens/calendar/ui";
import { Kbd, Row, SettingsSection } from "./common";

const toHhMm = (m: number | null) => (m === null ? null : `${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`);
const fromHhMm = (s: string) => {
  const m = /^(\d{1,2}):(\d{2})$/.exec(s);
  return m ? Number(m[1]) * 60 + Number(m[2]) : null;
};

function useShared(): [SharedSettings, (patch: Partial<SharedSettings>) => void] {
  const shards = useDaily((s) => s.shards);
  const settings = sharedSettings(shards);
  const update = (patch: Partial<SharedSettings>) => {
    const e = saveSettingsEdit({ ...sharedSettings(useDaily.getState().shards), ...patch });
    useDaily.getState().mutate(e.shard, e.fn);
  };
  return [settings, update];
}

export function SleepSection() {
  const [s, update] = useShared();
  return (
    <SettingsSection id="sleep" title="Sleep targets" hint="Used by the Sleep card and Analysis. Alarms and the fill-out reminder are scheduled on the phone.">
      <Row label="Target bedtime">
        <TimeField className="w-36" value={toHhMm(s.sleepTargetBedtimeMinutes)} onChange={(v) => update({ sleepTargetBedtimeMinutes: fromHhMm(v) })} />
      </Row>
      <Row label="Target wake-up">
        <TimeField className="w-36" value={toHhMm(s.sleepTargetWakeupMinutes)} onChange={(v) => update({ sleepTargetWakeupMinutes: fromHhMm(v) })} />
      </Row>
      <Row label="Fill-out reminder" hint="Set on the phone.">
        <div className="pt-1 text-sm text-muted">{toHhMm(s.filloutReminderMinutes) ?? "Off"}</div>
      </Row>
    </SettingsSection>
  );
}

/** Same bounds as the phone's LearningSettings.sanitized. */
const parseIntervals = (s: string) =>
  s.split(/[,\s]+/).map((x) => Number(x)).filter((n) => Number.isInteger(n) && n >= 1 && n <= 3650).slice(0, 12);

export function LearningSection() {
  const [s, update] = useShared();
  const [onHold, setOnHold] = useState(String(s.learnOnHoldDays));
  const [intervals, setIntervals] = useState(s.learnReviewIntervals.join(", "));
  const [quiz, setQuiz] = useState(s.learnQuizSize);
  const [pass, setPass] = useState(s.learnPassPercent);

  useEffect(() => {
    setOnHold(String(s.learnOnHoldDays));
    setIntervals(s.learnReviewIntervals.join(", "));
    setQuiz(s.learnQuizSize);
    setPass(s.learnPassPercent);
  }, [s.learnOnHoldDays, s.learnReviewIntervals, s.learnQuizSize, s.learnPassPercent]);

  const parsed = parseIntervals(intervals);
  const hold = Number(onHold);
  const valid = parsed.length > 0 && Number.isInteger(hold) && hold >= 1 && hold <= 365;
  const dirty =
    hold !== s.learnOnHoldDays || parsed.join(",") !== s.learnReviewIntervals.join(",") || quiz !== s.learnQuizSize || pass !== s.learnPassPercent;

  return (
    <SettingsSection id="learning" title="Learning" hint="How Learn tiles move through review. Shared with the phone.">
      <Row label="On hold after" hint="Days without activity before an in-progress tile is flagged.">
        <div className="flex items-center gap-2">
          <Input className="w-24" inputMode="numeric" value={onHold} onChange={(e) => setOnHold(e.target.value)} />
          <span className="text-sm text-muted">days</span>
        </div>
      </Row>
      <Row label="Review intervals" hint="Days between reviews after each passed quiz, comma-separated.">
        <Input value={intervals} onChange={(e) => setIntervals(e.target.value)} placeholder={DEFAULT_SHARED_SETTINGS.learnReviewIntervals.join(", ")} />
        {parsed.length > 0 && <div className="mt-1 text-xs text-item">{parsed.map((d) => `${d}d`).join(" → ")}</div>}
      </Row>
      <Row label={`Quiz size · ${quiz} questions`}>
        <Slider value={quiz} min={1} max={20} step={1} onChange={setQuiz} />
      </Row>
      <Row label={`Pass mark · ${pass}%`}>
        <Slider value={pass} min={10} max={100} step={5} onChange={setPass} />
      </Row>
      <div className="mt-4 flex justify-end gap-2">
        <Button
          variant="secondary"
          size="sm"
          onClick={() => update({
            learnOnHoldDays: DEFAULT_SHARED_SETTINGS.learnOnHoldDays,
            learnReviewIntervals: DEFAULT_SHARED_SETTINGS.learnReviewIntervals,
            learnQuizSize: DEFAULT_SHARED_SETTINGS.learnQuizSize,
            learnPassPercent: DEFAULT_SHARED_SETTINGS.learnPassPercent,
          })}
        >
          Reset defaults
        </Button>
        <Button size="sm" disabled={!valid || !dirty} onClick={() => update({ learnOnHoldDays: hold, learnReviewIntervals: parsed, learnQuizSize: quiz, learnPassPercent: pass })}>
          Save
        </Button>
      </div>
    </SettingsSection>
  );
}

export function MotionSection() {
  const { brain } = useBrain();
  const commit = useBrain((s) => s.commit);
  const [target, setTarget] = useState(brain.settings.motionDailyTarget);
  useEffect(() => setTarget(brain.settings.motionDailyTarget), [brain.settings.motionDailyTarget]);

  return (
    <SettingsSection id="motion" title="Motion" hint="Shared with the phone.">
      <Row label={`Daily target · ${target} pts`}>
        <Slider value={target} min={5} max={200} step={5} onChange={setTarget} />
        <div className="mt-2 flex justify-end">
          <Button size="sm" variant="secondary" disabled={target === brain.settings.motionDailyTarget}
            onClick={() => commit((s) => ({ ...s, settings: { ...s.settings, motionDailyTarget: target } }))}>Apply</Button>
        </div>
      </Row>
      <Row label="Supermarket day">
        <Select<DayOfWeek>
          value={brain.settings.supermarketDay}
          options={DAYS.map((d) => ({ value: d, label: d.charAt(0) + d.slice(1).toLowerCase() }))}
          onChange={(d) => commit((s) => ({ ...s, settings: { ...s.settings, supermarketDay: d } }))}
          className="w-48"
        />
      </Row>
    </SettingsSection>
  );
}

const SHORTCUTS: [string, string[]][] = [
  ["Quick capture (anywhere in Windows)", ["Ctrl Shift Space"]],
  ["Search Brain", ["Ctrl K"]],
  ["Back / forward", ["Alt ←", "Alt →"]],
  ["Save a dialog", ["Ctrl Enter"]],
  ["Calendar: save the open day", ["Ctrl S"]],
  ["Calendar: previous / next day", ["[", "]"]],
  ["Calendar: jump to today", ["T"]],
  ["Calendar: switch category", ["1", "2", "3", "4"]],
];

export function ShortcutsSection() {
  return (
    <SettingsSection id="shortcuts" title="Shortcuts">
      <div className="grid grid-cols-[minmax(0,1fr)_auto] gap-x-6 gap-y-2 text-sm">
        {SHORTCUTS.map(([label, keys]) => (
          <div key={label} className="contents">
            <div className="text-muted">{label}</div>
            <div className="flex gap-1">{keys.map((k) => <Kbd key={k}>{k}</Kbd>)}</div>
          </div>
        ))}
      </div>
    </SettingsSection>
  );
}
