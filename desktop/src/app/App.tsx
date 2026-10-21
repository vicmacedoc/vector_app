import { useEffect, useMemo, useState } from "react";
import {
  ArrowLeft, ArrowRight, BarChart3, Brain, CalendarDays, ChevronDown, Home, Inbox, Library, ListChecks, Search,
  Settings, ShoppingCart, Sparkles, Zap,
} from "lucide-react";
import { MergeDialog, ShardMergeDialog } from "@/components/MergeDialog";
import { SearchDialog } from "@/components/SearchDialog";
import { SyncChip } from "@/components/SyncChip";
import { Toasts } from "@/components/Toasts";
import { VoiceCapture } from "@/components/VoiceCapture";
import { cn } from "@/lib/cn";
import { native } from "@/platform/native";
import { AnalysisScreen } from "@/screens/analysis/AnalysisScreen";
import { CalendarScreen } from "@/screens/calendar/CalendarScreen";
import { HomeScreen } from "@/screens/Home";
import { InboxScreen } from "@/screens/Inbox";
import { MemoryScreen } from "@/screens/Memory";
import { ModelScreen } from "@/screens/Model";
import { MotionScreen } from "@/screens/Motion";
import { SettingsScreen } from "@/screens/settings/SettingsScreen";
import { SupermarketScreen } from "@/screens/Supermarket";
import { useBrain } from "@/store/brain";
import { useDaily } from "@/store/daily";
import { useNav, type View } from "@/store/nav";
import { useCaptureReceiver } from "@/capture/receiver";

interface NavItem {
  view: View;
  label: string;
  icon: React.ReactNode;
}

const TOP: NavItem[] = [
  { view: "home", label: "Home", icon: <Home size={18} /> },
  { view: "calendar", label: "Calendar", icon: <CalendarDays size={18} /> },
];

const BRAIN: NavItem[] = [
  { view: "inbox", label: "Inbox", icon: <Inbox size={17} /> },
  { view: "memory", label: "Memory", icon: <Library size={17} /> },
  { view: "model", label: "Model", icon: <Brain size={17} /> },
  { view: "motion", label: "Motion", icon: <ListChecks size={17} /> },
  { view: "supermarket", label: "Supermarket", icon: <ShoppingCart size={17} /> },
];

const BOTTOM: NavItem[] = [{ view: "analysis", label: "Analysis", icon: <BarChart3 size={18} /> }];

/** Full-bleed views manage their own width and scrolling. */
const WIDE: View[] = ["calendar", "analysis", "home", "settings"];

function NavButton({ item, active, badge, onClick, nested }: { item: NavItem; active: boolean; badge?: number; onClick: () => void; nested?: boolean }) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm transition",
        nested && "py-1.5 pl-9 text-[13px]",
        active ? "bg-white font-semibold text-navy" : "text-white/75 hover:bg-white/10 hover:text-white",
      )}
    >
      {item.icon}
      <span className="flex-1 text-left">{item.label}</span>
      {!!badge && <span className="rounded-full bg-electric px-1.5 text-[11px] font-semibold text-white">{badge}</span>}
    </button>
  );
}

export function App() {
  const ready = useBrain((s) => s.ready);
  const init = useBrain((s) => s.init);
  const initDaily = useDaily((s) => s.init);
  const brain = useBrain((s) => s.brain);
  const nav = useNav();
  const { view, go } = nav;
  const [searchOpen, setSearchOpen] = useState(false);
  const [brainOpen, setBrainOpen] = useState(true);
  useCaptureReceiver();

  useEffect(() => {
    void init().then(() => initDaily());
  }, [init, initDaily]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setSearchOpen(true);
      } else if (e.altKey && e.key === "ArrowLeft") {
        e.preventDefault();
        useNav.getState().goBack();
      } else if (e.altKey && e.key === "ArrowRight") {
        e.preventDefault();
        useNav.getState().goForward();
      }
    };
    const onMouse = (e: MouseEvent) => {
      if (e.button === 3) useNav.getState().goBack();
      if (e.button === 4) useNav.getState().goForward();
    };
    window.addEventListener("keydown", onKey);
    window.addEventListener("mouseup", onMouse);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("mouseup", onMouse);
    };
  }, []);

  const inboxCount = useMemo(
    () => brain.notes.filter((n) => n.nodeId === null).length + brain.tasks.filter((t) => t.nodeId === null && t.status === "OPEN").length,
    [brain.notes, brain.tasks],
  );

  if (!ready) return <div className="flex h-full items-center justify-center text-sm text-muted">Loading Vector…</div>;

  const inBrain = BRAIN.some((b) => b.view === view);

  return (
    <div className="flex h-full">
      <aside className="flex w-56 shrink-0 flex-col bg-navy text-white">
        <div className="flex items-center gap-2.5 px-5 pt-6 pb-5">
          <div className="relative flex h-8 w-8 items-center justify-center rounded-lg bg-white font-black text-navy">
            V<span className="absolute -top-0.5 -right-0.5 h-2.5 w-2.5 rounded-full bg-electric ring-2 ring-navy" />
          </div>
          <div>
            <div className="text-sm leading-tight font-semibold">Vector</div>
            <div className="text-[11px] text-white/50">Life tracking · Brain</div>
          </div>
        </div>
        <nav className="flex-1 space-y-0.5 overflow-y-auto px-3">
          {TOP.map((item) => (
            <NavButton key={item.view} item={item} active={view === item.view} onClick={() => go(item.view)} />
          ))}
          <button
            onClick={() => setBrainOpen((o) => !o)}
            className={cn(
              "flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm transition hover:bg-white/10",
              inBrain && !brainOpen ? "text-white font-semibold" : "text-white/75",
            )}
          >
            <Sparkles size={18} />
            <span className="flex-1 text-left">Brain</span>
            {!brainOpen && inboxCount > 0 && <span className="rounded-full bg-electric px-1.5 text-[11px] font-semibold">{inboxCount}</span>}
            <ChevronDown size={15} className={cn("transition", !brainOpen && "-rotate-90")} />
          </button>
          {brainOpen &&
            BRAIN.map((item) => (
              <NavButton
                key={item.view}
                item={item}
                nested
                active={view === item.view}
                badge={item.view === "inbox" ? inboxCount : undefined}
                onClick={() => go(item.view)}
              />
            ))}
          {BOTTOM.map((item) => (
            <NavButton key={item.view} item={item} active={view === item.view} onClick={() => go(item.view)} />
          ))}
        </nav>
        <div className="space-y-0.5 px-3 pb-4">
          <button
            title="Ctrl+Shift+Space from anywhere"
            onClick={() => void native.openCapture()}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm text-white/75 hover:bg-white/10 hover:text-white"
          >
            <Zap size={18} /> <span className="flex-1 text-left whitespace-nowrap">Quick capture</span>
          </button>
          <NavButton item={{ view: "settings", label: "Settings", icon: <Settings size={18} /> }} active={view === "settings"} onClick={() => go("settings")} />
        </div>
      </aside>

      <main className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 shrink-0 items-center gap-2 border-b border-hairline px-4">
          <button
            title="Back (Alt+Left)"
            disabled={!nav.back.length}
            onClick={nav.goBack}
            className="rounded-md p-1.5 text-muted hover:bg-offwhite hover:text-black disabled:opacity-30"
          >
            <ArrowLeft size={17} />
          </button>
          <button
            title="Forward (Alt+Right)"
            disabled={!nav.forward.length}
            onClick={nav.goForward}
            className="mr-2 rounded-md p-1.5 text-muted hover:bg-offwhite hover:text-black disabled:opacity-30"
          >
            <ArrowRight size={17} />
          </button>
          <button
            onClick={() => setSearchOpen(true)}
            className="flex h-9 w-80 items-center gap-2 rounded-lg border border-hairline bg-offwhite px-3 text-sm text-item hover:border-electric/40"
          >
            <Search size={15} /> Search Brain <kbd className="ml-auto text-[11px]">Ctrl K</kbd>
          </button>
          <div className="ml-auto flex items-center gap-2">
            <VoiceCapture />
            <SyncChip />
          </div>
        </header>
        {WIDE.includes(view) ? (
          <div className="min-h-0 flex-1">
            {view === "home" && <HomeScreen />}
            {view === "calendar" && <CalendarScreen />}
            {view === "analysis" && <AnalysisScreen />}
            {view === "settings" && <SettingsScreen />}
          </div>
        ) : (
          <div className="min-h-0 flex-1 overflow-y-auto">
            <div className="mx-auto max-w-6xl px-8 py-7">
              {view === "inbox" && <InboxScreen />}
              {view === "memory" && <MemoryScreen />}
              {view === "model" && <ModelScreen />}
              {view === "motion" && <MotionScreen />}
              {view === "supermarket" && <SupermarketScreen />}
            </div>
          </div>
        )}
      </main>

      <SearchDialog open={searchOpen} onClose={() => setSearchOpen(false)} />
      <MergeDialog />
      <ShardMergeDialog />
      <Toasts />
    </div>
  );
}
