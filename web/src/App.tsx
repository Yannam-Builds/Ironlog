import { useEffect, useLayoutEffect, useMemo, useRef, useState, lazy, Suspense, type CSSProperties, type PointerEvent as ReactPointerEvent } from "react";
import { readSnapshot, subscribeSnapshot, reconcileBadges } from "./data/store";
import type { AppSnapshot } from "./domain/types";
import { deriveSnapshot } from "./domain/engine";
import { AppProvider, navigate } from "./ui/context";
import { applyTheme, currentTheme } from "./ui/theme";
import { Button, Icon, IconButton } from "./ui/components";
import { Onboarding } from "./features/Onboarding";
import { Home } from "./features/Home";
import { Plans, PlanEditor } from "./features/Plans";
import { Workout } from "./features/Workout";
import {
  History,
  HistoryDetail,
  Stats,
  Analytics,
  Body,
  Photos,
} from "./features/Progress";
import { Recovery, Ledger } from "./features/Recovery";
import { Settings } from "./features/Settings";
import { Intelligence } from "./features/Intelligence";
import { HistoricalWorkoutEditor, WorkoutCalendar } from "./features/history/HistoryTools";
import { Research } from "./research";
import { backFrom } from "./ui/navigation";
const tabs = ["Home", "Plans", "Log", "Stats", "Settings"];
const tabRoutes = tabs.map((tab) => tab.toLowerCase());
const detailTitles: Record<string, string> = {
  workout: "ACTIVE WORKOUT",
  recovery: "MUSCLE RECOVERY",
  ledger: "IRON LEDGER",
  body: "BODY TRACKER",
  photos: "PROGRESS PHOTOS",
  analytics: "VOLUME ANALYTICS",
  intelligence: "ATHLETE PROFILE",
  research: "RESEARCH",
};
const UpdateNotice = lazy(() =>
  import("./ui/UpdateNotice").then((m) => ({ default: m.UpdateNotice })),
);
export function App() {
  const [data, setData] = useState<AppSnapshot>();
  const [route, setRoute] = useState(
    () => location.hash.replace(/^#\//, "") || "home",
  );
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const [busy, setBusy] = useState(false);
  const running = useRef(false);
  const [now, setNow] = useState(Date.now());
  const [online, setOnline] = useState(navigator.onLine);
  const main = useRef<HTMLElement>(null);
  const routeRef = useRef(route);
  routeRef.current = route;
  const routeDirection = useRef<"next" | "previous">("next");
  const swipe = useRef<{
    pointerId: number;
    startX: number;
    startY: number;
    currentX: number;
    startedAt: number;
    axis?: "horizontal" | "vertical";
  } | undefined>(undefined);
  const swipeTimer = useRef<number | undefined>(undefined);
  const clickShieldTimer = useRef<number | undefined>(undefined);
  const suppressClickUntil = useRef(0);
  useLayoutEffect(() => {
    // Focus the committed route before it can be interacted with. A deferred
    // animation frame can otherwise steal focus between Enter's key events.
    window.scrollTo(0, 0);
    main.current?.focus({ preventScroll: true });
  }, [route]);
  useEffect(() => {
    applyTheme(currentTheme());
    return subscribeSnapshot(setData, (e) =>
      setError(
        `Storage is unavailable: ${String(e)}. Data has not been cleared.`,
      ),
    );
  }, []);
  useEffect(() => {
    const hash = () => {
      const nextRoute = location.hash.replace(/^#\//, "") || "home";
      const from = tabRoutes.indexOf(routeRef.current);
      const to = tabRoutes.indexOf(nextRoute);
      if (from >= 0 && to >= 0 && from !== to) {
        routeDirection.current = to > from ? "next" : "previous";
      }
      setRoute(nextRoute);
    };
    const visible = () => {
      if (document.visibilityState === "visible") {
        setNow(Date.now());
        void readSnapshot()
          .then(setData)
          .catch((e) => setError(String(e)));
      }
    };
    const network = () => setOnline(navigator.onLine);
    const theme = (e: StorageEvent) => {
      if (e.key === "ironlog-theme") applyTheme(currentTheme());
    };
    window.addEventListener("hashchange", hash);
    document.addEventListener("visibilitychange", visible);
    window.addEventListener("pageshow", visible);
    window.addEventListener("online", network);
    window.addEventListener("offline", network);
    window.addEventListener("storage", theme);
    return () => {
      window.removeEventListener("hashchange", hash);
      document.removeEventListener("visibilitychange", visible);
      window.removeEventListener("pageshow", visible);
      window.removeEventListener("online", network);
      window.removeEventListener("offline", network);
      window.removeEventListener("storage", theme);
    };
  }, []);
  useEffect(() => () => {
    if (swipeTimer.current !== undefined) window.clearTimeout(swipeTimer.current);
    if (clickShieldTimer.current !== undefined) window.clearTimeout(clickShieldTimer.current);
  }, []);

  const swipeStage = () => main.current?.querySelector<HTMLElement>(".route-stage");
  const clearSwipeStage = () => {
    const stage = swipeStage();
    if (stage) stage.removeAttribute("style");
    main.current?.classList.remove("tab-swiping");
  };
  const cancelTabSwipe = () => {
    const stage = swipeStage();
    swipe.current = undefined;
    if (!stage) return;
    if (swipeTimer.current !== undefined) window.clearTimeout(swipeTimer.current);
    stage.style.animation = "none";
    stage.style.transition = "transform 180ms cubic-bezier(0.2, 0.82, 0.2, 1), opacity 180ms ease";
    stage.style.transform = "translate3d(0, 0, 0)";
    stage.style.opacity = "1";
    swipeTimer.current = window.setTimeout(() => {
      clearSwipeStage();
      swipeTimer.current = undefined;
    }, 190);
  };
  const finishTabSwipe = (event: ReactPointerEvent<HTMLElement>, cancelled = false) => {
    const gesture = swipe.current;
    if (!gesture || gesture.pointerId !== event.pointerId) return;
    if (event.currentTarget.hasPointerCapture?.(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    const dx = gesture.currentX - gesture.startX;
    const elapsed = Math.max(1, performance.now() - gesture.startedAt);
    const velocity = Math.abs(dx) / elapsed;
    const currentIndex = tabRoutes.indexOf(routeRef.current);
    const nextIndex = dx < 0 ? currentIndex + 1 : currentIndex - 1;
    const qualifies = !cancelled && gesture.axis === "horizontal" &&
      (Math.abs(dx) >= Math.max(56, (main.current?.clientWidth ?? innerWidth) * 0.18) ||
        (Math.abs(dx) >= 28 && velocity >= 0.55)) &&
      nextIndex >= 0 && nextIndex < tabRoutes.length;
    if (!qualifies) {
      cancelTabSwipe();
      return;
    }
    event.preventDefault();
    swipe.current = undefined;
    suppressClickUntil.current = performance.now() + 400;
    main.current?.classList.add("swipe-click-shield");
    if (clickShieldTimer.current !== undefined) window.clearTimeout(clickShieldTimer.current);
    clickShieldTimer.current = window.setTimeout(() => {
      main.current?.classList.remove("swipe-click-shield");
      clickShieldTimer.current = undefined;
    }, 450);
    routeDirection.current = dx < 0 ? "next" : "previous";
    const stage = swipeStage();
    if (stage) {
      stage.style.animation = "none";
      stage.style.transition = "transform 150ms cubic-bezier(0.4, 0, 1, 1), opacity 150ms ease";
      stage.style.transform = `translate3d(${dx < 0 ? "-32%" : "32%"}, 0, 0)`;
      stage.style.opacity = "0.55";
    }
    if (swipeTimer.current !== undefined) window.clearTimeout(swipeTimer.current);
    swipeTimer.current = window.setTimeout(() => {
      clearSwipeStage();
      navigate(tabRoutes[nextIndex]);
      swipeTimer.current = undefined;
    }, 150);
  };
  const run = async (work: () => Promise<unknown>, message?: string) => {
    if (running.current) return false;
    running.current = true;
    setBusy(true);
    setError("");
    setStatus("");
    try {
      await work();
      await reconcileBadges();
      setData(await readSnapshot());
      setNow(Date.now());
      if (message) setStatus(message);
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      try {
        setData(await readSnapshot());
      } catch {}
      return false;
    } finally {
      running.current = false;
      setBusy(false);
    }
  };
  const derived = useMemo(
    () => (data ? deriveSnapshot(data, now) : undefined),
    [data, now],
  );
  if (!data || !derived)
    return (
      <main className="app-shell">
        <h1>IronLog</h1>
        <p role="status">Opening your training log…</p>
        {error && (
          <div role="alert" className="error">
            {error}
            <Button onClick={() => location.reload()}>Retry</Button>
          </div>
        )}
      </main>
    );
  const active = data.workouts.find((w) => w.status === "active");
  const detail = !["home", "plans", "log", "stats", "settings"].includes(route);
  const selectedTabRoute = route.startsWith("plan/")
    ? "plans"
    : route.startsWith("history/") || route === "calendar"
      ? "log"
      : ["analytics", "body", "photos"].includes(route)
        ? "stats"
        : ["workout", "recovery", "ledger", "intelligence", "research"].includes(route)
          ? "home"
          : route;
  const selectedTabIndex = Math.max(0, tabs.findIndex((tab) => tab.toLowerCase() === selectedTabRoute));
  const screen =
    route === "home" ? (
      <Home />
    ) : route === "plans" ? (
      <Plans />
    ) : route.startsWith("plan/") ? (
      <PlanEditor key={route} id={route.slice(5)} />
    ) : route === "workout" ? (
      <Workout />
    ) : route === "log" ? (
      <History />
    ) : route === "history/new" || route.startsWith("history/new/") ? (
      <HistoricalWorkoutEditor initialDate={route.startsWith("history/new/") ? route.slice(12) : undefined} />
    ) : route.startsWith("history/") ? (
      <HistoryDetail key={route} id={route.slice(8)} />
    ) : route === "calendar" ? (
      <WorkoutCalendar />
    ) : route === "stats" ? (
      <Stats />
    ) : route === "settings" ? (
      <Settings />
    ) : route === "recovery" ? (
      <Recovery />
    ) : route === "ledger" ? (
      <Ledger />
    ) : route === "body" ? (
      <Body />
    ) : route === "photos" ? (
      <Photos />
    ) : route === "analytics" ? (
      <Analytics />
    ) : route === "intelligence" ? (
      <Intelligence />
    ) : route === "research" ? (
      <Research />
    ) : (
      <>
        <h1>Page not found</h1>
        <Button onClick={() => navigate("home")}>Go Home</Button>
      </>
    );
  return (
    <AppProvider value={{ data, derived, busy, run, error }}>
      <a
        className="skip-link"
        href="#main-content"
        onClick={(e) => {
          e.preventDefault();
          main.current?.focus();
        }}
      >
        Skip to content
      </a>
      {!online && (
        <div className="offline" role="status">
          Offline · your log still saves on this device
        </div>
      )}
      {error && (
        <div className="message error" role="alert">
          <span>{error}</span>
          <button onClick={() => setError("")}>Dismiss</button>
        </div>
      )}
      {status && (
        <div className="message" role="status">
          <span>{status}</span>
          <button onClick={() => setStatus("")}>Dismiss</button>
        </div>
      )}
      {!data.profile.onboarded ? (
        <Onboarding />
      ) : (
        <>
          <div className="app-shell">
            {detail && (
              <header className="app-header detail-header">
                <IconButton
                  name="back"
                  label="Back"
                  onClick={() => backFrom(route)}
                />
                <strong>
                  {route.startsWith("plan/")
                    ? "EDIT PLAN"
                    : route.startsWith("history/")
                      ? "WORKOUT"
                      : detailTitles[route] ?? "IRONLOG"}
                </strong>
                <span aria-hidden="true" />
              </header>
            )}
            {active && route !== "workout" && (
              <button className="resume" onClick={() => navigate("workout")}>
                <Icon name="log" />
                <span>Resume {active.name}</span>
                <Icon name="next" />
              </button>
            )}
            <main
              id="main-content"
              tabIndex={-1}
              ref={main}
              className="app-content"
              onClickCapture={(event) => {
                if (performance.now() < suppressClickUntil.current) {
                  event.preventDefault();
                  event.stopPropagation();
                }
              }}
              onPointerDownCapture={(event) => {
                if ((event.pointerType === "mouse" && event.button !== 0) ||
                  tabRoutes.indexOf(routeRef.current) < 0) return;
                const target = event.target as HTMLElement;
                if (target.closest("input, textarea, select, [contenteditable=true], [role=slider], [data-swipe-ignore]")) return;
                let node: HTMLElement | null = target;
                while (node && node !== event.currentTarget) {
                  const style = getComputedStyle(node);
                  if ((style.overflowX === "auto" || style.overflowX === "scroll") &&
                    node.scrollWidth > node.clientWidth + 1) return;
                  node = node.parentElement;
                }
                if (swipeTimer.current !== undefined) {
                  window.clearTimeout(swipeTimer.current);
                  swipeTimer.current = undefined;
                  clearSwipeStage();
                }
                swipe.current = {
                  pointerId: event.pointerId,
                  startX: event.clientX,
                  startY: event.clientY,
                  currentX: event.clientX,
                  startedAt: performance.now(),
                };
              }}
              onPointerMoveCapture={(event) => {
                const gesture = swipe.current;
                if (!gesture || gesture.pointerId !== event.pointerId) return;
                gesture.currentX = event.clientX;
                const dx = event.clientX - gesture.startX;
                const dy = event.clientY - gesture.startY;
                if (!gesture.axis && Math.max(Math.abs(dx), Math.abs(dy)) >= 9) {
                  gesture.axis = Math.abs(dx) > Math.abs(dy) * 1.15 ? "horizontal" : "vertical";
                  if (gesture.axis === "horizontal") {
                    event.currentTarget.classList.add("tab-swiping");
                    window.getSelection()?.removeAllRanges();
                    try { event.currentTarget.setPointerCapture(event.pointerId); } catch {}
                  }
                }
                if (gesture.axis !== "horizontal") return;
                event.preventDefault();
                const index = tabRoutes.indexOf(routeRef.current);
                const atBoundary = (dx > 0 && index === 0) || (dx < 0 && index === tabRoutes.length - 1);
                const shownDx = atBoundary ? dx * 0.24 : dx;
                const stage = swipeStage();
                if (stage) {
                  stage.style.animation = "none";
                  stage.style.transition = "none";
                  stage.style.transform = `translate3d(${shownDx}px, 0, 0)`;
                  stage.style.opacity = String(Math.max(0.72, 1 - Math.abs(shownDx) / 700));
                }
              }}
              onPointerUpCapture={(event) => finishTabSwipe(event)}
              onPointerCancelCapture={(event) => finishTabSwipe(event, true)}
            >
              <div key={route} className={`route-stage route-${routeDirection.current}`}>
                {screen}
              </div>
            </main>
          </div>
          <nav className="bottom-nav" aria-label="Main">
            <span
              className="bottom-nav-selection"
              aria-hidden="true"
              style={{ "--tab-index": selectedTabIndex } as CSSProperties}
            />
            {tabs.map((tab) => {
              const r = tab.toLowerCase();
              return (
                <a
                  key={r}
                  href={`#/${r}`}
                  aria-current={route === r ? "page" : undefined}
                >
                  <Icon name={r} size={21} />
                  <span>{tab}</span>
                </a>
              );
            })}
          </nav>
        </>
      )}
      {busy && (
        <span className="saving" role="status">
          Saving…
        </span>
      )}
      {import.meta.env.PROD && (
        <Suspense fallback={null}>
          <UpdateNotice />
        </Suspense>
      )}
    </AppProvider>
  );
}
