"""MySolMed ultra-minimal logo set.

Even more restrained: each mark has at most 3-4 visual primitives.
Either pure wordmark, or one symbol + wordmark, or wordmark with embedded detail.
"""

from pathlib import Path as FilePath

import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
from matplotlib.patches import Circle, FancyBboxPatch, PathPatch, Rectangle, Wedge
from matplotlib.path import Path
import numpy as np

BRAND_DIR = FilePath(r"c:\Users\vinkin.yx.yu\文件\05_其他\DMS\mysolmed-brand")
FONT_DIR = FilePath(r"c:\Users\vinkin.yx.yu\.trae-cn\skills\canvas-design\canvas-fonts")
OUT_DIR = BRAND_DIR / "logos-ultra-min"
OUT_DIR.mkdir(parents=True, exist_ok=True)

def load_font(name):
    return fm.FontProperties(fname=str(FONT_DIR / name))

FONT_BIG_BOLD    = load_font("BigShoulders-Bold.ttf")
FONT_GEIST_BOLD  = load_font("GeistMono-Bold.ttf")
FONT_GEIST_REG   = load_font("GeistMono-Regular.ttf")
FONT_OUTFIT_BOLD = load_font("Outfit-Bold.ttf")
FONT_OUTFIT_REG  = load_font("Outfit-Regular.ttf")
FONT_GLOOCK      = load_font("Gloock-Regular.ttf")
FONT_ITALIANA    = load_font("Italiana-Regular.ttf")
FONT_JURA_LIGHT  = load_font("Jura-Light.ttf")
FONT_POIRET      = load_font("PoiretOne-Regular.ttf")

NAVY        = "#0B2545"
PULSE_CYAN  = "#00B4D8"
SOL_AMBER   = "#E89B3C"
SOFT_WHITE  = "#FAFBFC"
MUTED       = "#5A6573"

def spaced(text, gap=" "):
    return gap.join(list(text))

def setup(figsize=(7, 2.4), bg=SOFT_WHITE):
    """Very wide, very short - logo band feel."""
    fig, ax = plt.subplots(figsize=figsize, dpi=220, facecolor=bg)
    ax.set_facecolor(bg)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 34.3)
    ax.set_aspect('equal')
    ax.axis('off')
    return fig, ax

def save(fig, name):
    path = OUT_DIR / name
    fig.savefig(path, bbox_inches='tight', pad_inches=0.3,
                facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close(fig)
    print(f"  saved: {path.name}")

# ============================================================
# U1 · Dot Pulse — single dot + tiny cyan pulse dot + wordmark
# ============================================================
def u1_dot_pulse():
    fig, ax = setup()

    # Big navy dot
    ax.add_patch(Circle((18, 17), 4.5, facecolor=NAVY))
    # Tiny cyan pulse dot (offset upper-right, vital accent)
    ax.add_patch(Circle((22.5, 21.5), 1.0, facecolor=PULSE_CYAN))

    # Wordmark
    ax.text(60, 17, spaced("mysolmed"), fontproperties=FONT_GEIST_BOLD,
            fontsize=14, color=NAVY, ha='center', va='center')

    save(fig, "u1_dot_pulse.png")

# ============================================================
# U2 · M Stroke — single continuous line drawing an M
# ============================================================
def u2_m_stroke():
    fig, ax = setup()

    # Single continuous M stroke (one path)
    m_path = Path([
        (15, 8), (15, 26), (22, 14), (29, 26), (29, 8)
    ], [Path.MOVETO, Path.LINETO, Path.LINETO, Path.LINETO, Path.LINETO])
    ax.add_patch(PathPatch(m_path, fill=False, edgecolor=NAVY, lw=2.6,
                            joinstyle='miter', capstyle='round'))

    ax.text(60, 17, spaced("mysolmed"), fontproperties=FONT_GLOOCK,
            fontsize=16, color=NAVY, ha='center', va='center')

    save(fig, "u2_m_stroke.png")

# ============================================================
# U3 · Crosshair — ring + interior cross
# ============================================================
def u3_crosshair():
    fig, ax = setup()

    cx, cy = 17, 17
    # Single ring
    ax.add_patch(Circle((cx, cy), 7, fill=False, edgecolor=NAVY, lw=2))
    # Interior small cross
    ax.plot([cx-3, cx+3], [cy, cy], color=NAVY, lw=2.5, solid_capstyle='round')
    ax.plot([cx, cx], [cy-3, cy+3], color=NAVY, lw=2.5, solid_capstyle='round')

    ax.text(60, 17, spaced("mysolmed"), fontproperties=FONT_OUTFIT_BOLD,
            fontsize=14, color=NAVY, ha='center', va='center')

    save(fig, "u3_crosshair.png")

# ============================================================
# U4 · Dot Matrix M — 6 dots forming an M
# ============================================================
def u4_dot_matrix():
    fig, ax = setup()

    # 6 dots arranged as M (3 rows × varying positions)
    # bottom-left, top-left, middle-dip, top-right, bottom-right  → 5 dots
    dots = [
        (13, 8),   # bottom-left
        (13, 26),  # top-left
        (19, 15),  # middle dip
        (25, 26),  # top-right
        (25, 8),   # bottom-right
    ]
    for (x, y) in dots:
        ax.add_patch(Circle((x, y), 1.4, facecolor=NAVY))
    # Make middle dip a different color (cyan accent)
    ax.add_patch(Circle((19, 15), 1.4, facecolor=PULSE_CYAN))

    ax.text(60, 17, spaced("mysolmed"), fontproperties=FONT_GEIST_BOLD,
            fontsize=14, color=NAVY, ha='center', va='center')

    save(fig, "u4_dot_matrix.png")

# ============================================================
# U5 · Arc + Dot — pure abstract mark
# ============================================================
def u5_arc_dot():
    fig, ax = setup()

    cx, cy = 17, 17
    # Single arc (open circle - top half)
    theta = np.linspace(np.deg2rad(20), np.deg2rad(160), 50)
    xs = cx + 7 * np.cos(theta)
    ys = cy + 7 * np.sin(theta)
    ax.plot(xs, ys, color=NAVY, lw=2.6, solid_capstyle='round')
    # Cyan dot at the opening (vital pulse)
    ax.add_patch(Circle((cx, cy + 0), 1.2, facecolor=PULSE_CYAN))

    ax.text(60, 17, spaced("mysolmed"), fontproperties=FONT_ITALIANA,
            fontsize=18, color=NAVY, ha='center', va='center')

    save(fig, "u5_arc_dot.png")

# ============================================================
# U6 · Pure Wordmark — no symbol, just typography
# ============================================================
def u6_pure_wordmark():
    fig, ax = setup()

    # Pure wordmark, but split: mysol in navy, med in cyan
    ax.text(50, 17, spaced("mysol"), fontproperties=FONT_JURA_LIGHT,
            fontsize=22, color=NAVY, ha='center', va='center')
    # med in cyan, positioned to right of mysol (approximate)
    # Better: single text with one color; do them as separate texts
    # Reset and do cleaner: one text with full word, then small accent dot
    ax.cla()
    ax.set_facecolor(SOFT_WHITE)
    ax.set_xlim(0, 100); ax.set_ylim(0, 34.3)
    ax.set_aspect('equal'); ax.axis('off')

    # Just the wordmark, large and tracked
    ax.text(50, 17, spaced("mysolmed", gap="  "), fontproperties=FONT_JURA_LIGHT,
            fontsize=24, color=NAVY, ha='center', va='center')
    # Tiny cyan dot at the end (terminal accent)
    ax.add_patch(Circle((82, 16), 0.8, facecolor=PULSE_CYAN))

    save(fig, "u6_pure_wordmark.png")

# ============================================================
# U7 · Square + Cross — minimal Swiss-style mark
# ============================================================
def u7_square_cross():
    fig, ax = setup()

    # Single navy square outline + cyan cross inside
    s = 12
    cx, cy = 17, 17
    ax.add_patch(Rectangle((cx-s/2, cy-s/2), s, s, fill=False,
                            edgecolor=NAVY, lw=2))
    # Cyan cross inside
    ax.plot([cx-3, cx+3], [cy, cy], color=PULSE_CYAN, lw=2, solid_capstyle='round')
    ax.plot([cx, cx], [cy-3, cy+3], color=PULSE_CYAN, lw=2, solid_capstyle='round')

    ax.text(60, 17, spaced("mysolmed"), fontproperties=FONT_GEIST_BOLD,
            fontsize=14, color=NAVY, ha='center', va='center')

    save(fig, "u7_square_cross.png")

# ============================================================
# U8 · M Letterspace — letter "m" alone as the brand mark
# ============================================================
def u8_m_alone():
    fig, ax = setup()

    # Single large lowercase 'm' as standalone mark, then wordmark
    ax.text(17, 17, "m", fontproperties=FONT_ITALIANA,
            fontsize=42, color=NAVY, ha='center', va='center')
    # Tiny cyan dot above 'm' (vital pulse accent)
    ax.add_patch(Circle((17, 27), 0.7, facecolor=PULSE_CYAN))

    ax.text(60, 17, spaced("mysolmed"), fontproperties=FONT_ITALIANA,
            fontsize=18, color=NAVY, ha='center', va='center')

    save(fig, "u8_m_alone.png")

# ============================================================
# Overview board (ultra-minimal)
# ============================================================
def overview():
    fig, axes = plt.subplots(4, 2, figsize=(14, 16), dpi=150, facecolor=SOFT_WHITE)
    fig.suptitle("MySolMed  —  Ultra-Minimal Variants",
                 fontproperties=FONT_BIG_BOLD, fontsize=20, color=NAVY, y=0.97)

    titles = [
        ("U1 · Dot Pulse",      "Navy dot + cyan accent dot"),
        ("U2 · M Stroke",       "Single-line M"),
        ("U3 · Crosshair",      "Ring + interior cross"),
        ("U4 · Dot Matrix",      "5 dots forming M"),
        ("U5 · Arc + Dot",      "Open arc + cyan dot"),
        ("U6 · Pure Wordmark",  "Typography only"),
        ("U7 · Square Cross",   "Navy square + cyan cross"),
        ("U8 · M Alone",        "Single 'm' letter"),
    ]
    files = [
        "u1_dot_pulse.png", "u2_m_stroke.png", "u3_crosshair.png", "u4_dot_matrix.png",
        "u5_arc_dot.png", "u6_pure_wordmark.png", "u7_square_cross.png", "u8_m_alone.png",
    ]

    for idx, ax in enumerate(axes.flat):
        img = plt.imread(str(OUT_DIR / files[idx]))
        ax.imshow(img)
        ax.axis('off')
        ax.set_title(f"{titles[idx][0]}  ·  {titles[idx][1]}",
                     fontproperties=FONT_GEIST_BOLD, fontsize=10, color=NAVY, pad=8)

    out = OUT_DIR / "overview_ultra_min.png"
    fig.savefig(out, bbox_inches='tight', pad_inches=0.25,
                facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close(fig)
    print(f"  saved: {out.name}")

if __name__ == "__main__":
    print("Generating ultra-minimal MySolMed variants...")
    u1_dot_pulse()
    u2_m_stroke()
    u3_crosshair()
    u4_dot_matrix()
    u5_arc_dot()
    u6_pure_wordmark()
    u7_square_cross()
    u8_m_alone()
    overview()
    print(f"\nDone. All assets in: {OUT_DIR}")
