import { expect, it } from "vitest";
import { resolveDateComparePhotos, updateDateCompareSelection, resolveViewerPhotos, validateProgressPhoto, viewerDismissal } from "../src/domain/photo-selection";
import type { Photo } from "../src/domain/types";

const photo = (id: string, date: string, capturedAt: number): Photo => ({ id, date, capturedAt, notes: "", blob: new Blob([id], { type: "image/jpeg" }) });

it("matches native two-date selection transitions", () => {
  expect(updateDateCompareSelection(undefined, undefined, "2026-09-01")).toEqual(["2026-09-01", undefined]);
  expect(updateDateCompareSelection("2026-09-01", undefined, "2026-09-02")).toEqual(["2026-09-01", "2026-09-02"]);
  expect(updateDateCompareSelection("2026-09-01", "2026-09-02", "2026-09-03")).toEqual(["2026-09-03", undefined]);
  expect(updateDateCompareSelection("2026-09-01", undefined, "2026-09-01")).toEqual([undefined, undefined]);
});

it("chooses the latest photo deterministically when a date has several", () => {
  const early = photo("early", "2026-09-01", 1), late = photo("late", "2026-09-01", 2), after = photo("after", "2026-09-02", 3);
  expect(resolveDateComparePhotos([late, after, early], "2026-09-01", "2026-09-02").map((item) => item?.id)).toEqual(["late", "after"]);
  expect(resolveViewerPhotos({ kind: "compare", ids: ["late", "after"] }, [early, late, after]).map((item) => item.id)).toEqual(["late", "after"]);
  expect(resolveViewerPhotos({ kind: "compare", ids: ["late", "missing"] }, [late])).toEqual([]);
});

it("guards viewer notes from accidental dismissal", () => {
  expect(viewerDismissal("same", "same", false)).toBe("close");
  expect(viewerDismissal("saved", "draft", false)).toBe("confirm-discard");
  expect(viewerDismissal("saved", "draft", true)).toBe("wait-for-save");
});

it("accepts supported photos, ignores a cancelled picker and rejects unsafe input", () => {
  expect(validateProgressPhoto(undefined)).toBeUndefined();
  const jpeg = new File(["photo"], "photo.jpg", { type: "image/jpeg" });
  expect(validateProgressPhoto(jpeg)).toBe(jpeg);
  expect(() => validateProgressPhoto(new File(["text"], "notes.txt", { type: "text/plain" }))).toThrow("JPEG, PNG or WebP");
  const oversized = new File(["x"], "large.jpg", { type: "image/jpeg" });
  Object.defineProperty(oversized, "size", { value: 15_000_001 });
  expect(() => validateProgressPhoto(oversized)).toThrow("smaller than 15 MB");
});
