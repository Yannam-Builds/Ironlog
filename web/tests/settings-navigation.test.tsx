import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it } from "vitest";
import { App } from "../src/App";
import { bootstrap, db, readSnapshot, saveProfile } from "../src/data/store";

afterEach(cleanup);
beforeEach(async () => {
  await db.delete();
  await db.open();
  await bootstrap([]);
  await saveProfile({ onboarded: true, onboardingStep: 10 });
  window.location.hash = "#/settings";
});

it("opens focused settings destinations and returns to the searchable console", async () => {
  render(<App />);
  expect(await screen.findByRole("heading", { name: "Training Console" })).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Profile & training" })).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Search settings"), { target: { value: "gym rest" } });
  expect(screen.getByRole("button", { name: /^Training/ })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: /^Appearance/ })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: /^Training/ }));
  expect(screen.getByRole("heading", { name: "Training" })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "Profile & training" })).toBeInTheDocument();
  expect(screen.getByLabelText("Search settings")).not.toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: /Back to settings/ }));
  expect(screen.getByRole("heading", { name: "Training Console" })).toBeInTheDocument();
});

it("runs the scoped history action only after confirmation", async () => {
  render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: /^Data & Privacy/ }));
  fireEvent.click(screen.getByRole("button", { name: /Clear completed history/ }));
  expect(screen.getByRole("dialog", { name: "Clear completed history?" })).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Clear history" }));
  await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
});

it("persists independent shine and navigation-glass effects with real consumers", async () => {
  render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: /^Appearance/ }));
  const shine = screen.getByRole("switch", { name: "Animated card shine" });
  const glass = screen.getByRole("switch", { name: "Liquid glass navigation" });
  expect(shine).toBeChecked();
  expect(glass).toBeChecked();
  expect(document.querySelector(".bottom-nav")).toHaveClass("liquid-glass");
  fireEvent.click(shine);
  await waitFor(() => expect(shine).not.toBeChecked());
  await waitFor(() => expect(glass).toBeEnabled());
  fireEvent.click(glass);
  await waitFor(() => expect(glass).not.toBeChecked());
  expect(document.querySelector(".bottom-nav")).not.toHaveClass("liquid-glass");
  fireEvent.click(screen.getByRole("link", { name: /^Home/ }));
  await waitFor(() => expect(window.location.hash).toBe("#/home"));
  expect(document.querySelector(".animated-card-shine")).not.toBeInTheDocument();
  expect((await readSnapshot()).profile).toMatchObject({
    cardShineEnabled: false,
    liquidGlassEnabled: false,
  });
});
