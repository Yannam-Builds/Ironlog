export type IronLogHistoryState = {
  ironlogRoute: string;
  ironlogParent: string;
  ironlogIndex?: number;
  ironlogScrollY?: number;
  ironlogEpoch?: string;
};

export const routeFromHash = (hash = window.location.hash) =>
  hash.replace(/^#\//, "") || "home";

export const tabRoutes = ["home", "plans", "log", "stats", "settings"];
export const isTabRoute = (route: string) => tabRoutes.includes(route);

let guard: ((proceed: () => void) => void) | undefined;
let approvedRouteChange = false;
export function guardNavigation(block: (proceed: () => void) => void) {
  guard = block;
  return () => { if (guard === block) guard = undefined; };
}
function requestNavigation(proceed: () => void) {
  if (guard) guard(() => { approvedRouteChange = true; proceed(); });
  else proceed();
}

// History indices let a cancelled browser traversal restore the actual editor
// entry, keeping both its draft and the browser's Back/Forward chain intact.
export function subscribeNavigation(onRoute: (route: string, direction: "next" | "previous") => void) {
  approvedRouteChange = false;
  let currentRoute = routeFromHash();
  let currentIndex = (history.state as IronLogHistoryState | null)?.ironlogIndex ?? 0;
  let currentEpoch = history.state?.ironlogEpoch ?? crypto.randomUUID();
  let currentState = { ...history.state, ironlogIndex: currentIndex, ironlogEpoch: currentEpoch };
  history.replaceState(currentState, "");
  const previousScrollRestoration = history.scrollRestoration;
  history.scrollRestoration = "manual";
  const scrolled = () => {
    if (!restoring && routeFromHash() === currentRoute) {
      history.replaceState({ ...history.state, ironlogScrollY: window.scrollY }, "");
      currentState = history.state;
    }
  };
  let restoreTimer: ReturnType<typeof setTimeout> | undefined;
  let restoring: { index: number; proceed?: () => void } | undefined;
  const preserveAtBoundary = (target: string, targetState: IronLogHistoryState | null, alreadyApproved = false) => {
    // Legacy browser entries have no trustworthy distance or direction. Seed a
    // new boundary and a replacement editor entry instead of guessing go().
    // This retains the draft and target, but replaces the unknown forward tail.
    currentEpoch = crypto.randomUUID();
    history.replaceState({ ...targetState, ironlogRoute: target, ironlogParent: parentRoute(target), ironlogIndex: 0, ironlogEpoch: currentEpoch }, "", `#/${target}`);
    currentIndex = 1;
    currentState = { ...currentState, ironlogRoute: currentRoute, ironlogParent: target, ironlogIndex: 1, ironlogEpoch: currentEpoch };
    history.pushState(currentState, "", `#/${currentRoute}`);
    restoring = undefined;
    const proceed = () => { approvedRouteChange = true; history.back(); };
    if (alreadyApproved) proceed();
    else guard?.(proceed);
  };
  const changed = () => {
    const nextRoute = routeFromHash();
    const state = history.state as IronLogHistoryState | null;
    const nextIndex = state?.ironlogIndex ?? currentIndex + 1;
    if (restoring) {
      if (nextIndex === restoring.index && state?.ironlogEpoch === currentEpoch && nextRoute === currentRoute) {
        clearTimeout(restoreTimer);
        const proceed = restoring.proceed;
        restoring = undefined;
        proceed?.();
      }
      return;
    }
    if (nextRoute === currentRoute) { currentIndex = nextIndex; return; }
    if (guard && !approvedRouteChange) {
      if (state?.ironlogIndex === undefined || state.ironlogEpoch !== currentEpoch || nextIndex === currentIndex) {
        preserveAtBoundary(nextRoute, state);
        return;
      }
      const delta = nextIndex - currentIndex;
      // An ordinary hash link creates an unindexed browser entry.
      if (state?.ironlogIndex === undefined) history.replaceState({ ...state, ironlogRoute: nextRoute, ironlogParent: currentRoute, ironlogIndex: nextIndex, ironlogScrollY: 0 }, "");
      restoring = { index: currentIndex };
      history.go(-delta);
      restoreTimer = setTimeout(() => {
        if (restoring) preserveAtBoundary(nextRoute, state, Boolean(restoring.proceed));
      }, 300);
      const approved = () => {
        const traverse = () => {
          approvedRouteChange = true;
          history.go(delta);
        };
        if (restoring) restoring.proceed = traverse;
        else traverse();
      };
      guard(approved);
      return;
    }
    approvedRouteChange = false;
    if (state?.ironlogIndex === undefined) history.replaceState({ ...state, ironlogRoute: nextRoute, ironlogParent: currentRoute, ironlogIndex: nextIndex, ironlogScrollY: 0, ironlogEpoch: currentEpoch }, "");
    const direction = nextIndex < currentIndex ? "previous" : "next";
    currentRoute = nextRoute;
    currentIndex = nextIndex;
    currentEpoch = history.state?.ironlogEpoch ?? currentEpoch;
    currentState = history.state;
    onRoute(nextRoute, direction);
  };
  window.addEventListener("popstate", changed);
  window.addEventListener("hashchange", changed);
  window.addEventListener("scroll", scrolled, { passive: true });
  return () => {
    window.removeEventListener("popstate", changed);
    window.removeEventListener("hashchange", changed);
    window.removeEventListener("scroll", scrolled);
    clearTimeout(restoreTimer);
    history.scrollRestoration = previousScrollRestoration;
  };
}

export function parentRoute(route: string): string {
  if (route === "library") return "settings";
  if (route.startsWith("plan/")) return "plans";
  if (route.startsWith("history/")) return "log";
  if (route === "calendar" || route.startsWith("exercise/")) return "stats";
  if (["body", "measurements", "photos", "analytics"].includes(route)) return "stats";
  return "home";
}

export function navigateTo(route: string) {
  const current = routeFromHash();
  if (current === route) return;
  requestNavigation(() => pushRoute(route));
}

function pushRoute(route: string) {
  const current = routeFromHash();
  const state: IronLogHistoryState = {
    ironlogRoute: route,
    ironlogParent: current,
    ironlogIndex: ((history.state as IronLogHistoryState | null)?.ironlogIndex ?? 0) + 1,
    ironlogScrollY: 0,
    ironlogEpoch: history.state?.ironlogEpoch,
  };
  history.pushState(state, "", `#/${route}`);
  window.dispatchEvent(new HashChangeEvent("hashchange"));
}

export function backFrom(route: string) {
  const state = history.state as Partial<IronLogHistoryState> | null;
  if (
    state?.ironlogRoute === route &&
    typeof state.ironlogParent === "string" &&
    state.ironlogParent.length > 0
  ) {
    requestNavigation(() => history.back());
    return;
  }
  navigateTo(parentRoute(route));
}
