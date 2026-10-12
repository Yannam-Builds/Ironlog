import { download } from "./context";
export async function shareCard(title: string, lines: string[], filename: string) {
  const canvas = document.createElement("canvas"); canvas.width = 1080; canvas.height = 340 + lines.length * 72;
  const ctx = canvas.getContext("2d"); if (!ctx) throw Error("Image sharing is unavailable in this browser.");
  ctx.fillStyle = "#111211"; ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = "#ff8758"; ctx.font = "bold 32px sans-serif"; ctx.fillText("IRONLOG / TRAINING RECORD", 64, 80);
  ctx.fillStyle = "#fafafa"; ctx.font = "bold 48px sans-serif"; ctx.fillText(title, 64, 164, 950);
  ctx.font = "32px sans-serif"; ctx.fillStyle = "#bec2bc";
  lines.forEach((line, index) => ctx.fillText(line, 64, 250 + index * 72, 950));
  ctx.fillStyle = "#ff8758"; ctx.fillRect(64, canvas.height - 60, 140, 4);
  const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(value => value ? resolve(value) : reject(Error("Could not create the share card.")), "image/png"));
  const file = new File([blob], filename, { type: "image/png" });
  if (navigator.share && navigator.canShare?.({ files: [file] })) {
    try { await navigator.share({ title, files: [file] }); return; }
    catch (error) { if (error instanceof DOMException && error.name === "AbortError") return; }
  }
  download(blob, filename);
}
