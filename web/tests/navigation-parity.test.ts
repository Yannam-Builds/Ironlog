import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  backFrom,
  navigateTo,
  parentRoute,
  routeFromHash,
  subscribeNavigation,
  guardNavigation,
} from "../src/ui/navigation";

describe("native-style web navigation", () => {
  beforeEach(() => {
    history.replaceState(null, "", "#/home");
  });

  it.each([
    ["plan/strength", "plans"],
    ["library", "settings"],
    ["history/workout-1", "log"],
    ["body", "stats"],
    ["photos", "stats"],
    ["analytics", "stats"],
    ["calendar", "stats"],
    ["exercise/bench", "stats"],
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

  it("records the originating tab scroll position before opening a detail", () => {
    const release = subscribeNavigation(() => undefined);
    Object.defineProperty(window, "scrollY", { configurable: true, value: 240 });
    window.dispatchEvent(new Event("scroll"));
    expect(history.state).toMatchObject({ ironlogScrollY: 240 });
    navigateTo("library");
    expect(history.state).toMatchObject({ ironlogScrollY: 0 });
    release();
    Object.defineProperty(window, "scrollY", { configurable: true, value: 0 });
  });

  it("reports forward pushes and returning detail transitions separately", () => {
    const changed = vi.fn();
    const release = subscribeNavigation(changed);
    const origin = history.state;
    navigateTo("recovery");
    expect(changed).toHaveBeenLastCalledWith("recovery", "next");
    history.replaceState(origin, "", "#/home");
    window.dispatchEvent(new PopStateEvent("popstate", { state: origin }));
    expect(changed).toHaveBeenLastCalledWith("home", "previous");
    release();
  });

  it("restores a dirty direct-entry editor after Back to a pre-subscription entry", () => {
    history.replaceState(null, "", "#/plan/direct");
    const release = subscribeNavigation(() => undefined);
    let proceed: (() => void) | undefined;
    const unblock = guardNavigation((approved) => { proceed = approved; });
    const go = vi.spyOn(history, "go").mockImplementation(() => undefined);
    history.replaceState(null, "", "#/plans");
    window.dispatchEvent(new PopStateEvent("popstate", { state: null }));
    expect(go).not.toHaveBeenCalled();
    expect(routeFromHash()).toBe("plan/direct");
    expect(history.state).toMatchObject({ ironlogParent: "plans", ironlogIndex: 1 });
    const back = vi.spyOn(history, "back").mockImplementation(() => undefined);
    proceed?.();
    expect(back).toHaveBeenCalledOnce();
    unblock();
    release();
    go.mockRestore();
    back.mockRestore();
  });

  it("recovers an indexed restoration that cannot deliver a browser event", () => {
    vi.useFakeTimers();
    const release = subscribeNavigation(() => undefined);
    navigateTo("plans");
    const target = history.state;
    navigateTo("plan/guard");
    let proceed: (() => void) | undefined;
    const unblock = guardNavigation((approved) => { proceed = approved; });
    const go = vi.spyOn(history, "go").mockImplementation(() => undefined);
    history.replaceState(target, "", "#/plans");
    window.dispatchEvent(new PopStateEvent("popstate", { state: target }));
    expect(go).toHaveBeenCalledWith(1);
    const stale = { ...target, ironlogIndex: target.ironlogIndex + 1, ironlogEpoch: "legacy" };
    history.replaceState(stale, "", "#/calendar");
    window.dispatchEvent(new PopStateEvent("popstate", { state: stale }));
    vi.advanceTimersByTime(301);
    expect(routeFromHash()).toBe("plan/guard");
    const back = vi.spyOn(history, "back").mockImplementation(() => undefined);
    proceed?.();
    expect(back).toHaveBeenCalledOnce();
    unblock();
    release();
    go.mockRestore();
    back.mockRestore();
    vi.useRealTimers();
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
