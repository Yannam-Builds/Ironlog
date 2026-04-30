import hashlib
from pathlib import Path

apk = Path(r"Z:\ironlog\android\app\build\outputs\apk\release\IronLog v1.0.0.apk")
out = apk.with_suffix(apk.suffix + ".sha256")
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
out.write_text(f"{digest}  {apk.name}\n", encoding="utf-8")
