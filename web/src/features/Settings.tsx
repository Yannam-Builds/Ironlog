import { useState } from "react";
import { useApp, navigate, download } from "../ui/context";
import {
  Button,
  Field,
  Icon,
  IconButton,
  Sheet,
  ThemePicker,
} from "../ui/components";
import { useTheme, applyTheme } from "../ui/theme";
import {
  saveProfile,
  restoreSnapshot,
  resetData,
  bootstrap,
  saveGym,
  deleteGym,
  newId,
  readSnapshot,
} from "../data/store";
import {
  encodeAndroidBackup,
  decodeAndroidBackup,
  encodeWebBackup,
  decodeWebBackup,
} from "../domain/codecs";
import type { AppSnapshot } from "../domain/types";
import { ExercisePicker } from "./Plans";
import { loadCatalog } from "../catalog";
export function Settings() {
  const { data, run, busy } = useApp();
  const p = data.profile;
  const theme = useTheme();
  const [name, setName] = useState(p.name);
  const [reset, setReset] = useState(false);
  const [confirm, setConfirm] = useState("");
  const [restore, setRestore] = useState<AppSnapshot>();
  const [warnings, setWarnings] = useState<string[]>([]);
  const [library, setLibrary] = useState(false);
  const [gym, setGym] = useState(false);
  const [gymName, setGymName] = useState("");
  const [bar, setBar] = useState(String(p.barKg));
  const [plates, setPlates] = useState(p.platesKg.join(", "));
  const [storage, setStorage] = useState("");
  const backup = () =>
    run(async () => {
      const snapshot = await readSnapshot();
      const bytes = await encodeWebBackup(snapshot);
      download(
        new Blob([new Uint8Array(bytes)], { type: "application/zip" }),
        `ironlog-web-${new Date().toISOString().slice(0, 10)}.zip`,
      );
      await saveProfile({ lastBackupAt: Date.now() });
    }, "Backup download started. Keep the file somewhere safe.");
  return (
    <>
      <h1>Settings</h1>
      <section>
        <h2>Profile & training</h2>
        <div className="workout-inputs">
          <Field label="Name">
            <input value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Button
            variant="secondary"
            disabled={busy || !name.trim()}
            onClick={() =>
              run(() => saveProfile({ name: name.trim() }), "Profile saved")
            }
          >
            Save
          </Button>
        </div>
        <div className="two-col">
          <Field label="Weight units">
            <select
              value={p.unit}
              onChange={(e) =>
                run(() => saveProfile({ unit: e.target.value as "kg" | "lb" }))
              }
            >
              <option value="kg">Kilograms</option>
              <option value="lb">Pounds</option>
            </select>
          </Field>
          <Field label="Effort tracking">
            <select
              value={p.effort}
              onChange={(e) =>
                run(() =>
                  saveProfile({ effort: e.target.value as "rpe" | "rir" }),
                )
              }
            >
              <option value="rpe">RPE</option>
              <option value="rir">RIR</option>
            </select>
          </Field>
        </div>
        <Field label="Weekly session goal">
          <select
            value={p.weeklyGoal}
            onChange={(e) =>
              run(() => saveProfile({ weeklyGoal: Number(e.target.value) }))
            }
          >
            {[1, 2, 3, 4, 5, 6, 7].map((x) => (
              <option key={x}>{x}</option>
            ))}
          </select>
        </Field>
        <label className="check-label">
          <input
            type="checkbox"
            checked={p.keepAwake}
            onChange={(e) =>
              run(() => saveProfile({ keepAwake: e.target.checked }))
            }
          />
          Keep workout screen awake when supported
        </label>
      </section>
      <section>
        <h2>Appearance</h2>
        <p className="muted">
          The same 12 native palettes. Your choice also applies to the website.
        </p>
        <ThemePicker
          value={theme}
          onChange={(id) => {
            applyTheme(id);
            void run(() => saveProfile({ theme: id }));
          }}
        />
      </section>
      <section>
        <h2>Training tools</h2>
        <button className="list-row" onClick={() => setGym(true)}>
          <strong>Gym & plate setup</strong>
          <Icon name="next" />
        </button>
        <button className="list-row" onClick={() => setLibrary(true)}>
          <strong>Exercise library & custom exercises</strong>
          <Icon name="next" />
        </button>
        <button className="list-row" onClick={() => navigate("intelligence")}>
          <strong>Training intelligence</strong>
          <Icon name="next" />
        </button>
      </section>
      <section id="data">
        <h2>Your data</h2>
        <p>
          {p.lastBackupAt
            ? `Last backup download: ${new Date(p.lastBackupAt).toLocaleString()}`
            : "No browser backup downloaded yet."}
        </p>
        <p className="muted">
          Clearing site data or browser storage can erase your log. Safari and a
          Home Screen installation may hold separate databases. Keep a backup
          before switching.
        </p>
        <Button disabled={busy} onClick={backup}>
          Download complete browser backup
        </Button>
        <Button
          variant="secondary"
          disabled={busy}
          onClick={() =>
            run(
              async () =>
                download(
                  encodeAndroidBackup(await readSnapshot()),
                  "ironlog-android-export.json",
                ),
              "Android-compatible JSON downloaded",
            )
          }
        >
          Export Android-compatible JSON
        </Button>
        <p className="muted">
          Android JSON includes training data, not photo files or native-only
          settings. Keep the original Android backup when migrating.
        </p>
        <Field label="Restore a browser ZIP or Android JSON">
          <input
            type="file"
            accept=".zip,.json,application/zip,application/json"
            disabled={busy || data.workouts.some((w) => w.status === "active")}
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file)
                void run(async () => {
                  if (file.size > 150_000_000)
                    throw Error("Backup exceeds 150 MB");
                  if (file.name.toLowerCase().endsWith(".zip")) {
                    setRestore(await decodeWebBackup(file));
                    setWarnings([]);
                  } else {
                    const decoded = decodeAndroidBackup(await file.text());
                    setRestore(decoded.snapshot);
                    setWarnings(decoded.result.warnings);
                  }
                });
            }}
          />
        </Field>
        {data.workouts.some((w) => w.status === "active") && (
          <p>Finish or discard your active workout before restoring data.</p>
        )}
        <Button
          variant="ghost"
          onClick={() =>
            run(async () => {
              const retained = await navigator.storage?.persist?.();
              const estimate = await navigator.storage?.estimate?.();
              setStorage(
                `${retained ? "Persistent storage granted." : "Persistence not guaranteed by this browser."} ${estimate?.usage ? `${Math.round(estimate.usage / 1024 / 1024)} MB used.` : ""}`,
              );
            })
          }
        >
          Check storage protection
        </Button>
        {storage && <p role="status">{storage}</p>}
      </section>
      <section>
        <h2>About IronLog Web</h2>
        <p>
          A local-first browser edition. No account, analytics tracker,
          subscription, or automatic phone sync.
        </p>
        <p>
          Locked-screen alarms, native widgets, Health Connect, wallpaper-based
          Monet, and Android on-device AI are unavailable here. Built-in
          guidance is deterministic, not an offline generative model.
        </p>
        <button className="list-row" onClick={() => navigate("research")}>
          <strong>Research & methodology</strong>
          <Icon name="next" />
        </button>
        <a className="list-row" href={`${import.meta.env.BASE_URL}#install`}>
          iPhone installation guide <Icon name="next" />
        </a>
        <a className="list-row" href={`${import.meta.env.BASE_URL}#privacy`}>
          Privacy & acknowledgments <Icon name="next" />
        </a>
      </section>
      <Button variant="danger" onClick={() => setReset(true)}>
        Reset this browser’s IronLog data
      </Button>
      {restore && (
        <Sheet
          title="Replace this browser’s data?"
          onClose={() => setRestore(undefined)}
        >
          <p>
            The backup contains {restore.plans.length} plans,{" "}
            {restore.workouts.length} workouts, {restore.measurements.length}{" "}
            measurements, and {restore.photos.length} photos.
          </p>
          {warnings.map((w, i) => (
            <p key={i} className="notice">
              {w}
            </p>
          ))}
          <p>
            Existing data will be replaced atomically. Download a backup first.
          </p>
          <Button variant="secondary" onClick={backup}>
            Back up current data
          </Button>
          {data.workouts.some((w) => w.status === "active") && (
            <p role="alert">
              An active workout was opened. Finish or discard it before
              restoring.
            </p>
          )}
          <Button
            variant="danger"
            disabled={busy || data.workouts.some((w) => w.status === "active")}
            onClick={() =>
              run(async () => {
                const catalog = await loadCatalog();
                await restoreSnapshot(restore);
                await bootstrap(catalog);
                applyTheme(restore.profile.theme);
                setRestore(undefined);
              }, "Backup restored")
            }
          >
            Replace with this backup
          </Button>
        </Sheet>
      )}
      {reset && (
        <Sheet title="Reset local data" onClose={() => setReset(false)}>
          <p>
            This permanently removes plans, workouts, measurements, and photos
            from this browser. It does not change your Android app.
          </p>
          <Field label="Type RESET to confirm">
            <input
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
            />
          </Field>
          <Button
            variant="danger"
            disabled={busy || confirm !== "RESET"}
            onClick={() =>
              run(async () => {
                const catalog = await loadCatalog();
                await resetData();
                await bootstrap(catalog);
                setReset(false);
                navigate("home");
              })
            }
          >
            Delete browser data
          </Button>
        </Sheet>
      )}
      {library && (
        <ExercisePicker
          onClose={() => setLibrary(false)}
          onPick={() => setLibrary(false)}
        />
      )}
      {gym && (
        <Sheet title="Gym profiles" onClose={() => setGym(false)}>
          <Field label="Profile name">
            <input
              value={gymName}
              onChange={(e) => setGymName(e.target.value)}
            />
          </Field>
          <Field label="Bar weight (kg)">
            <input
              type="number"
              min="0"
              value={bar}
              onChange={(e) => setBar(e.target.value)}
            />
          </Field>
          <Field label="Plate sizes (kg, comma separated)">
            <input value={plates} onChange={(e) => setPlates(e.target.value)} />
          </Field>
          <Button
            disabled={busy}
            onClick={() =>
              run(async () => {
                const values = plates.split(",").map((x) => Number(x.trim()));
                if (values.some((x) => !Number.isFinite(x) || x <= 0))
                  throw Error("Enter positive plate sizes separated by commas");
                const barKg = Number(bar);
                await saveProfile({ barKg, platesKg: values });
                if (gymName.trim())
                  await saveGym({
                    id: newId(),
                    name: gymName.trim(),
                    barKg,
                    platesKg: values,
                  });
                setGym(false);
              }, "Gym setup saved")
            }
          >
            Save & use setup
          </Button>
          {data.gyms.map((g) => (
            <div className="list-row" key={g.id}>
              <button
                className="text-button"
                onClick={() =>
                  run(
                    () => saveProfile({ barKg: g.barKg, platesKg: g.platesKg }),
                    "Gym selected",
                  )
                }
              >
                {g.name} · {g.barKg} kg bar
              </button>
              <IconButton
                name="trash"
                label={`Delete ${g.name}`}
                onClick={() => run(() => deleteGym(g.id))}
              />
            </div>
          ))}
        </Sheet>
      )}
    </>
  );
}
