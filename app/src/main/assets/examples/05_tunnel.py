# Hypnotic tunnel made with numpy.
import numpy as np
import kinganim

W = H = 320
N = 90
yy, xx = np.mgrid[0:H, 0:W]
cx = xx - W / 2.0
cy = yy - H / 2.0
ang = np.arctan2(cy, cx)
rad = np.sqrt(cx * cx + cy * cy) + 1e-6

for i in range(N):
    t = i / N * 2 * np.pi
    v = np.sin(8 * ang + t * 4) * np.cos(rad / 8.0 - t * 3)
    kinganim.show(v)

print("frames:", kinganim.frame_count())
