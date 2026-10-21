import { useEffect, useMemo, useState } from "react";
import { Activity, Apple, BarChart3, Dumbbell, Moon, Scale, Smile, type LucideIcon } from "lucide-react";
import {
  loadBody, loadFood, loadOverview, loadRoutine, loadSelf, loadSleep, loadTraining, mergedSetsInRange, resolveDateRange,
  type AnalysisWindow,
} from "@/domain/daily/analysis";
import { eachDayInclusive } from "@/domain/daily/stats";
import { cn } from "@/lib/cn";
import { listAlbums, imageTakenAt, type Album } from "@/drive/self";
import { useBrain } from "@/store/brain";
import { useDaily } from "@/store/daily";
import { useNav, type AnalysisDashboard } from "@/store/nav";
import { Input, Segmented } from "@/ui/primitives";
import { shortDate } from "./charts";
import {
  BodyDashboard, FoodDashboard, OverviewDashboard, RoutineDashboard, SelfDashboard, SleepDashboard, TrainingDashboard,
} from "./dashboards";

const DASHBOARDS: { id: AnalysisDashboard; label: string; icon: LucideIcon; hint: string }[] = [
  { id: "overview", label: "Overview", icon: BarChart3, hint: "Score, consistency and streaks" },
  { id: "routine", label: "Routine", icon: Activity, hint: "Time allocation and habits" },
  { id: "food", label: "Food", icon: Apple, hint: "Calories, macros and timing" },
  { id: "training", label: "Training", icon: Dumbbell, hint: "Volume, tonnage and strength" },
  { id: "body", label: "Body", icon: Scale, hint: "Weight and composition" },
  { id: "sleep", label: "Sleep", icon: Moon, hint: "Duration, stages and rhythm" },
  { id: "self", label: "Self", icon: Smile, hint: "Mood, scales and reflection" },
];

const WINDOWS: { value: AnalysisWindow; label: string }[] = [
  { value: "DAYS_7", label: "7D" },
  { value: "DAYS_30", label: "30D" },
  { value: "DAYS_90", label: "90D" },
  { value: "ALL", label: "All" },
  { value: "CUSTOM", label: "Custom" },
];

const RANGE_KEY = "vector:analysisRange";

interface RangePrefs {
  window: AnalysisWindow;
  customStart: string | null;
  customEnd: string | null;
}

function loadPrefs(): RangePrefs {
  try {
    const p = JSON.parse(localStorage.getItem(RANGE_KEY) ?? "null") as RangePrefs | null;
    if (p && WINDOWS.some((w) => w.value === p.window)) return p;
  } catch { /* fall through */ }
  return { window: "DAYS_30", customStart: null, customEnd: null };
}

/** Self photos captured inside the range, counted from the Drive album file names. */
function usePhotoCount(start: string, end: string): number | null {
  const signedIn = useBrain((s) => s.signedIn);
  const root = useBrain((s) => s.settings.rootFolderId);
  const [albums, setAlbums] = useState<Album[] | null>(null);
  useEffect(() => {
    if (!signedIn || !root) return setAlbums(null);
    let live = true;
    listAlbums().then((a) => live && setAlbums(a), () => live && setAlbums(null));
    return () => { live = false; };
  }, [signedIn, root]);
  return useMemo(() => {
    if (!albums) return null;
    const from = new Date(`${start}T00:00:00`).getTime();
    const to = new Date(`${end}T00:00:00`).getTime() + 86_400_000;
    let n = 0;
    for (const a of albums) for (const img of a.images) {
      const t = imageTakenAt(img.name);
      if (t !== null && t >= from && t < to) n++;
    }
    return n;
  }, [albums, start, end]);
}

export function AnalysisScreen() {
  const dashboard = useNav((s) => s.dashboard);
  const shards = useDaily((s) => s.shards);
  const [prefs, setPrefs] = useState(loadPrefs);
  const [muscle, setMuscle] = useState<string | null>(null);
  const [exercise, setExercise] = useState<string | null>(null);

  const update = (patch: Partial<RangePrefs>) =>
    setPrefs((p) => {
      const next = { ...p, ...patch };
      localStorage.setItem(RANGE_KEY, JSON.stringify(next));
      return next;
    });

  const [start, end] = useMemo(
    () => resolveDateRange(shards, prefs.window, prefs.customStart, prefs.customEnd),
    [shards, prefs],
  );
  const days = useMemo(() => eachDayInclusive(start, end).length, [start, end]);
  const photoCount = usePhotoCount(start, end);

  const body = useMemo(() => {
    switch (dashboard) {
      case "overview": return <OverviewDashboard data={loadOverview(shards, start, end)} />;
      case "routine": return <RoutineDashboard data={loadRoutine(shards, start, end)} />;
      case "food": return <FoodDashboard data={loadFood(shards, start, end)} />;
      case "training": return (
        <TrainingDashboard
          data={loadTraining(mergedSetsInRange(shards, start, end), start, end, muscle, exercise)}
          onSelectMuscle={setMuscle} onSelectExercise={setExercise}
        />
      );
      case "body": return <BodyDashboard data={loadBody(shards, start, end)} />;
      case "sleep": return <SleepDashboard data={loadSleep(shards, start, end)} />;
      case "self": return <SelfDashboard data={loadSelf(shards, start, end)} photoCount={photoCount} />;
    }
  }, [dashboard, shards, start, end, muscle, exercise, photoCount]);

  const current = DASHBOARDS.find((d) => d.id === dashboard) ?? DASHBOARDS[0];

  return (
    <div className="flex h-full">
      <nav className="w-56 shrink-0 overflow-y-auto border-r border-hairline px-3 py-6">
        <h1 className="mb-4 px-3 text-2xl font-semibold">Analysis</h1>
        <div className="space-y-0.5">
          {DASHBOARDS.map((d) => {
            const Icon = d.icon;
            const active = d.id === dashboard;
            return (
              <button
                key={d.id}
                onClick={() => useNav.getState().open({ view: "analysis", dashboard: d.id })}
                className={cn(
                  "flex w-full items-start gap-3 rounded-lg px-3 py-2 text-left transition",
                  active ? "bg-navy text-white" : "text-muted hover:bg-offwhite hover:text-black",
                )}
              >
                <Icon size={16} className="mt-0.5 shrink-0" />
                <span className="min-w-0">
                  <span className="block text-sm font-medium">{d.label}</span>
                  <span className={cn("block truncate text-[11px]", active ? "text-white/70" : "text-item")}>{d.hint}</span>
                </span>
              </button>
            );
          })}
        </div>
      </nav>

      <div className="min-w-0 flex-1 overflow-y-auto">
        <header className="sticky top-0 z-10 flex flex-wrap items-center gap-3 border-b border-hairline bg-white/95 px-8 py-4 backdrop-blur">
          <div className="mr-auto">
            <h2 className="text-lg font-semibold">{current.label}</h2>
            <div className="font-mono text-xs text-item">
              {shortDate(start)} – {shortDate(end)} · {days} {days === 1 ? "day" : "days"}
            </div>
          </div>
          {prefs.window === "CUSTOM" && (
            <div className="flex items-center gap-2">
              <Input type="date" className="h-9 w-40" value={prefs.customStart ?? start} max={end} onChange={(e) => update({ customStart: e.target.value || null })} />
              <span className="text-item">–</span>
              <Input type="date" className="h-9 w-40" value={prefs.customEnd ?? end} onChange={(e) => update({ customEnd: e.target.value || null })} />
            </div>
          )}
          <Segmented value={prefs.window} options={WINDOWS} onChange={(w) => update({ window: w })} />
        </header>
        <div className="px-8 py-6">{body}</div>
      </div>
    </div>
  );
}
