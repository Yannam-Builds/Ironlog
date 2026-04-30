import json
import re
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "artifacts" / "longhaul-strength.json"
TARGET = ROOT / "src" / "data" / "exerciseLibraryAdditions.js"
SOURCE_URL = "https://raw.githubusercontent.com/longhaul-fitness/exercises/main/strength.json"

INCLUDE_PATTERNS = [
    "trap bar",
    "landmine",
    "rear delt row",
    "hip abduction",
    "hip adduction",
    "upright row",
    "high row",
    "pullover",
    "pull-apart",
    "glute kickback",
    "leg press",
    "shoulder press - machine",
    "close-grip feet-up bench press",
    "pause squat",
    "t-bar",
    "belt",
    "hack squat",
    "smith machine",
    "wood chop",
    "chest fly - machine",
    "calf raise - machine",
    "seated row - machine",
    "chest press - machine",
    "hip thrust - machine",
    "crunch - machine",
    "reverse fly - machine",
    "bench press - machine",
    "incline bench press - machine",
    "decline bench press - machine",
    "overhead tricep extension - machine",
    "external shoulder rotation",
    "internal shoulder rotation",
    "standing external shoulder rotation",
]

EXCLUDE_PATTERNS = [
    "single-arm hang",
    "single arm hang",
    "walking lunge",
    "dead clean",
    "thruster",
    "front rack",
    "bottoms-up",
    "burpee",
    "bosu",
]

MUSCLE_MAP = {
    "chest": "chest",
    "shoulder - front": "front delts",
    "shoulder - side": "side delts",
    "shoulder - back": "rear delts",
    "tricep": "triceps",
    "bicep": "biceps",
    "lat": "lats",
    "trap": "traps",
    "rotator cuff - front": "rotator cuff",
    "rotator cuff - back": "rotator cuff",
    "forearm - inner": "forearms",
    "forearm - outer": "forearms",
    "quad": "quadriceps",
    "hamstring": "hamstrings",
    "glute": "glutes",
    "calf": "calves",
    "lower back": "lower back",
    "abdominal": "abdominals",
    "oblique": "obliques",
    "thigh - inner": "adductors",
    "thigh - outer": "abductors",
}


def normalize_slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.lower()).strip("_")


def dedupe(items):
    seen = set()
    output = []
    for item in items:
        if not item:
            continue
        key = item.lower().strip()
        if key in seen:
            continue
        seen.add(key)
        output.append(item)
    return output


def normalize_muscles(values):
    return dedupe([MUSCLE_MAP.get(value.lower().strip(), value.lower().strip()) for value in values or []])


def infer_equipment(name: str) -> str:
    lowered = name.lower()
    if "smith machine" in lowered or "machine" in lowered or "leg press machine" in lowered:
        return "Machine"
    if "cable" in lowered:
        return "Cable"
    if "band" in lowered:
        return "Band"
    if "dumbbell" in lowered:
        return "Dumbbell"
    if "kettlebell" in lowered:
        return "Kettlebell"
    if "trap bar" in lowered or "landmine" in lowered or "barbell" in lowered or "t-bar" in lowered:
        return "Barbell"
    if "bodyweight" in lowered or "rings" in lowered or "bar" in lowered:
        return "Bodyweight"
    return "Other"


def infer_force(name: str) -> str:
    lowered = name.lower()
    if any(token in lowered for token in ["press", "push", "extension", "kickback", "fly", "squat", "lunge", "thrust", "raise"]):
        return "push"
    if any(token in lowered for token in ["row", "pull", "curl", "deadlift", "shrug", "pullover"]):
        return "pull"
    return "static"


def infer_mechanic(name: str) -> str:
    lowered = name.lower()
    compound_tokens = [
        "press",
        "row",
        "squat",
        "deadlift",
        "lunge",
        "leg press",
        "hip thrust",
        "hack squat",
        "pull-up",
        "pulldown",
        "landmine",
    ]
    return "compound" if any(token in lowered for token in compound_tokens) else "isolation"


def include_item(item) -> bool:
    lowered = item["name"].lower()
    if any(pattern in lowered for pattern in EXCLUDE_PATTERNS):
        return False
    return any(pattern in lowered for pattern in INCLUDE_PATTERNS)


def transform_item(item):
    primary = normalize_muscles(item.get("primaryMuscles", []))
    secondary = normalize_muscles(item.get("secondaryMuscles", []))
    if not primary:
        return None

    return {
        "id": f"longhaul_{normalize_slug(item['slug'])}",
        "name": item["name"].replace("–", "-").replace("  ", " ").strip(),
        "force": infer_force(item["name"]),
        "level": "intermediate",
        "mechanic": infer_mechanic(item["name"]),
        "equipment": infer_equipment(item["name"]),
        "primaryMuscles": primary,
        "primaryMuscle": primary[0],
        "secondaryMuscles": secondary,
        "instructions": item.get("steps", []),
        "category": "strength",
        "images": [],
        "isCustom": False,
        "coachingCues": [item["notes"]] if item.get("notes") else None,
        "source": "longhaul-fitness/exercises",
    }


def main():
    if SOURCE.exists():
        raw = json.loads(SOURCE.read_text(encoding="utf-8"))
    else:
        raw = json.loads(urllib.request.urlopen(SOURCE_URL, timeout=20).read().decode("utf-8"))
    selected = [transform_item(item) for item in raw if include_item(item)]
    selected = [item for item in selected if item]
    selected.sort(key=lambda item: item["name"])

    payload = json.dumps(selected, ensure_ascii=True, indent=2)
    content = (
        "// Curated supplement built from longhaul-fitness/exercises (MIT) to fill\n"
        "// gaps in trap, landmine, machine, and sub-muscle-specific coverage.\n"
        f"export const EXERCISE_LIBRARY_ADDITIONS = {payload};\n"
    )
    TARGET.write_text(content, encoding="utf-8")
    print(f"Wrote {len(selected)} additions to {TARGET}")


if __name__ == "__main__":
    main()
