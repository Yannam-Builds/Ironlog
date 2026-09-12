import { readdir, readFile } from "node:fs/promises";
import { resolve, extname, relative, dirname } from "node:path";
import { fileURLToPath } from "node:url";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "../dist");
async function walk(path) {
  return (
    await Promise.all(
      (await readdir(path, { withFileTypes: true })).map((e) =>
        e.isDirectory() ? walk(resolve(path, e.name)) : [resolve(path, e.name)],
      ),
    )
  ).flat();
}
const files = await walk(root);
const forbiddenNames =
  /backup_2026|codex-clipboard|local\.properties|\.jks$|\.apk$|\.aab$|\.db$|\.sqlite|\.map$|fixture|seed_private|pasted-text/i;
for (const file of files) {
  if (forbiddenNames.test(relative(root, file)))
    throw Error(`Forbidden output: ${relative(root, file)}`);
  if ([".js", ".json", ".html", ".css"].includes(extname(file))) {
    const text = await readFile(file, "utf8");
    if (
      /[A-Z]:\\\\Users\\\\|\bR5[A-Z0-9]{9}\b|sk-proj-[a-zA-Z0-9]{20}|BEGIN PRIVATE KEY/.test(
        text,
      )
    )
      throw Error(`Private-looking content: ${relative(root, file)}`);
  }
}
for (const required of [
  "index.html",
  "app/index.html",
  "manifest.webmanifest",
  "sw.js",
  "data/exercises.json",
  "assets/icon-180.png",
  "assets/icon-512.png",
])
  if (!files.includes(resolve(root, required)))
    throw Error(`Missing ${required}`);
const manifest = JSON.parse(
  await readFile(resolve(root, "manifest.webmanifest"), "utf8"),
);
const expectedBase = process.env.VERCEL ? "/" : "/Ironlog/";
if (manifest.start_url !== `${expectedBase}app/` || manifest.scope !== expectedBase)
  throw Error(`Incorrect public base; expected ${expectedBase}`);
console.log(
  `Verified ${files.length} output files: expected app entries, PWA icons, base paths, and no known private fixture/secret patterns. This is a scoped check, not a secret-scanner guarantee.`,
);
