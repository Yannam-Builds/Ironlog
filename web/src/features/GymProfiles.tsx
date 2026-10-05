import { useState } from "react";
import { useApp, canonicalWeight } from "../ui/context";
import { Button, Field, IconButton, Sheet, Switch } from "../ui/components";
import { saveGym, selectGym, deleteGym, newId } from "../data/store";
import type { Gym, PlateStock } from "../domain/types";

const displayWeight = (kg: number, unit: string) =>
  Number((kg * (unit === "lb" ? 2.2046226218487757 : 1)).toFixed(2));
const equipment = [
  "Barbell",
  "Dumbbell",
  "Cable",
  "Machine",
  "Kettlebell",
  "Band",
];
const colors = [
  ["Red", "#D32F2F"],
  ["Blue", "#1565C0"],
  ["Yellow", "#F9A825"],
  ["Green", "#2E7D32"],
  ["White", "#F5F5F5"],
  ["Black", "#212121"],
];
const defaults = [20, 15, 10, 5, 2.5, 1.25].map((weightKg) => ({
  weightKg,
  quantity: 4,
}));

export function GymProfiles({ onClose }: { onClose: () => void }) {
  const { data, run, busy } = useApp();
  const unit = data.profile.unit;
  const [editing, setEditing] = useState<Gym>();
  const [name, setName] = useState("");
  const [bar, setBar] = useState("");
  const [initialBar, setInitialBar] = useState("");
  const [stock, setStock] = useState<PlateStock[]>([]);
  const [finite, setFinite] = useState(true);
  const [unavailable, setUnavailable] = useState<string[]>([]);
  const [newWeight, setNewWeight] = useState("");
  const [palette, setPalette] = useState<number>();
  const [deleting, setDeleting] = useState<Gym>();
  const edit = (gym: Gym) => {
    setEditing(gym);
    setName(gym.name);
    const text = String(
      Math.round((unit === "lb" ? gym.barKg * 2.2046226218 : gym.barKg) * 100) /
        100,
    );
    setBar(text);
    setInitialBar(text);
    setStock(
      gym.plateInventory ??
        gym.platesKg.map((weightKg) => ({ weightKg, quantity: 4 })),
    );
    setFinite(gym.plateInventory !== undefined);
    setUnavailable(gym.unavailableEquipment ?? []);
    setNewWeight("");
    setPalette(undefined);
  };
  const change = (index: number, patch: Partial<PlateStock>) =>
    setStock((rows) =>
      rows.map((row, i) => (i === index ? { ...row, ...patch } : row)),
    );
  if (deleting)
    return (
      <Sheet
        title={`Delete ${deleting.name}?`}
        onClose={() => setDeleting(undefined)}
      >
        <p>
          Your workout history will remain. If this gym is active, another saved
          gym will be selected.
        </p>
        <Button
          variant="danger"
          disabled={busy}
          onClick={() =>
            run(async () => {
              await deleteGym(deleting.id);
              setDeleting(undefined);
            }, "Gym deleted")
          }
        >
          Delete gym
        </Button>
        <Button variant="secondary" onClick={() => setDeleting(undefined)}>
          Cancel
        </Button>
      </Sheet>
    );
  if (editing)
    return (
      <Sheet
        title={
          data.gyms.some((g) => g.id === editing.id)
            ? "Edit gym profile"
            : "Add gym profile"
        }
        onClose={() => setEditing(undefined)}
      >
        <Field label="Profile name">
          <input
            maxLength={60}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </Field>
        <Field label={`Bar weight (${unit})`}>
          <input
            type="number"
            min="0"
            step="any"
            value={bar}
            onChange={(e) => setBar(e.target.value)}
          />
        </Field>
        <h3>Available plates</h3>
        <Switch
          label="Limit to my physical plates"
          checked={finite}
          onChange={setFinite}
        />
        <p className="muted">
          {finite
            ? "Quantities are pairs: one plate on each side. Odd spare plates remain saved and are excluded from loading."
            : "Unlimited pairs assumed for each size."}
        </p>
        <div className="gym-stock">
          {stock.map((row, i) => (
            <div className="gym-stock-row" key={row.weightKg}>
              <button
                className="gym-color"
                aria-label={`Choose color for ${displayWeight(row.weightKg, unit)} ${unit} plate`}
                style={{ background: row.color || "var(--muted)" }}
                onClick={() => setPalette(palette === i ? undefined : i)}
              />
              <strong>
                {displayWeight(row.weightKg, unit)} {unit}
              </strong>
              {finite && (
                <div className="gym-pairs">
                  <button
                    aria-label={`Remove pair of ${displayWeight(row.weightKg, unit)} ${unit}`}
                    disabled={busy || row.quantity < 2}
                    onClick={() => change(i, { quantity: row.quantity - 2 })}
                  >
                    −
                  </button>
                  <output
                    aria-label={`${displayWeight(row.weightKg, unit)} ${unit} pairs`}
                  >
                    {Math.floor(row.quantity / 2)}
                  </output>
                  <button
                    aria-label={`Add pair of ${displayWeight(row.weightKg, unit)} ${unit}`}
                    disabled={busy}
                    onClick={() => change(i, { quantity: row.quantity + 2 })}
                  >
                    +
                  </button>
                </div>
              )}
              <IconButton
                name="trash"
                label={`Remove ${displayWeight(row.weightKg, unit)} ${unit} plate size`}
                onClick={() =>
                  setStock((rows) => rows.filter((_, index) => index !== i))
                }
              />
              {palette === i && (
                <div className="gym-palette">
                  {colors.map(([label, color]) => (
                    <button
                      key={color}
                      aria-label={`${label} plate color`}
                      aria-pressed={row.color === color}
                      style={{ background: color }}
                      onClick={() => {
                        change(i, { color });
                        setPalette(undefined);
                      }}
                    />
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
        <Field label={`New plate weight (${unit})`}>
          <input
            type="number"
            min="0"
            step="any"
            value={newWeight}
            onChange={(e) => setNewWeight(e.target.value)}
          />
        </Field>
        <Button
          variant="secondary"
          disabled={busy}
          onClick={() =>
            run(async () => {
              const entered = Number(newWeight);
              if (
                !newWeight.trim() ||
                !Number.isFinite(entered) ||
                entered <= 0
              )
                throw Error("Enter a positive plate weight");
              const weightKg = canonicalWeight(entered, unit);
              if (
                stock.some((row) => Math.abs(row.weightKg - weightKg) < 0.001)
              )
                throw Error("This plate size is already listed");
              setStock((rows) =>
                [...rows, { weightKg, quantity: 4 }].sort(
                  (a, b) => b.weightKg - a.weightKg,
                ),
              );
              setNewWeight("");
            })
          }
        >
          Add plate size
        </Button>
        <fieldset className="choice-fieldset">
          <legend>Unavailable equipment</legend>
          <p className="muted">
            Hide these types from the exercise chooser while this gym is active.
          </p>
          {equipment.map((value) => (
            <label key={value}>
              <input
                type="checkbox"
                checked={unavailable.includes(value)}
                onChange={(e) =>
                  setUnavailable((rows) =>
                    e.target.checked
                      ? [...rows, value]
                      : rows.filter((x) => x !== value),
                  )
                }
              />{" "}
              {value}
            </label>
          ))}
        </fieldset>
        <Button
          disabled={busy}
          onClick={() =>
            run(async () => {
              if (!name.trim()) throw Error("Enter a profile name");
              const parsed = Number(bar);
              if (!bar.trim() || !Number.isFinite(parsed) || parsed <= 0)
                throw Error("Enter a positive bar weight");
              await saveGym(
                {
                  ...editing,
                  name: name.trim(),
                  barKg:
                    bar === initialBar
                      ? editing.barKg
                      : canonicalWeight(parsed, unit),
                  platesKg: stock.map((row) => row.weightKg),
                  plateInventory: finite ? stock : undefined,
                  unavailableEquipment: unavailable,
                },
                true,
              );
              setEditing(undefined);
            }, "Gym setup saved")
          }
        >
          Save & use setup
        </Button>
      </Sheet>
    );
  return (
    <Sheet title="Gym profiles" onClose={onClose}>
      <p className="muted">
        Save the bar, plates and equipment available at each gym.
      </p>
      <Button
        disabled={busy}
        onClick={() =>
          edit({
            id: newId(),
            name: "",
            barKg: unit === "lb" ? canonicalWeight(45, unit) : 20,
            platesKg: defaults.map((p) => p.weightKg),
            plateInventory: defaults,
          })
        }
      >
        Add profile
      </Button>
      {!data.gyms.length && (
        <Button
          variant="secondary"
          disabled={busy}
          onClick={() =>
            run(
              () =>
                saveGym(
                  {
                    id: newId(),
                    name: "Default gym",
                    barKg: 20,
                    platesKg: defaults.map((p) => p.weightKg),
                    plateInventory: defaults,
                  },
                  true,
                ),
              "Default gym added",
            )
          }
        >
          Seed default profile
        </Button>
      )}
      {data.gyms.map((gym) => (
        <section className="card" key={gym.id}>
          <h3>
            {gym.name}
            {data.profile.activeGymId === gym.id && " · Active"}
          </h3>
          <p>
            {displayWeight(gym.barKg, unit)} {unit} bar ·{" "}
            {gym.plateInventory === undefined
              ? "Unlimited pairs"
              : `${gym.plateInventory.reduce((sum, row) => sum + Math.floor(row.quantity / 2), 0)} plate pairs`}
          </p>
          {!!gym.unavailableEquipment?.length && (
            <p className="muted">
              Unavailable: {gym.unavailableEquipment.join(", ")}
            </p>
          )}
          <div className="row gym-actions">
            <Button
              variant="secondary"
              disabled={busy || data.profile.activeGymId === gym.id}
              onClick={() => run(() => selectGym(gym.id), "Gym selected")}
            >
              {data.profile.activeGymId === gym.id
                ? "Active"
                : `Use ${gym.name}`}
            </Button>
            <IconButton
              name="edit"
              label={`Edit ${gym.name}`}
              onClick={() => edit(gym)}
            />
            <Button
              variant="ghost"
              disabled={busy}
              onClick={() =>
                run(
                  () =>
                    saveGym({
                      ...gym,
                      id: newId(),
                      name: `${gym.name} (Copy)`,
                    }),
                  "Gym duplicated",
                )
              }
            >
              Duplicate {gym.name}
            </Button>
            <IconButton
              name="trash"
              label={`Delete ${gym.name}`}
              onClick={() => setDeleting(gym)}
            />
          </div>
        </section>
      ))}
    </Sheet>
  );
}
