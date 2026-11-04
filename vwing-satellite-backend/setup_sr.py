"""Download only safetensors from a pinned ESAOpenSR model revision; no remote Python execution."""
from pathlib import Path
import hashlib
import json
import urllib.request

REVISION = "469e9b3a049f9320ba66b3a39b9655dbd31480b3"
BASE = f"https://huggingface.co/tacofoundation/SEN2SR/resolve/{REVISION}/SEN2SRLite/NonReference_RGBN_x4/"
EXPECTED_SHA = {"model.safetensor": "479aa796d5068d0b1206118ccbca27bd3223df0214db1a9b31a1e18349ed1c7e", "hard_constraint.safetensor": "fbad981519066387c413ead1d6af7ef3e0d2947c34147ba90163fc79ae539239", "mlm.json": "419d562511737b0e465a34983d634944f6e84f97ad9207a86dd94d8e430ca606"}
def setup():
    target = Path(__file__).resolve().parent / "models/sen2srlite-rgbn-x4"
    target.mkdir(parents=True, exist_ok=True)
    files = {}
    for name in ("model.safetensor", "hard_constraint.safetensor", "mlm.json"):
        with urllib.request.urlopen(BASE + name, timeout=90) as response:
            data = response.read(16 * 1024 * 1024 + 1)
        if len(data) > 16 * 1024 * 1024:
            raise RuntimeError("Model asset exceeds download limit")
        if hashlib.sha256(data).hexdigest() != EXPECTED_SHA[name]:
            raise RuntimeError("Model asset checksum does not match pinned revision")
        (target / name).write_bytes(data)
        files[name] = hashlib.sha256(data).hexdigest()
    (target / "provenance.json").write_text(json.dumps({"repository": "tacofoundation/SEN2SR", "revision": REVISION, "license": "CC0-1.0", "sha256": files}, indent=2))
    print(json.dumps({"model_directory": str(target), "revision": REVISION, "sha256": files}, indent=2))
if __name__ == "__main__":
    setup()
