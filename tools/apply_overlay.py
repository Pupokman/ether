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


# Backend-aware Iris lifecycle seams. Keep shaderpack parsing alive on Vulkan
# without touching OpenGL-only DH compatibility or creating the legacy GL pipeline.
iris = root / "common/src/main/java/net/irisshaders/iris/Iris.java"
text = iris.read_text(encoding="utf-8")
if "import net.irisshaders.iris.backend.IrisBackendRuntime;" not in text:
    text = text.replace(
        "import net.irisshaders.iris.compat.dh.DHCompat;\n",
        "import net.irisshaders.iris.backend.IrisBackendRuntime;\nimport net.irisshaders.iris.compat.dh.DHCompat;\n",
        1,
    )

old = "\t\tDHCompat.run();"
new = "\t\tif (!IrisBackendRuntime.isVulkan()) {\n\t\t\tDHCompat.run();\n\t\t}"
if old not in text:
    raise SystemExit("Iris.onEarlyInitialize DHCompat seam changed upstream")
text = text.replace(old, new, 1)

old = "\t\tif (Minecraft.getInstance().level != null) {\n\t\t\tIris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());\n\t\t}"
new = "\t\tif (Minecraft.getInstance().level != null && !IrisBackendRuntime.isVulkan()) {\n\t\t\tIris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());\n\t\t}"
if old not in text:
    raise SystemExit("Iris.reload pipeline seam changed upstream")
text = text.replace(old, new, 1)
iris.write_text(text, encoding="utf-8")
print("patched: Iris Vulkan-safe early lifecycle")

standard = root / "common/src/main/java/net/irisshaders/iris/gl/shader/StandardMacros.java"
text = standard.read_text(encoding="utf-8")
if "import net.irisshaders.iris.backend.IrisBackendRuntime;" not in text:
    text = text.replace(
        "import net.irisshaders.iris.Iris;\n",
        "import net.irisshaders.iris.Iris;\nimport net.irisshaders.iris.backend.IrisBackendRuntime;\n",
        1,
    )

old = (
    '\t\tdefine(standardDefines, "MC_GL_VERSION", getGlVersion(GL20C.GL_VERSION));\n'
    '\t\tdefine(standardDefines, "MC_GLSL_VERSION", getGlVersion(GL20C.GL_SHADING_LANGUAGE_VERSION));'
)
new = (
    '\t\tif (IrisBackendRuntime.isVulkan()) {\n'
    '\t\t\t// Compatibility values for legacy OptiFine/Iris shaderpack feature gates.\n'
    '\t\t\t// Actual compilation is performed by the RenderPearl Vulkan shader compiler.\n'
    '\t\t\tdefine(standardDefines, "MC_GL_VERSION", "460");\n'
    '\t\t\tdefine(standardDefines, "MC_GLSL_VERSION", "460");\n'
    '\t\t} else {\n'
    '\t\t\tdefine(standardDefines, "MC_GL_VERSION", getGlVersion(GL20C.GL_VERSION));\n'
    '\t\t\tdefine(standardDefines, "MC_GLSL_VERSION", getGlVersion(GL20C.GL_SHADING_LANGUAGE_VERSION));\n'
    '\t\t}'
)
if old not in text:
    raise SystemExit("StandardMacros GL version seam changed upstream")
text = text.replace(old, new, 1)

old = (
    '\t\tif (IrisPlatformHelpers.getInstance().isModLoaded("distanthorizons") && DHCompat.hasRenderingEnabled()) {'
)
new = (
    '\t\tif (IrisPlatformHelpers.getInstance().isModLoaded("distanthorizons")\n'
    '\t\t\t&& (IrisBackendRuntime.isVulkan() || DHCompat.hasRenderingEnabled())) {'
)
if old not in text:
    raise SystemExit("StandardMacros DH macro seam changed upstream")
text = text.replace(old, new, 1)

old = '\t\tfor (String glExtension : getGlExtensions()) {\n\t\t\tdefine(standardDefines, glExtension);\n\t\t}'
new = (
    '\t\tif (!IrisBackendRuntime.isVulkan()) {\n'
    '\t\t\tfor (String glExtension : getGlExtensions()) {\n'
    '\t\t\t\tdefine(standardDefines, glExtension);\n'
    '\t\t\t}\n'
    '\t\t}'
)
if old not in text:
    raise SystemExit("StandardMacros GL extension seam changed upstream")
text = text.replace(old, new, 1)
standard.write_text(text, encoding="utf-8")
print("patched: StandardMacros Vulkan compatibility macros")


# Treat an active parsed shaderpack as "in use" on Vulkan so backend-neutral
# vertex-format and submission mixins can operate before the Vulkan renderer
# replaces the legacy IrisRenderingPipeline.
iris = root / "common/src/main/java/net/irisshaders/iris/Iris.java"
text = iris.read_text(encoding="utf-8")
old = (
    "\tpublic static boolean isPackInUseQuick() {\n"
    "\t\treturn getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline;\n"
    "\t}"
)
new = (
    "\tpublic static boolean isPackInUseQuick() {\n"
    "\t\tif (IrisBackendRuntime.isVulkan()) {\n"
    "\t\t\treturn currentPack != null && !fallback;\n"
    "\t\t}\n"
    "\t\treturn getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline;\n"
    "\t}"
)
if old not in text:
    raise SystemExit("Iris.isPackInUseQuick seam changed upstream")
iris.write_text(text.replace(old, new, 1), encoding="utf-8")
print("patched: Iris Vulkan shaderpack-active state")
