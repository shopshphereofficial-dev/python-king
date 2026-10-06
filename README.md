# Python King

A free, **ad-free, offline** Android studio for Python: a real **CPython 3.13**
interpreter with a proper code editor, a live animation stage, and export to
**MP4, GIF, WebP, APNG, PDF, PNG, sprite sheet or a ZIP of frames**.

## Features

### Editor
- Python **syntax highlighting**, **line numbers**, and **auto-save** of your draft
- **Paste / Copy / Select all / Clear** and **Open .py**
- **Font size** (11–20 sp) and a **luxury dark / light / system theme**
- **Script library** — save scripts by name, open or delete them later
- **Code snippets** and a **built-in examples gallery** (6 ready animations)
- **Python packages** screen — see exactly what is installed and what is not

### Animation stage
- **Run Animation** renders your frames live, with an FPS + frame counter
- **STOP** button that aborts a running script
- **Timeline scrubber** to step through frames, **Play / Pause**, **speed** (0.5×–4×) and **loop**
- **Pinch-to-zoom**, pan and double-tap-to-reset on the preview
- **Log** panel showing exactly what your script printed

### Export
- **MP4 video** — choose frame rate (15 / 24 / 30 / 60) and resolution (source/1080p/720p/480p)
- **Animated GIF**, **animated WebP**, **APNG**
- **PDF** (one page per frame), **PNG** (last frame)
- **Sprite sheet** (all frames in one grid PNG) and **ZIP of PNG frames**
- **Share** the finished file straight to any other app

Everything is saved to `Downloads/PythonKing/`. No ads, no telemetry, no internet needed.

## How animations work

Python has no Pygame on Android, so Python King uses a simple, reliable frame API.
Your script builds frames and hands each one to `kinganim.show(...)`:

```python
from PIL import Image, ImageDraw
import math, kinganim

W = H = 480
for i in range(90):
    t = i / 90.0
    img = Image.new("RGB", (W, H), (12, 12, 32))
    d = ImageDraw.Draw(img)
    cx = W / 2 + 150 * math.cos(2 * math.pi * t)
    cy = H / 2 + 150 * math.sin(2 * math.pi * t)
    d.ellipse([cx - 30, cy - 30, cx + 30, cy + 30], fill=(0, 200, 255))
    kinganim.show(img)
```

`kinganim.show()` accepts a **Pillow image**, a **matplotlib Figure**, or a **numpy array**.

| Function | Purpose |
| --- | --- |
| `kinganim.show(image)` | Add one frame and display it |
| `kinganim.pause(seconds)` | Sleep between frames |
| `kinganim.frame_count()` | Frames so far |
| `kinganim.clear()` | Drop all frames |

`show()` and `pause()` also check the Stop button, so a loop stops within one frame.

## Environment

- **Python 3.13** (real CPython, embedded via [Chaquopy](https://chaquo.com/chaquopy/)), fully offline
- **Installed:** `numpy`, `pillow`, `matplotlib`, `pandas`, `sympy`, `pywavelets` + most of the standard library
- **Not available:** `pygame`, `tkinter`, `turtle`, `curses`, `scipy`, `cv2`, `scikit-learn`,
  `requests`, `kivy`, GUI windows, audio, camera, file access, network, `pip`, `subprocess`, `input()`
- Limits: up to **900 frames**, frames drawn by CPython at roughly **10–40 fps**,
  and the script must finish on its own (no `while True`)

## Requirements

- Android 7.0 (API 24)+, **arm64-v8a** or **x86_64**
- To build locally: JDK 17 and Python 3.13

## How to get the APK

A GitHub Actions workflow builds a debug APK on every push to `main`.

1. Open the **Actions** tab of this repository.
2. Open the latest **Build Debug APK** run.
3. Download the **python-king-debug-apk** artifact (a zip containing `app-debug.apk`).
4. Copy it to your phone and install (enable "Install unknown apps").

Manual build: **Actions → Build Debug APK → Run workflow**.

## Build it yourself

```bash
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

## Project structure

```
app/src/main/
  java/com/pythonking/app/
    MainActivity.kt       editor, clipboard, library, snippets, packages, theme
    StageActivity.kt      stage: stop, timeline, playback, zoom, export
    PythonHighlighter.kt  syntax highlighting
    LineNumberView.kt     line number gutter
    ZoomImageView.kt      pinch zoom / pan
    Mp4Encoder.kt         Bitmap frames -> H.264 -> MP4
    FileSaver.kt          MediaStore save + share Uri
    Prefs.kt / App.kt     theme and settings
    Guide.kt              in-app guide text
  python/
    kinganim.py           frame API, stop control, all export formats
    runner.py             runs your code, captures output
    pkinfo.py             environment report
  assets/examples/        the built-in example gallery
```

## Package

`com.pythonking.app`
