import { AlertTriangle, Check, CloudOff, GitMerge, LogIn, RefreshCw } from "lucide-react";
import { cn } from "@/lib/cn";
import { useBrain } from "@/store/brain";
import { useDaily } from "@/store/daily";
import { useNav } from "@/store/nav";

export function ago(ms: number | null): string {
  if (!ms) return "never";
  const s = Math.round((Date.now() - ms) / 1000);
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  return new Date(ms).toLocaleDateString();
}

/** One chip for both sync channels: Brain state.json and the daily shards. */
export function SyncChip({ compact = false }: { compact?: boolean }) {
  const brainPhase = useBrain((s) => s.phase);
  const brainMessage = useBrain((s) => s.syncMessage);
  const brainLast = useBrain((s) => s.settings.lastSyncAt);
  const syncBrain = useBrain((s) => s.syncNow);
  const openBrainMerge = useBrain((s) => s.openMerge);
  const dailyPhase = useDaily((s) => s.phase);
  const dailyMessage = useDaily((s) => s.message);
  const dailyLast = useDaily((s) => s.lastSyncAt);
  const syncDaily = useDaily((s) => s.syncNow);
  const openDailyMerge = useDaily((s) => s.openMerge);
  const go = useNav((s) => s.go);

  const syncAll = () => {
    void syncBrain();
    void syncDaily(true);
  };
  const last = brainLast && dailyLast ? Math.min(brainLast, dailyLast) : brainLast ?? dailyLast;

  const phase =
    brainPhase === "signedOut" || brainPhase === "notConfigured" ? brainPhase
      : brainPhase === "conflict" || dailyPhase === "conflict" ? "conflict"
        : brainPhase === "syncing" || dailyPhase === "syncing" ? "syncing"
          : brainPhase === "error" || dailyPhase === "error" ? "error"
            : brainPhase === "ok" || dailyPhase === "ok" ? "ok" : "idle";

  const config = {
    idle: { icon: <RefreshCw size={14} />, label: "Sync", tone: "text-muted", onClick: syncAll },
    syncing: { icon: <RefreshCw size={14} className="animate-spin-slow" />, label: "Syncing…", tone: "text-electric", onClick: undefined },
    ok: { icon: <Check size={14} />, label: `Synced ${ago(last)}`, tone: "text-muted", onClick: syncAll },
    error: { icon: <CloudOff size={14} />, label: "Sync failed · retry", tone: "text-danger", onClick: syncAll },
    conflict: {
      icon: <GitMerge size={14} />,
      label: "Merge needed",
      tone: "text-electric font-semibold",
      onClick: () => void (brainPhase === "conflict" ? openBrainMerge() : openDailyMerge()),
    },
    signedOut: { icon: <LogIn size={14} />, label: "Sign in", tone: "text-electric", onClick: () => go("settings") },
    notConfigured: { icon: <AlertTriangle size={14} />, label: "Choose Drive folder", tone: "text-electric", onClick: () => go("settings") },
  }[phase];

  const message = brainMessage ?? dailyMessage;
  return (
    <button
      title={message ?? `Brain ${ago(brainLast)} · Calendar ${ago(dailyLast)}`}
      onClick={config.onClick}
      disabled={!config.onClick}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border border-hairline bg-white px-3 py-1.5 text-xs transition hover:border-electric/50",
        config.tone,
      )}
    >
      {config.icon}
      {!compact && <span className="max-w-56 truncate">{config.label}</span>}
    </button>
  );
}
