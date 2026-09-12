import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { App } from "../src/App";
import { bootstrap, db, readSnapshot, savePlan, saveProfile } from "../src/data/store";
import type { Plan } from "../src/domain/types";

const plan = (id: string, name: string, order: number): Plan => ({
  id,
  name,
  description: "",
  goal: "Strength",
  order,
  days: [],
});

beforeEach(async () => {
  await db.delete();
  await db.open();
  await bootstrap([]);
  await saveProfile({ name: "Motion Tester", onboarded: true, activePlanId: "one" });
  await savePlan(plan("one", "Plan One", 0));
  await savePlan(plan("two", "Plan Two", 1));
  window.location.hash = "#/plans";
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  document.body.classList.remove("plan-reordering");
});

it("drags a plan to the top and makes it active", async () => {
  Object.defineProperties(HTMLElement.prototype, {
    setPointerCapture: { configurable: true, value: vi.fn() },
    hasPointerCapture: { configurable: true, value: vi.fn(() => true) },
    releasePointerCapture: { configurable: true, value: vi.fn() },
  });
  render(<App />);
  const firstCard = (await screen.findByText("Plan One")).closest<HTMLElement>("[data-plan-id]")!;
  const handle = screen.getByRole("button", { name: "Drag to reorder Plan Two" });
  Object.defineProperty(document, "elementFromPoint", {
    configurable: true,
    value: vi.fn(() => firstCard),
  });

  fireEvent.pointerDown(handle, { pointerId: 1, clientY: 400 });
  expect(handle).toHaveAttribute("aria-pressed", "true");
  fireEvent.pointerMove(handle, { pointerId: 1, clientY: 100 });
  fireEvent.pointerUp(handle, { pointerId: 1, clientY: 100 });

  await waitFor(async () => {
    const snapshot = await readSnapshot();
    expect(snapshot.plans.map((saved) => saved.id)).toEqual(["two", "one"]);
    expect(snapshot.profile.activePlanId).toBe("two");
  });
});

it("supports keyboard plan reordering and top-plan activation", async () => {
  render(<App />);
  const handle = await screen.findByRole("button", { name: "Drag to reorder Plan Two" });
  await act(async () => fireEvent.keyDown(handle, { key: "ArrowUp" }));
  await waitFor(async () => {
    const snapshot = await readSnapshot();
    expect(snapshot.plans[0].id).toBe("two");
    expect(snapshot.profile.activePlanId).toBe("two");
  });
});

it("applies the native animated shine to the Home workout card", async () => {
  render(<App />);
  fireEvent.click(await screen.findByRole("link", { name: "Home" }));
  const eyebrow = await screen.findByText("Today’s workout");
  expect(eyebrow.closest("section")).toHaveClass("animated-card-shine");
});
