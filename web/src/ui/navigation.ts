export type IronLogHistoryState = {
  ironlogRoute: string;
  ironlogParent: string;
};

export const routeFromHash = (hash = window.location.hash) =>
  hash.replace(/^#\//, "") || "home";

export function parentRoute(route: string): string {
  if (route.startsWith("plan/")) return "plans";
  if (route.startsWith("history/")) return "log";
  if (route === "calendar") return "log";
  if (["body", "photos", "analytics"].includes(route)) return "stats";
  return "home";
}

export function navigateTo(route: string) {
  const current = routeFromHash();
  if (current === route) return;
  const state: IronLogHistoryState = {
    ironlogRoute: route,
    ironlogParent: current,
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
    history.back();
    return;
  }
  navigateTo(parentRoute(route));
}
