import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it } from "vitest";
import { App } from "../src/App";
import { bootstrap, db, readSnapshot, saveProfile } from "../src/data/store";

beforeEach(async () => { await db.delete(); await db.open(); await bootstrap([]); await saveProfile({ onboarded: true, unit: "kg" }); });
afterEach(cleanup);

it("logs canonical bodyweight and persists a weight goal", async () => {
  history.replaceState(null, "", "#/body"); render(<App />);
  expect(await screen.findByRole("heading", { name: "Body weight" })).toBeVisible();
  fireEvent.change(screen.getByLabelText("kg"), { target: { value: "81.5" } });
  await act(async () => fireEvent.click(screen.getByRole("button", { name: "Log weight" })));
  await waitFor(() => expect(screen.getByRole("button", { name: "Save goals" })).toBeEnabled());
  fireEvent.change(screen.getByLabelText("Goal (kg)"), { target: { value: "78" } });
  await act(async () => fireEvent.click(screen.getByRole("button", { name: "Save goals" })));
  await waitFor(async () => {
    const data = await readSnapshot();
    expect(data.measurements.find((row) => row.type === "bodyweight")?.value).toBe(81.5);
    expect(data.profile.goalWeightKg).toBe(78);
  });
});

it("saves a multi-field measurement entry and its goal", async () => {
  history.replaceState(null, "", "#/measurements"); render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: "Add measurement" }));
  fireEvent.change(screen.getByLabelText("Chest (cm)"), { target: { value: "102" } });
  fireEvent.change(screen.getByLabelText("Waist (cm)"), { target: { value: "82" } });
  await act(async () => fireEvent.click(screen.getByRole("button", { name: "Save" })));
  await waitFor(() => expect(screen.queryByRole("heading", { name: "Add measurement" })).not.toBeInTheDocument());
  expect((await readSnapshot()).measurements).toHaveLength(2);
  fireEvent.click(screen.getByRole("button", { name: /Chest 102 cm/ }));
  fireEvent.change(screen.getByLabelText("Goal (cm)"), { target: { value: "105" } });
  await act(async () => fireEvent.click(screen.getByRole("button", { name: "Save goal" })));
  await waitFor(async () => expect((await readSnapshot()).profile.measurementGoals?.chest).toBe(105));
});
