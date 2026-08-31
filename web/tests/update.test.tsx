import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { defaultProfile, type AppSnapshot } from "../src/domain/types";
import { AppProvider } from "../src/ui/context";
import { deriveSnapshot } from "../src/domain/engine";
const mock = vi.hoisted(() => ({
  options: {} as { onNeedReload?: () => void },
  update: vi.fn(),
  ready: true,
}));
vi.mock("virtual:pwa-register/react", () => ({
  useRegisterSW: (options = {}) => {
    mock.options = options;
    return {
      needRefresh: [mock.ready, vi.fn()],
      updateServiceWorker: mock.update,
    };
  },
}));
import { UpdateNotice } from "../src/ui/UpdateNotice";
afterEach(() => {
  cleanup();
  mock.update.mockReset();
  mock.ready = true;
});
const data: AppSnapshot = {
  profile: defaultProfile,
  plans: [],
  workouts: [],
  exercises: [],
  measurements: [],
  photos: [],
  gyms: [],
  checkins: [],
};
const view = (busy: boolean) => (
  <AppProvider
    value={{ data, derived: deriveSnapshot(data), busy, run: async () => true }}
  >
    <UpdateNotice />
  </AppProvider>
);
it("blocks application updates while a write is pending", () => {
  render(view(true));
  expect(
    screen.getByRole("button", { name: "Update & reload" }),
  ).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "Update & reload" }));
  expect(mock.update).not.toHaveBeenCalled();
});
it("takes control of external activation reload and still waits for a safe user action", () => {
  mock.ready = false;
  const result = render(view(true));
  expect(mock.options.onNeedReload).toBeTypeOf("function");
  act(() => mock.options.onNeedReload?.());
  expect(
    screen.getByRole("button", { name: "Reload updated app" }),
  ).toBeDisabled();
  result.rerender(view(false));
  expect(
    screen.getByRole("button", { name: "Reload updated app" }),
  ).toBeEnabled();
});
