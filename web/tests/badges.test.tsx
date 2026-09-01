import { cleanup, render, screen } from "@testing-library/react";
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, expect, it } from "vitest";
import { deriveSnapshot } from "../src/domain/engine";
import { defaultProfile, type AppSnapshot } from "../src/domain/types";
import { Ledger } from "../src/features/Recovery";
import { AppProvider } from "../src/ui/context";

const canonicalBadges = [
  ["first_workout", "Iron Initiate", "ic_badge_dumbbell.png"],
  ["streak_3", "Spark", "ic_badge_flame.png"],
  ["first_rest_timer", "Patience", "ic_badge_hourglass.png"],
  ["first_plan", "Architect", "ic_badge_twin_dumbbells.png"],
  ["workouts_10", "Charged", "ic_badge_lightning.png"],
  ["consistency_4w", "Clockwork", "ic_badge_calendar.png"],
  ["first_pr", "Muscle Memory", "ic_badge_flexed_arm.png"],
  ["ai_activated", "Augmented", "ic_badge_atom.png"],
  ["progressive_streak", "Growth Curve", "ic_badge_chart.png"],
  ["workouts_50", "Champion", "ic_badge_trophy.png"],
  ["streak_30", "Ironclad", "ic_badge_shield.png"],
  ["workouts_100", "Sovereign", "ic_badge_crown.png"],
  ["volume_milestone", "Summit", "ic_badge_mountain.png"],
  ["member_365", "Eternal", "ic_badge_infinity.png"],
  ["all_goal_modes", "Multiclass", "ic_badge_3stars.png"],
] as const;

afterEach(cleanup);

it("publishes every canonical Android badge asset for the web", () => {
  const assetDirectory = resolve(
    import.meta.dirname,
    "../public/assets/badges",
  );

  expect(canonicalBadges).toHaveLength(15);
  for (const [, , icon] of canonicalBadges) {
    expect(existsSync(resolve(assetDirectory, icon)), icon).toBe(true);
  }
});

it("renders distinct canonical badge art and omits obsolete s_rank", () => {
  const badgeUnlocks = Object.fromEntries([
    ...canonicalBadges.map(([id]) => [id, 1] as const),
    ["s_rank", 1],
  ]);
  const data: AppSnapshot = {
    profile: { ...defaultProfile, badgeUnlocks },
    workouts: [],
    plans: [],
    exercises: [],
    photos: [],
    measurements: [],
    checkins: [],
    gyms: [],
  };
  const derived = deriveSnapshot(data, Date.now());
  const { container } = render(
    <AppProvider
      value={{ data, derived, busy: false, run: async () => true }}
    >
      <Ledger />
    </AppProvider>,
  );

  const rows = Array.from(
    container.querySelectorAll<HTMLElement>(".badge-list .list-row"),
  );
  expect(rows).toHaveLength(15);
  expect(screen.queryByText("s rank")).not.toBeInTheDocument();

  const renderedIcons = rows.map((row) =>
    row.querySelector("img")?.getAttribute("src")?.split("/").at(-1),
  );
  expect(new Set(renderedIcons)).toEqual(
    new Set(canonicalBadges.map(([, , icon]) => icon)),
  );
  for (const [, title] of canonicalBadges) {
    expect(screen.getByText(title)).toBeInTheDocument();
  }

  expect(
    screen
      .getByText("Augmented")
      .closest(".list-row")
      ?.querySelector("img")
      ?.getAttribute("src"),
  ).toMatch(/\/assets\/badges\/ic_badge_atom\.png$/);
  expect(
    screen
      .getByText("Multiclass")
      .closest(".list-row")
      ?.querySelector("img")
      ?.getAttribute("src"),
  ).toMatch(/\/assets\/badges\/ic_badge_3stars\.png$/);
});
