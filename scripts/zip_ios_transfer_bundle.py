from __future__ import annotations

from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(r"Z:\ironlog\IronlogIOS")
SRC = ROOT / "IronLogIOS_Source"
OUT = ROOT / "IronLogIOS_Source.zip"

if OUT.exists():
    OUT.unlink()

with ZipFile(OUT, "w", compression=ZIP_DEFLATED) as zf:
    for path in SRC.rglob("*"):
        if path.is_file():
            zf.write(path, path.relative_to(ROOT))

