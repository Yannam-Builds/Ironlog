import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it } from "vitest";
import { App } from "../src/App";
import { bootstrap, db, readSnapshot, resetData } from "../src/data/store";

beforeEach(async () => {
  await resetData();
  await db.open();
  await bootstrap([]);
  history.replaceState(null, "", "#/home");
});

afterEach(cleanup);

async function clickNext(name: RegExp) {
  const button = await screen.findByRole("button", { name });
  await waitFor(() => expect(button).toBeEnabled());
  await act(async () => fireEvent.click(button));
}

it("completes the ten-page native onboarding sequence and persists calibration", async () => {
  render(<App />);

  expect(await screen.findByText(/train with evidence/i)).toBeInTheDocument();
  await clickNext(/build my training system/i);

  expect(await screen.findByRole("heading", { name: /what should your ledger call you/i })).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Your name"), { target: { value: "Ada" } });
  await clickNext(/continue as ada/i);

  expect(await screen.findByRole("heading", { name: /tell us where training begins/i })).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Training age (months)"), { target: { value: "24" } });
  fireEvent.change(screen.getByLabelText("Push-ups"), { target: { value: "40" } });
  fireEvent.change(screen.getByLabelText("Pull-ups"), { target: { value: "10" } });
  fireEvent.change(screen.getByLabelText("Bench press (kg)"), { target: { value: "100" } });
  await clickNext(/use this baseline/i);

  expect(await screen.findByRole("heading", { name: /how should progression begin/i })).toBeInTheDocument();
  await clickNext(/use this progression/i);

  expect(await screen.findByRole("heading", { name: /choose days you can actually protect/i })).toBeInTheDocument();
  expect(screen.getByRole("checkbox", { name: "Monday" })).toBeChecked();
  await clickNext(/save weekly rhythm/i);

  expect(await screen.findByRole("heading", { name: /what should the plan optimize first/i })).toBeInTheDocument();
  await clickNext(/use this goal/i);

  expect(await screen.findByRole("heading", { name: /local by default/i })).toBeInTheDocument();
  await clickNext(/continue with local coaching/i);

  expect(await screen.findByRole("heading", { name: /browser capabilities/i })).toBeInTheDocument();
  await clickNext(/continue without integrations/i);

  expect(await screen.findByRole("heading", { name: /provisional profile is ready/i })).toBeInTheDocument();
  await clickNext(/save my baseline/i);

  expect(await screen.findByRole("heading", { name: /start with structure/i })).toBeInTheDocument();
  await clickNext(/start training/i);

  await waitFor(() => expect(screen.getByRole("navigation", { name: "Main" })).toBeInTheDocument());
  const saved = await readSnapshot();
  expect(saved.profile).toMatchObject({
    name: "Ada",
    onboarded: true,
    onboardingStep: 10,
    trainingAgeMonths: 24,
    baselinePushups: 40,
    baselinePullups: 10,
    baselineBenchKg: 100,
    selectedTrainingDays: [0, 2, 4],
  });
  expect(saved.profile.ledgerBaseline?.estimatedLifetimeSessions).toBe(313);
});

it("explore with defaults creates no provisional history or rewards", async () => {
  render(<App />);
  await clickNext(/explore with sensible defaults/i);

  await waitFor(() => expect(screen.getByRole("navigation", { name: "Main" })).toBeInTheDocument());
  const saved = await readSnapshot();
  expect(saved.profile).toMatchObject({
    onboarded: true,
    onboardingStep: 10,
    trainingAgeMonths: 0,
    onboardingBodyweightKg: undefined,
  });
  expect(saved.profile.ledgerBaseline).toMatchObject({
    estimatedLifetimeSessions: 0,
    xp: 0,
    supportedBadgeIds: [],
  });
});
