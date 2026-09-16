import { describe, it, expect } from "vitest";
import {
  decodePlans,
  encodePlan,
  encodeAndroidBackup,
  decodeAndroidBackup,
  encodeWebBackup,
  decodeWebBackup,
} from "../src/domain/codecs";
import {
  defaultProfile,
  type AppSnapshot,
  type Exercise,
} from "../src/domain/types";
import { creditedProof, workoutDurationSeconds } from "../src/domain/engine";
import { zipSync, strToU8 } from "fflate";
it.each([-1, "not-a-duration"])(
  "rejects corrupt native duration %j",
  (duration) => {
    const snapshot: AppSnapshot = {
      profile: defaultProfile,
      plans: [],
      workouts: [],
      exercises: [],
      measurements: [],
      photos: [],
      checkins: [],
      gyms: [],
    };
    const payload = JSON.parse(encodeAndroidBackup(snapshot));
    delete payload.webExtension;
    payload.data.workouts = [
      {
        id: "w",
        name: "Invalid duration",
        started_at: 0,
        completed_at: 0,
        status: "completed",
        duration_seconds: duration,
      },
    ];
    expect(() => decodeAndroidBackup(JSON.stringify(payload))).toThrow(
      "duration",
    );
  },
);
const library: Exercise[] = [
  {
    id: "bench",
    name: "Bench Press",
    muscle: "chest",
    equipment: "barbell",
    tracking: "weight_reps",
    movementPattern: "push",
  },
];
const raw = {
  plans: [
    {
      name: "Plan",
      plan_days: [
        {
          name: "Day",
          plan_exercises: [
            {
              exercise_name: "Bench Press",
              sets: 0,
              rest_seconds: -2,
              reps: "",
              is_warmup: true,
              note: "slow",
            },
            { name: "Unlisted move" },
          ],
        },
      ],
    },
  ],
};
describe("portable formats", () => {
  it("decodes native wrappers, aliases, bounds and unresolved counts without dropping names", () => {
    const { plans, result } = decodePlans(JSON.stringify(raw), library);
    expect(result).toMatchObject({ imported: 1, unresolved: 1, skipped: 0 });
    expect(plans[0].days[0].exercises[0]).toMatchObject({
      exerciseId: "bench",
      sets: 1,
      restSeconds: 0,
      reps: "8-12",
      isWarmup: true,
      notes: "slow",
    });
    expect(plans[0].days[0].exercises[1].name).toBe("Unlisted move");
  });
  it("roundtrips canonical native plan envelope with independent import IDs", () => {
    const plan = decodePlans(JSON.stringify(raw), library).plans[0];
    const encoded = encodePlan(plan);
    expect(JSON.parse(encoded).type).toBe("ironlog_plan");
    const imported = decodePlans(encoded, library).plans[0];
    expect(imported.name).toBe(plan.name);
    expect(imported.id).not.toBe(plan.id);
    expect(imported.days[0].exercises[0].notes).toBe("slow");
  });
  it("auto-links only strong fuzzy exercise matches", () => {
    const strongLibrary: Exercise[] = [{
      ...library[0],
      name: "Barbell Bench Press",
    }];
    const decoded = decodePlans(JSON.stringify({
      name: "Fuzzy",
      days: [{ exercises: [{ name: "barbell benhc press" }] }],
    }), strongLibrary);
    expect(decoded.plans[0].days[0].exercises[0]).toMatchObject({
      exerciseId: "bench",
      name: "Barbell Bench Press",
    });
    expect(decoded.result.unresolved).toBe(0);
  });
  it("keeps review-level exercise matches unlinked and reports candidates", () => {
    const reviewLibrary: Exercise[] = [
      ...library,
      { id: "incline", name: "Incline Dumbbell Press", muscle: "chest", equipment: "dumbbell", tracking: "weight_reps" },
    ];
    const decoded = decodePlans(JSON.stringify({
      name: "Review",
      days: [{ exercises: [{ name: "incline press" }] }],
    }), reviewLibrary);
    expect(decoded.plans[0].days[0].exercises[0]).toMatchObject({
      exerciseId: "",
      name: "incline press",
    });
    expect(decoded.result.unresolved).toBe(1);
    expect(decoded.result.warnings[0]).toMatch(/Review candidates: Incline Dumbbell Press/);
  });
  it("roundtrips Android relationships and honest unsupported photo warning", () => {
    const plans = decodePlans(JSON.stringify(raw), library).plans;
    const snap: AppSnapshot = {
      profile: defaultProfile,
      plans,
      exercises: library,
      workouts: [],
      measurements: [
        {
          id: "m",
          date: "2026-08-31",
          type: "bodyweight",
          value: 70,
          unit: "kg",
        },
      ],
      photos: [],
      checkins: [],
      gyms: [],
    };
    const encoded = encodeAndroidBackup(snap);
    expect(JSON.parse(encoded)).toMatchObject({
      type: "ironlog_watermelon_export",
      version: 1,
    });
    const decoded = decodeAndroidBackup(encoded);
    expect(decoded.snapshot.plans[0].days[0].exercises[0].exerciseId).toBe(
      "bench",
    );
    expect(decoded.snapshot.measurements[0].value).toBe(70);
    expect(decoded.counts.plans).toBe(1);
  });
  it("web archive preserves photo bytes, notes, all records and schema", async () => {
    const blob = new Blob(["private-test-only"], { type: "image/png" });
    const snapshot: AppSnapshot = {
      profile: defaultProfile,
      plans: [],
      workouts: [],
      exercises: library,
      measurements: [],
      photos: [{ id: "p", date: "2026-08-31", capturedAt: 42, notes: "test", blob }],
      checkins: [],
      gyms: [],
    };
    const zipped = await encodeWebBackup(snapshot);
    const decoded = await decodeWebBackup(zipped);
    expect(await decoded.photos[0].blob.text()).toBe("private-test-only");
    expect(decoded.photos[0].notes).toBe("test");
    expect(decoded.photos[0].capturedAt).toBe(42);
    expect(decoded.profile).toEqual(defaultProfile);
  });
  it("rejects unknown versions and malformed JSON before mutation", () => {
    expect(() => decodePlans("{", library)).toThrow();
    expect(() =>
      decodeAndroidBackup('{"type":"ironlog_watermelon_export","version":2}'),
    ).toThrow();
  });
  it("resolves exact catalogue aliases with native deterministic priority", () => {
    const parsed = decodePlans(
      '{"name":"Alias","days":[{"exercises":[{"name":"BB Bench"}]}]}',
      [{ ...library[0], aliases: ["BB Bench"] }],
    );
    expect(parsed.plans[0].days[0].exercises[0].exerciseId).toBe("bench");
    expect(parsed.result.unresolved).toBe(0);
  });
  it("rejects an empty-looking Android envelope instead of treating corruption as empty data", () => {
    expect(() =>
      decodeAndroidBackup('{"type":"ironlog_watermelon_export","version":1}'),
    ).toThrow("missing");
  });
  it("reports orphan rows rather than silently counting them as restored", () => {
    const snapshot: AppSnapshot = {
      profile: defaultProfile,
      plans: [],
      workouts: [],
      exercises: library,
      measurements: [],
      photos: [],
      checkins: [],
      gyms: [],
    };
    const data = JSON.parse(encodeAndroidBackup(snapshot));
    data.data.workout_sets = [{ id: "orphan", workout_exercise_id: "missing" }];
    const result = decodeAndroidBackup(JSON.stringify(data)).result;
    expect(result.skipped).toBe(1);
    expect(result.warnings.join(" ")).toContain("orphan");
  });
  it("preserves unresolved plan exercise names through Android relational backup", () => {
    const snapshot: AppSnapshot = {
      profile: defaultProfile,
      plans: decodePlans(JSON.stringify(raw), library).plans,
      workouts: [],
      exercises: library,
      measurements: [],
      photos: [],
      checkins: [],
      gyms: [],
    };
    const decoded = decodeAndroidBackup(encodeAndroidBackup(snapshot));
    expect(decoded.snapshot.plans[0].days[0].exercises[1].name).toBe(
      "Unlisted move",
    );
  });
  it("preserves native canonical duration independently of equal timestamps and credits three sets", () => {
    const at = Date.now() - 86400000;
    const data = {
      exercises: [
        {
          id: "bench",
          name: "Bench Press",
          primary_muscle: "chest",
          tracking_type: "weight_reps",
        },
      ],
      plans: [],
      plan_days: [],
      plan_exercises: [],
      workouts: [
        {
          id: "w",
          name: "Native import",
          started_at: at,
          completed_at: at,
          duration_seconds: 1800,
          status: "completed",
        },
      ],
      workout_exercises: [{ id: "we", workout_id: "w", exercise_id: "bench" }],
      workout_sets: [0, 1, 2].map((i) => ({
        id: `s${i}`,
        workout_exercise_id: "we",
        weight: 50,
        reps: 8,
        set_index: i,
      })),
    };
    const decoded = decodeAndroidBackup(
      JSON.stringify({ type: "ironlog_watermelon_export", version: 1, data }),
    );
    expect(decoded.snapshot.workouts[0].durationSeconds).toBe(1800);
    expect(workoutDurationSeconds(decoded.snapshot.workouts[0])).toBe(1800);
    expect(creditedProof(decoded.snapshot.workouts[0])).toBe(true);
    expect(
      JSON.parse(encodeAndroidBackup(decoded.snapshot)).data.workouts[0]
        .duration_seconds,
    ).toBe(1800);
  });
  it.each(["workout_exercises", "workout_sets"])(
    "rejects a full Android backup missing core %s",
    (section) => {
      const snapshot: AppSnapshot = {
        profile: defaultProfile,
        plans: [],
        workouts: [],
        exercises: [],
        measurements: [],
        photos: [],
        checkins: [],
        gyms: [],
      };
      const payload = JSON.parse(encodeAndroidBackup(snapshot));
      delete payload.data[section];
      expect(() => decodeAndroidBackup(JSON.stringify(payload))).toThrow(
        `missing: ${section}`,
      );
    },
  );
  it("keeps an ID-only unresolved exercise visible and valid", () => {
    const decoded = decodePlans(
      '{"name":"ID-only","days":[{"exercises":[{"exerciseId":"custom-42"}]}]}',
      [],
    );
    expect(decoded.plans[0].days[0].exercises[0].name).toBe("custom-42");
    expect(decoded.result.unresolved).toBe(1);
  });
  it.each(["plan_days", "plan_exercises"])(
    "rejects a full Android backup missing core %s",
    (section) => {
      const snapshot: AppSnapshot = {
        profile: defaultProfile,
        plans: [],
        workouts: [],
        exercises: [],
        measurements: [],
        photos: [],
        checkins: [],
        gyms: [],
      };
      const payload = JSON.parse(encodeAndroidBackup(snapshot));
      delete payload.data[section];
      expect(() => decodeAndroidBackup(JSON.stringify(payload))).toThrow(
        `missing: ${section}`,
      );
    },
  );
  it.each([undefined, null, [null], [{}]])(
    "rejects malformed photo manifest entries before normalizing (%j)",
    async (photos) => {
      const snapshot = {
        profile: defaultProfile,
        plans: [],
        workouts: [],
        exercises: [],
        measurements: [],
        photos,
        checkins: [],
        gyms: [],
      };
      const archive = zipSync({
        "manifest.json": strToU8(
          JSON.stringify({ type: "ironlog_web_backup", version: 1, snapshot }),
        ),
        "photos/p.bin": strToU8("bytes"),
      });
      await expect(decodeWebBackup(archive)).rejects.toThrow();
    },
  );
  it("rejects unreferenced photo bytes rather than silently dropping them", async () => {
    const snapshot = {
      profile: defaultProfile,
      plans: [],
      workouts: [],
      exercises: [],
      measurements: [],
      photos: [],
      checkins: [],
      gyms: [],
    };
    const archive = zipSync({
      "manifest.json": strToU8(
        JSON.stringify({ type: "ironlog_web_backup", version: 1, snapshot }),
      ),
      "photos/orphan.bin": strToU8("bytes"),
    });
    await expect(decodeWebBackup(archive)).rejects.toThrow(
      "Unreferenced photo",
    );
  });
  it("rejects malformed Android table rows rather than filtering them away", () => {
    const snapshot: AppSnapshot = {
      profile: defaultProfile,
      plans: [],
      workouts: [],
      exercises: [],
      measurements: [],
      photos: [],
      checkins: [],
      gyms: [],
    };
    const payload = JSON.parse(encodeAndroidBackup(snapshot));
    payload.data.workout_sets = [null];
    expect(() => decodeAndroidBackup(JSON.stringify(payload))).toThrow(
      "Invalid row",
    );
  });
  it("rejects a web extension that hides canonical Android workout history", () => {
    const snapshot: AppSnapshot = {
      profile: defaultProfile,
      plans: [],
      workouts: [],
      exercises: [],
      measurements: [],
      photos: [],
      checkins: [],
      gyms: [],
    };
    const payload = JSON.parse(encodeAndroidBackup(snapshot));
    payload.data.workouts = [
      {
        id: "w",
        name: "Missing extension entry",
        started_at: Date.now() - 10000,
        completed_at: Date.now(),
        status: "completed",
      },
    ];
    expect(() => decodeAndroidBackup(JSON.stringify(payload))).toThrow(
      "extension",
    );
  });
  it.each([
    { status: "mystery", started_at: 0 },
    { status: "completed", started_at: "invalid date" },
  ])(
    "rejects corrupt native lifecycle or clock instead of defaulting %j",
    (row) => {
      const snapshot: AppSnapshot = {
        profile: defaultProfile,
        plans: [],
        workouts: [],
        exercises: [],
        measurements: [],
        photos: [],
        checkins: [],
        gyms: [],
      };
      const payload = JSON.parse(encodeAndroidBackup(snapshot));
      delete payload.webExtension;
      payload.data.workouts = [
        { id: "w", name: "Corrupt", completed_at: 1, ...row },
      ];
      expect(() => decodeAndroidBackup(JSON.stringify(payload))).toThrow();
    },
  );
  it("warns explicitly for every missing non-core native data section", () => {
    const snapshot: AppSnapshot = {
      profile: defaultProfile,
      plans: [],
      workouts: [],
      exercises: [],
      measurements: [],
      photos: [],
      checkins: [],
      gyms: [],
    };
    const payload = JSON.parse(encodeAndroidBackup(snapshot));
    delete payload.data.body_measurements;
    delete payload.data.progress_photos;
    const warnings = decodeAndroidBackup(
      JSON.stringify(payload),
    ).result.warnings.join(" ");
    expect(warnings).toContain("missing body_measurements");
    expect(warnings).toContain("missing progress_photos");
  });
  it("rejects a web extension that hides canonical body measurements", () => {
    const snapshot: AppSnapshot = {
      profile: defaultProfile,
      plans: [],
      workouts: [],
      exercises: [],
      measurements: [
        {
          id: "m",
          date: "2026-08-31",
          type: "bodyweight",
          value: 70,
          unit: "kg",
        },
      ],
      photos: [],
      checkins: [],
      gyms: [],
    };
    const payload = JSON.parse(encodeAndroidBackup(snapshot));
    payload.webExtension.measurements = [];
    expect(() => decodeAndroidBackup(JSON.stringify(payload))).toThrow(
      "extension",
    );
  });
});
