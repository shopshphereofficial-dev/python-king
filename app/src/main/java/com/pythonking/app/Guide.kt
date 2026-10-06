package com.pythonking.app

/** In-app guide: what the app can and cannot run. */
object Guide {

    val TEXT = """
HOW TO WRITE A SCRIPT FOR THIS APP

Runtime
  Python 3.13 (real CPython, embedded, works offline).
  One script, top to bottom, on a background thread.
  There is no console input - input() will fail.

Installed libraries
  numpy, pillow (PIL), matplotlib, pandas, sympy, pywavelets
  Plus most of the standard library: math, random, cmath,
  itertools, collections, json, re, datetime, statistics,
  colorsys, hashlib, struct, base64 ...

Not available (code using these will fail)
  pygame / pygame-ce, tkinter, turtle, curses, cv2, scikit-learn,
  seaborn, moviepy, imageio, requests, kivy, PyQt, scipy
  No GUI window, no audio, no camera, no file access, no network,
  no pip install, no subprocess.

Making an animation
  import kinganim
  kinganim.show(obj)      add ONE frame (the only way)
  kinganim.pause(sec)     optional delay
  kinganim.frame_count()  frames so far
  kinganim.clear()        drop all frames

  show() accepts exactly three things:
    1. a Pillow image        Image.new / ImageDraw
    2. a matplotlib Figure   fig, not the Axes
    3. a numpy array         (H,W) or (H,W,3), any dtype

  Do NOT call img.save(), plt.show() or fig.show().
  Just call kinganim.show(...) once per frame.

Limits
  - up to 900 frames
  - keep frames 360-720 px on the long side
  - roughly 10-40 fps (frames are drawn by Python, not the GPU)
  - the script must finish on its own; no while True

matplotlib tip
  import matplotlib
  matplotlib.use("Agg")
  import matplotlib.pyplot as plt
  ... then plt.close(fig) after each figure.

Export formats
  MP4 video (choose fps and resolution), animated GIF,
  animated WebP, APNG, PDF, PNG image, sprite sheet,
  ZIP of PNG frames. All saved to Downloads/PythonKing.

Steps
  Paste code -> Run (checks for errors) -> Run Animation
  -> Stop / Replay / scrub the timeline -> Export.
    """.trimIndent()
}
