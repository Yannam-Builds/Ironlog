import { useRegisterSW } from "virtual:pwa-register/react";
import { useState } from "react";
import { useApp } from "./context";
export function UpdateNotice() {
  const { data, busy } = useApp();
  const [reloadReady, setReloadReady] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const {
    needRefresh: [ready, setReady],
    updateServiceWorker,
  } = useRegisterSW({
    // Another tab may activate a waiting worker. Never reload this editor automatically.
    onNeedReload: () => {
      setReloadReady(true);
      setDismissed(false);
    },
    onNeedRefresh: () => setDismissed(false),
  });
  if ((!ready && !reloadReady) || dismissed) return null;
  const active = data.workouts.some((w) => w.status === "active");
  return (
    <aside className="update-notice" role="status">
      <p>
        {active
          ? "An update is ready. Finish your workout before updating."
          : busy
            ? "Your changes are saving. Updating can wait."
            : "A new IronLog version is ready. Save any open editors before reloading."}
      </p>
      <button
        disabled={active || busy}
        onClick={() => {
          if (active || busy) return;
          if (reloadReady) location.reload();
          else void updateServiceWorker(true);
        }}
      >
        {reloadReady ? "Reload updated app" : "Update & reload"}
      </button>
      <button
        onClick={() => {
          setReady(false);
          setDismissed(true);
        }}
      >
        Later
      </button>
    </aside>
  );
}
