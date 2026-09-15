import { createContext, useContext, type ReactNode } from "react";
import type { AppSnapshot, Workout } from "../domain/types";
import { deriveSnapshot } from "../domain/engine";
import { navigateTo } from "./navigation";
export const navigate = navigateTo;
export type AppContextValue = {
  error?: string;
  data: AppSnapshot;
  derived: ReturnType<typeof deriveSnapshot>;
  busy: boolean;
  run: (work: () => Promise<unknown>, message?: string) => Promise<boolean>;
};
const Context = createContext<AppContextValue | null>(null);
export const useOptionalApp = () => useContext(Context);
export const AppProvider = ({
  value,
  children,
}: {
  value: AppContextValue;
  children: ReactNode;
}) => <Context.Provider value={value}>{children}</Context.Provider>;
export function useApp() {
  const v = useContext(Context);
  if (!v) throw Error("App provider missing");
  return v;
}
export const displayWeight = (kg: number, unit: string) =>
  Number((kg * (unit === "lb" ? 2.2046226218487757 : 1)).toFixed(1));
export const canonicalWeight = (weight: number, unit: string) =>
  weight / (unit === "lb" ? 2.2046226218487757 : 1);
export const formatNumber = (n: number) =>
  new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(n);
export const hasTrainingHistory = (workouts: Workout[], now = Date.now()) =>
  workouts.some(
    (w) =>
      w.status === "completed" &&
      w.startedAt <= now &&
      (w.completedAt ?? w.startedAt) <= now &&
      w.exercises.some((e) =>
        e.loggedSets.some(
          (s) => s.kind !== "warmup" && (s.reps > 0 || s.durationSeconds > 0),
        ),
      ),
  );
export function download(data: Blob | string, name: string) {
  const blob =
    typeof data === "string"
      ? new Blob([data], { type: "application/json" })
      : data;
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}
