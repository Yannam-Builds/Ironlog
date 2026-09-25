import type { Photo } from "./types";

export function validateProgressPhoto(file?: File) {
  if (!file) return undefined;
  if (file.size > 15_000_000) throw Error("Choose an image smaller than 15 MB");
  if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) throw Error("Choose JPEG, PNG or WebP");
  return file;
}

export type DateSelection = [string | undefined, string | undefined];
export function updateDateCompareSelection(selectedA: string | undefined, selectedB: string | undefined, tappedDate: string): DateSelection {
  if (!selectedA) return [tappedDate, undefined];
  if (selectedA === tappedDate && !selectedB) return [undefined, undefined];
  if (selectedA === tappedDate) return [selectedB, undefined];
  if (selectedB === tappedDate) return [selectedA, undefined];
  if (!selectedB) return [selectedA, tappedDate];
  return [tappedDate, undefined];
}

const latestForDate = (photos: Photo[], date?: string) => date ? photos.filter((photo) => photo.date === date).sort((a, b) => (b.capturedAt ?? 0) - (a.capturedAt ?? 0) || b.id.localeCompare(a.id))[0] : undefined;
export function resolveDateComparePhotos(photos: Photo[], selectedA?: string, selectedB?: string): [Photo | undefined, Photo | undefined] {
  return [latestForDate(photos, selectedA), latestForDate(photos, selectedB)];
}

export type PhotoViewerState = { kind: "single"; ids: [string] } | { kind: "compare"; ids: [string, string] };
export function resolveViewerPhotos(state: PhotoViewerState, photos: Photo[]) {
  const resolved = state.ids.map((id) => photos.find((photo) => photo.id === id));
  return resolved.some((photo) => !photo) ? [] : resolved as Photo[];
}

export type ViewerDismissal = "close" | "confirm-discard" | "wait-for-save";
export function viewerDismissal(savedNote: string, draft: string, saving: boolean): ViewerDismissal {
  return saving ? "wait-for-save" : savedNote !== draft ? "confirm-discard" : "close";
}
