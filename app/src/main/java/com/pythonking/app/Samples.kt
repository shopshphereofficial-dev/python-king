package com.pythonking.app

/** Ready-made animation scripts shown in the app. */
object Samples {

    val PIL = """
        # Draw an animation frame by frame with Pillow.
        from PIL import Image, ImageDraw
        import math
        import kinganim

        W = H = 480
        N = 90
        for i in range(N):
            t = i / N
            img = Image.new("RGB", (W, H), (12, 12, 32))
            d = ImageDraw.Draw(img)
            for k in range(24):
                a = 2 * math.pi * (k / 24.0 + t)
                x = W / 2 + 160 * math.cos(a)
                y = H / 2 + 160 * math.sin(a)
                r = 6 + 10 * (0.5 + 0.5 * math.sin(6 * t + k))
                d.ellipse([x - r, y - r, x + r, y + r], fill=(0, 200, 255))
            d.ellipse([W / 2 - 14, H / 2 - 14, W / 2 + 14, H / 2 + 14], fill=(255, 120, 0))
            kinganim.show(img)

        print("frames:", kinganim.frame_count())
    """.trimIndent()

    val NUMPY = """
        # Animate a numpy array - kinganim turns it into a colour image.
        import numpy as np
        import kinganim

        W = H = 360
        N = 60
        x = np.linspace(-3, 3, W)
        y = np.linspace(-3, 3, H)
        X, Y = np.meshgrid(x, y)
        for i in range(N):
            t = i / N * 2 * np.pi
            r = np.sqrt((X - 1.3 * np.cos(t)) ** 2 + (Y - 1.3 * np.sin(t)) ** 2)
            kinganim.show(np.sin(7 * r - 3 * t))

        print("frames:", kinganim.frame_count())
    """.trimIndent()

    val MATPLOTLIB = """
        # Animate a matplotlib figure.
        import numpy as np
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        import kinganim

        x = np.linspace(0, 4 * np.pi, 200)
        N = 60
        for i in range(N):
            fig = plt.figure(figsize=(4, 3), dpi=80)
            ax = fig.add_subplot(111)
            ax.plot(x, np.sin(x - i / 6.0), color="#00d0ff", linewidth=2)
            ax.plot(x, np.cos(x - i / 6.0), color="#ff8a00", linewidth=2)
            ax.set_ylim(-1.6, 1.6)
            ax.set_title("Python King frame " + str(i))
            fig.tight_layout()
            kinganim.show(fig)
            plt.close(fig)

        print("frames:", kinganim.frame_count())
    """.trimIndent()

    val ALL: List<Pair<String, String>> = listOf(
        "Pillow drawing" to PIL,
        "NumPy waves" to NUMPY,
        "Matplotlib plot" to MATPLOTLIB
    )
}
