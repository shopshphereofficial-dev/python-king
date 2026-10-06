# Bouncing ball with a fading motion trail.
from PIL import Image, ImageDraw
import kinganim

W, H = 480, 320
N = 120
R = 18
x, y = 60.0, 60.0
vx, vy = 5.2, 3.4
trail = []

for i in range(N):
    x += vx
    y += vy
    if x < R or x > W - R:
        vx = -vx
    if y < R or y > H - R:
        vy = -vy
    trail.append((x, y))
    if len(trail) > 18:
        trail.pop(0)

    img = Image.new("RGB", (W, H), (16, 18, 26))
    d = ImageDraw.Draw(img)
    for k, (tx, ty) in enumerate(trail):
        a = (k + 1) / float(len(trail))
        r = R * a
        c = int(80 + 175 * a)
        d.ellipse([tx - r, ty - r, tx + r, ty + r], fill=(c, int(c * 0.6), 40))
    d.ellipse([x - R, y - R, x + R, y + R], fill=(255, 210, 70))
    kinganim.show(img)

print("frames:", kinganim.frame_count())
