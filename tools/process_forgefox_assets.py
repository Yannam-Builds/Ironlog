#!/usr/bin/env python3
"""
Process Forge Fox mascot images into Android-ready transparent PNG assets.

Inputs are read from Z:\\KOTLIN\\Mascot by default. Outputs are written into this
Kotlin project without modifying the original source images.
"""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
from dataclasses import dataclass
from difflib import SequenceMatcher
from pathlib import Path
from typing import Iterable

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

try:
    from rembg import remove as rembg_remove
except Exception:  # pragma: no cover - handled at runtime for local tooling
    rembg_remove = None


SUPPORTED_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"}
MASTER_SIZE = 2048
RUNTIME_SIZE = 1024
FALLBACK_SIZE = 512
TARGET_MAX_DIMENSION = 1750
RUNTIME_MIN_MARGIN = 32


@dataclass(frozen=True)
class ExpressionSpec:
    index: int
    slug: str
    display_name: str
    category: str
    source_aliases: tuple[str, ...]

    @property
    def resource_id(self) -> str:
        return f"forgefox_{self.index:02d}_{self.slug}"

    @property
    def filename(self) -> str:
        return f"{self.resource_id}.png"

    @property
    def enum_name(self) -> str:
        return "".join(part.capitalize() for part in self.slug.split("_"))


EXPRESSIONS: tuple[ExpressionSpec, ...] = (
    ExpressionSpec(1, "neutral", "Neutral", "core", ("neutral idle", "neutral", "idle")),
    ExpressionSpec(2, "smile", "Smile", "core", ("smile",)),
    ExpressionSpec(3, "big_happy", "Big Happy", "emotion", ("big happy", "big and happy", "happy")),
    ExpressionSpec(4, "excited", "Excited", "emotion", ("excited",)),
    ExpressionSpec(5, "laughing", "Laughing", "emotion", ("laughing", "laugh")),
    ExpressionSpec(6, "wink_teasing", "Wink / Teasing", "emotion", ("wink teasing", "wink", "teasing")),
    ExpressionSpec(7, "determined", "Determined", "fitness", ("determined",)),
    ExpressionSpec(8, "serious", "Serious", "fitness", ("serious",)),
    ExpressionSpec(9, "angry_coach", "Angry Coach", "warning", ("angry coach", "angry")),
    ExpressionSpec(10, "disappointed", "Disappointed", "warning", ("disappointed",)),
    ExpressionSpec(11, "sad", "Sad", "emotion", ("sad",)),
    ExpressionSpec(12, "exhausted", "Exhausted", "recovery", ("exhausted",)),
    ExpressionSpec(13, "sleepy", "Sleepy", "recovery", ("sleepy",)),
    ExpressionSpec(14, "shocked", "Shocked", "warning", ("shocked",)),
    ExpressionSpec(15, "confused", "Confused", "warning", ("confused",)),
    ExpressionSpec(16, "proud", "Proud", "reward", ("proud",)),
    ExpressionSpec(17, "flexing", "Flexing", "fitness", ("flexing", "flex")),
    ExpressionSpec(18, "fist_pump", "Fist Pump", "reward", ("fist pump", "fist")),
    ExpressionSpec(19, "pointing_forward", "Pointing Forward", "widget", ("pointing forward", "pointing")),
    ExpressionSpec(20, "clipboard", "Clipboard", "widget", ("holding clipboard todays plan", "holding clipboard", "clipboard", "today plan")),
    ExpressionSpec(21, "water_bottle", "Water Bottle", "widget", ("holding water bottle", "water bottle", "bottle")),
    ExpressionSpec(22, "dumbbell", "Dumbbell", "fitness", ("lifting dumbbell", "dumbbell")),
    ExpressionSpec(23, "pull_up", "Pull Up", "fitness", ("pullup", "pull up")),
    ExpressionSpec(24, "tired_towel", "Tired Towel", "recovery", ("sitting tired with towel", "tired towel", "towel")),
    ExpressionSpec(25, "trophy_medal", "Trophy Medal", "reward", ("pr trophy medal", "trophy medal", "medal", "trophy")),
    ExpressionSpec(26, "streak_fire", "Streak Fire", "reward", ("streak fire pose", "streak fire", "fire")),
    ExpressionSpec(27, "repair_streak", "Repair Streak", "recovery", ("repair streak injured or worn out pose", "repair streak", "injured", "worn out")),
    ExpressionSpec(28, "rest_blanket", "Rest Blanket", "recovery", ("rest day under the blanket", "rest blanket", "blanket")),
    ExpressionSpec(29, "checking_watch", "Checking Watch", "warning", ("late reminder checking watch", "checking watch", "watch")),
    ExpressionSpec(30, "coach_arms_crossed", "Coach Arms Crossed", "widget", ("coach mode arms crossed", "arms crossed", "coach")),
    ExpressionSpec(31, "supportive_failure", "Supportive Failure", "recovery", ("failure missed target but supportive", "supportive failure", "failure")),
    ExpressionSpec(32, "level_up", "Level Up", "reward", ("transformation level up glowing pose", "level up", "transformation")),
)


SOURCE_OVERRIDES = {
    "forgefox_01_neutral.png": "Neutral Idle.png",
    # The provided folder does not include a standalone Sad.png. This closest
    # single-expression source avoids synthesizing or altering the mascot.
    "forgefox_11_sad.png": "Disappointed.png",
}


def normalized_name(value: str) -> str:
    value = value.lower()
    value = value.replace("’", "").replace("'", "")
    value = re.sub(r"[^a-z0-9]+", " ", value)
    return re.sub(r"\s+", " ", value).strip()


def android_valid_name(name: str) -> bool:
    return re.fullmatch(r"[a-z][a-z0-9_]*", name) is not None


def source_images(source_dir: Path) -> list[Path]:
    return sorted(p for p in source_dir.iterdir() if p.is_file() and p.suffix.lower() in SUPPORTED_EXTENSIONS)


def score_match(source_stem: str, aliases: Iterable[str]) -> float:
    stem = normalized_name(source_stem)
    scores: list[float] = []
    for alias in aliases:
        alias_norm = normalized_name(alias)
        if stem == alias_norm:
            scores.append(1.0)
        elif alias_norm and alias_norm in stem:
            scores.append(0.96)
        elif stem and stem in alias_norm:
            scores.append(0.92)
        else:
            scores.append(SequenceMatcher(None, stem, alias_norm).ratio())
    return max(scores) if scores else 0.0


def build_mapping(files: list[Path], mapping_review_path: Path) -> dict[ExpressionSpec, Path]:
    remaining = set(files)
    mapping: dict[ExpressionSpec, Path] = {}
    review: list[dict[str, object]] = []

    for spec in EXPRESSIONS:
        override_name = SOURCE_OVERRIDES.get(spec.filename)
        if override_name:
            override_path = next((p for p in files if p.name == override_name), None)
            if override_path is None:
                review.append(
                    {
                        "target": spec.filename,
                        "issue": "missing_override_source",
                        "expected": override_name,
                    }
                )
                continue
            mapping[spec] = override_path
            remaining.discard(override_path)
            continue

        candidates = sorted(
            ((score_match(path.stem, spec.source_aliases), path) for path in remaining),
            key=lambda item: item[0],
            reverse=True,
        )
        if not candidates:
            review.append({"target": spec.filename, "issue": "missing_source", "candidates": []})
            continue
        best_score, best_path = candidates[0]
        second_score = candidates[1][0] if len(candidates) > 1 else 0.0
        if best_score < 0.70 or (best_score - second_score) < 0.04:
            review.append(
                {
                    "target": spec.filename,
                    "issue": "uncertain_match",
                    "best": best_path.name,
                    "bestScore": round(best_score, 3),
                    "candidates": [
                        {"file": path.name, "score": round(score, 3)}
                        for score, path in candidates[:5]
                    ],
                }
            )
            continue
        mapping[spec] = best_path
        remaining.remove(best_path)

    if review or remaining:
        mapping_review_path.parent.mkdir(parents=True, exist_ok=True)
        mapping_review_path.write_text(
            json.dumps(
                {
                    "warnings": review,
                    "unusedSources": [p.name for p in sorted(remaining)],
                    "mapped": {spec.filename: path.name for spec, path in mapping.items()},
                },
                indent=2,
            ),
            encoding="utf-8",
        )
        if review:
            print(f"WARNING: uncertain source mapping. Review {mapping_review_path}", file=sys.stderr)
    return mapping


def remove_background(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    if rembg_remove is not None:
        try:
            return rembg_remove(rgba).convert("RGBA")
        except Exception as exc:
            print(f"WARNING: rembg failed, using dark-background fallback: {exc}", file=sys.stderr)
    return dark_background_fallback(rgba)


def dark_background_fallback(image: Image.Image) -> Image.Image:
    arr = np.asarray(image.convert("RGBA")).astype(np.float32)
    rgb = arr[:, :, :3]
    brightness = rgb.mean(axis=2)
    max_channel = rgb.max(axis=2)
    saturation = max_channel - rgb.min(axis=2)
    alpha = np.where(
        (brightness < 28) & (saturation < 22),
        0,
        np.where(brightness < 54, np.clip((brightness - 28) / 26, 0, 1) * 255, 255),
    )
    arr[:, :, 3] = np.minimum(arr[:, :, 3], alpha)
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")


def refine_alpha_and_decontaminate(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A")
    alpha = alpha.filter(ImageFilter.MedianFilter(size=3))
    alpha = alpha.filter(ImageFilter.GaussianBlur(radius=0.35))

    arr = np.asarray(rgba).astype(np.float32)
    a = np.asarray(alpha).astype(np.float32)
    rgb = arr[:, :, :3]

    # Edge decontamination: lighten black-contaminated semi-transparent pixels
    # using nearby opaque mascot color. This reduces black halos without changing
    # the mascot's solid dark outfit/shoes.
    semi = (a > 4) & (a < 230)
    dark_edge = semi & (rgb.mean(axis=2) < 70)
    if np.any(dark_edge):
        opaque = Image.fromarray(np.where(a[:, :, None] > 220, rgb, 0).astype(np.uint8), "RGB")
        expanded = opaque.filter(ImageFilter.GaussianBlur(radius=2.2))
        expanded_arr = np.asarray(expanded).astype(np.float32)
        rgb[dark_edge] = np.maximum(rgb[dark_edge], expanded_arr[dark_edge] * 0.65)

    arr[:, :, :3] = rgb
    arr[:, :, 3] = a
    refined = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")
    return refined


def alpha_bounds(image: Image.Image, threshold: int = 8) -> tuple[int, int, int, int] | None:
    alpha = np.asarray(image.getchannel("A"))
    ys, xs = np.where(alpha > threshold)
    if len(xs) == 0 or len(ys) == 0:
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def compose_square(image: Image.Image, canvas_size: int) -> Image.Image:
    bounds = alpha_bounds(image)
    if bounds is None:
        raise ValueError("empty alpha mask after background removal")
    trimmed = image.crop(bounds)
    width, height = trimmed.size
    scale = min(TARGET_MAX_DIMENSION / max(width, height), (canvas_size - 220) / width, (canvas_size - 220) / height)
    target = (max(1, int(round(width * scale))), max(1, int(round(height * scale))))
    resized = trimmed.resize(target, Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    x = (canvas_size - target[0]) // 2
    y = (canvas_size - target[1]) // 2
    canvas.alpha_composite(resized, (x, y))
    return canvas


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)


def checkerboard(size: tuple[int, int], cell: int = 24) -> Image.Image:
    width, height = size
    img = Image.new("RGB", size, (236, 236, 236))
    draw = ImageDraw.Draw(img)
    for y in range(0, height, cell):
        for x in range(0, width, cell):
            if ((x // cell) + (y // cell)) % 2:
                draw.rectangle((x, y, x + cell - 1, y + cell - 1), fill=(205, 205, 205))
    return img


def generate_contact_sheet(fallback_dir: Path, output_path: Path) -> None:
    cols, rows = 4, 8
    tile, label_h, pad = 300, 44, 24
    sheet_w = cols * tile + (cols + 1) * pad
    sheet_h = rows * (tile + label_h) + (rows + 1) * pad
    sheet = checkerboard((sheet_w, sheet_h), cell=20).convert("RGBA")
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("arial.ttf", 18)
    except Exception:
        font = ImageFont.load_default()

    for i, spec in enumerate(EXPRESSIONS):
        row, col = divmod(i, cols)
        x = pad + col * (tile + pad)
        y = pad + row * (tile + label_h + pad)
        img = Image.open(fallback_dir / spec.filename).convert("RGBA")
        preview = img.resize((tile, tile), Image.Resampling.LANCZOS)
        sheet.alpha_composite(preview, (x, y))
        label = spec.resource_id
        draw.rectangle((x, y + tile, x + tile, y + tile + label_h), fill=(20, 20, 20, 210))
        draw.text((x + 8, y + tile + 10), label, fill=(255, 255, 255, 255), font=font)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGBA").save(output_path, "PNG", optimize=True)


def generate_manifest(source_dir: Path, manifest_path: Path) -> None:
    manifest = {
        "version": 1,
        "character": "Forge Fox",
        "app": "IRONLOG",
        "assetType": "mascot_expression_set",
        "background": "transparent",
        "runtimeDrawableSize": RUNTIME_SIZE,
        "masterSize": MASTER_SIZE,
        "fallbackSize": FALLBACK_SIZE,
        "sourceFolder": str(source_dir),
        "expressions": [
            {
                "index": spec.index,
                "id": spec.resource_id,
                "name": spec.display_name,
                "category": spec.category,
                "runtimeDrawable": f"R.drawable.{spec.resource_id}",
                "master2048": f"app/src/main/assets/forgefox/transparent/2048/{spec.filename}",
                "fallback512": f"app/src/main/assets/forgefox/transparent/512/{spec.filename}",
            }
            for spec in EXPRESSIONS
        ],
    }
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")


def generate_kotlin_enum(path: Path) -> None:
    constants = []
    for spec in EXPRESSIONS:
        suffix = "," if spec.index < len(EXPRESSIONS) else ";"
        constants.append(
            f"""    {spec.enum_name}(
        id = "{spec.resource_id}",
        displayName = "{spec.display_name}",
        category = "{spec.category}",
        drawableRes = R.drawable.{spec.resource_id}
    ){suffix}"""
        )
    content = """package com.ironlog.assets

import androidx.annotation.DrawableRes
import com.ironlog.app.R

enum class ForgeFoxExpression(
    val id: String,
    val displayName: String,
    val category: String,
    @DrawableRes val drawableRes: Int
) {
%s

    companion object {
        fun fromId(id: String): ForgeFoxExpression =
            entries.firstOrNull { it.id == id } ?: Neutral
    }
}
""" % ("\n\n".join(constants))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def verify_outputs(project_dir: Path, manifest_path: Path) -> list[str]:
    errors: list[str] = []
    runtime_dir = project_dir / "app/src/main/res/drawable-nodpi"
    master_dir = project_dir / "app/src/main/assets/forgefox/transparent/2048"
    fallback_dir = project_dir / "app/src/main/assets/forgefox/transparent/512"
    expected = [
        (runtime_dir, RUNTIME_SIZE),
        (master_dir, MASTER_SIZE),
        (fallback_dir, FALLBACK_SIZE),
    ]
    for spec in EXPRESSIONS:
        if not android_valid_name(spec.resource_id):
            errors.append(f"Invalid Android resource name: {spec.resource_id}")
        for directory, size in expected:
            path = directory / spec.filename
            if not path.exists():
                errors.append(f"Missing {path}")
                continue
            img = Image.open(path).convert("RGBA")
            if img.size != (size, size):
                errors.append(f"Wrong size {path}: {img.size}, expected {(size, size)}")
            alpha = np.asarray(img.getchannel("A"))
            if alpha.max() == 0:
                errors.append(f"Empty alpha mask: {path}")
            if alpha.min() == 255:
                errors.append(f"No transparency: {path}")
            bounds = alpha_bounds(img)
            if bounds is None:
                errors.append(f"No visible pixels: {path}")
            elif size == RUNTIME_SIZE:
                left, top, right, bottom = bounds
                margin = min(left, top, size - right, size - bottom)
                if margin < RUNTIME_MIN_MARGIN:
                    errors.append(f"Runtime margin too small ({margin}px): {path}")
    if not manifest_path.exists():
        errors.append(f"Missing manifest: {manifest_path}")
    else:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        if len(manifest.get("expressions", [])) != len(EXPRESSIONS):
            errors.append("Manifest does not contain exactly 32 expressions")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", default=r"Z:\KOTLIN\Mascot", help="Source mascot image folder")
    parser.add_argument("--project", default=str(Path(__file__).resolve().parents[1]), help="UnifiedPort project folder")
    parser.add_argument("--allow-mapping-warnings", action="store_true", help="Continue if only unused source warnings exist")
    args = parser.parse_args()

    source_dir = Path(args.source)
    project_dir = Path(args.project)
    runtime_dir = project_dir / "app/src/main/res/drawable-nodpi"
    master_dir = project_dir / "app/src/main/assets/forgefox/transparent/2048"
    fallback_dir = project_dir / "app/src/main/assets/forgefox/transparent/512"
    assets_dir = project_dir / "app/src/main/assets/forgefox"
    mapping_review_path = assets_dir / "source_mapping.json"
    manifest_path = assets_dir / "forgefox_manifest.json"
    contact_sheet_path = assets_dir / "forgefox_contact_sheet.png"
    enum_path = project_dir / "app/src/main/java/com/ironlog/assets/ForgeFoxAssets.kt"

    if not source_dir.exists():
        raise FileNotFoundError(f"Source folder not found: {source_dir}")

    files = source_images(source_dir)
    mapping = build_mapping(files, mapping_review_path)
    missing = [spec.filename for spec in EXPRESSIONS if spec not in mapping]
    if missing:
        print("Failed to map required expressions:", file=sys.stderr)
        for item in missing:
            print(f"  - {item}", file=sys.stderr)
        return 2

    processed = 0
    failed: list[str] = []
    for spec in EXPRESSIONS:
        source_path = mapping[spec]
        try:
            source = Image.open(source_path)
            transparent = remove_background(source)
            transparent = refine_alpha_and_decontaminate(transparent)
            master = compose_square(transparent, MASTER_SIZE)
            runtime = master.resize((RUNTIME_SIZE, RUNTIME_SIZE), Image.Resampling.LANCZOS)
            fallback = master.resize((FALLBACK_SIZE, FALLBACK_SIZE), Image.Resampling.LANCZOS)
            save_png(master, master_dir / spec.filename)
            save_png(runtime, runtime_dir / spec.filename)
            save_png(fallback, fallback_dir / spec.filename)
            processed += 1
        except Exception as exc:
            failed.append(f"{spec.filename}: {exc}")
            print(f"FAILED {spec.filename}: {exc}", file=sys.stderr)

    generate_contact_sheet(fallback_dir, contact_sheet_path)
    generate_manifest(source_dir, manifest_path)
    generate_kotlin_enum(enum_path)

    errors = verify_outputs(project_dir, manifest_path)
    if errors:
        print("Quality check failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 3

    print()
    print("Forge Fox asset processing complete.")
    print()
    print("Source folder:")
    print(str(source_dir))
    print()
    print("Processed:")
    print(processed)
    print()
    print("Failed:")
    print(len(failed))
    print()
    print("Runtime drawable output:")
    print("app/src/main/res/drawable-nodpi/")
    print()
    print("Master transparent output:")
    print("app/src/main/assets/forgefox/transparent/2048/")
    print()
    print("Fallback transparent output:")
    print("app/src/main/assets/forgefox/transparent/512/")
    print()
    print("Contact sheet:")
    print("app/src/main/assets/forgefox/forgefox_contact_sheet.png")
    print()
    print("Manifest:")
    print("app/src/main/assets/forgefox/forgefox_manifest.json")
    print()
    print("Kotlin enum:")
    print("app/src/main/java/com/ironlog/assets/ForgeFoxAssets.kt")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
