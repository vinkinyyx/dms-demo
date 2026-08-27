"""MySolMed logo design generator.

Generates 6 distinct logo concepts under the "Clinical Architecture" design philosophy.
Outputs individual PNGs plus an overview board.
"""

import os
from pathlib import Path as FilePath

import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
from matplotlib.patches import Circle, Rectangle, Polygon, FancyBboxPatch, PathPatch
from matplotlib.path import Path
import matplotlib.patheffects as pe
import numpy as np

# ---------- Paths ----------
BRAND_DIR = FilePath(r"c:\Users\vinkin.yx.yu\文件\05_其他\DMS\mysolmed-brand")
FONT_DIR = FilePath(r"c:\Users\vinkin.yx.yu\.trae-cn\skills\canvas-design\canvas-fonts")
OUT_DIR = BRAND_DIR / "logos"
OUT_DIR.mkdir(parents=True, exist_ok=True)

# ---------- Fonts ----------
def load_font(name):
    return fm.FontProperties(fname=str(FONT_DIR / name))

FONT_BIG_BOLD   = load_font("BigShoulders-Bold.ttf")
FONT_BIG_REG    = load_font("BigShoulders-Regular.ttf")
FONT_GEIST_BOLD = load_font("GeistMono-Bold.ttf")
FONT_GEIST_REG  = load_font("GeistMono-Regular.ttf")
FONT_OUTFIT_BOLD = load_font("Outfit-Bold.ttf")
FONT_OUTFIT_REG = load_font("Outfit-Regular.ttf")
FONT_TEKTUR     = load_font("Tektur-Medium.ttf")
FONT_INSTR_SERIF= load_font("InstrumentSerif-Regular.ttf")
FONT_JURA_LIGHT = load_font("Jura-Light.ttf")
FONT_GLOOCK     = load_font("Gloock-Regular.ttf")
FONT_ITALIANA   = load_font("Italiana-Regular.ttf")
FONT_NATIONAL_BOLD = load_font("NationalPark-Bold.ttf")

# ---------- Palette (Clinical Architecture) ----------
NAVY        = "#0B2545"
CLINICAL    = "#13315C"
PULSE_CYAN  = "#00B4D8"
SOL_AMBER   = "#E89B3C"
SOFT_WHITE  = "#FAFBFC"
GRAPHITE    = "#1A1A2E"
GRID_GRAY   = "#D9DDE3"
MUTED       = "#5A6573"

def spaced(text, gap=" "):
    """Insert single spaces between every character for letter-spacing effect."""
    return gap.join(list(text))

def setup_axes(figsize=(8, 4.5), bg=SOFT_WHITE):
    fig, ax = plt.subplots(figsize=figsize, dpi=200, facecolor=bg)
    ax.set_facecolor(bg)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 56.25)
    ax.set_aspect('equal')
    ax.axis('off')
    return fig, ax

def save(fig, name):
    path = OUT_DIR / name
    fig.savefig(path, bbox_inches='tight', pad_inches=0.15,
                facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close(fig)
    print(f"  saved: {path.name}")

# ============================================================
# Logo 1: Pulse Monogram
# ============================================================
def logo1_pulse_monogram():
    fig, ax = setup_axes(figsize=(9, 5))

    # subtle hairline grid
    for gx in range(0, 101, 10):
        ax.plot([gx, gx], [0, 56.25], color=GRID_GRAY, lw=0.3, alpha=0.45, zorder=0)
    for gy in range(0, 57, 10):
        ax.plot([0, 100], [gy, gy], color=GRID_GRAY, lw=0.3, alpha=0.45, zorder=0)

    # Big M as thick stroke
    mx_left, mx_right = 15, 45
    my_bottom, my_top = 12, 44
    midx = (mx_left + mx_right) / 2
    m_path = Path([
        (mx_left, my_bottom),
        (mx_left, my_top),
        (midx, my_bottom + 14),
        (mx_right, my_top),
        (mx_right, my_bottom),
    ], [Path.MOVETO, Path.LINETO, Path.LINETO, Path.LINETO, Path.LINETO])
    ax.add_patch(PathPatch(m_path, fill=False, edgecolor=NAVY, lw=8,
                            joinstyle='miter', capstyle='butt', zorder=3))

    # ECG pulse line cutting through M
    pulse_x = [0,  12, 16, 19, 21, 24, 27, 70, 100]
    pulse_y = [30, 30, 30, 30, 45, 15, 30, 30, 30]
    ax.plot(pulse_x, pulse_y, color=PULSE_CYAN, lw=3, solid_capstyle='round', zorder=4)
    ax.add_patch(Circle((21, 45), 1.0, color=PULSE_CYAN, zorder=5))

    # Wordmark (manual letter-spacing via spaces)
    ax.text(75, 28, spaced("mysolmed"), fontproperties=FONT_GEIST_BOLD, fontsize=15,
            color=NAVY, ha='center', va='center')
    ax.text(75, 18, spaced("CLINICAL SYSTEMS", gap=""), fontproperties=FONT_GEIST_REG, fontsize=5.5,
            color=MUTED, ha='center', va='center')

    for cx, cy in [(3, 53), (97, 53), (3, 3), (97, 3)]:
        ax.add_patch(Circle((cx, cy), 0.35, color=MUTED, alpha=0.5, zorder=2))

    save(fig, "logo1_pulse_monogram.png")

# ============================================================
# Logo 2: Sol Cross
# ============================================================
def logo2_sol_cross():
    fig, ax = setup_axes(figsize=(9, 5))

    cx, cy = 28, 30

    # Radiating sun rays
    n_rays = 12
    ray_inner, ray_outer = 11, 19
    for i in range(n_rays):
        ang = (2 * np.pi * i / n_rays) + np.pi/n_rays/2
        x1 = cx + ray_inner * np.cos(ang)
        y1 = cy + ray_inner * np.sin(ang)
        x2 = cx + ray_outer * np.cos(ang)
        y2 = cy + ray_outer * np.sin(ang)
        ax.plot([x1, x2], [y1, y2], color=SOL_AMBER, lw=2.5, solid_capstyle='round', zorder=2)

    # Medical cross
    cross_w, cross_h = 16, 5
    ax.add_patch(FancyBboxPatch((cx-cross_w/2, cy-cross_h/2), cross_w, cross_h,
                                 boxstyle="round,pad=0,rounding_size=1.2",
                                 linewidth=0, facecolor=NAVY, zorder=3))
    ax.add_patch(FancyBboxPatch((cx-cross_h/2, cy-cross_w/2), cross_h, cross_w,
                                 boxstyle="round,pad=0,rounding_size=1.2",
                                 linewidth=0, facecolor=NAVY, zorder=3))

    # Outer clinical ring
    ax.add_patch(Circle((cx, cy), 22, fill=False, edgecolor=NAVY, lw=1.0, zorder=4))
    ax.add_patch(Circle((cx, cy), 22.6, fill=False, edgecolor=MUTED, lw=0.3, alpha=0.6, zorder=4))

    # Cardinal tick marks
    for ang in [0, np.pi/2, np.pi, 3*np.pi/2]:
        x1 = cx + 22 * np.cos(ang)
        y1 = cy + 22 * np.sin(ang)
        x2 = cx + 24 * np.cos(ang)
        y2 = cy + 24 * np.sin(ang)
        ax.plot([x1, x2], [y1, y2], color=NAVY, lw=1.5, zorder=4)

    # Wordmark
    ax.text(70, 33, "mysol", fontproperties=FONT_BIG_BOLD, fontsize=26,
            color=NAVY, ha='center', va='center')
    ax.text(70, 22, "med", fontproperties=FONT_BIG_BOLD, fontsize=26,
            color=PULSE_CYAN, ha='center', va='center')
    ax.text(70, 13, spaced("MY SOLUTION MED"), fontproperties=FONT_GEIST_REG, fontsize=5.5,
            color=MUTED, ha='center', va='center')

    save(fig, "logo2_sol_cross.png")

# ============================================================
# Logo 3: Hex Shield
# ============================================================
def logo3_hex_shield():
    fig, ax = setup_axes(figsize=(9, 5))

    for gx in range(0, 101, 10):
        ax.plot([gx, gx], [0, 56.25], color=GRID_GRAY, lw=0.3, alpha=0.35, zorder=0)

    cx, cy = 28, 30
    R = 18
    hex_pts = []
    for i in range(6):
        ang = np.pi/2 + i * np.pi/3
        hex_pts.append((cx + R * np.cos(ang), cy + R * np.sin(ang)))
    ax.add_patch(Polygon(hex_pts, closed=True, fill=False, edgecolor=NAVY, lw=2.5, zorder=3))

    inner_pts = []
    for i in range(6):
        ang = np.pi/2 + i * np.pi/3
        inner_pts.append((cx + (R-2.5) * np.cos(ang), cy + (R-2.5) * np.sin(ang)))
    ax.add_patch(Polygon(inner_pts, closed=True, fill=False, edgecolor=MUTED, lw=0.5, alpha=0.7, zorder=4))

    # M inside hex
    mxl, mxr = cx-8, cx+8
    myb, myt = cy-9, cy+9
    midx = (mxl + mxr) / 2
    m_path = Path([
        (mxl, myb), (mxl, myt), (midx, myb + 7), (mxr, myt), (mxr, myb),
    ], [Path.MOVETO, Path.LINETO, Path.LINETO, Path.LINETO, Path.LINETO])
    ax.add_patch(PathPatch(m_path, fill=False, edgecolor=NAVY, lw=4, joinstyle='miter', zorder=5))

    ax.add_patch(Circle((cx, cy + R), 0.7, color=PULSE_CYAN, zorder=6))

    # Wordmark
    ax.text(70, 33, spaced("mysolmed"), fontproperties=FONT_OUTFIT_BOLD, fontsize=18,
            color=NAVY, ha='center', va='center')
    ax.plot([55, 85], [25, 25], color=NAVY, lw=1.2, zorder=3)
    ax.text(70, 20, spaced("ENTERPRISE MEDICAL SYSTEMS", gap=""), fontproperties=FONT_GEIST_REG, fontsize=5.5,
            color=MUTED, ha='center', va='center')

    save(fig, "logo3_hex_shield.png")

# ============================================================
# Logo 4: Solution Blocks (S + M modular)
# ============================================================
def logo4_solution_blocks():
    fig, ax = setup_axes(figsize=(9, 5))

    # dot grid background
    for gx in range(5, 96, 5):
        for gy in range(5, 55, 5):
            ax.add_patch(Circle((gx, gy), 0.12, color=GRID_GRAY, alpha=0.6, zorder=0))

    # Build S from 5 blocks
    s_blocks = [
        (12, 38, 16, 6),
        (12, 32, 6, 6),
        (12, 26, 16, 6),
        (22, 20, 6, 6),
        (12, 14, 16, 6),
    ]
    for (x, y, w, h) in s_blocks:
        ax.add_patch(FancyBboxPatch((x, y), w, h,
                                     boxstyle="round,pad=0,rounding_size=0.6",
                                     facecolor=NAVY, edgecolor='none', zorder=3))

    # Build M: two verticals + V middle
    m_blocks = [
        (42, 14, 5, 30),
        (57, 14, 5, 30),
    ]
    for (x, y, w, h) in m_blocks:
        ax.add_patch(FancyBboxPatch((x, y), w, h,
                                     boxstyle="round,pad=0,rounding_size=0.6",
                                     facecolor=NAVY, edgecolor='none', zorder=3))
    # M V (two diagonal bars)
    ax.plot([47, 52], [44, 22], color=NAVY, lw=5, solid_capstyle='round', zorder=3)
    ax.plot([52, 57], [22, 44], color=NAVY, lw=5, solid_capstyle='round', zorder=3)
    ax.add_patch(Circle((52, 22), 1.5, color=PULSE_CYAN, zorder=5))

    # Wordmark
    ax.text(75, 32, "mysol", fontproperties=FONT_TEKTUR, fontsize=18,
            color=NAVY, ha='center', va='center')
    ax.text(75, 22, "med", fontproperties=FONT_TEKTUR, fontsize=18,
            color=PULSE_CYAN, ha='center', va='center')
    ax.text(75, 13, spaced("MODULAR CLINICAL SYSTEMS", gap=""), fontproperties=FONT_GEIST_REG, fontsize=5.5,
            color=MUTED, ha='center', va='center')

    save(fig, "logo4_solution_blocks.png")

# ============================================================
# Logo 5: Axis Mark (axes + filled M)
# ============================================================
def logo5_axis_mark():
    fig, ax = setup_axes(figsize=(9, 5))

    # subtle grid
    for gx in range(0, 101, 5):
        ax.plot([gx, gx], [0, 56.25], color=GRID_GRAY, lw=0.25, alpha=0.3, zorder=0)
    for gy in range(0, 57, 5):
        ax.plot([0, 100], [gy, gy], color=GRID_GRAY, lw=0.25, alpha=0.3, zorder=0)

    cx, cy = 28, 30

    # Main axes
    ax.plot([cx, cx], [8, 52], color=NAVY, lw=1.2, zorder=2)
    ax.plot([8, 48], [cy, cy], color=NAVY, lw=1.2, zorder=2)

    # tick marks
    for ty in [12, 20, 28, 36, 44, 48]:
        ax.plot([cx-0.8, cx+0.8], [ty, ty], color=NAVY, lw=1, zorder=3)
    for tx in [12, 20, 36, 44]:
        ax.plot([tx, tx], [cy-0.8, cy+0.8], color=NAVY, lw=1, zorder=3)

    # Filled M shape (architectural)
    m_verts = [(12, 12), (16, 44), (28, 22), (40, 44), (44, 12)]
    ax.add_patch(Polygon(m_verts, closed=True, facecolor=NAVY, edgecolor='none', zorder=4))

    # Cyan accent at peak
    ax.add_patch(Circle((28, 22), 1.2, color=PULSE_CYAN, zorder=6))
    ax.add_patch(Circle((cx, cy), 4, fill=False, edgecolor=PULSE_CYAN, lw=1.2, zorder=5))

    # Wordmark
    ax.text(72, 32, spaced("mysolmed"), fontproperties=FONT_GLOOCK, fontsize=18,
            color=NAVY, ha='center', va='center')
    ax.plot([55, 89], [25, 25], color=NAVY, lw=0.8, zorder=3)
    ax.text(72, 20, spaced("MY · SOLUTION · MED", gap=""), fontproperties=FONT_GEIST_REG, fontsize=5.5,
            color=MUTED, ha='center', va='center')

    save(fig, "logo5_axis_mark.png")

# ============================================================
# Logo 6: Vital Wave
# ============================================================
def logo6_vital_wave():
    fig, ax = setup_axes(figsize=(9, 5))

    # subtle grid behind waveform
    for gx in range(5, 96, 4):
        ax.plot([gx, gx], [10, 50], color=GRID_GRAY, lw=0.2, alpha=0.4, zorder=0)
    for gy in range(10, 51, 5):
        ax.plot([5, 95], [gy, gy], color=GRID_GRAY, lw=0.2, alpha=0.4, zorder=0)

    # Baseline
    ax.plot([5, 95], [30, 30], color=MUTED, lw=0.6, alpha=0.5, zorder=1)

    # ECG waveform
    ecg_x = [5, 15, 18, 20, 22, 23.5, 25, 26.5, 28, 30, 33, 36, 45, 95]
    ecg_y = [30, 30, 31.5, 30, 30, 42, 18, 38, 30, 28, 30, 31, 30, 30]
    ax.plot(ecg_x, ecg_y, color=NAVY, lw=2.8, solid_capstyle='round',
            solid_joinstyle='round', zorder=3)

    ax.add_patch(Circle((23.5, 42), 1.2, color=PULSE_CYAN, zorder=5))

    # Wordmark
    ax.text(50, 14, spaced("mysolmed"), fontproperties=FONT_ITALIANA, fontsize=26,
            color=NAVY, ha='center', va='center')
    ax.text(50, 6.5, spaced("VITAL SYSTEMS", gap=""), fontproperties=FONT_GEIST_REG, fontsize=5.5,
            color=MUTED, ha='center', va='center')

    # corner marks
    for cx, cy in [(5, 50), (95, 50), (5, 4), (95, 4)]:
        ax.plot([cx-1.5, cx+1.5], [cy, cy], color=MUTED, lw=0.8, alpha=0.6, zorder=2)
        ax.plot([cx, cx], [cy-1.5, cy+1.5], color=MUTED, lw=0.8, alpha=0.6, zorder=2)

    save(fig, "logo6_vital_wave.png")

# ============================================================
# Overview board
# ============================================================
def overview_board():
    fig, axes = plt.subplots(3, 2, figsize=(16, 16), dpi=150, facecolor=SOFT_WHITE)
    fig.suptitle("MySolMed  —  Logo Concepts  ·  Clinical Architecture",
                 fontproperties=FONT_BIG_BOLD, fontsize=22, color=NAVY, y=0.965)

    fig.text(0.5, 0.93, spaced("MY SOLUTION MED", gap=" "),
             fontproperties=FONT_GEIST_REG, fontsize=10, color=MUTED, ha='center')

    titles = [
        ("01 · Pulse Monogram",   "M + ECG pulse line"),
        ("02 · Sol Cross",         "Medical cross + solar rays"),
        ("03 · Hex Shield",        "Hexagon + M monogram"),
        ("04 · Solution Blocks",   "S/M modular assembly"),
        ("05 · Axis Mark",         "Architectural axes + M"),
        ("06 · Vital Wave",        "ECG waveform + wordmark"),
    ]
    files = [
        "logo1_pulse_monogram.png",
        "logo2_sol_cross.png",
        "logo3_hex_shield.png",
        "logo4_solution_blocks.png",
        "logo5_axis_mark.png",
        "logo6_vital_wave.png",
    ]

    for idx, ax in enumerate(axes.flat):
        img = plt.imread(str(OUT_DIR / files[idx]))
        ax.imshow(img)
        ax.axis('off')
        ax.set_title(f"{titles[idx][0]}  ·  {titles[idx][1]}",
                     fontproperties=FONT_GEIST_BOLD, fontsize=11, color=NAVY, pad=10)

    fig.text(0.5, 0.025,
             spaced("Design philosophy: Clinical Architecture  ·  pick a number, refine next", gap=""),
             fontproperties=FONT_GEIST_REG, fontsize=9, color=MUTED, ha='center')

    out = OUT_DIR / "overview_all_6.png"
    fig.savefig(out, bbox_inches='tight', pad_inches=0.3,
                facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close(fig)
    print(f"  saved: {out.name}")

# ============================================================
# Main
# ============================================================
if __name__ == "__main__":
    print("Generating MySolMed logo concepts...")
    logo1_pulse_monogram()
    logo2_sol_cross()
    logo3_hex_shield()
    logo4_solution_blocks()
    logo5_axis_mark()
    logo6_vital_wave()
    overview_board()
    print(f"\nDone. All assets in: {OUT_DIR}")
