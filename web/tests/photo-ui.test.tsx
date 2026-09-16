import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { App } from "../src/App";
import { bootstrap, db, readSnapshot, savePhoto, saveProfile } from "../src/data/store";

const photo = (id: string, date: string, capturedAt: number, notes = "") => ({
  id, date, capturedAt, notes, blob: new Blob([id], { type: "image/jpeg" }),
});

beforeEach(async () => {
  await db.delete(); await db.open(); await bootstrap([]); await saveProfile({ onboarded: true });
  vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:test");
  vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined);
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it("compares the latest capture from each selected calendar date", async () => {
  await savePhoto(photo("before-old", "2026-09-01", 1));
  await savePhoto(photo("before-latest", "2026-09-01", 2));
  await savePhoto(photo("after", "2026-09-02", 3));
  history.replaceState(null, "", "#/photos"); render(<App />);
  expect(await screen.findByRole("heading", { name: "Progress photos" })).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "Compare dates" }));
  fireEvent.click(screen.getByRole("button", { name: "Compare 2026-09-01, has photo" }));
  fireEvent.click(screen.getByRole("button", { name: "Compare 2026-09-02, has photo" }));
  expect(screen.getByRole("heading", { name: "Before & after" })).toBeVisible();
  expect(screen.getAllByAltText(/Progress from 2026-09-0[12]/)).toHaveLength(5);
  fireEvent.click(screen.getAllByAltText("Progress from 2026-09-01")[0]);
  expect(screen.getByRole("dialog", { name: "Compare progress photos" })).toBeVisible();
  expect(screen.getByLabelText("Before and after mix")).toHaveValue("0.5");
  expect(screen.queryByLabelText("Photo zoom")).not.toBeInTheDocument();
});

it("opens the browser camera and gallery equivalents for an empty calendar day", async () => {
  history.replaceState(null, "", "#/photos"); render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: "Add photo for 2026-09-04" }));
  expect(screen.getByRole("heading", { name: "Add photo for 2026-09-04" })).toBeVisible();
  expect(screen.getAllByLabelText("Camera").at(-1)).toHaveAttribute("capture", "environment");
  expect(screen.getByLabelText("Gallery")).not.toHaveAttribute("capture");
});

it("saves notes and confirms destructive bulk deletion", async () => {
  await savePhoto(photo("one", "2026-09-03", 1, "Start"));
  history.replaceState(null, "", "#/photos"); render(<App />);
  fireEvent.click((await screen.findAllByAltText("Progress from 2026-09-03"))[0]);
  fireEvent.change(screen.getByLabelText("Photo notes"), { target: { value: "Front relaxed" } });
  await act(async () => fireEvent.click(screen.getByRole("button", { name: "Save note" })));
  await waitFor(async () => expect((await readSnapshot()).photos[0]?.notes).toBe("Front relaxed"));
  await waitFor(() => expect(screen.getByRole("button", { name: "Close" })).toBeEnabled());
  fireEvent.click(screen.getByRole("button", { name: "Close" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "Clear all" })).toBeEnabled());
  fireEvent.click(screen.getByRole("button", { name: "Clear all" }));
  expect(screen.getByRole("heading", { name: "Clear all progress photos?" })).toBeVisible();
  await waitFor(() => expect(screen.getByRole("button", { name: "Clear all photos" })).toBeEnabled());
  await act(async () => fireEvent.click(screen.getByRole("button", { name: "Clear all photos" })));
  await waitFor(async () => expect((await readSnapshot()).photos).toHaveLength(0));
});
