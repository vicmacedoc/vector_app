import { useMemo } from "react";
import { sharedSettings } from "@/domain/daily/day";
import type { LearnRules } from "@/domain/learn";
import { useDaily } from "@/store/daily";
import { CONFIG_SHARD } from "@/sync/shards";

export interface LearnSettings extends LearnRules {
  quizSize: number;
  passPercent: number;
}

export function learnSettingsNow(): LearnSettings {
  const s = sharedSettings(useDaily.getState().shards);
  return { onHoldDays: s.learnOnHoldDays, reviewIntervals: s.learnReviewIntervals, quizSize: s.learnQuizSize, passPercent: s.learnPassPercent };
}

/** Learn rules from the synced config shard (Settings → Learning), shared with the phone. */
export function useLearnSettings(): LearnSettings {
  const config = useDaily((s) => s.shards[CONFIG_SHARD]);
  return useMemo(learnSettingsNow, [config]);
}
