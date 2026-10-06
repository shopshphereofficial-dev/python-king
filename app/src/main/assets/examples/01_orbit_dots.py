# Orbit - a ring of dots spinning around a pulsing core.
from PIL import Image, ImageDraw
import math
import kinganim

W = H = 480
N = 90
for i in range(N):
    t = i / N
    img = Image.new("RGB", (W, H), (10, 12, 30))
    d = ImageDraw.Draw(img)
    for k in range(24):
        a = 2 * math.pi * (k / 24.0 + t)
        x = W / 2 + 160 * math.cos(a)
        y = H / 2 + 160 * math.sin(a)
        r = 6 + 10 * (0.5 + 0.5 * math.sin(6 * t + k))
        d.ellipse([x - r, y - r, x + r, y + r], fill=(0, 200, 255))
    cr = 18 + 10 * math.sin(2 * math.pi * t)
    d.ellipse([W / 2 - cr, H / 2 - cr, W / 2 + cr, H / 2 + cr], fill=(255, 190, 60))
    kinganim.show(img)

print("frames:", kinganim.frame_count())
