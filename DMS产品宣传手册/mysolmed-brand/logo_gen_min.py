"""MySolMed minimal logo variants.

Stripped-down versions of the 6 concepts: no grid, no corner marks,
no taglines. Just the mark + the wordmark, with maximum negative space.
"""

from pathlib import Path as FilePath

import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
from matplotlib.patches import Circle, Polygon, FancyBboxPatch, PathPatch
from matplotlib.path import Path
import numpy as np

BRAND_DIR = FilePath(r"c:\Users\vinkin.yx.yu\文件\05_其他\DMS\mysolmed-brand")
FONT_DIR = FilePath(r"c:\Users\vinkin.yx.yu\.trae-cn\skills\canvas-design\canvas-fonts")
OUT_DIR = BRAND_DIR / "logos-minimal"
OUT_DIR.mkdir(parents=True, exist_ok=True)

def load_font(name):
    return fm.FontProperties(fname=str(FONT_DIR / name))

FONT_BIG_BOLD   = load_font("BigShoulders-Bold.ttf")
FONT_GEIST_BOLD = load_font("GeistMono-Bold.ttf")
FONT_OUTFIT_BOLD = load_font("Outfit-Bold.ttf")
FONT_TEKTUR     = load_font("Tektur-Medium.ttf")
FONT_GLOOCK     = load_font("Gloock-Regular.ttf")
FONT_ITALIANA   = load_font("Italiana-Regular.ttf")

# Palette
NAVY        = "#0B2545"
PULSE_CYAN  = "#00B4D8"
SOL_AMBER   = "#E89B3C"
SOFT_WHITE  = "#FAFBFC"
MUTED       = "#5A6573"

def spaced(text, gap=" "):
    return gap.join(list(text))

def setup(figsize=(7, 3.2), bg=SOFT_WHITE):
    """Wider, shorter canvas - more negative space, logo-band feel."""
    fig, ax = plt.subplots(figsize=figsize, dpi=220, facecolor=bg)
    ax.set_facecolor(bg)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 45.7)  # 100 / 2.19 ~ aspect for 7x3.2
    ax.set_aspect('equal')
    ax.axis('off')
    return fig, ax

def save(fig, name):
    path = OUT_DIR / name
    fig.savefig(path, bbox_inches='tight', pad_inches=0.25,
                facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close(fig)
    print(f"  saved: {path.name}")

# ============================================================
# M1 · Pulse (minimal): M stroke + single ECG pulse
# ============================================================
def m1_pulse():
    fig, ax = setup()

    # M centered-left
    mx_left, mx_right = 22, 42
    my_bottom, my_top = 12, 36
    midx = (mx_left + mx_right) / 2
    m_path = Path([
        (mx_left, my_bottom), (mx_left, my_top),
        (midx, my_bottom + 11), (mx_right, my_top), (mx_right, my_bottom),
    ], [Path.MOVETO, Path.LINETO, Path.LINETO, Path.LINETO, Path.LINETO])
    ax.add_patch(PathPatch(m_path, fill=False, edgecolor=NAVY, lw=6,
                            joinstyle='miter', capstyle='butt'))

    # Single clean pulse through middle
    px = [50, 70, 75, 80, 95]
    py = [24, 24, 34, 14, 24]
    ax.plot(px, py, color=PULSE_CYAN, lw=2.4, solid_capstyle='round')
    ax.add_patch(Circle((75, 34), 0.7, color=PULSE_CYAN))

    # Wordmark (lowercase, tracked)
    ax.text(50, 7, spaced("mysolmed"), fontproperties=FONT_GEIST_BOLD,
            fontsize=12, color=NAVY, ha='center', va='center')

    save(fig, "m1_pulse.png")

# ============================================================
# M2 · Sol Cross (minimal): cross + 4 rays only
# ============================================================
def m2_sol_cross():
    fig, ax = setup()

    cx, cy = 25, 26
    # Just 4 cardinal rays (top, bottom, left, right offset diagonals)
    rays = [
        (cx-14, cy), (cx+14, cy),     # horizontal
        (cx, cy+14), (cx, cy-14),    # vertical
    ]
    # Actually: 4 diagonal short rays
    for ang_deg in [45, 135, 225, 315]:
        ang = np.deg2rad(ang_deg)
        x1 = cx + 9 * np.cos(ang)
        y1 = cy + 9 * np.sin(ang)
        x2 = cx + 16 * np.cos(ang)
        y2 = cy + 16 * np.sin(ang)
        ax.plot([x1, x2], [y1, y2], color=SOL_AMBER, lw=2.2,
                solid_capstyle='round')

    # Plain medical cross (no rounding)
    arm = 6
    thick = 2.4
    ax.plot([cx-arm, cx+arm], [cy, cy], color=NAVY, lw=thick*4, solid_capstyle='butt')
    ax.plot([cx, cx], [cy-arm, cy+arm], color=NAVY, lw=thick*4, solid_capstyle='butt')

    # Wordmark
    ax.text(70, 26, spaced("mysolmed"), fontproperties=FONT_BIG_BOLD,
            fontsize=22, color=NAVY, ha='center', va='center')

    save(fig, "m2_sol_cross.png")

# ============================================================
# M3 · Hex (minimal): single hexagon outline + thin M
# ============================================================
def m3_hex():
    fig, ax = setup()

    cx, cy = 25, 23
    R = 14
    pts = []
    for i in range(6):
        ang = np.pi/2 + i * np.pi/3
        pts.append((cx + R * np.cos(ang), cy + R * np.sin(ang)))
    ax.add_patch(Polygon(pts, closed=True, fill=False, edgecolor=NAVY, lw=1.8))

    # M inside
    mxl, mxr = cx-6, cx+6
    myb, myt = cy-7, cy+7
    midx = (mxl + mxr) / 2
    m_path = Path([
        (mxl, myb), (mxl, myt), (midx, myb + 5), (mxr, myt), (mxr, myb),
    ], [Path.MOVETO, Path.LINETO, Path.LINETO, Path.LINETO, Path.LINETO])
    ax.add_patch(PathPatch(m_path, fill=False, edgecolor=NAVY, lw=2.6,
                            joinstyle='miter'))

    ax.text(60, 23, spaced("mysolmed"), fontproperties=FONT_OUTFIT_BOLD,
            fontsize=14, color=NAVY, ha='center', va='center')

    save(fig, "m3_hex.png")

# ============================================================
# M4 · SM Blocks (minimal): S + M simplified
# ============================================================
def m4_sm():
    fig, ax = setup()

    # S as 3 stacked rounded bars (no middle connector complexity)
    s_blocks = [
        (10, 34, 14, 4),    # top
        (10, 23, 14, 4),    # middle (offset right)
        (10, 12, 14, 4),    # bottom
    ]
    for (x, y, w, h) in s_blocks:
        ax.add_patch(FancyBboxPatch((x, y), w, h,
                                     boxstyle="round,pad=0,rounding_size=0.4",
                                     facecolor=NAVY, edgecolor='none'))
    # S connectors (minimal: thin vertical bars)
    ax.plot([12, 12], [38, 27], color=NAVY, lw=4, solid_capstyle='butt')
    ax.plot([22, 22], [27, 16], color=NAVY, lw=4, solid_capstyle='butt')

    # M: just two verticals + peak
    ax.plot([32, 32], [12, 38], color=NAVY, lw=4, solid_capstyle='butt')
    ax.plot([46, 46], [12, 38], color=NAVY, lw=4, solid_capstyle='butt')
    ax.plot([32, 39], [38, 22], color=NAVY, lw=4, solid_capstyle='round')
    ax.plot([39, 46], [22, 38], color=NAVY, lw=4, solid_capstyle='round')
    # cyan accent dot at peak
    ax.add_patch(Circle((39, 22), 0.9, color=PULSE_CYAN))

    ax.text(72, 25, spaced("mysolmed"), fontproperties=FONT_TEKTUR,
            fontsize=14, color=NAVY, ha='center', va='center')

    save(fig, "m4_sm.png")

# ============================================================
# M5 · Axis (minimal): single circle + M only
# ============================================================
def m5_axis():
    fig, ax = setup()

    cx, cy = 25, 23
    # Single ring
    ax.add_patch(Circle((cx, cy), 14, fill=False, edgecolor=NAVY, lw=1.5))

    # M inside, thin
    mxl, mxr = cx-8, cx+8
    myb, myt = cy-8, cy+8
    midx = (mxl + mxr) / 2
    m_path = Path([
        (mxl, myb), (mxl, myt), (midx, myb + 6), (mxr, myt), (mxr, myb),
    ], [Path.MOVETO, Path.LINETO, Path.LINETO, Path.LINETO, Path.LINETO])
    ax.add_patch(PathPatch(m_path, fill=False, edgecolor=NAVY, lw=2.5,
                            joinstyle='miter'))

    # Single small cyan dot at top of ring (vital accent)
    ax.add_patch(Circle((cx, cy + 14), 0.6, color=PULSE_CYAN))

    ax.text(65, 23, spaced("mysolmed"), fontproperties=FONT_GLOOCK,
            fontsize=15, color=NAVY, ha='center', va='center')

    save(fig, "m5_axis.png")

# ============================================================
# M6 · Wave (minimal): single ECG spike + wordmark
# ============================================================
def m6_wave():
    fig, ax = setup()

    # Single clean ECG with just one QRS spike
    px = [10, 35, 42, 46, 50, 55, 90]
    py = [22, 22, 22, 34, 12, 22, 22]
    ax.plot(px, py, color=NAVY, lw=2.2, solid_capstyle='round',
            solid_joinstyle='round')
    ax.add_patch(Circle((46, 34), 0.8, color=PULSE_CYAN))

    ax.text(50, 8, spaced("mysolmed"), fontproperties=FONT_ITALIANA,
            fontsize=22, color=NAVY, ha='center', va='center')

    save(fig, "m6_wave.png")

# ============================================================
# Overview board (minimal)
# ============================================================
def overview():
    fig, axes = plt.subplots(3, 2, figsize=(14, 13), dpi=150, facecolor=SOFT_WHITE)
    fig.suptitle("MySolMed  —  Minimal Variants",
                 fontproperties=FONT_BIG_BOLD, fontsize=20, color=NAVY, y=0.97)

    titles = [
        ("M1 · Pulse",      "M + ECG spike"),
        ("M2 · Sol Cross", "Cross + 4 rays"),
        ("M3 · Hex",       "Hexagon + M"),
        ("M4 · SM Blocks", "S + M geometric"),
        ("M5 · Ring",      "Single ring + M"),
        ("M6 · Wave",      "ECG + wordmark"),
    ]
    files = [f"m{i+1}_{'pulse sol_cross hex sm axis wave'.split()[i]}.png" for i in range(6)]
    files = [
        "m1_pulse.png", "m2_sol_cross.png", "m3_hex.png",
        "m4_sm.png", "m5_axis.png", "m6_wave.png",
    ]

    for idx, ax in enumerate(axes.flat):
        img = plt.imread(str(OUT_DIR / files[idx]))
        ax.imshow(img)
        ax.axis('off')
        ax.set_title(f"{titles[idx][0]}  ·  {titles[idx][1]}",
                     fontproperties=FONT_GEIST_BOLD, fontsize=10, color=NAVY, pad=8)

    out = OUT_DIR / "overview_minimal.png"
    fig.savefig(out, bbox_inches='tight', pad_inches=0.25,
                facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close(fig)
    print(f"  saved: {out.name}")

if __name__ == "__main__":
    print("Generating minimal MySolMed variants...")
    m1_pulse()
    m2_sol_cross()
    m3_hex()
    m4_sm()
    m5_axis()
    m6_wave()
    overview()
    print(f"\nDone. All assets in: {OUT_DIR}")
