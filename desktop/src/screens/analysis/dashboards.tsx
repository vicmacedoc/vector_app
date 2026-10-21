// The seven dashboards, same sections and copy as the phone, laid out on a responsive grid.
import type { ReactNode } from "react";
import type {
  BodyData, FoodData, OverviewData, RoutineData, SelfData, SelfScaleSeries, SleepData, TrainingData,
} from "@/domain/daily/analysis";
import type { HabitStatus } from "@/domain/daily/models";
import { HABIT_COLOR } from "@/screens/calendar/RoutineView";
import { Select } from "@/ui/primitives";
import {
  BarChart, clock, DeltaPill, Donut, DotStrip, EmptyState, formatStat, heat, Heatmap, HeroStat, Legend, LineChart,
  moodColor, percentOrDash, RankBars, Section, shortDate, signed, StackedBarChart, StatRow, statOrDash, THEME,
} from "./charts";

const HOURS_PER_DAY = 24;
const TIMING_LANES = 21;
const MACRO = { protein: "#0074d9", carbs: "#ff9800", fats: "#003366" };

/** Two columns from xl up; `wide` sections span the full row. */
function Grid({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-1 gap-4 xl:grid-cols-2 2xl:grid-cols-3">{children}</div>;
}
const WIDE = "xl:col-span-2 2xl:col-span-3";
const HALF_WIDE = "2xl:col-span-2";
const note = (text: string) => <p className="mt-3 text-[11px] text-item">{text}</p>;
const gap = (a: number | null, b: number | null) => (a == null || b == null ? "—" : signed(a - b));

// ---- Overview ----

export function OverviewDashboard({ data }: { data: OverviewData }) {
  if (data.daysLogged === 0) return <EmptyState message="Nothing logged in this range yet." />;
  const score = data.scoreAverage ?? 0;
  return (
    <Grid>
      <Section title="Vector score" trailing={`${data.daysLogged} / ${data.dates.length} days`} subtitle="Average completion across Routine, Food, Exercise and Self.">
        <Donut
          slices={[{ name: "Score", value: score, color: THEME.accent }, { name: "Remaining", value: Math.max(0, 100 - score), color: THEME.empty }]}
          centerValue={`${Math.trunc(score)}%`} centerCaption="period avg"
        />
        {data.scoreDelta != null && (
          <div className="mt-3 flex items-center justify-center gap-2 text-[11px] text-item">
            vs first half of range <DeltaPill delta={data.scoreDelta} unit="pt" />
          </div>
        )}
      </Section>
      <Section title="Score over time" subtitle="Daily completion share." className={HALF_WIDE}>
        <LineChart dates={data.dates} layers={[{ name: "Score", values: data.score, color: THEME.accent, filled: true }]} yLabel="% complete" height={260} />
      </Section>
      <Section title="Consistency" subtitle="One column per day; darker means a higher share completed." className={WIDE}>
        <Heatmap
          rows={data.domains.map((d) => ({
            label: d.name,
            cells: d.values.map((v) => (v == null ? THEME.empty : heat(v / 100))),
            titles: d.values.map((v, i) => `${shortDate(data.dates[i])}: ${v == null ? "—" : `${Math.round(v)}%`}`),
            trailing: percentOrDash(d.average),
          }))}
          cellHeight={24}
          startCaption={data.dates[0] && shortDate(data.dates[0])}
          endCaption={data.dates.at(-1) && shortDate(data.dates.at(-1)!)}
        />
      </Section>
      <Section title="Streaks" subtitle="Consecutive days with something logged in each domain.">
        <div className="space-y-2.5">
          {data.domains.map((d) => (
            <div key={d.name} className="flex items-center gap-4">
              <span className="flex-1 text-sm font-medium">{d.name}</span>
              {([["Now", d.currentStreak], ["Best", d.bestStreak], ["Days", d.daysWithData]] as const).map(([l, v]) => (
                <div key={l} className="w-10 text-right">
                  <div className="text-[9px] text-item">{l}</div>
                  <div className="font-mono text-sm font-semibold text-navy">{v}</div>
                </div>
              ))}
            </div>
          ))}
        </div>
      </Section>
      <Section title="Momentum" subtitle="Second half of the range versus the first." className={HALF_WIDE}>
        <StatRow stats={data.domains.map((d) => [d.name, d.delta == null ? "—" : `${d.delta >= 0 ? "+" : "−"}${Math.trunc(Math.abs(d.delta))}pt`])} />
      </Section>
    </Grid>
  );
}

// ---- Routine ----

const habitCell = (s: HabitStatus | null) => (s === null ? "transparent" : s === "NONE" ? THEME.empty : HABIT_COLOR[s]);

export function RoutineDashboard({ data }: { data: RoutineData }) {
  if (!data.labels.length && !data.habits.length) return <EmptyState message="No habits or activities logged in this range." />;
  const best = [...data.habits].sort((a, b) => b.currentStreak - a.currentStreak)[0];
  return (
    <Grid>
      {data.labels.length > 0 && (
        <Section title="Where the day goes" trailing={`${data.planCompletionPercent}% of plan`} subtitle="Average day. Inner ring is planned, outer ring is done.">
          <Donut
            slices={data.labels.map((l) => ({ name: l.name, value: l.plannedMinutes, color: l.color }))}
            outer={data.labels.map((l) => ({ name: l.name, value: l.doneMinutes, color: l.color }))}
            centerValue={`${formatStat(data.avgDoneHours)}h`} centerCaption="done / day" total={HOURS_PER_DAY * 60} size={220}
          />
          <Legend className="mt-3" entries={data.labels.map((l) => [l.name, l.color])} />
          <StatRow
            className="mt-4"
            stats={[
              ["Planned / day", `${formatStat(data.avgPlannedHours)}h`],
              ["Done / day", `${formatStat(data.avgDoneHours)}h`],
              ["Unlogged", `${formatStat(Math.max(0, HOURS_PER_DAY - data.avgDoneHours))}h`],
            ]}
          />
        </Section>
      )}
      {data.weeklyLabelHours.length > 0 && data.weeks.length > 0 && (
        <Section title="Weekly allocation" subtitle="Hours completed each week, split by activity label." className={HALF_WIDE}>
          <StackedBarChart labels={data.weeks} segments={data.weeklyLabelHours} yLabel="hours" height={280} />
          <Legend className="mt-3" entries={data.weeklyLabelHours.map((s) => [s.name, s.color])} />
        </Section>
      )}
      {data.habits.length > 0 && (
        <Section title="Habits" trailing={percentOrDash(data.habitOverallRate)} subtitle="One column per day. A gap means the habit was not scheduled." className={WIDE}>
          <Heatmap
            rows={data.habits.map((h) => ({
              label: h.name,
              cells: h.cells.map(habitCell),
              titles: h.cells.map((s, i) => `${shortDate(data.dates[i])}: ${s ?? "not scheduled"}`),
              trailing: percentOrDash(h.completionRate),
            }))}
            labelWidth={140}
            startCaption={data.dates[0] && shortDate(data.dates[0])}
            endCaption={data.dates.at(-1) && shortDate(data.dates.at(-1)!)}
          />
          <Legend
            className="mt-3"
            entries={[
              ["Done", HABIT_COLOR.DONE], ["Exceeded", HABIT_COLOR.EXCEEDED], ["Partial", HABIT_COLOR.PARTIAL],
              ["Not done", HABIT_COLOR.NOT_DONE], ["N/A", HABIT_COLOR.NA], ["Unanswered", THEME.empty],
            ]}
          />
          {best && best.currentStreak > 0 && note(`Longest live streak: ${best.name} at ${best.currentStreak} days.`)}
        </Section>
      )}
      <Section title="Execution" subtitle="How closely activities ran to the time they were planned for." className={HALF_WIDE}>
        <StatRow
          stats={[
            ["Activities", String(data.activitiesLogged)],
            ["Adjusted", percentOrDash(data.adjustedPercent)],
            ["Start drift", data.avgDriftMinutes == null ? "—" : `${formatStat(data.avgDriftMinutes)}m`],
          ]}
        />
      </Section>
    </Grid>
  );
}

// ---- Food ----

export function FoodDashboard({ data }: { data: FoodData }) {
  if (data.daysLogged === 0) return <EmptyState message="No food logged in this range." />;
  const lanes = data.mealTimes.slice(-TIMING_LANES);
  return (
    <Grid>
      <Section title="Macro split" trailing={`${formatStat(data.avgKcal)} kcal / day`} subtitle="Share of calories from each macro, averaged over logged days.">
        <Donut
          slices={[
            { name: "Protein", value: data.proteinKcalShare, color: MACRO.protein },
            { name: "Carbs", value: data.carbsKcalShare, color: MACRO.carbs },
            { name: "Fat", value: data.fatsKcalShare, color: MACRO.fats },
          ]}
          centerValue={String(Math.trunc(data.avgKcal))} centerCaption="kcal / day"
        />
        <Legend
          className="mt-3"
          entries={[
            [`Protein ${Math.trunc(data.proteinKcalShare)}%`, MACRO.protein],
            [`Carbs ${Math.trunc(data.carbsKcalShare)}%`, MACRO.carbs],
            [`Fat ${Math.trunc(data.fatsKcalShare)}%`, MACRO.fats],
          ]}
        />
        <StatRow
          className="mt-4"
          stats={[["Protein", `${formatStat(data.avgProteinG)}g`], ["Carbs", `${formatStat(data.avgCarbsG)}g`], ["Fat", `${formatStat(data.avgFatsG)}g`]]}
        />
      </Section>
      <Section title="Calories" subtitle="Daily intake with a 7-day rolling average." className={HALF_WIDE}>
        <BarChart labels={data.dates} values={data.kcal} overlay={data.kcalRolling} yLabel="kcal" height={260} />
        <Legend className="mt-3" entries={[["Daily", THEME.accent], ["7-day average", THEME.navy]]} />
        <StatRow
          className="mt-4"
          stats={[["Highest", statOrDash(data.kcalStat?.high)], ["Average", statOrDash(data.kcalStat?.avg)], ["Lowest", statOrDash(data.kcalStat?.low)]]}
        />
      </Section>
      {data.adherence.length > 0 && data.weeks.length > 0 && (
        <Section title="Plan adherence" trailing={percentOrDash(data.compliancePercent)} subtitle="Food items each week by how they matched the plan.">
          <StackedBarChart labels={data.weeks} segments={data.adherence} yLabel="items" />
          <Legend className="mt-3" entries={data.adherence.map((s) => [s.name, s.color])} />
        </Section>
      )}
      {data.mealTimes.length > 0 && (
        <Section
          title="Eating window"
          trailing={data.avgEatingWindowHours == null ? undefined : `${formatStat(data.avgEatingWindowHours)}h`}
          subtitle="Every meal you ate, placed at the time you ate it."
          className={HALF_WIDE}
        >
          <DotStrip rows={lanes.map((d) => ({ label: shortDate(d.date), minutes: d.minutes }))} />
          <StatRow
            className="mt-4"
            stats={[
              ["First meal", clock(data.avgFirstMealMinutes)],
              ["Last meal", clock(data.avgLastMealMinutes)],
              ["Window", data.avgEatingWindowHours == null ? "—" : `${formatStat(data.avgEatingWindowHours)}h`],
            ]}
          />
          {data.mealTimes.length > TIMING_LANES && note(`Showing the most recent ${TIMING_LANES} logged days.`)}
        </Section>
      )}
    </Grid>
  );
}

// ---- Training ----

export function TrainingDashboard({ data, onSelectMuscle, onSelectExercise }: {
  data: TrainingData; onSelectMuscle: (m: string) => void; onSelectExercise: (e: string) => void;
}) {
  if (data.setsLogged === 0) return <EmptyState message="No sets logged in this range." />;
  return (
    <Grid>
      {data.muscleRanking.length > 0 && (
        <Section title="Muscle volume" trailing={`${data.sessionDays} session days`} subtitle="Set-equivalents over the range. Click a muscle to chart it." className={HALF_WIDE}>
          <div className="grid gap-6 lg:grid-cols-2">
            <RankBars
              items={data.muscleRanking.map((m) => ({ label: m.name, value: m.setEquivalents, valueLabel: formatStat(m.setEquivalents) }))}
              selected={data.selectedMuscle} onSelect={onSelectMuscle} maxRows={20}
            />
            {data.selectedMuscle && data.weeks.length > 0 && (
              <div>
                <LineChart dates={data.weeks} layers={[{ name: data.selectedMuscle, values: data.muscleWeekly, color: THEME.navy, filled: true }]} yLabel="sets / week" />
                <StatRow
                  className="mt-3"
                  stats={[["Peak week", statOrDash(data.muscleStat?.high)], ["Average", statOrDash(data.muscleStat?.avg)], ["Lowest", statOrDash(data.muscleStat?.low)]]}
                />
              </div>
            )}
          </div>
        </Section>
      )}
      <Section title="Execution" subtitle="Intensity and how closely sets matched their targets.">
        <StatRow
          stats={[
            ["Sets done", `${data.setsCompleted}/${data.setsLogged}`],
            ["Completion", percentOrDash(data.completionPercent)],
            ["Avg RPE", statOrDash(data.avgRpe)],
          ]}
        />
        <StatRow
          className="mt-3"
          stats={[
            ["Avg RIR", statOrDash(data.avgRir)],
            ["Load vs target", signed(data.avgLoadDelta, "kg")],
            ["Session days", String(data.sessionDays)],
          ]}
        />
      </Section>
      {data.weeks.length > 0 && data.totalTonnage > 0 && (
        <Section title="Tonnage" trailing={`${formatStat(data.totalTonnage)} kg total`} subtitle="Load times reps for every completed set, by week.">
          <LineChart dates={data.weeks} layers={[{ name: "Tonnage", values: data.tonnageWeekly, color: THEME.accent, filled: true }]} yLabel="kg / week" />
        </Section>
      )}
      {data.exercises.length > 0 && (
        <Section title="Strength progression" subtitle="Best estimated one-rep max per session (Epley)." className={HALF_WIDE}>
          <Select
            className="w-full max-w-sm"
            value={data.selectedExercise ?? ""}
            options={data.exercises.map((e) => ({ value: e, label: e }))}
            onChange={onSelectExercise}
          />
          {data.e1rmDates.length >= 2 ? (
            <div className="mt-4">
              <HeroStat value={statOrDash(data.bestE1rm)} unit="kg" caption="best estimated 1RM" delta={data.e1rmDelta} deltaUnit="kg" />
              <div className="mt-3">
                <LineChart dates={data.e1rmDates} layers={[{ name: "e1RM", values: data.e1rm, color: THEME.accent, filled: true }]} yLabel="kg" />
              </div>
            </div>
          ) : (
            note("Log load and reps on at least two sessions to chart progression.")
          )}
        </Section>
      )}
    </Grid>
  );
}

// ---- Body ----

export function BodyDashboard({ data }: { data: BodyData }) {
  if (!data.weight.some((v) => v != null) && !data.bodyFat.some((v) => v != null)) {
    return <EmptyState message="No body composition data in this range." />;
  }
  return (
    <Grid>
      <Section title="Body composition" subtitle="Lean mass is derived from weight and body fat." className={WIDE}>
        <HeroStat
          value={statOrDash(data.latestWeight)} unit="kg"
          caption={data.latestBodyFat == null ? null : `${formatStat(data.latestBodyFat)}% body fat`}
          delta={data.weightDelta} deltaUnit="kg" higherIsBetter={false}
        />
        <div className="mt-4">
          <LineChart
            dates={data.dates} height={300} yLabel="kg"
            layers={[
              { name: "Weight", values: data.weight, color: THEME.accent, filled: true },
              { name: "Lean mass", values: data.leanMass, color: THEME.navy },
              { name: "Trend", values: data.weightTrend, color: THEME.muted, dashed: true },
            ]}
          />
        </div>
        <Legend className="mt-3" entries={[["Weight", THEME.accent], ["Lean mass", THEME.navy], ["Trend", THEME.muted]]} />
        <StatRow
          className="mt-4"
          stats={[["Weight Δ", signed(data.weightDelta, "kg")], ["Body fat Δ", signed(data.bodyFatDelta, "%")], ["Lean Δ", signed(data.leanMassDelta, "kg")]]}
        />
      </Section>
      {data.bodyFat.some((v) => v != null) && (
        <Section title="Body fat" subtitle="Logged body fat percentage." className={WIDE}>
          <LineChart dates={data.dates} layers={[{ name: "Body fat %", values: data.bodyFat, color: THEME.warning, filled: true }]} yLabel="%" />
        </Section>
      )}
    </Grid>
  );
}

// ---- Sleep ----

export function SleepDashboard({ data }: { data: SleepData }) {
  const hasHours = data.sleepHours.some((v) => v != null);
  const hasStages = data.stageSeries.some((s) => s.values.some((v) => v > 0));
  if (!hasHours && !hasStages) return <EmptyState message="No sleep data in this range." />;
  return (
    <Grid>
      {hasHours && (
        <Section
          title="Duration" trailing={data.sleepStat ? `${formatStat(data.sleepStat.avg)}h avg` : undefined}
          subtitle="Nightly sleep against your planned window. Attributed to the morning it ended." className={HALF_WIDE}
        >
          <BarChart labels={data.dates} values={data.sleepHours} yLabel="hours" target={data.sleepTargetHours} height={260} />
          <StatRow
            className="mt-4"
            stats={[
              ["Avg bedtime", clock(data.avgBedtimeMinutes)],
              ["Avg wake", clock(data.avgWakeMinutes)],
              ["Bedtime spread", data.bedtimeVarianceMinutes == null ? "—" : `±${formatStat(data.bedtimeVarianceMinutes)}m`],
            ]}
          />
        </Section>
      )}
      {(data.efficiency.some((v) => v != null) || data.latencyMinutes.some((v) => v != null)) && (
        <Section title="Efficiency and latency" subtitle="Asleep share of time in bed, and minutes to fall asleep.">
          <LineChart
            dates={data.dates} yLabel="% / min"
            layers={[
              { name: "Efficiency %", values: data.efficiency, color: THEME.accent, filled: true },
              { name: "Latency min", values: data.latencyMinutes, color: THEME.navy },
            ]}
          />
          <StatRow
            className="mt-4"
            stats={[
              ["Avg efficiency", data.avgEfficiency == null ? "—" : `${formatStat(data.avgEfficiency)}%`],
              ["Avg latency", data.avgLatencyMinutes == null ? "—" : `${formatStat(data.avgLatencyMinutes)}m`],
            ]}
          />
        </Section>
      )}
      {hasStages && (
        <Section title="Stages" subtitle="Hours in deep, light, REM and awake across the night." className={HALF_WIDE}>
          <StackedBarChart labels={data.dates} segments={data.stageSeries} yLabel="hours" height={260} />
          <Legend className="mt-3" entries={data.stageSeries.map((s) => [s.name, s.color])} />
        </Section>
      )}
      {data.correlations.length > 0 && (
        <Section title="Correlations" subtitle="Pearson r against mood, energy and training volume.">
          <RankBars items={data.correlations.map((c) => ({ label: c.label, value: Math.abs(c.r), valueLabel: c.r.toFixed(2) }))} />
        </Section>
      )}
      {(data.bedtimeRows.length > 0 || data.wakeRows.length > 0) && (
        <Section title="Consistency" subtitle="Bedtimes and wakes on the 24h clock." className={WIDE}>
          <div className="grid gap-6 lg:grid-cols-2">
            {data.bedtimeRows.length > 0 && (
              <div>
                <div className="mb-2 text-xs font-medium text-muted">Bedtime</div>
                <DotStrip
                  rows={data.bedtimeRows.slice(-TIMING_LANES).map((r) => ({ label: shortDate(r.date), minutes: r.minutes.map((m) => (m < 12 * 60 ? m + 24 * 60 : m)) }))}
                  startMinute={18 * 60} endMinute={30 * 60} color={THEME.navy}
                />
              </div>
            )}
            {data.wakeRows.length > 0 && (
              <div>
                <div className="mb-2 text-xs font-medium text-muted">Wake</div>
                <DotStrip rows={data.wakeRows.slice(-TIMING_LANES).map((r) => ({ label: shortDate(r.date), minutes: r.minutes }))} startMinute={4 * 60} endMinute={12 * 60} />
              </div>
            )}
          </div>
        </Section>
      )}
    </Grid>
  );
}

// ---- Self ----

function ScaleRow({ scale }: { scale: SelfScaleSeries }) {
  return (
    <div className="flex items-center gap-2">
      <div className="min-w-0 flex-1">
        <div className="truncate text-xs font-medium">{scale.label}</div>
        <div className="truncate text-[10px] text-item">{scale.lowLabel} → {scale.highLabel}</div>
      </div>
      <span className="font-mono text-sm font-semibold text-navy">{statOrDash(scale.average)}</span>
      <span className="w-8 text-right font-mono text-[11px] text-item">{scale.daysRated}d</span>
      {scale.delta != null && <DeltaPill delta={scale.delta} />}
    </div>
  );
}

const MOOD_LEGEND: [string, string][] = [...[1, 2, 3, 4, 5].map((l): [string, string] => [String(l), moodColor(l)]), ["None", THEME.empty]];

export function SelfDashboard({ data, photoCount }: { data: SelfData; photoCount: number | null }) {
  if (data.loggedDays === 0) return <EmptyState message="No self entries in this range." />;
  const rated = data.scales.filter((s) => s.daysRated > 0);
  const crossDomain = data.moodOnGoodSleep != null || data.moodOnPoorSleep != null || data.moodOnTrainingDays != null || data.moodOnHighHabitDays != null;
  return (
    <Grid>
      <Section title="Affect" subtitle="Mood, energy and stress on the same 1–5 scale." className={HALF_WIDE}>
        <HeroStat value={statOrDash(data.moodAverage)} unit="/ 5 mood" caption={`${data.loggedDays} of ${data.dates.length} days logged`} delta={data.moodDelta} />
        <div className="mt-4">
          <LineChart
            dates={data.dates} yLabel="level" height={240}
            layers={[
              { name: "Mood", values: data.mood, color: THEME.accent, filled: true },
              { name: "Energy", values: data.energy, color: THEME.navy },
              { name: "Stress", values: data.stress, color: THEME.negative },
              { name: "Trend", values: data.moodTrend, color: THEME.muted, dashed: true },
            ]}
          />
        </div>
        <Legend className="mt-3" entries={[["Mood", THEME.accent], ["Energy", THEME.navy], ["Stress", THEME.negative], ["Trend", THEME.muted]]} />
        <StatRow className="mt-4" stats={[["Mood", statOrDash(data.moodAverage)], ["Energy", statOrDash(data.energyAverage)], ["Stress", statOrDash(data.stressAverage)]]} />
      </Section>
      {data.moodRows.length > 0 && (
        <Section title="Mood calendar" trailing={data.moodAverage == null ? undefined : `${formatStat(data.moodAverage)} / 5`} subtitle="One row per week, Monday through Sunday.">
          <Heatmap
            rows={data.moodRows.slice(-26).map((r) => ({
              label: shortDate(r.monday),
              cells: r.cells.map((l) => (l == null ? THEME.empty : moodColor(l))),
              titles: r.cells.map((l) => (l == null ? "—" : `Mood ${l}`)),
            }))}
            labelWidth={56} cellHeight={22} startCaption="Mon" endCaption="Sun"
          />
          <Legend className="mt-3" entries={MOOD_LEGEND} />
          {data.moodDistribution.length === 5 && (
            <StatRow className="mt-4" stats={data.moodDistribution.map((c, i): [string, string] => [`Mood ${i + 1}`, String(c)])} />
          )}
        </Section>
      )}
      {rated.length > 0 && (
        <Section title="Scales" subtitle="One row per scale, one cell per day. Every scale runs worst to best." className={WIDE}>
          <div className="grid gap-6 lg:grid-cols-[2fr_1fr]">
            <div>
              <Heatmap
                rows={rated.map((s) => ({
                  label: s.label,
                  cells: s.values.map((v) => (v == null ? THEME.empty : moodColor(v))),
                  titles: s.values.map((v, i) => `${shortDate(data.dates[i])}: ${v ?? "—"}`),
                  trailing: statOrDash(s.average),
                }))}
                labelWidth={110} cellHeight={18}
              />
              <Legend className="mt-3" entries={MOOD_LEGEND} />
            </div>
            <div className="space-y-2.5">
              {[...rated].sort((a, b) => (b.average ?? 0) - (a.average ?? 0)).map((s) => <ScaleRow key={s.label} scale={s} />)}
              {note("Average level and how it moved between the first and second half of the range.")}
            </div>
          </div>
        </Section>
      )}
      {data.moodCorrelations.length > 0 && (
        <Section title="Tracks with mood" subtitle="Correlation with mood across the days both were rated.">
          <RankBars
            items={data.moodCorrelations.map((c) => ({ label: c.label, value: Math.abs(c.r), valueLabel: `${c.r >= 0 ? "+" : "−"}${Math.abs(c.r).toFixed(2)}` }))}
          />
          {note("Bar length is the strength; a negative value means the two move apart. Correlation is not causation, and short ranges are noisy.")}
        </Section>
      )}
      {crossDomain && (
        <Section title="What moves mood" subtitle="Average mood split by sleep, training and habit adherence.">
          <div className="space-y-3">
            <StatRow stats={[[`Slept ${formatStat(data.goodSleepThresholdHours)}h+`, statOrDash(data.moodOnGoodSleep)], ["Short sleep", statOrDash(data.moodOnPoorSleep)], ["Gap", gap(data.moodOnGoodSleep, data.moodOnPoorSleep)]]} />
            <StatRow stats={[["Trained", statOrDash(data.moodOnTrainingDays)], ["Rest day", statOrDash(data.moodOnRestDays)], ["Day after", statOrDash(data.moodDayAfterTraining)]]} />
            <StatRow stats={[[`Habits ${formatStat(data.habitThresholdPercent)}%+`, statOrDash(data.moodOnHighHabitDays)], ["Habits below", statOrDash(data.moodOnLowHabitDays)], ["Gap", gap(data.moodOnHighHabitDays, data.moodOnLowHabitDays)]]} />
            {(data.sleepRatingOnGoodSleep != null || data.sleepRatingOnPoorSleep != null) && (
              <>
                <StatRow stats={[["Rated sleep, long", statOrDash(data.sleepRatingOnGoodSleep)], ["Rated sleep, short", statOrDash(data.sleepRatingOnPoorSleep)], ["Gap", gap(data.sleepRatingOnGoodSleep, data.sleepRatingOnPoorSleep)]]} />
                {note("How restorative the night felt against how long it actually was.")}
              </>
            )}
          </div>
        </Section>
      )}
      {data.intentionDays > 0 && (
        <Section title="Intention" trailing={`${data.intentionDays} days`} subtitle="Mood on the days you named an intention against the days you did not.">
          <StatRow stats={[["With intention", statOrDash(data.moodWithIntention)], ["Without", statOrDash(data.moodWithoutIntention)], ["Gap", gap(data.moodWithIntention, data.moodWithoutIntention)]]} />
        </Section>
      )}
      <Section title="Reflection" subtitle="Writing only after good days hides the days worth reading back.">
        <div className="space-y-3">
          <StatRow stats={[["Journal days", String(data.journalDays)], ["Avg words", statOrDash(data.avgWordsPerJournalDay)], ["Photos", photoCount == null ? "—" : String(photoCount)]]} />
          <StatRow stats={[["Wrote on low days", percentOrDash(data.journalRateOnLowMoodDays)], ["Wrote on good days", percentOrDash(data.journalRateOnHighMoodDays)], ["Days in range", String(data.dates.length)]]} />
          <StatRow stats={[["Fuelled", String(data.highlightDays)], ["Drained", String(data.lowlightDays)], ["Grateful", String(data.gratitudeDays)]]} />
          {note("Days each journal prompt was answered.")}
        </div>
      </Section>
    </Grid>
  );
}
