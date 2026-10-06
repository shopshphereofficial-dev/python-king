# Matplotlib - two travelling sine waves.
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import kinganim

x = np.linspace(0, 4 * np.pi, 200)
N = 60
for i in range(N):
    fig = plt.figure(figsize=(4, 3), dpi=100)
    ax = fig.add_subplot(111)
    ax.plot(x, np.sin(x - i / 6.0), color="#00d0ff", linewidth=2)
    ax.plot(x, np.cos(x - i / 6.0), color="#ff8a00", linewidth=2)
    ax.set_ylim(-1.6, 1.6)
    ax.set_title("frame " + str(i))
    fig.tight_layout()
    kinganim.show(fig)
    plt.close(fig)

print("frames:", kinganim.frame_count())
