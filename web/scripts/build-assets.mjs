import sharp from "sharp";
import { readdir, mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
const root = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "../public/assets",
);
await mkdir(resolve(root, "optimized"), { recursive: true });
for (const name of await readdir(root)) {
  if (/^(forgefox_|iron_grade_|recovery_circuit_|ic_forge_).*\.png$/.test(name))
    await sharp(resolve(root, name))
      .resize({
        width: 512,
        height: 512,
        fit: "inside",
        withoutEnlargement: true,
      })
      .webp({ quality: 86, alphaQuality: 100 })
      .toFile(resolve(root, "optimized", name.replace(/\.png$/, ".webp")));
}
for (const size of [180, 192, 512])
  await sharp(resolve(root, "ironlog-logo.svg"))
    .resize(size, size)
    .flatten({ background: "#000000" })
    .png()
    .toFile(resolve(root, `icon-${size}.png`));
const web = resolve(root, "../..");
const notices = resolve(web, "public/licenses");
await mkdir(notices, { recursive: true });
const packagedLicense = resolve(notices, "IronLog-LICENSE.txt");
const licenseBytes = process.env.VERCEL
  ? await readFile(packagedLicense)
  : await readFile(resolve(web, "../LICENSE"));
await writeFile(
  packagedLicense,
  licenseBytes,
);
const packages = [
  "react",
  "react-dom",
  "scheduler",
  "dexie",
  "dexie-react-hooks",
  "zod",
  "fflate",
  "vite-plugin-pwa",
  ...(await readdir(resolve(web, "node_modules"))).filter((name) =>
    name.startsWith("workbox-"),
  ),
];
const texts = await Promise.all(
  packages.map(async (name) => {
    const folder = resolve(web, "node_modules", name);
    const pkg = JSON.parse(
      await readFile(resolve(folder, "package.json"), "utf8"),
    );
    const license = (await readdir(folder)).find((file) =>
      /^LICENSE(?:\.txt|\.md)?$/i.test(file),
    );
    if (!license) throw Error(`Review missing license for ${name}`);
    return `${name} ${pkg.version}\n${await readFile(resolve(folder, license), "utf8")}`;
  }),
);
const exerciseDataNotices = await readFile(
  resolve(web, "../app/src/main/assets/third_party_notices.txt"),
  "utf8",
);
await writeFile(
  resolve(notices, "third-party-notices.txt"),
  `${texts.join("\n\n---\n\n")}\n\n---\n\n${exerciseDataNotices}`,
);
console.log("Generated public PWA icons and mobile-sized native artwork.");
