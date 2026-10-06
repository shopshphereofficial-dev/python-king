"""kinganim - the animation helper bundled with Python King.

Build frames and hand each one to ``kinganim.show(...)``. The app shows them
live and can export the whole animation as MP4, GIF, WebP, APNG, PDF, PNG,
a sprite sheet or a ZIP of frames.
"""

import io
import math
import os
import time
import zipfile

try:
    from PIL import Image
except Exception:  # pragma: no cover
    Image = None

_frames = []        # PNG bytes, one per frame
_sizes = []         # (width, height) per frame
_bridge = None      # Java object with onFrame(byte[], int, int)
_bridge_error = ""  # last problem while talking to the app
_tmp_dir = None     # writable folder supplied by the app
_stop = False       # set by the app when the user taps Stop
_target_tid = None  # python thread id of the running script
MAX_FRAMES = 900

try:
    _ADAPTIVE = Image.Palette.ADAPTIVE
except Exception:
    _ADAPTIVE = getattr(Image, "ADAPTIVE", 1)


# --------------------------------------------------------------------------
# app <-> python glue
# --------------------------------------------------------------------------

def attach(bridge):
    global _bridge, _bridge_error
    _bridge = bridge
    _bridge_error = ""


def set_tmp_dir(path):
    global _tmp_dir
    _tmp_dir = path


def bridge_error():
    return _bridge_error


# --------------------------------------------------------------------------
# stop control
# --------------------------------------------------------------------------

def mark_thread():
    """Remember which python thread is running the script."""
    global _target_tid
    try:
        import threading
        _target_tid = threading.current_thread().ident
    except Exception:
        _target_tid = None


def reset_stop():
    global _stop
    _stop = False


def stopped():
    return _stop


def request_stop():
    """Called by the app when the user taps Stop."""
    global _stop
    _stop = True
    try:
        import ctypes
        if _target_tid:
            api = ctypes.pythonapi
            fn = api.PyThreadState_SetAsyncExc
            fn.argtypes = [ctypes.c_ulong, ctypes.py_object]
            fn.restype = ctypes.c_int
            fn(ctypes.c_ulong(_target_tid), ctypes.py_object(KeyboardInterrupt))
    except Exception:
        pass


def check():
    """Raise if the user asked to stop."""
    if _stop:
        raise KeyboardInterrupt("stopped by user")


# --------------------------------------------------------------------------
# frames
# --------------------------------------------------------------------------

def _to_pil(obj):
    if Image is None:
        raise RuntimeError("Pillow is not available")
    if isinstance(obj, Image.Image):
        return obj
    if hasattr(obj, "savefig"):          # matplotlib Figure
        buf = io.BytesIO()
        obj.savefig(buf, format="png", dpi=100)
        buf.seek(0)
        return Image.open(buf)
    if hasattr(obj, "shape") or hasattr(obj, "tolist"):   # numpy array
        import numpy as np
        arr = np.asarray(obj)
        if arr.ndim == 2:
            arr = arr[:, :, None]
        if arr.ndim != 3:
            raise TypeError("numpy array must have 2 or 3 dimensions")
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


def _emit(data, w, h):
    global _bridge, _bridge_error
    if _bridge is None:
        return
    try:
        _bridge.onFrame(data, w, h)
        return
    except Exception as e:
        _bridge_error = "onFrame failed: " + str(e)
    try:
        if _tmp_dir is None:
            raise RuntimeError("no temp dir available")
        path = os.path.join(_tmp_dir, "kingframe_%d.png" % len(_frames))
        with open(path, "wb") as fh:
            fh.write(data)
        _bridge.onFrameFile(path, w, h)
    except Exception as e:
        _bridge_error = "onFrameFile failed: " + str(e)
        _bridge = None


def show(obj):
    """Add one frame to the animation and display it in the app."""
    check()
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
    _emit(data, im.width, im.height)


def pause(seconds):
    """Sleep between frames, e.g. kinganim.pause(1 / 30)."""
    check()
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


# --------------------------------------------------------------------------
# export
# --------------------------------------------------------------------------

def capabilities():
    caps = ["gif", "pdf", "png", "apng", "sprite", "zip"]
    try:
        from PIL import features
        if features.check("webp"):
            caps.append("webp")
    except Exception:
        pass
    return caps


def _duration(fps):
    return int(round(1000.0 / max(1, int(fps))))


def _save_gif(path, fps):
    pal = [_load(i).convert("P", palette=_ADAPTIVE) for i in range(len(_frames))]
    pal[0].save(path, save_all=True, append_images=pal[1:],
                duration=_duration(fps), loop=0)


def _save_webp(path, fps):
    frames = [_load(i).convert("RGBA") for i in range(len(_frames))]
    frames[0].save(path, save_all=True, append_images=frames[1:],
                   duration=_duration(fps), loop=0, format="WEBP",
                   quality=92, method=4)


def _save_apng(path, fps):
    frames = [_load(i).convert("RGBA") for i in range(len(_frames))]
    frames[0].save(path, save_all=True, append_images=frames[1:],
                   duration=_duration(fps), loop=0, format="PNG")


def _save_pdf(path):
    frames = [_load(i).convert("RGB") for i in range(len(_frames))]
    frames[0].save(path, save_all=True, append_images=frames[1:])


def _save_png(path, index):
    _load(index).convert("RGB").save(path)


def _save_sprite(path):
    n = len(_frames)
    cols = int(math.ceil(math.sqrt(n)))
    rows = int(math.ceil(n / float(cols)))
    w, h = _sizes[0]
    sheet = Image.new("RGB", (w * cols, h * rows), (0, 0, 0))
    for i in range(n):
        im = _load(i).convert("RGB")
        if im.size != (w, h):
            im = im.resize((w, h))
        sheet.paste(im, ((i % cols) * w, (i // cols) * h))
    sheet.save(path)


def _save_zip(path):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for i in range(len(_frames)):
            z.writestr("frame_%04d.png" % i, _frames[i])


def export(path, kind, fps=20, index=-1):
    """Write the stored frames to `path` in the requested format."""
    if not _frames:
        return False
    if kind == "gif":
        _save_gif(path, fps)
    elif kind == "webp":
        _save_webp(path, fps)
    elif kind == "apng":
        _save_apng(path, fps)
    elif kind == "pdf":
        _save_pdf(path)
    elif kind == "png":
        _save_png(path, index)
    elif kind == "sprite":
        _save_sprite(path)
    elif kind == "zip":
        _save_zip(path)
    else:
        raise ValueError("unknown export kind: " + str(kind))
    return True
