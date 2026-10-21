// Three-column Home: Brain | Daily inputs + habits | Body. The date drives the grid end, habits and body inputs.
import { useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { addDays, formatDay, todayKey } from "@/domain/dates";
import { useDaily } from "@/store/daily";
import { Button } from "@/ui/primitives";
import { BodyCard } from "./home/BodyCard";
import { BrainColumn } from "./home/BrainColumn";
import { DailyInputs } from "./home/DailyInputs";
import { HabitsTracker } from "./home/HabitsTracker";

export function HomeScreen() {
  const today = todayKey();
  const [date, setDate] = useState(today);
  const ready = useDaily((s) => s.ready);

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-[1600px] px-8 py-6">
        <div className="mb-5 flex flex-wrap items-center gap-3">
          <div>
            <div className="text-sm text-muted">{date === today ? "Today" : date < today ? "Looking back" : "Looking ahead"}</div>
            <h1 className="text-2xl font-semibold">{formatDay(date)}</h1>
          </div>
          <div className="ml-auto flex items-center gap-1">
            <Button size="icon" variant="ghost" title="Previous day" onClick={() => setDate(addDays(date, -1))}><ChevronLeft size={18} /></Button>
            <input
              type="date"
              value={date}
              onChange={(e) => e.target.value && setDate(e.target.value)}
              className="h-9 rounded-lg border border-hairline px-2 text-sm outline-none focus:border-electric"
            />
            <Button size="icon" variant="ghost" title="Next day" onClick={() => setDate(addDays(date, 1))}><ChevronRight size={18} /></Button>
            {date !== today && <Button size="sm" variant="secondary" onClick={() => setDate(today)}>Today</Button>}
          </div>
        </div>

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(260px,1fr)_minmax(0,2fr)] 2xl:grid-cols-[minmax(280px,1fr)_minmax(0,2.2fr)_minmax(280px,1fr)]">
          <section>
            <h2 className="mb-2 text-xs font-semibold tracking-wide text-item uppercase">Brain</h2>
            <BrainColumn />
          </section>
          <section className="space-y-5">
            <h2 className="-mb-3 text-xs font-semibold tracking-wide text-item uppercase">Daily</h2>
            {ready ? (
              <>
                <DailyInputs endDate={date} />
                <HabitsTracker endDate={date} />
                <div className="2xl:hidden"><BodyCard date={date} /></div>
              </>
            ) : (
              <div className="text-sm text-muted">Loading daily data…</div>
            )}
          </section>
          <section className="hidden 2xl:block">
            <h2 className="mb-2 text-xs font-semibold tracking-wide text-item uppercase">Body</h2>
            {ready && <BodyCard date={date} />}
          </section>
        </div>
      </div>
    </div>
  );
}
