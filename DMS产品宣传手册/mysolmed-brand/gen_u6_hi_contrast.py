"""Regenerate U6 (pure wordmark) logo with higher contrast for light backgrounds.

Keeps the U6 design: 'mysolmed' wordmark + tiny cyan terminal dot.
Changes vs original:
  - Jura-Medium (heavier weight) instead of Jura-Light
  - Transparent background (alpha) instead of SOFT_WHITE
  - Larger font + tighter tracking for more visual weight
Outputs overwrite assets/00-mysolmed-logo.png and 00-mysolmed-logo-cover.png
so all pages (index/scenarios/print/mobile) pick it up automatically.
"""
from pathlib import Path as FilePath
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
from matplotlib.patches import Circle

ROOT = FilePath(r"c:\Users\vinkin.yx.yu\文件\05_其他\DMS\DMS产品宣传手册")
FONT_DIR = FilePath(r"c:\Users\vinkin.yx.yu\.trae-cn\skills\canvas-design\canvas-fonts")
ASSETS = ROOT / "assets"

NAVY = "#0B2545"
PULSE_CYAN = "#00B4D8"


def spaced(s, gap=" "):
    return gap.join(list(s))


def gen(out_name, fontsize=30, dot_r=1.0, dot_x=82, dot_y=15.5):
    fig = plt.figure(figsize=(7, 2.4), dpi=200)
    fig.patch.set_alpha(0.0)  # transparent figure background
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_facecolor("none")  # transparent axes background
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 34.3)
    ax.set_aspect("equal")
    ax.axis("off")

    font = fm.FontProperties(fname=str(FONT_DIR / "Jura-Medium.ttf"))

    ax.text(
        50, 17, spaced("mysolmed", gap="  "),
        fontproperties=font, fontsize=fontsize, color=NAVY,
        ha="center", va="center",
    )
    ax.add_patch(Circle((dot_x, dot_y), dot_r, facecolor=PULSE_CYAN))

    out_path = ASSETS / out_name
    fig.savefig(
        out_path,
        bbox_inches="tight", pad_inches=0.25,
        transparent=True, edgecolor="none",
    )
    plt.close(fig)
    print(f"saved: {out_path}")


if __name__ == "__main__":
    gen("00-mysolmed-logo.png")
    gen("00-mysolmed-logo-cover.png")
    print("done")
