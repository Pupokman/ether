from __future__ import annotations

import shutil
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
repo = Path(__file__).resolve().parents[1]
overlay = repo / "overlay"

if not root.exists():
    raise SystemExit(f"upstream tree does not exist: {root}")

for src in overlay.rglob("*"):
    if not src.is_file():
        continue
    rel = src.relative_to(overlay)
    dst = root / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)
    print(f"overlay: {rel}")

transform = root / "common/src/main/java/net/irisshaders/iris/pipeline/transform/TransformPatcher.java"
text = transform.read_text(encoding="utf-8")
old = "if (IrisLimits.VK_CONFORMANCE) {"
new = "if (IrisLimits.requiresVkConformance()) {"
if old not in text:
    raise SystemExit("TransformPatcher Vulkan conformance seam changed upstream")
transform.write_text(text.replace(old, new, 1), encoding="utf-8")
print("patched: TransformPatcher Vulkan conformance switch")
