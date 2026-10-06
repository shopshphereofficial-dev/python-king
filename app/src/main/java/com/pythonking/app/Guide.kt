package com.pythonking.app

/** In-app guide: what the app can and cannot run. */
object Guide {

    val TEXT = """
HOW TO WRITE A SCRIPT FOR THIS APP

Runtime
  - Python 3.13 (real CPython, embedded in the app, works offline)
  - Installed libraries: numpy, pillow (PIL), matplotlib
  - Plus most of the standard library: math, random, cmath,
    itertools, collections, json, re, time, datetime, statistics,
    colorsys, array, struct and more.

Not available (code using these will fail)
  - pygame, tkinter, cv2/opencv, scipy, pandas, requests,
    PyQt, kivy, seaborn, moviepy, imageio
  - No GUI or window of any kind
  - input() is not supported (there is no keyboard input)
  - No internet, no installing packages, no writing files

Making an animation
  import kinganim
  kinganim.show(obj)      add ONE frame (this is the only way)
  kinganim.pause(sec)     optional delay between frames
  kinganim.frame_count()  how many frames so far

  show() accepts exactly three things:
    1. a Pillow image            Image.new / ImageDraw
    2. a matplotlib Figure       fig, not the Axes
    3. a numpy array             shape (H,W) or (H,W,3), any dtype

  Do NOT call img.save(), plt.show() or fig.show().
  Just call kinganim.show(...) once per frame.

Limits
  - up to 900 frames
  - keep frames 360-720 px on the long side
  - expect roughly 10-40 fps (frames are drawn by Python)

matplotlib tip
  import matplotlib
  matplotlib.use("Agg")
  import matplotlib.pyplot as plt
  ... then plt.close(fig) after each figure.

Typical script
  from PIL import Image, ImageDraw
  import math, kinganim

  W = H = 480
  N = 90
  for i in range(N):
      t = i / N
      img = Image.new("RGB", (W, H), (10, 12, 28))
      d = ImageDraw.Draw(img)
      x = W/2 + 150*math.cos(2*math.pi*t)
      y = H/2 + 150*math.sin(2*math.pi*t)
      d.ellipse([x-30, y-30, x+30, y+30], fill=(0,200,255))
      kinganim.show(img)

  print("frames:", kinganim.frame_count())

Steps: paste the code, tap Run to check for errors, then
Run Animation, then Export (MP4 / GIF / PDF / PNG).
    """.trimIndent()
}
