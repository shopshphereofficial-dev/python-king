"""Environment report shown in the app's "Python packages" screen."""

import sys
import platform

CHECK = [
    "numpy", "PIL", "matplotlib", "pandas", "sympy", "pywt",
    "scipy", "requests", "pygame", "cv2", "sklearn", "seaborn",
    "tkinter", "kivy", "imageio", "moviepy", "yaml", "lxml",
]


def report():
    lines = []
    lines.append("Python " + sys.version.split()[0] + "  (" + platform.machine() + ")")
    lines.append("")

    lines.append("THIRD-PARTY PACKAGES")
    import importlib
    for name in CHECK:
        try:
            mod = importlib.import_module(name)
            ver = getattr(mod, "__version__", "")
            lines.append("  available    " + name + ("   " + str(ver) if ver else ""))
        except Exception:
            lines.append("  MISSING      " + name)

    lines.append("")
    lines.append("PILLOW IMAGE FORMATS")
    try:
        from PIL import features
        for f in ("zlib", "jpg", "webp", "webp_anim", "libtiff", "freetype2"):
            try:
                lines.append("  " + f + ": " + str(features.check(f)))
            except Exception:
                lines.append("  " + f + ": unknown")
    except Exception as e:
        lines.append("  " + str(e))

    lines.append("")
    lines.append("NOT AVAILABLE ON THIS PLATFORM")
    lines.append("  GUI windows (tkinter, turtle, curses)")
    lines.append("  pygame / pygame-ce (no SDL2)")
    lines.append("  input(), file access, network, pip, subprocess")
    lines.append("  interactive plt.show() - use the Agg backend")

    return "\n".join(lines)
