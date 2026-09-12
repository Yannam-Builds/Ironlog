import {
  readFileSync,
  writeFileSync,
  mkdirSync,
  copyFileSync,
  readdirSync,
} from "node:fs";
import { resolve, dirname, basename } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";

export function nativeLogoSvg(xml) {
  const paths = [...xml.matchAll(/android:pathData="([^"]+)"/g)];
  if (paths.length !== 1 || !/android:fillColor="#FFFFFF"/.test(xml))
    throw Error("Review changed native monochrome logo before converting");
  const d = paths[0][1];
  if (!/^[MLZmlz0-9. ,\-]+$/.test(d))
    throw Error("Unsupported logo path syntax");
  // Original native vector geometry, framed for a browser icon rather than Android adaptive-icon padding.
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="120 112 1014 1014"><path fill="#FFFFFF" fill-rule="evenodd" d="${d}"/></svg>\n`;
}

// Balanced Kotlin constructor reader. Quoted parentheses are content, not syntax.
function constructors(source, name) {
  const result = [];
  const regex = new RegExp(`\\b${name}\\s*\\(`, "g");
  let m;
  while ((m = regex.exec(source))) {
    const start = regex.lastIndex;
    let depth = 1,
      quote = false,
      escaped = false,
      end = start;
    for (; end < source.length; end++) {
      const c = source[end];
      if (quote) {
        if (escaped) escaped = false;
        else if (c === "\\") escaped = true;
        else if (c === '"') quote = false;
      } else if (c === '"') quote = true;
      else if (c === "(") depth++;
      else if (c === ")" && !--depth) break;
    }
    if (depth) throw Error(`Unclosed ${name}`);
    result.push(source.slice(start, end));
    regex.lastIndex = end + 1;
  }
  return result;
}
const stringField = (body, key, fallback = "") => {
  const m = body.match(new RegExp(`\\b${key}\\s*=\\s*("(?:[^"\\\\]|\\\\.)*")`));
  return m ? JSON.parse(m[1]) : fallback;
};
const numberField = (body, key, fallback) =>
  Number(body.match(new RegExp(`\\b${key}\\s*=\\s*(\\d+)`))?.[1] ?? fallback);
export function parseThemes(source) {
  return Object.fromEntries(
    constructors(source, "IronLogThemeTokens")
      .filter((b) => stringField(b, "name"))
      .map((body) => {
        const colors = {};
        for (const match of body.matchAll(
          /(\w+)\s*=\s*Color(?:\(0x([A-Fa-f0-9]{8})\)|\.(White|Black))/g,
        )) {
          const [, key, hex, named] = match;
          colors[key] = named
            ? named === "White"
              ? "#FFFFFF"
              : "#000000"
            : `#${hex.slice(2)}${hex.slice(0, 2).toUpperCase() === "FF" ? "" : hex.slice(0, 2)}`;
        }
        return [stringField(body, "name").toLowerCase(), colors];
      }),
  );
}
export function parseTemplates(source) {
  return constructors(source, "ProgramTemplate")
    .filter((b) => stringField(b, "id"))
    .map((body, order) => {
      const id = stringField(body, "id");
      return {
        id,
        templateId: id,
        name: stringField(body, "name"),
        description: stringField(body, "description"),
        goal: stringField(body, "category"),
        order,
        days: constructors(body, "FullPlanDay").map((day, di) => {
          const strings = [...day.matchAll(/"(?:[^"\\]|\\.)*"/g)]
            .slice(0, 2)
            .map((m) => JSON.parse(m[0]));
          return {
            id: `${id}-${di}`,
            name: strings[0],
            color: strings[1],
            exercises: constructors(day, "PlanExerciseInput").map((ex, ei) => ({
              id: `${id}-${di}-${ei}`,
              exerciseId: stringField(ex, "exerciseId"),
              name: stringField(ex, "name"),
              sets: numberField(ex, "sets", 3),
              reps: stringField(ex, "reps", "8"),
              restSeconds: numberField(ex, "restSeconds", 90),
              notes: stringField(ex, "notes"),
              supersetGroup: stringField(ex, "supersetGroup"),
              isWarmup: /isWarmup\s*=\s*true/.test(ex),
            })),
          };
        }),
      };
    });
}
function extract(source) {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
  const manifest = {
    revision: execFileSync("git", ["-C", source, "rev-parse", "HEAD"], {
      encoding: "utf8",
    }).trim(),
    reference: "Native working tree (file hashes are authoritative)",
    files: [],
  };
  const read = (path) => {
    const bytes = readFileSync(resolve(source, path));
    manifest.files.push({
      path,
      sha256: createHash("sha256").update(bytes).digest("hex"),
    });
    return bytes;
  };
  const output = (path, value) => {
    const full = resolve(root, path);
    mkdirSync(dirname(full), { recursive: true });
    writeFileSync(full, JSON.stringify(value, null, 2) + "\n");
  };
  const prefix = "app/src/main/";
  const themes = parseThemes(
    read(prefix + "java/com/ironlog/app/ui/theme/IronLogThemes.kt").toString(),
  );
  if (
    Object.keys(themes).length !== 12 ||
    Object.values(themes).some((t) => Object.keys(t).length !== 24)
  )
    throw Error("Native theme contract changed");
  output("src/generated/themes.json", themes);
  const raw = JSON.parse(read(prefix + "assets/exerciseLibrary.json"));
  const exercises = raw.exercises.map((e) => ({
    id: e.id,
    name: e.name,
    aliases: e.aliases ?? [],
    muscle: e.primaryMuscle ?? "",
    equipment: e.equipment ?? "",
    secondaryMuscles: e.secondaryMuscles ?? [],
    tracking:
      e.trackingType === "duration_distance"
        ? "duration_distance"
        : e.trackingType === "duration"
          ? "duration"
          : e.isBodyweight && !e.requiresExternalLoad
            ? "bodyweight_reps"
            : "weight_reps",
  }));
  output("public/data/exercises.json", exercises);
  const norm = (s) => s.toLowerCase().replace(/[^a-z0-9]/g, "");
  const lookup = new Map(
    raw.exercises.flatMap((e) =>
      [e.name, ...(e.aliases ?? [])].map((n) => [norm(n), e.id]),
    ),
  );
  const templates = parseTemplates(
    read(
      prefix + "java/com/ironlog/app/data/seed/ProgramTemplates.kt",
    ).toString(),
  );
  for (const p of templates)
    for (const d of p.days)
      for (const e of d.exercises)
        e.exerciseId = lookup.get(norm(e.name)) ?? "";
  output("src/generated/templates.json", templates);
  output(
    "src/generated/body-map.json",
    JSON.parse(read(prefix + "assets/ironlog/body_map_paths.json")),
  );
  // drawable-nodpi contains the canonical, density-independent artwork used
  // by Compose. Export the complete set so web screens can select the same
  // mascot pose, badge and grade art instead of maintaining approximations.
  const art = prefix + "res/drawable-nodpi/";
  const artwork = readdirSync(resolve(source, art)).filter((n) => n.endsWith(".png"));
  const fonts = readdirSync(resolve(source, prefix + "res/font/")).filter((n) =>
    n.endsWith(".ttf"),
  );
  const paths = [
    ...artwork.map((n) => art + n),
    ...fonts.map((n) => prefix + "res/font/" + n),
  ];
  for (const path of paths) {
    read(path);
    const name = basename(path);
    const target = resolve(
      root,
      path.includes("/res/font/") && name !== "lexend_variable.ttf"
        ? "public/fonts"
        : /^ic_badge_/.test(name)
          ? "public/assets/badges"
          : "public/assets",
      name,
    );
    mkdirSync(dirname(target), { recursive: true });
    copyFileSync(resolve(source, path), target);
  }
  const logo = nativeLogoSvg(
    read(prefix + "res/drawable/ic_launcher_foreground.xml").toString(),
  );
  writeFileSync(resolve(root, "public/assets/ironlog-logo.svg"), logo);
  output("src/generated/native-provenance.json", manifest);
  console.log(
    `Extracted ${Object.keys(themes).length} themes, ${templates.length} templates, ${exercises.length} exercises, ${artwork.length} artwork files and ${fonts.length} native fonts. No user data read.`,
  );
}
if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const arg = process.argv.indexOf("--source");
  if (arg < 0 || !process.argv[arg + 1])
    throw Error("Pass --source <native repository>");
  extract(resolve(process.argv[arg + 1]));
}
