import { creditedProof } from "./engine";
import { localDateKey } from "./dates";
import type { Workout } from "./types";
type Proof = { headline: string; detail: string; action: string; route: string; fox: string };
export function dailyProof(workouts: Workout[], hasPlan: boolean, readiness: number | undefined, pain: string[], now = Date.now()): Proof {
  const active = workouts.find(w => w.status === "active");
  if (active?.name.trim()) return { headline: active.name, detail: pain.length ? "Pain flagged: review affected movements before continuing." : "Current session is still in progress.", action: "Resume workout", route: "workout", fox: "07_determined" };
  if (pain.length) return { headline: "Check in before training", detail: `Pain flagged in ${[...pain].sort().join(", ")}. A readiness estimate does not clear painful movements.`, action: "Review recovery", route: "recovery", fox: "28_rest_blanket" };
  const history = workouts.filter(w => w.status === "completed");
  if (!history.length && !hasPlan) return { headline: "Set your proof loop", detail: "Pick a plan so Home can drive the next session automatically.", action: "Choose a program", route: "plans", fox: "20_clipboard" };
  const credited = history.filter(w => creditedProof(w, now));
  if (!credited.length) return { headline: "First proof awaits", detail: "Complete your first qualifying session to start the Iron Ledger.", action: "Start workout", route: "home", fox: "07_determined" };
  const latest = Math.max(...credited.map(w => w.startedAt));
  if (localDateKey(latest) === localDateKey(now)) return { headline: "Proof saved", detail: "Today's session already counts toward your ledger.", action: "Open Iron Ledger", route: "ledger", fox: "16_proud" };
  if ((readiness ?? 0) >= 85) return { headline: "Fresh enough to press", detail: "Recovery is high enough to push the next proof session.", action: "Train today", route: "home", fox: "17_flexing" };
  const date = (at: number) => { const d = new Date(at); return Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()); };
  if ((date(now) - date(latest)) / 86400000 >= 2) return { headline: "Your rhythm is slipping", detail: "The next workout matters more than another scroll.", action: "Train today", route: "home", fox: "29_checking_watch" };
  return { headline: "Recover on purpose", detail: "Use today's readiness before forcing extra fatigue.", action: "Open recovery", route: "recovery", fox: "28_rest_blanket" };
}
