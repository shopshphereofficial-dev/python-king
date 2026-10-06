# A particle swarm drifting towards the centre.
from PIL import Image, ImageDraw
import math
import random
import kinganim

W = H = 440
N = 110
random.seed(7)

P = []
for _ in range(160):
    a = random.uniform(0, 2 * math.pi)
    r = random.uniform(60, 210)
    P.append([W / 2 + r * math.cos(a), H / 2 + r * math.sin(a),
              random.uniform(0.004, 0.02)])

for i in range(N):
    img = Image.new("RGB", (W, H), (8, 10, 20))
    d = ImageDraw.Draw(img)
    for p in P:
        p[0] += (W / 2 - p[0]) * p[2]
        p[1] += (H / 2 - p[1]) * p[2]
        if math.hypot(p[0] - W / 2, p[1] - H / 2) < 12:
            a = random.uniform(0, 2 * math.pi)
            p[0] = W / 2 + 200 * math.cos(a)
            p[1] = H / 2 + 200 * math.sin(a)
        d.ellipse([p[0] - 2, p[1] - 2, p[0] + 2, p[1] + 2], fill=(120, 220, 255))
    d.ellipse([W / 2 - 8, H / 2 - 8, W / 2 + 8, H / 2 + 8], fill=(255, 190, 70))
    kinganim.show(img)

print("frames:", kinganim.frame_count())
