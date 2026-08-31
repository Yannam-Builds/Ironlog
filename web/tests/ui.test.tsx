import { render, screen, fireEvent, cleanup } from "@testing-library/react";
import { afterEach, describe, it, expect, vi } from "vitest";
import { Sheet, NumberWheel } from "../src/ui/components";
import { applyTheme, themeNames } from "../src/ui/theme";
import { SetEditor } from "../src/features/Workout";
afterEach(cleanup);

it("replaces the opposite effort scale instead of keeping contradictory values", () => {
  const save = vi.fn();
  render(
    <SetEditor
      set={{
        id: "s",
        weightKg: 65,
        reps: 8,
        durationSeconds: 0,
        distanceKm: 0,
        kind: "normal",
        rir: 2,
        notes: "",
        loggedAt: 1,
      }}
      unit="kg"
      effort="rpe"
      onSave={save}
      onClose={() => {}}
    />,
  );
  fireEvent.change(screen.getByLabelText("RPE"), { target: { value: "10" } });
  fireEvent.click(screen.getByRole("button", { name: "Save set" }));
  expect(save).toHaveBeenCalledWith(
    expect.objectContaining({ rpe: 10, rir: undefined }),
  );
});
describe("native UI contracts", () => {
  it("lets a keyboard user clear and retype a bounded number", () => {
    render(
      <NumberWheel
        label="Height"
        value={170}
        min={100}
        max={250}
        onChange={() => {}}
      />,
    );
    const field = screen.getByLabelText("Height");
    fireEvent.change(field, { target: { value: "" } });
    expect(field).toHaveValue(null);
    fireEvent.change(field, { target: { value: "220" } });
    expect(field).toHaveValue(220);
  });
  it("has all twelve palettes and all twenty-four roles", () => {
    expect(Object.keys(themeNames)).toHaveLength(12);
    applyTheme("dark");
    expect(document.documentElement.style.getPropertyValue("--bg")).toBe(
      "#121212",
    );
    expect(
      document.documentElement.style.getPropertyValue("--card"),
    ).not.toMatch(/.{8}$/);
  });
  it("exposes bounded number entry rather than infinite accessibility traversal", () => {
    const change = vi.fn();
    render(
      <NumberWheel
        label="Weight"
        value={70}
        min={20}
        max={300}
        step={0.5}
        onChange={change}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Increase Weight" }));
    expect(change).toHaveBeenCalledWith(70.5);
    expect(screen.getByRole("spinbutton", { name: "Weight" })).toHaveAttribute(
      "max",
      "300",
    );
  });
  it("labels overlays and provides a dismissal control", () => {
    const close = vi.fn();
    render(
      <Sheet title="Edit set" onClose={close}>
        <p>Form</p>
      </Sheet>,
    );
    expect(screen.getByRole("dialog")).toHaveAccessibleName("Edit set");
    fireEvent.click(screen.getByRole("button", { name: "Close Edit set" }));
    expect(close).toHaveBeenCalled();
  });
});
