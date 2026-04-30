from __future__ import annotations

import shutil
from pathlib import Path

ROOT = Path(r"Z:\ironlog")
OUT = ROOT / "IronlogIOS" / "IronLogIOS_Source"

INCLUDE_FILES = [
    "App.js",
    "index.js",
    "app.json",
    "package.json",
    "package-lock.json",
    ".gitignore",
    "eas.json",
]

INCLUDE_DIRS = [
    "src",
    "assets",
]

if OUT.exists():
    shutil.rmtree(OUT)
OUT.mkdir(parents=True, exist_ok=True)

for rel in INCLUDE_FILES:
    src = ROOT / rel
    if src.exists():
        shutil.copy2(src, OUT / src.name)

for rel in INCLUDE_DIRS:
    src = ROOT / rel
    dst = OUT / rel
    if src.exists():
        shutil.copytree(src, dst)

