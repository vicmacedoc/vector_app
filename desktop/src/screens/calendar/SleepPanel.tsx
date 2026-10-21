// Sleep strip for a Calendar day: last night's bed/wake, tonight's bedtime, details and AI fill.
import { useState } from "react";
import { Moon, Sparkles, Sun } from "lucide-react";
import { cn } from "@/lib/cn";
import { aiSleep } from "@/ai/calendar";
import type { SleepEntry } from "@/domain/daily/models";
import {
  SLEEP_STAGES, latencyMinutes, setLatency, setStageMinutes, setStagePercent, stageMinutes, stagePercent,
  timesMatch, totalSleepMinutes, applySleepEstimate, type SleepStage,
} from "@/domain/daily/sleep";
import { durationMinutes, formatDuration, formatHhMm } from "@/domain/daily/time";
import { errorMessage } from "@/platform/native";
import { Button, Dialog, Textarea } from "@/ui/primitives";
import { ImageDrop } from "./ImageDrop";
import { ErrorLine, NumField, TimeField } from "./ui";

const STAGE_LABEL: Record<SleepStage, string> = { DEEP: "Deep", LIGHT: "Light", REM: "REM", AWAKE: "Awake" };

function TimeColumn({
  title, planned, actual, enabled, onPlanned, onActual,
}: {
  title: string;
  planned: string;
  actual: string | null;
  enabled: boolean;
  onPlanned: (v: string) => void;
  onActual: (v: string) => void;
}) {
  const matches = actual === null || timesMatch(actual, planned);
  return (
    <div className="flex min-w-0 flex-1 flex-col items-center gap-1">
      <div className="text-xs font-medium">{title}</div>
      <label className="flex items-center gap-1 text-[11px] text-item">
        plan
        <TimeField value={planned} onChange={onPlanned} disabled={!enabled} className="h-7 border-transparent bg-offwhite px-1 text-[11px]" />
      </label>
      <TimeField
        value={actual ?? planned}
        onChange={onActual}
        disabled={!enabled}
        className={cn("h-10 w-full border-2 text-center font-semibold", matches ? "border-prio-low" : "border-prio-high")}
      />
    </div>
  );
}

function Connector({ icon, minutes }: { icon: React.ReactNode; minutes: number | null }) {
  return (
    <div className="flex w-20 shrink-0 flex-col items-center justify-center gap-1 pt-7 text-item">
      {icon}
      <span className="text-[10px]">{formatDuration(minutes)}</span>
    </div>
  );
}

export function SleepPanel({
  previousNight, tonight, bedDefault, wakeDefault, enabled, onChange,
}: {
  previousNight: SleepEntry | null;
  tonight: SleepEntry | null;
  bedDefault: number;
  wakeDefault: number;
  enabled: boolean;
  onChange: (which: "previousNight" | "tonight", next: SleepEntry) => void;
}) {
  const [aiOpen, setAiOpen] = useState(false);
  const defBed = formatHhMm(bedDefault);
  const defWake = formatHhMm(wakeDefault);
  const prev = previousNight;
  const plannedPrevBed = prev?.plannedBedtime ?? defBed;
  const plannedWake = prev?.plannedWakeUp ?? defWake;
  const plannedTonight = tonight?.plannedBedtime ?? defBed;
  const showPrevBed = prev?.actualBedtime ?? plannedPrevBed;
  const showWake = prev?.actualWakeUp ?? plannedWake;
  const showTonight = tonight?.actualBedtime ?? plannedTonight;
  const editPrev = (fn: (e: SleepEntry) => SleepEntry) => prev && onChange("previousNight", fn(prev));
  const editTonight = (fn: (e: SleepEntry) => SleepEntry) => tonight && onChange("tonight", fn(tonight));
  const total = prev ? totalSleepMinutes(prev) : null;
  const latency = prev ? latencyMinutes(prev) : null;

  return (
    <div className="space-y-4">
      <div className="flex items-start">
        <TimeColumn
          title="Bedtime"
          planned={plannedPrevBed}
          actual={prev?.actualBedtime ?? null}
          enabled={enabled && !!prev}
          onPlanned={(v) => editPrev((e) => ({ ...e, plannedBedtime: v }))}
          onActual={(v) => editPrev((e) => ({ ...e, actualBedtime: v }))}
        />
        <Connector icon={<Moon size={16} />} minutes={durationMinutes(showPrevBed, showWake)} />
        <TimeColumn
          title="Wake-up"
          planned={plannedWake}
          actual={prev?.actualWakeUp ?? null}
          enabled={enabled && !!prev}
          onPlanned={(v) => editPrev((e) => ({ ...e, plannedWakeUp: v }))}
          onActual={(v) => editPrev((e) => ({ ...e, actualWakeUp: v }))}
        />
        <Connector icon={<Sun size={16} />} minutes={durationMinutes(showWake, showTonight)} />
        <TimeColumn
          title="Bedtime"
          planned={plannedTonight}
          actual={tonight?.actualBedtime ?? null}
          enabled={enabled && !!tonight}
          onPlanned={(v) => editTonight((e) => ({ ...e, plannedBedtime: v }))}
          onActual={(v) => editTonight((e) => ({ ...e, actualBedtime: v }))}
        />
      </div>

      {prev && (
        <div className="rounded-lg bg-offwhite p-3">
          <div className="mb-2 flex items-center justify-between">
            <div className="text-xs font-semibold uppercase tracking-wide text-muted">Last night's details</div>
            <Button size="sm" variant="secondary" disabled={!enabled} onClick={() => setAiOpen(true)}>
              <Sparkles size={14} /> Fill from AI
            </Button>
          </div>
          <div className="grid grid-cols-[auto_1fr_1fr] items-center gap-x-3 gap-y-2 text-sm">
            <span className="text-xs">Fell asleep</span>
            <TimeField
              value={prev.fellAsleepAt}
              disabled={!enabled}
              onChange={(v) => editPrev((e) => ({ ...e, fellAsleepAt: v }))}
              className="w-full"
            />
            <div className="flex items-center gap-1">
              <NumField value={latency} integer min={0} disabled={!enabled} placeholder="after" onChange={(v) => editPrev((e) => setLatency(e, v))} />
              <span className="text-[11px] text-item">min</span>
            </div>
            {SLEEP_STAGES.map((s) => {
              const mins = stageMinutes(prev, s);
              return (
                <div key={s} className="contents">
                  <span className="text-xs">{STAGE_LABEL[s]}</span>
                  <div className="flex items-center gap-1">
                    <NumField value={mins} integer min={0} disabled={!enabled} placeholder="--" onChange={(v) => editPrev((e) => setStageMinutes(e, s, v))} />
                    <span className="text-[11px] text-item">min</span>
                  </div>
                  <div className="flex items-center gap-1">
                    <NumField
                      value={stagePercent(mins, total)}
                      integer
                      min={0}
                      max={100}
                      disabled={!enabled || total === null}
                      placeholder="--"
                      onChange={(v) => editPrev((e) => setStagePercent(e, s, v))}
                    />
                    <span className="text-[11px] text-item">%</span>
                  </div>
                </div>
              );
            })}
          </div>
          <div className="mt-2 text-[11px] text-item">Total sleep {formatDuration(total)} · percentages use fell-asleep → wake-up.</div>
        </div>
      )}

      {aiOpen && prev && (
        <SleepAiDialog
          onClose={() => setAiOpen(false)}
          onApply={(est) => {
            editPrev((e) => applySleepEstimate(e, est));
            setAiOpen(false);
          }}
        />
      )}
    </div>
  );
}

function SleepAiDialog({ onClose, onApply }: { onClose: () => void; onApply: (e: Awaited<ReturnType<typeof aiSleep>>) => void }) {
  const [image, setImage] = useState<Blob | null>(null);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const run = async () => {
    setBusy(true);
    setError(null);
    try {
      onApply(await aiSleep(image, text));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog
      open
      onClose={onClose}
      title="Fill sleep from AI"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={run} disabled={busy || (!image && !text.trim())}>{busy ? "Reading…" : "Fill"}</Button>
        </>
      }
    >
      <div className="space-y-3">
        <ImageDrop images={image ? [image] : []} onChange={(l) => setImage(l[l.length - 1] ?? null)} max={1} hint="Drop or paste a sleep-tracker screenshot" />
        <Textarea rows={3} value={text} onChange={(e) => setText(e.target.value)} placeholder="Description (optional with a screenshot)" />
        <ErrorLine>{error}</ErrorLine>
      </div>
    </Dialog>
  );
}
