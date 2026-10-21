import { create } from "zustand";
import { todayKey } from "@/domain/dates";
import type { DraftCategory } from "@/domain/daily/drafts";

export type View =
  | "home" | "calendar" | "inbox" | "memory" | "model" | "motion" | "supermarket" | "analysis" | "settings";

export type CalendarMode = "day" | "week";
export type AnalysisDashboard = "overview" | "routine" | "food" | "training" | "body" | "sleep" | "self";

export interface Route {
  view: View;
  /** Open tile (Memory/Model) or list (Motion); null shows the grid. */
  selected: string | null;
  date: string;
  category: DraftCategory;
  calendarMode: CalendarMode;
  dashboard: AnalysisDashboard;
  /** Settings anchor to scroll to. */
  section: string | null;
}

interface Nav extends Route {
  back: Route[];
  forward: Route[];
  go(view: View, selected?: string | null): void;
  /** Navigates with history; omitted params keep their current value. */
  open(patch: Partial<Route> & { view: View }): void;
  /** Changes params in place (no history entry), e.g. stepping days in the Calendar. */
  replace(patch: Partial<Route>): void;
  goBack(): void;
  goForward(): void;
}

const HISTORY_LIMIT = 50;

const routeOf = (s: Route): Route => ({
  view: s.view, selected: s.selected, date: s.date, category: s.category,
  calendarMode: s.calendarMode, dashboard: s.dashboard, section: s.section,
});

const same = (a: Route, b: Route) => JSON.stringify(a) === JSON.stringify(b);

export const useNav = create<Nav>((set, get) => ({
  view: "home",
  selected: null,
  date: todayKey(),
  category: "ROUTINE",
  calendarMode: "day",
  dashboard: "overview",
  section: null,
  back: [],
  forward: [],

  go: (view, selected = null) => get().open({ view, selected }),

  open(patch) {
    const cur = routeOf(get());
    const next: Route = { ...cur, selected: null, section: null, ...patch };
    if (same(cur, next)) return;
    set({ ...next, back: [...get().back, cur].slice(-HISTORY_LIMIT), forward: [] });
  },

  replace: (patch) => set(patch),

  goBack() {
    const back = get().back;
    if (!back.length) return;
    const prev = back[back.length - 1];
    set({ ...prev, back: back.slice(0, -1), forward: [routeOf(get()), ...get().forward] });
  },

  goForward() {
    const [next, ...rest] = get().forward;
    if (!next) return;
    set({ ...next, forward: rest, back: [...get().back, routeOf(get())] });
  },
}));

/** Opens the Calendar on [date] / [category]. */
export const openCalendar = (date: string, category?: DraftCategory) =>
  useNav.getState().open({ view: "calendar", date, ...(category ? { category } : {}), calendarMode: "day" });
