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
root.render(
  <main className="app-shell">
    <h1>IronLog</h1>
    <p>Opening your local training log…</p>
  </main>,
);
async function start() {
  const catalog = await loadCatalog();
  root.render(
    <main className="app-shell">
      <h1>IronLog</h1>
      <p>Preparing local storage…</p>
    </main>,
  );
  await bootstrap(catalog);
  await saveProfile({ theme: currentTheme() });
  root.render(
    <main className="app-shell">
      <h1>IronLog</h1>
      <p>Updating your training snapshot…</p>
    </main>,
  );
  await reconcileBadges();
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
