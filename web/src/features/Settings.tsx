import { useState } from "react";
import { useApp, navigate, download } from "../ui/context";
import {
  Button,
  Field,
  Icon,
  IconButton,
  Sheet,
  Switch,
  ThemePicker,
} from "../ui/components";
import { useTheme, applyTheme } from "../ui/theme";
import { FontPicker } from "../ui/FontPicker";
import { SpacingPicker } from "../ui/SpacingPicker";
import {
  saveProfile,
  restoreSnapshot,
  resetData,
  bootstrap,
  saveGym,
  deleteGym,
  clearCompletedHistory,
  resetPersonalRecords,
  scheduleTutorialRestart,
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
import {
  filterSettingsDestinations,
  settingsDestinations,
  type SettingsDestinationId,
} from "../domain/settings-console";
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
  const [editingGymId, setEditingGymId] = useState<string>();
  const [bar, setBar] = useState(String(p.barKg));
  const [plates, setPlates] = useState(p.platesKg.join(", "));
  const [finitePlates, setFinitePlates] = useState(p.plateInventory !== undefined);
  const [quantities, setQuantities] = useState(p.plateInventory?.map(x => x.quantity).join(", ") ?? "");
  const [storage, setStorage] = useState("");
  const [settingsQuery, setSettingsQuery] = useState("");
  const [destination, setDestination] = useState<Exclude<SettingsDestinationId, "intelligence">>();
  const [dangerAction, setDangerAction] = useState<"history" | "records" | "tutorial">();
  const destinationSpec = settingsDestinations.find((item) => item.id === destination);
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
      <header className="native-screen-title settings-title">
        {destination && (
          <button className="text-button" onClick={() => setDestination(undefined)}>
            <Icon name="back" /> Back to settings
          </button>
        )}
        <span className="eyebrow">Settings</span>
        <h1>{destinationSpec?.title ?? "Training Console"}</h1>
        <p>{destinationSpec?.description ?? "Your training setup, integrations and local data in six focused areas."}</p>
      </header>
      <section className="card local-record-card" hidden={destination !== undefined}>
        <div><span className="eyebrow">Local training record</span><strong>Ready on this device</strong></div>
        <b>6 areas</b>
      </section>
      <label className="settings-search" hidden={destination !== undefined}>
        <Icon name="search" />
        <input aria-label="Search settings" placeholder="Search settings" value={settingsQuery} onChange={(event) => setSettingsQuery(event.target.value)} />
      </label>
      <section className="card settings-destinations" hidden={destination !== undefined}>
        <span className="eyebrow">Destinations</span>
        {filterSettingsDestinations(settingsQuery).map((item) => {
          const statusText = item.id === "training"
            ? `${p.weeklyGoal} days · ${p.unit.toUpperCase()} · ${p.keepAwake ? "On" : "Off"}`
            : item.id === "appearance" ? theme
              : item.id === "notifications" ? "Browser reminders"
                : item.id === "data" ? "Stored locally · backup tools"
                  : item.id === "about" ? "IronLog Web" : "Built-in intelligence";
          return <button key={item.id} onClick={() => item.id === "intelligence" ? navigate("intelligence") : setDestination(item.id)}>
            <div><strong>{item.title}</strong><p>{item.description}</p><b>{statusText}</b></div><Icon name="next" />
          </button>
        })}
        {settingsQuery.trim() && filterSettingsDestinations(settingsQuery).length === 0 && (
          <p role="status">No settings match “{settingsQuery.trim()}”.</p>
        )}
      </section>
      <section id="profile-training" hidden={destination !== "training"}>
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
        <Switch
          checked={p.keepAwake}
          disabled={busy}
          onChange={(checked) => run(() => saveProfile({ keepAwake: checked }))}
          label="Keep workout screen awake when supported"
        />
      </section>
      <section id="appearance" hidden={destination !== "appearance"}>
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
      <section id="typography" hidden={destination !== "appearance"}>
        <h2>Typography</h2>
        <FontPicker />
      </section>
      <section id="spacing" hidden={destination !== "appearance"}>
        <h2>Layout spacing</h2>
        <SpacingPicker />
      </section>
      <section id="visual-effects" hidden={destination !== "appearance"}>
        <h2>Optional effects</h2>
        <Switch
          checked={p.cardShineEnabled}
          disabled={busy}
          onChange={(checked) => run(() => saveProfile({ cardShineEnabled: checked }))}
          label="Animated card shine"
        />
        <p className="muted">Controls the decorative light sweep on featured cards.</p>
        <Switch
          checked={p.liquidGlassEnabled}
          disabled={busy}
          onChange={(checked) => run(() => saveProfile({ liquidGlassEnabled: checked }))}
          label="Liquid glass navigation"
        />
        <p className="muted">Controls the translucent navigation material independently from card shine.</p>
        <p className="muted">Motion also follows your browser or operating system’s reduced-motion preference.</p>
      </section>
      <section id="training-tools" hidden={destination !== "training"}>
        <h2>Training tools</h2>
        <button className="list-row" onClick={() => {
          setBar(String(p.barKg));
          setPlates((p.plateInventory?.map(x => x.weightKg) ?? p.platesKg).join(", "));
          setFinitePlates(p.plateInventory !== undefined);
          setQuantities(p.plateInventory?.map(x => x.quantity).join(", ") ?? "");
          setGymName("");
          setEditingGymId(undefined);
          setGym(true);
        }}>
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
      <section id="notifications" hidden={destination !== "notifications"}>
        <h2>Notifications</h2>
        <p className="muted">
          Workout reminders depend on browser notification and background-task
          support. IronLog keeps your training record usable when those APIs are
          unavailable.
        </p>
      </section>
      <section id="data" hidden={destination !== "data"}>
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
        <div className="card danger-zone">
          <span className="eyebrow">Danger area</span>
          <p className="muted">These changes permanently alter your training record. Back up first if you may need to restore it.</p>
          <button className="list-row" disabled={busy} onClick={() => setDangerAction("history")}>
            <span><strong>Clear completed history</strong><small>Deletes completed workouts; the active workout is preserved.</small></span>
            <Icon name="trash" />
          </button>
          <button className="list-row" disabled={busy} onClick={() => setDangerAction("records")}>
            <span><strong>Reset all personal records</strong><small>Starts new PR baselines without deleting workout history.</small></span>
            <Icon name="next" />
          </button>
        </div>
      </section>
      <section id="about" hidden={destination !== "about"}>
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
        <button className="list-row" onClick={() => setDangerAction("tutorial")}>
          <strong>Restart app tutorial</strong>
          <Icon name="next" />
        </button>
      </section>
      <div hidden={destination !== "data"}>
        <Button variant="danger" onClick={() => setReset(true)}>
          Reset this browser’s IronLog data
        </Button>
      </div>
      {dangerAction === "history" && (
        <Sheet title="Clear completed history?" onClose={() => setDangerAction(undefined)}>
          <p>Every completed workout will be deleted. Your active workout, plans, profile, measurements, photos, and gym setups will be preserved.</p>
          <Button variant="danger" disabled={busy} onClick={() =>
            run(async () => {
              await clearCompletedHistory();
              setDangerAction(undefined);
            }, "Completed workout history cleared")
          }>Clear history</Button>
        </Sheet>
      )}
      {dangerAction === "records" && (
        <Sheet title="Reset all personal records?" onClose={() => setDangerAction(undefined)}>
          <p>Your workout history stays intact. Future completed sets will establish new personal-record baselines.</p>
          <Button variant="danger" disabled={busy} onClick={() =>
            run(async () => {
              await resetPersonalRecords();
              setDangerAction(undefined);
            }, "Personal-record baselines reset")
          }>Reset PRs</Button>
        </Sheet>
      )}
      {dangerAction === "tutorial" && (
        <Sheet title="Restart tutorial?" onClose={() => setDangerAction(undefined)}>
          <p>The onboarding tutorial will restart the next time IronLog Web is fully opened. Your training data will stay intact.</p>
          <Button disabled={busy} onClick={() =>
            run(async () => {
              await scheduleTutorialRestart();
              setDangerAction(undefined);
            }, "Tutorial scheduled for the next app open")
          }>Restart next time</Button>
        </Sheet>
      )}
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
          <Switch label="Limit to my physical plates" checked={finitePlates} onChange={setFinitePlates} />
          {finitePlates ? <>
            <Field label="Total quantities (comma separated)">
              <input value={quantities} onChange={e => setQuantities(e.target.value)} placeholder="2, 2, 4" />
            </Field>
            <p className="muted">Enter total physical plates for each size, in the same order. Two plates make one pair; an odd spare is excluded. Use 0 for a size you do not have.</p>
          </> : <p className="muted">Unlimited pairs assumed for every plate size.</p>}
          <Button
            disabled={busy}
            onClick={() =>
              run(async () => {
                const values = plates.split(",").map((x) => Number(x.trim()));
                if (values.some((x) => !Number.isFinite(x) || x <= 0))
                  throw Error("Enter positive plate sizes separated by commas");
                const barKg = Number(bar);
                if (!bar.trim() || !Number.isFinite(barKg) || barKg < 0)
                  throw Error("Enter a nonnegative bar weight");
                const counts = quantities.split(",").map(x => Number(x.trim()));
                if (finitePlates && (counts.length !== values.length ||
                    quantities.split(",").some(x => !x.trim()) ||
                    counts.some(x => !Number.isSafeInteger(x) || x < 0)))
                  throw Error("Enter one nonnegative whole quantity for each plate size");
                const plateInventory = finitePlates ? values.map((weightKg, i) => ({ weightKg, quantity: counts[i] })) : undefined;
                const gymId = editingGymId ?? (gymName.trim() ? newId() : undefined);
                await saveProfile({ barKg, platesKg: values, plateInventory, activeGymId: gymId ?? p.activeGymId });
                if (gymId && gymName.trim())
                  await saveGym({
                    id: gymId,
                    name: gymName.trim(),
                    barKg,
                    platesKg: values,
                    plateInventory,
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
                    async () => {
                      await saveProfile({ barKg: g.barKg, platesKg: g.platesKg, plateInventory: g.plateInventory, activeGymId: g.id });
                      setBar(String(g.barKg));
                      setPlates((g.plateInventory?.map(x => x.weightKg) ?? g.platesKg).join(", "));
                      setFinitePlates(g.plateInventory !== undefined);
                      setQuantities(g.plateInventory?.map(x => x.quantity).join(", ") ?? "");
                      setGymName("");
                    },
                    "Gym selected",
                  )
                }
              >
                {g.name}{p.activeGymId === g.id ? " · Active" : ""} · {g.barKg} kg bar · {g.plateInventory === undefined ? "Unlimited pairs" : "Saved physical quantities"}
              </button>
              <IconButton
                name="edit"
                label={`Edit ${g.name}`}
                onClick={() => {
                  setEditingGymId(g.id);
                  setGymName(g.name);
                  setBar(String(g.barKg));
                  setPlates((g.plateInventory?.map(x => x.weightKg) ?? g.platesKg).join(", "));
                  setFinitePlates(g.plateInventory !== undefined);
                  setQuantities(g.plateInventory?.map(x => x.quantity).join(", ") ?? "");
                }}
              />
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
