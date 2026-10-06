# Python King

A free, **ad-free, offline** Android app that runs real **Python 3.13** and turns
Python-drawn animations into downloadable **MP4 video, GIF, PDF or PNG** — all on the phone.

## What it does

- **Full CPython 3.13** embedded via [Chaquopy](https://chaquo.com/chaquopy/) — real Python, not a toy interpreter.
- **Batteries included:** `numpy`, `pillow` and `matplotlib` are pre-installed in the APK. No internet needed to run.
- **Code workspace:** monospace editor with **Paste / Copy / Select all / Sample / Open .py / Save / Clear** and a **Run** button that shows stdout, stderr and full tracebacks.
- **Run Animation:** renders the frames your script produces on a fullscreen immersive stage, with a live **FPS counter** and a **Replay** button.
- **Export the animation** to any of these, saved to `Downloads/PythonKing/`:
  - **MP4 video** (H.264, encoded on-device with `MediaCodec`)
  - **GIF** (animated)
  - **PDF** (one page per frame)
  - **PNG image**
- No ads, no telemetry, no background network calls.

## How animations work

Python has no Pygame on Android (see below), so Python King uses a simple, reliable
frame API instead. Your script builds frames and hands each one to `kinganim.show(...)`:

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

`kinganim.show()` accepts:
- a **Pillow** `Image`,
- a **matplotlib** `Figure`,
- a **numpy** array (auto-scaled to an image).

Three ready-made samples are built in (⋮ nothing — tap **Sample** on the main screen).

### kinganim API

| Function | Purpose |
| --- | --- |
| `kinganim.show(image)` | Add one frame and display it |
| `kinganim.pause(seconds)` | Sleep between frames |
| `kinganim.frame_count()` | How many frames so far |
| `kinganim.clear()` | Drop all stored frames |
| `kinganim.export(path, "gif"\|"pdf"\|"png", fps)` | Write frames to a file (used by the app) |

Frames are stored as compressed PNG bytes, so memory stays low (up to 900 frames).

## Why not Pygame?

Pygame (and Pygame-CE) is **not available on Chaquopy**: it needs SDL2 and its native
dependencies, and the Chaquopy maintainers say a full UI framework like Pygame/Kivy/PyQt
would need significant changes to the framework itself to support Android. Pygame on
Android only works through the completely separate `python-for-android` / Buildozer
toolchain, not Chaquopy. So Python King renders Python-generated frames itself instead —
which works with Pillow, NumPy and Matplotlib, and exports cleanly to video.

Also note: because frames are produced by CPython, the frame rate is limited by how fast
your script draws (usually 10–40 fps), not by the display's refresh rate.

## Requirements

- Android 7.0 (API 24) and above, **arm64** (or x86_64 for emulators).
- To build locally: JDK 17 and Python 3.13 on your machine.

## How to get the APK (no Android Studio needed)

A GitHub Actions workflow builds a debug APK on every push to `main`.

1. Go to the **Actions** tab of this repository.
2. Open the latest **Build Debug APK** run.
3. Download the **python-king-debug-apk** artifact (a zip containing `app-debug.apk`).
4. Copy the APK to your phone and install it (enable "Install unknown apps").

To trigger a fresh build manually: **Actions → Build Debug APK → Run workflow**.

## Build it yourself (Android Studio)

1. Install Python 3.13 and make sure `python3` on your PATH is 3.13.
2. Open the project in Android Studio and let Gradle sync (it downloads the Python
   runtime and the numpy/pillow/matplotlib wheels).
3. Run it, or:

```bash
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

## Project structure

```
app/src/main/
  java/com/pythonking/app/
    MainActivity.kt     # code editor + clipboard + Run
    StageActivity.kt    # animation stage, FPS, replay, export
    Mp4Encoder.kt       # Bitmap frames -> H.264 -> MP4
    FileSaver.kt        # MediaStore save to Downloads/PythonKing
    Samples.kt          # built-in sample scripts
  python/
    kinganim.py         # the frame/animation helper
    runner.py           # runs the user's code and captures output
  res/layout/  menu/  values/  drawable/  mipmap-*/
.github/workflows/build.yml
```

## Notes

- **APK size** is large (roughly 80–180 MB) because it embeds CPython, NumPy and
  Matplotlib for two ABIs. Drop `x86_64` in `app/build.gradle` to shrink it if you only
  use a real phone.
- Add more Python packages by editing the `pip { ... }` block in `app/build.gradle`
  (e.g. `install "pandas"`). Not every package has an Android build — `scipy` is not
  available for Python 3.13 on Chaquopy.
- `input()` is not supported (no console for interactive input).
- MP4 export uses the device's hardware H.264 encoder at 20 fps.

## Package

`com.pythonking.app`
