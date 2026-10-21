import { useEffect, useRef, useState } from "react";
import { cn } from "@/lib/cn";
import { useNav } from "@/store/nav";
import { AccountSection, SyncSection } from "./AccountSync";
import { AiSection, PrivacySection } from "./AiSection";
import { LabelsSection, PresetsSection } from "./PresetsLabels";
import { LearningSection, MotionSection, ShortcutsSection, SleepSection } from "./Preferences";

const SECTIONS: { id: string; label: string }[] = [
  { id: "account", label: "Account" },
  { id: "sync", label: "Sync" },
  { id: "ai", label: "AI" },
  { id: "privacy", label: "Privacy" },
  { id: "presets", label: "Presets" },
  { id: "labels", label: "Labels" },
  { id: "sleep", label: "Sleep" },
  { id: "learning", label: "Learning" },
  { id: "motion", label: "Motion" },
  { id: "shortcuts", label: "Shortcuts" },
];

export function SettingsScreen() {
  const section = useNav((s) => s.section);
  const scroller = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(SECTIONS[0].id);

  const jump = (id: string, smooth = true) => {
    document.getElementById(`settings-${id}`)?.scrollIntoView({ behavior: smooth ? "smooth" : "auto", block: "start" });
    setActive(id);
  };

  useEffect(() => {
    if (section) jump(section, false);
  }, [section]);

  useEffect(() => {
    const root = scroller.current;
    if (!root) return;
    const visible = new Map<string, number>();
    const observer = new IntersectionObserver(
      (entries) => {
        for (const e of entries) visible.set((e.target as HTMLElement).dataset.section!, e.isIntersecting ? e.boundingClientRect.top : Infinity);
        const top = [...visible.entries()].filter(([, y]) => y !== Infinity).sort((a, b) => a[1] - b[1])[0];
        if (top) setActive(top[0]);
      },
      { root, rootMargin: "0px 0px -60% 0px" },
    );
    root.querySelectorAll("[data-section]").forEach((el) => observer.observe(el));
    return () => observer.disconnect();
  }, []);

  return (
    <div ref={scroller} className="h-full overflow-y-auto">
      <div className="mx-auto flex max-w-6xl gap-8 px-8 py-7">
        <nav className="sticky top-7 hidden h-fit w-44 shrink-0 lg:block">
          <h1 className="mb-4 text-2xl font-semibold">Settings</h1>
          <div className="space-y-0.5">
            {SECTIONS.map((s) => (
              <button
                key={s.id}
                onClick={() => jump(s.id)}
                className={cn(
                  "block w-full rounded-lg px-3 py-1.5 text-left text-sm transition",
                  active === s.id ? "bg-navy font-medium text-white" : "text-muted hover:bg-offwhite hover:text-black",
                )}
              >
                {s.label}
              </button>
            ))}
          </div>
        </nav>
        <div className="min-w-0 flex-1 space-y-5 pb-[40vh]">
          <h1 className="text-2xl font-semibold lg:hidden">Settings</h1>
          <AccountSection />
          <SyncSection />
          <AiSection />
          <PrivacySection />
          <PresetsSection />
          <LabelsSection />
          <SleepSection />
          <LearningSection />
          <MotionSection />
          <ShortcutsSection />
        </div>
      </div>
    </div>
  );
}
