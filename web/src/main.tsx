import { Component, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { bootstrap, reconcileBadges, saveProfile } from "./data/store";
import { App } from "./App";
import type { Exercise } from "./domain/types";
import { loadCatalog } from "./catalog";
import { applyTheme, currentTheme } from "./ui/theme";
import "./styles.css";
import "./generated/font-faces.css";
import "./typography.css";
import { initializeTypography } from "./ui/typography";
initializeTypography();
import { initializeSpacing } from "./ui/spacing";
initializeSpacing();
function Splash() {
  const base = import.meta.env.BASE_URL;
  return (
    <main className="native-splash" aria-label="IronLog is opening">
      <div className="native-splash-lockup">
        <div className="native-splash-logo" aria-label="IRONLOG">
          <img className="native-splash-iron" src={`${base}assets/logo_iron.png`} alt="" />
          <span
            className="native-splash-log"
            aria-hidden="true"
            style={{
              maskImage: `url(${base}assets/logo_log.png)`,
              WebkitMaskImage: `url(${base}assets/logo_log.png)`,
            }}
          />
        </div>
        <div className="native-splash-dots" aria-hidden="true"><i /><i /><i /></div>
      </div>
    </main>
  );
}
class Boundary extends Component<{ children: ReactNode }, { error: string }> {
  state = { error: "" };
  static getDerivedStateFromError(error: unknown) {
    return { error: String(error) };
  }
  render() {
    return this.state.error ? (
      <main className="app-shell">
        <h1>IronLog couldn’t open this screen.</h1>
        <p>Your stored data has not been cleared.</p>
        <pre>{this.state.error}</pre>
        <button onClick={() => location.reload()}>Reload</button>
      </main>
    ) : (
      this.props.children
    );
  }
}
applyTheme(currentTheme());
const root = createRoot(document.getElementById("root")!);
let showSplash = true;
try { showSplash = sessionStorage.getItem("ironlog-splash-seen") !== "1"; } catch {}
const splashStarted = performance.now();
root.render(showSplash ? <Splash /> : (
  <main className="app-shell"><h1>IronLog</h1><p>Opening your local training log…</p></main>
));
async function start() {
  const catalog = await loadCatalog();
  if (!showSplash) root.render(
    <main className="app-shell">
      <h1>IronLog</h1>
      <p>Preparing local storage…</p>
    </main>,
  );
  await bootstrap(catalog);
  await saveProfile({ theme: currentTheme() });
  if (!showSplash) root.render(
    <main className="app-shell">
      <h1>IronLog</h1>
      <p>Updating your training snapshot…</p>
    </main>,
  );
  await reconcileBadges();
  if (showSplash) {
    const remaining = Math.max(0, 1550 - (performance.now() - splashStarted));
    if (remaining) await new Promise((resolve) => window.setTimeout(resolve, remaining));
    try { sessionStorage.setItem("ironlog-splash-seen", "1"); } catch {}
  }
  root.render(
    <Boundary>
      <App />
    </Boundary>,
  );
}
void start().catch((e) =>
  root.render(
    <main className="app-shell">
      <h1>Unable to open local storage</h1>
      <p>{String(e)}</p>
      <p>
        Try a regular browser window with storage enabled. No data was cleared.
      </p>
      <button onClick={() => location.reload()}>Retry</button>
    </main>,
  ),
);
