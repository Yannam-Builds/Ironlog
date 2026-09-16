import { useEffect, useState } from "react";
import { savePhoto } from "../../data/store";
import { resolveViewerPhotos, viewerDismissal, type PhotoViewerState } from "../../domain/photo-selection";
import type { Photo } from "../../domain/types";
import { Button, Field, Sheet } from "../../ui/components";
import { useApp } from "../../ui/context";

function usePhotoUrl(photo: Photo) {
  const [url, setUrl] = useState("");
  useEffect(() => { const next = URL.createObjectURL(photo.blob); setUrl(next); return () => URL.revokeObjectURL(next); }, [photo.blob]);
  return url;
}

export function PhotoImage({ photo, onClick }: { photo: Photo; onClick?: () => void }) {
  const url = usePhotoUrl(photo);
  return <figure className="progress-photo"><button onClick={onClick} disabled={!onClick}>{url && <img src={url} alt={`Progress from ${photo.date}`} />}</button><figcaption>{photo.date}{photo.notes ? ` · ${photo.notes}` : ""}</figcaption></figure>;
}

function CompareStage({ before, after, mix }: { before: Photo; after: Photo; mix: number }) {
  const beforeUrl = usePhotoUrl(before), afterUrl = usePhotoUrl(after);
  return <div className="photo-compare-stage">{beforeUrl && <img src={beforeUrl} alt={`Before photo from ${before.date}`} />}{afterUrl && <img className="after" style={{ clipPath: `inset(0 ${(1 - mix) * 100}% 0 0)` }} src={afterUrl} alt={`After photo from ${after.date}`} />}</div>;
}

export function PhotoViewer({ state, photos, onClose }: { state: PhotoViewerState; photos: Photo[]; onClose: () => void }) {
  const { run, busy } = useApp(); const resolved = resolveViewerPhotos(state, photos), primary = resolved[0];
  const [note, setNote] = useState(primary?.notes ?? ""), [compareMix, setCompareMix] = useState(.5), [discard, setDiscard] = useState(false);
  const close = () => { const action = viewerDismissal(primary?.notes ?? "", note, busy); if (action === "close") onClose(); else if (action === "confirm-discard") setDiscard(true); };
  if (!resolved.length) return null;
  return <div className="photo-viewer" role="dialog" aria-modal="true" aria-label={state.kind === "compare" ? "Compare progress photos" : "Progress photo viewer"}><header><strong>{state.kind === "compare" ? "BEFORE / AFTER" : "PHOTO VIEWER"}</strong><Button variant="ghost" disabled={busy} onClick={close}>Close</Button></header><div className={`photo-viewer-stage ${state.kind}`}>{state.kind === "compare" ? <CompareStage before={resolved[0]} after={resolved[1]} mix={compareMix} /> : <PhotoImage photo={primary} />}</div>{state.kind === "compare" ? <><input aria-label="Before and after mix" type="range" min="0" max="1" step="0.01" value={compareMix} onChange={(event) => setCompareMix(Number(event.target.value))} /><p className="photo-compare-labels"><span>Before: {resolved[0].date}</span><span>After: {resolved[1].date}</span></p></> : <><p>{primary.date}</p><Field label="Photo notes"><textarea value={note} onChange={(event) => setNote(event.target.value)} /></Field><Button disabled={busy || note === primary.notes} onClick={() => void run(() => savePhoto({ ...primary, notes: note.trim() }), "Photo note saved")}>Save note</Button></>}{discard && <Sheet title="Discard note changes?" onClose={() => setDiscard(false)}><p>Your unsaved note will be lost.</p><Button variant="danger" onClick={onClose}>Discard</Button><Button variant="secondary" onClick={() => setDiscard(false)}>Keep editing</Button></Sheet>}</div>;
}
