# Plasma - a smooth flowing colour field made with numpy.
import numpy as np
import kinganim

W = H = 320
N = 80
x = np.linspace(0, 4, W)
y = np.linspace(0, 4, H)
X, Y = np.meshgrid(x, y)
for i in range(N):
    t = i / N * 2 * np.pi
    v = (np.sin(X + t)
         + np.sin(Y * 1.3 - t)
         + np.sin((X + Y) * 0.8 + t * 2)
         + np.sin(np.sqrt(X * X + Y * Y) * 3 - t * 3))
    kinganim.show((v + 4) / 8.0)

print("frames:", kinganim.frame_count())
