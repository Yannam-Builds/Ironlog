import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  backFrom,
  navigateTo,
  parentRoute,
  routeFromHash,
} from "../src/ui/navigation";

describe("native-style web navigation", () => {
  beforeEach(() => {
    history.replaceState(null, "", "#/home");
  });

  it.each([
    ["plan/strength", "plans"],
    ["history/workout-1", "log"],
    ["body", "stats"],
    ["photos", "stats"],
    ["analytics", "stats"],
    ["recovery", "home"],
    ["ledger", "home"],
    ["intelligence", "home"],
    ["workout", "home"],
  ])("maps %s to its direct-entry parent", (route, parent) => {
    expect(parentRoute(route)).toBe(parent);
  });

  it("records the actual origin so a detail back action returns there", () => {
    const changed = vi.fn();
    window.addEventListener("hashchange", changed, { once: true });

    navigateTo("intelligence");

    expect(routeFromHash()).toBe("intelligence");
    expect(history.state).toMatchObject({
      ironlogRoute: "intelligence",
      ironlogParent: "home",
    });
    expect(changed).toHaveBeenCalledOnce();
  });

  it("uses browser history for an in-app entry and a safe parent for a direct entry", () => {
    navigateTo("plans");
    navigateTo("plan/strength");
    const back = vi.spyOn(history, "back").mockImplementation(() => undefined);

    backFrom("plan/strength");
    expect(back).toHaveBeenCalledOnce();

    history.replaceState(null, "", "#/body");
    back.mockClear();
    backFrom("body");
    expect(back).not.toHaveBeenCalled();
    expect(routeFromHash()).toBe("stats");
  });
});
