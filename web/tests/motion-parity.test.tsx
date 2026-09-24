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
  const secondCard = screen.getByText("Plan Two").closest<HTMLElement>("[data-plan-id]")!;
  const handle = screen.getByRole("button", { name: "Drag to reorder Plan Two" });
  Object.defineProperty(firstCard, "getBoundingClientRect", {
    configurable: true,
    value: () => ({ top: 80, bottom: 180, left: 0, right: 320, width: 320, height: 100, x: 0, y: 80, toJSON: () => ({}) }),
  });
  Object.defineProperty(secondCard, "getBoundingClientRect", {
    configurable: true,
    value: () => ({ top: 300, bottom: 400, left: 0, right: 320, width: 320, height: 100, x: 0, y: 300, toJSON: () => ({}) }),
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
  const card = eyebrow.closest("section")!;
  expect(card).toHaveClass("animated-card-shine");
  const gradient = card.querySelector("linearGradient")!;
  expect(gradient).toHaveAttribute("gradientUnits", "userSpaceOnUse");
  expect(gradient).toHaveAttribute("x1", "-600");
  expect(gradient).toHaveAttribute("x2", "300");
  expect(gradient).toHaveAttribute("y1", "0");
  expect(gradient).toHaveAttribute("y2", "100%");
  expect([...gradient.querySelectorAll("stop")].map((stop) => [
    stop.getAttribute("offset"), stop.getAttribute("stop-opacity"),
  ])).toEqual([["0", "0.03"], ["0.35", "0.2"], ["0.65", "0.28"], ["1", "0.04"]]);
  const animations = gradient.querySelectorAll("animate");
  expect(animations).toHaveLength(2);
  expect(animations[0]).toHaveAttribute("values", "-600;1800;-600");
  expect(animations[1]).toHaveAttribute("values", "300;2700;300");
  expect(animations[0]).toHaveAttribute("dur", "10s");
  expect(animations[0]).toHaveAttribute("calcMode", "linear");
});

it("navigates one adjacent primary tab from a touch swipe", async () => {
  Object.defineProperties(HTMLElement.prototype, {
    setPointerCapture: { configurable: true, value: vi.fn() },
    hasPointerCapture: { configurable: true, value: vi.fn(() => true) },
    releasePointerCapture: { configurable: true, value: vi.fn() },
  });
  render(<App />);
  await screen.findByRole("heading", { name: "Plans" });
  const main = document.getElementById("main-content")!;

  fireEvent.pointerDown(main, { pointerId: 42, pointerType: "touch", clientX: 320, clientY: 160 });
  fireEvent.pointerMove(main, { pointerId: 42, pointerType: "touch", clientX: 80, clientY: 164 });
  fireEvent.pointerUp(main, { pointerId: 42, pointerType: "touch", clientX: 80, clientY: 164 });

  await screen.findByRole("heading", { name: "History" });
  expect(window.location.hash).toBe("#/log");
});
