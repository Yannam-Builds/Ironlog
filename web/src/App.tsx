import { useEffect, useLayoutEffect, useMemo, useRef, useState, lazy, Suspense, type CSSProperties } from "react";
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
import { Research } from "./research";
const tabs = ["Home", "Plans", "Log", "Stats", "Settings"];
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
      setRoute(location.hash.replace(/^#\//, "") || "home");
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
    : route.startsWith("history/")
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
    ) : route.startsWith("history/") ? (
      <HistoryDetail key={route} id={route.slice(8)} />
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
                  onClick={() =>
                    navigate(
                      route.startsWith("plan/")
                        ? "plans"
                        : route.startsWith("history/")
                          ? "log"
                          : "home",
                    )
                  }
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
            >
              <div key={route} className="route-stage">
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
