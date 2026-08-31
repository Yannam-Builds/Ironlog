import { beforeEach, expect, it } from "vitest";
import { instantiatePlan } from "../src/domain/plans";
import {
  bootstrap,
  readSnapshot,
  resetData,
  restoreSnapshot,
  savePlan,
} from "../src/data/store";
import { encodeWebBackup, decodeWebBackup } from "../src/domain/codecs";
import templates from "../src/generated/templates.json";
import type { Plan } from "../src/domain/types";

beforeEach(async () => {
  await resetData();
  await bootstrap([]);
});

it("instantiates independent plan, day and slot IDs while retaining prescriptions and exercise references", () => {
  const source = templates[0] as Plan;
  const original = structuredClone(source);
  const a = instantiatePlan(source, { order: 2 });
  const b = instantiatePlan(source, { order: 3 });
  const ids = (p: Plan) => [
    p.id,
    ...p.days.flatMap((d) => [d.id, ...d.exercises.map((e) => e.id)]),
  ];
  const all = [...ids(source), ...ids(a), ...ids(b)];
  expect(new Set(all).size).toBe(all.length);
  expect(a.order).toBe(2);
  expect(a.templateId).toBe(source.templateId);
  expect(a.days[0].exercises[0]).toEqual({
    ...source.days[0].exercises[0],
    id: a.days[0].exercises[0].id,
  });
  a.days[0].exercises[0].notes = "Edit one copy";
  expect(source).toEqual(original);
  expect(b.days[0].exercises[0].notes).toBe(
    original.days[0].exercises[0].notes,
  );
});

it("can back up and restore two independently added copies of the same template", async () => {
  await savePlan(instantiatePlan(templates[0] as Plan, { order: 0 }));
  await savePlan(instantiatePlan(templates[0] as Plan, { order: 1 }));
  const snapshot = await readSnapshot();
  const roundtrip = await decodeWebBackup(await encodeWebBackup(snapshot));
  await expect(restoreSnapshot(roundtrip)).resolves.toBeUndefined();
  expect((await readSnapshot()).plans).toHaveLength(2);
});
