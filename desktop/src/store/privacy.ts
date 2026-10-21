// Windows Hello gate for the Self journal: one unlock per session, re-locks after the app is hidden for a while.
import { create } from "zustand";
import { errorMessage, native } from "@/platform/native";

const REQUIRE_KEY = "vector:requireHelloForSelf";
const RELOCK_AFTER_HIDDEN_MS = 5 * 60_000;

interface Privacy {
  requireHello: boolean;
  unlocked: boolean;
  /** Set when Hello isn't configured on this PC, so the gate explains why it let the user in. */
  helloUnavailable: boolean;
  error: string | null;
  setRequireHello(on: boolean): void;
  unlock(): Promise<void>;
  lock(): void;
}

export const usePrivacy = create<Privacy>((set) => ({
  requireHello: localStorage.getItem(REQUIRE_KEY) !== "false",
  unlocked: false,
  helloUnavailable: false,
  error: null,

  setRequireHello(on) {
    localStorage.setItem(REQUIRE_KEY, String(on));
    set({ requireHello: on });
  },

  async unlock() {
    set({ error: null });
    try {
      const r = await native.helloVerify("Unlock your Self journal in Vector");
      if (r === "verified") set({ unlocked: true, helloUnavailable: false });
      else if (r === "unavailable") set({ unlocked: true, helloUnavailable: true });
      else set({ error: "Verification cancelled" });
    } catch (e) {
      set({ error: errorMessage(e) });
    }
  },

  lock: () => set({ unlocked: false }),
}));

let hiddenAt = 0;
document.addEventListener("visibilitychange", () => {
  if (document.hidden) hiddenAt = Date.now();
  else if (hiddenAt && Date.now() - hiddenAt > RELOCK_AFTER_HIDDEN_MS) usePrivacy.getState().lock();
});
