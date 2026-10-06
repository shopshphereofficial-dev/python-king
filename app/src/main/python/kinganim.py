"""kinganim - the animation helper bundled with Python King.

Build frames and hand each one to ``kinganim.show(...)``. The app shows them
live and can export the whole animation as MP4, GIF, PDF or PNG.

Example
-------
    from PIL import Image, ImageDraw
    import math, kinganim

    W = H = 480
    for i in range(90):
        t = i / 90.0
        img = Image.new("RGB", (W, H), (12, 12, 30))
        d = ImageDraw.Draw(img)
        cx = W / 2 + 150 * math.cos(2 * math.pi * t)
        cy = H / 2 + 150 * math.sin(2 * math.pi * t)
        d.ellipse([cx - 30, cy - 30, cx + 30, cy + 30], fill=(0, 220, 255))
        kinganim.show(img)
"""

import io
import time

try:
    from PIL import Image
except Exception:  # pragma: no cover
    Image = None

_frames = []      # PNG bytes, one per frame
_sizes = []       # (width, height) per frame
_bridge = None    # optional Java object with an onFrame(byte[], int, int) method
MAX_FRAMES = 900

try:
    _ADAPTIVE = Image.Palette.ADAPTIVE
except Exception:
    _ADAPTIVE = getattr(Image, "ADAPTIVE", 1)


def attach(bridge):
    """Called by the app to receive frames as they are produced."""
    global _bridge
    _bridge = bridge


def _to_pil(obj):
    if Image is None:
        raise RuntimeError("Pillow is not available")
    if isinstance(obj, Image.Image):
        return obj
    # matplotlib Figure (anything with savefig)
    if hasattr(obj, "savefig"):
        buf = io.BytesIO()
        obj.savefig(buf, format="png", dpi=100)
        buf.seek(0)
        return Image.open(buf)
    # numpy array or nested list
    if hasattr(obj, "shape") or hasattr(obj, "tolist"):
        import numpy as np
        arr = np.asarray(obj)
        if arr.ndim == 2:
            arr = arr[:, :, None]
        if arr.shape[2] == 1:
            arr = np.repeat(arr, 3, axis=2)
        if arr.shape[2] == 4:
            arr = arr[:, :, :3]
        if arr.dtype != np.uint8:
            lo = float(arr.min())
            hi = float(arr.max())
            if hi - lo < 1e-9:
                hi = lo + 1.0
            arr = ((arr - lo) * (255.0 / (hi - lo))).astype("uint8")
        return Image.fromarray(arr.astype("uint8"), "RGB")
    raise TypeError(
        "kinganim.show() needs a PIL Image, a matplotlib Figure or a numpy array"
    )


def show(obj):
    """Add one frame to the animation and display it in the app."""
    im = _to_pil(obj)
    if im.mode not in ("RGB", "RGBA"):
        im = im.convert("RGB")
    buf = io.BytesIO()
    im.save(buf, format="PNG")
    data = buf.getvalue()
    _frames.append(data)
    _sizes.append((im.width, im.height))
    if len(_frames) > MAX_FRAMES:
        _frames.pop(0)
        _sizes.pop(0)
    if _bridge is not None:
        _bridge.onFrame(data, im.width, im.height)


def pause(seconds):
    """Sleep between frames, e.g. kinganim.pause(1 / 30)."""
    time.sleep(float(seconds))


def frame_count():
    return len(_frames)


def frame_bytes(index):
    return _frames[index]


def frame_size(index):
    w, h = _sizes[index]
    return [w, h]


def clear():
    del _frames[:]
    del _sizes[:]


def _load(index):
    return Image.open(io.BytesIO(_frames[index]))


def _save_gif(path, fps):
    duration = int(round(1000.0 / max(1, int(fps))))
    pal = [_load(i).convert("P", palette=_ADAPTIVE) for i in range(len(_frames))]
    pal[0].save(
        path, save_all=True, append_images=pal[1:], duration=duration, loop=0
    )


def _save_pdf(path):
    frames = [_load(i).convert("RGB") for i in range(len(_frames))]
    frames[0].save(path, save_all=True, append_images=frames[1:])


def _save_png(path, index):
    _load(index).convert("RGB").save(path)


def export(path, kind, fps=20, index=-1):
    """Write the stored frames to `path` in the requested format."""
    if not _frames:
        return False
    if kind == "gif":
        _save_gif(path, fps)
    elif kind == "pdf":
        _save_pdf(path)
    elif kind == "png":
        _save_png(path, index)
    else:
        raise ValueError("unknown export kind: " + str(kind))
    return True
