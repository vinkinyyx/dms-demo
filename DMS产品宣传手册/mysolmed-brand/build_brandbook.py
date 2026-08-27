"""Assemble all MySolMed logo concepts into a single brand book PDF."""

from pathlib import Path as FilePath
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
from matplotlib.backends.backend_pdf import PdfPages
import matplotlib.image as mpimg

BRAND_DIR = FilePath(r"c:\Users\vinkin.yx.yu\文件\05_其他\DMS\mysolmed-brand")
FONT_DIR = FilePath(r"c:\Users\vinkin.yx.yu\.trae-cn\skills\canvas-design\canvas-fonts")

LOGOS_CLASSIC = BRAND_DIR / "logos"
LOGOS_MINIMAL = BRAND_DIR / "logos-minimal"
LOGOS_ULTRA   = BRAND_DIR / "logos-ultra-min"
OUT_PDF       = BRAND_DIR / "MySolMed-BrandBook.pdf"

def load_font(name):
    return fm.FontProperties(fname=str(FONT_DIR / name))

FONT_BIG_BOLD   = load_font("BigShoulders-Bold.ttf")
FONT_GEIST_BOLD = load_font("GeistMono-Bold.ttf")
FONT_GEIST_REG  = load_font("GeistMono-Regular.ttf")
FONT_OUTFIT_BOLD= load_font("Outfit-Bold.ttf")
FONT_ITALIANA   = load_font("Italiana-Regular.ttf")
FONT_GLOOCK     = load_font("Gloock-Regular.ttf")
FONT_JURA_LIGHT = load_font("Jura-Light.ttf")

NAVY       = "#0B2545"
PULSE_CYAN = "#00B4D8"
SOL_AMBER  = "#E89B3C"
SOFT_WHITE = "#FAFBFC"
MUTED      = "#5A6573"
GRID_GRAY  = "#D9DDE3"
HAIRLINE   = "#E2E5EA"

PAGE_W, PAGE_H = 11.69, 16.54  # A3 portrait inches (taller for content)

def new_page(pdf, bg=SOFT_WHITE):
    fig = plt.figure(figsize=(PAGE_W, PAGE_H), facecolor=bg)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_facecolor(bg)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100 / (PAGE_W / PAGE_H))
    ax.set_aspect('equal')
    ax.axis('off')
    return fig, ax

def commit(pdf, fig):
    pdf.savefig(fig, facecolor=fig.get_facecolor())
    plt.close(fig)

def spaced(text, gap=" "):
    return gap.join(list(text))

def page_header(ax, eyebrow, title, page_no=None):
    """Top-of-page editorial header."""
    ytop = ax.get_ylim()[1]
    # eyebrow (small caps)
    ax.text(6, ytop - 3, spaced(eyebrow.upper(), gap=" "),
            fontproperties=FONT_GEIST_REG, fontsize=7, color=MUTED,
            ha='left', va='top')
    # title
    ax.text(6, ytop - 6, title,
            fontproperties=FONT_BIG_BOLD, fontsize=22, color=NAVY,
            ha='left', va='top')
    # hairline
    ax.plot([6, 94], [ytop - 9, ytop - 9], color=HAIRLINE, lw=0.6)
    # page number top right
    if page_no:
        ax.text(94, ytop - 3, page_no,
                fontproperties=FONT_GEIST_REG, fontsize=8, color=MUTED,
                ha='right', va='top')
    # brand mark top right
    ax.text(94, ytop - 6, spaced("mysolmed"),
            fontproperties=FONT_GEIST_BOLD, fontsize=9, color=NAVY,
            ha='right', va='top')

def page_footer(ax, text):
    ybot = 0
    ax.plot([6, 94], [3, 3], color=HAIRLINE, lw=0.4)
    ax.text(6, 1.5, spaced(text),
            fontproperties=FONT_GEIST_REG, fontsize=6.5, color=MUTED,
            ha='left', va='center')
    ax.text(94, 1.5, spaced("BRAND BOOK  ·  V1"),
            fontproperties=FONT_GEIST_REG, fontsize=6.5, color=MUTED,
            ha='right', va='center')

# ============================================================
# Page 1 — Cover
# ============================================================
def page_cover(pdf):
    fig, ax = new_page(pdf)
    ytop = ax.get_ylim()[1]

    # corner clinical marks (subtle)
    for cx, cy in [(4, ytop-4), (96, ytop-4), (4, 4), (96, 4)]:
        ax.plot([cx-1.2, cx+1.2], [cy, cy], color=MUTED, lw=0.6, alpha=0.5)
        ax.plot([cx, cx], [cy-1.2, cy+1.2], color=MUTED, lw=0.6, alpha=0.5)

    # Eyebrow
    ax.text(50, ytop-25, spaced("BRAND IDENTITY  ·  CONCEPT BOOK", gap=" "),
            fontproperties=FONT_GEIST_REG, fontsize=8, color=MUTED,
            ha='center', va='center')

    # Wordmark — large
    ax.text(50, ytop/2 + 6, spaced("mysolmed", gap="  "),
            fontproperties=FONT_JURA_LIGHT, fontsize=42, color=NAVY,
            ha='center', va='center')
    # cyan terminal dot
    ax.add_patch(plt.Circle((67, ytop/2 + 4), 0.9, color=PULSE_CYAN))

    # subtitle
    ax.text(50, ytop/2 - 1, spaced("MY · SOLUTION · MED", gap=" "),
            fontproperties=FONT_GEIST_REG, fontsize=10, color=NAVY,
            ha='center', va='center')

    # divider
    ax.plot([35, 65], [ytop/2 - 5, ytop/2 - 5], color=NAVY, lw=0.8)

    # tagline
    ax.text(50, ytop/2 - 9, "Clinical Architecture for an Enterprise Medical System",
            fontproperties=FONT_ITALIANA, fontsize=14, color=NAVY,
            ha='center', va='center', style='italic')

    # meta block at bottom
    ax.text(50, 12, spaced("20 CONCEPTS  ·  3 EDITIONS  ·  1 BRAND", gap=" "),
            fontproperties=FONT_GEIST_BOLD, fontsize=9, color=NAVY,
            ha='center', va='center')
    ax.text(50, 9, spaced("V1  ·  2026", gap=" "),
            fontproperties=FONT_GEIST_REG, fontsize=7, color=MUTED,
            ha='center', va='center')

    commit(pdf, fig)

# ============================================================
# Page 2 — Design Philosophy
# ============================================================
def page_philosophy(pdf):
    fig, ax = new_page(pdf)
    page_header(ax, "01 · Philosophy", "Clinical Architecture", "02")
    ytop = ax.get_ylim()[1]

    # subtitle
    ax.text(6, ytop - 12, "A visual philosophy for the MySolMed identity",
            fontproperties=FONT_ITALIANA, fontsize=13, color=NAVY,
            ha='left', va='top', style='italic')

    # Body paragraphs (5)
    paras = [
        "Clinical Architecture is the visual conviction that trust is built from precision, and that care is communicated through restraint. The brand mark must feel like an instrument calibrated by hand — not a decoration, but a tool. Every line exists because it solves a problem; every gap exists because silence is part of the signal.",
        "The visual world draws from the intersection of medical instrument panels, Swiss architectural drafting, and the quiet authority of clinical typography. Forms lean toward the geometric — circles, hexagons, axes, hairline grids — but they are never cold. Cool is the failure mode of clinical design; what we seek instead is composed warmth.",
        "Color operates as a single sustained note: a deep clinical navy as the structural foundation (trust, gravity, contract-grade authority), accented by a single thread of either pulse-cyan (vital, alive, electric) or sol-amber (light, solution, dawn). Two colors, no more. The accent never raises its voice; it appears once, deliberately.",
        "Typography lives at the threshold of engineering and editorial. Wordmarks favor condensed grotesques or technical monospaces — fonts that read like specifications, not slogans. The lowercase treatment mysolmed is preferred to capitals because it lowers the voice: confident institutions do not shout.",
        "Composition follows the law of the single anchor: one primary form, one accent, one wordmark, one generous field of negative space. The final mark must feel like it could be etched onto glass lobby walls, stitched onto a hospital coat cuff, embedded in a software splash screen — and in every context, look like it belongs.",
    ]

    y = ytop - 18
    line_height = 2.0
    for i, p in enumerate(paras):
        # section number
        ax.text(6, y, f"0{i+1}", fontproperties=FONT_GEIST_BOLD, fontsize=9,
                color=PULSE_CYAN, ha='left', va='top')
        # paragraph text (wrapped manually with textwrap-style chunks)
        ax.text(11, y, p, fontproperties=FONT_GEIST_REG, fontsize=8.5,
                color=NAVY, ha='left', va='top',
                wrap=True, linespacing=1.55)
        # advance y by approx lines
        nlines = max(3, int(len(p) / 70) + 1)
        y -= nlines * line_height + 2.5

    page_footer(ax, "Clinical Architecture  ·  the product of countless hours by someone at the top of the field")
    commit(pdf, fig)

# ============================================================
# Pages 3-5 — Three Editions (overview images)
# ============================================================
def page_edition(pdf, page_no, eyebrow, title, subtitle, img_path, caption):
    fig, ax = new_page(pdf)
    page_header(ax, eyebrow, title, page_no)
    ytop = ax.get_ylim()[1]

    # subtitle
    ax.text(6, ytop - 12, subtitle,
            fontproperties=FONT_ITALIANA, fontsize=12, color=NAVY,
            ha='left', va='top', style='italic')

    # image embed - center
    img = mpimg.imread(str(img_path))
    # image axes
    ix = fig.add_axes([0.08, 0.13, 0.84, 0.65])  # left, bottom, w, h
    ix.imshow(img)
    ix.axis('off')

    # caption
    ax.text(6, 7, caption,
            fontproperties=FONT_GEIST_REG, fontsize=7.5, color=MUTED,
            ha='left', va='center')

    page_footer(ax, f"Edition: {eyebrow.split('·')[-1].strip()}")
    commit(pdf, fig)

# ============================================================
# Page 6 — Meaning matrix (all 20 concepts)
# ============================================================
def page_meaning(pdf):
    fig, ax = new_page(pdf)
    page_header(ax, "04 · Meaning Matrix", "Concept Decodings", "06")
    ytop = ax.get_ylim()[1]

    ax.text(6, ytop - 12, "Each concept is a different reading of mysolmed — Solution, Solid, Soul, Solar, Solo, Sole.",
            fontproperties=FONT_ITALIANA, fontsize=11, color=NAVY,
            ha='left', va='top', style='italic')

    # Table data: (id, name, reading, narrative, fit)
    rows = [
        # Classic
        ("C1", "Pulse Monogram",   "System pulse",        "M + ECG line: tech-bone, life-core",       "SaaS splash"),
        ("C2", "Sol Cross",         "Solar care",          "Cross + sun rays: warmth meets authority",  "Internet hospital"),
        ("C3", "Hex Shield",         "Architectural core",  "Hexagon + M: enterprise shield",           "B2B contracts"),
        ("C4", "Solution Blocks",   "Modular system",      "S+M blocks: plug-and-play architecture",    "Data platform"),
        ("C5", "Axis Mark",          "Clinical precision",  "Axes + M: coordinate-system medicine",     "LIS/PACS"),
        ("C6", "Vital Wave",        "Living signal",       "ECG waveform + wordmark",                  "App launch"),
        # Minimal
        ("M1", "Pulse (min)",        "Single pulse",       "Just M + one ECG spike",                    "Logo lockup"),
        ("M2", "Sol Cross (min)",    "Compass of care",    "Cross + 4 rays only",                      "Sub-mark"),
        ("M3", "Hex (min)",          "Pure shield",        "One hexagon + M",                           "Letterhead"),
        ("M4", "SM Blocks (min)",   "Solution pair",      "S+M geometric",                             "DevOps tool"),
        ("M5", "Axis (min)",         "Ring of focus",      "Single ring + M",                           "Footer"),
        ("M6", "Wave (min)",         "Quiet signal",       "ECG + wordmark only",                       "Splash"),
        # Ultra-minimal
        ("U1", "Dot Pulse",          "Pulse in the system","Navy dot + cyan accent",                   "App icon"),
        ("U2", "M Stroke",           "One-line solution",  "Single continuous M",                      "Editorial"),
        ("U3", "Crosshair",          "Precision located",  "Ring + interior cross",                     "Lab/Imaging"),
        ("U4", "Dot Matrix",         "Data-driven",        "5 dots forming M",                         "Data ops"),
        ("U5", "Arc + Dot",          "Open & ongoing",     "Open arc + cyan dot",                       "Chronic care"),
        ("U6", "Pure Wordmark",      "Name as promise",    "Typography only + terminal dot",           "Universal"),
        ("U7", "Square Cross",       "Bounded care",       "Square + interior cross",                  "Compliance"),
        ("U8", "M Alone",             "Root letter",        "Single 'm' + accent dot",                  "Content brand"),
    ]

    # Table header
    cols_x = [6, 12, 22, 36, 68, 92]
    col_titles = ["ID", "NAME", "READING", "NARRATIVE", "FIT"]
    y0 = ytop - 17
    for i, (cx, ct) in enumerate(zip(cols_x[:-1], col_titles)):
        ax.text(cx, y0, spaced(ct, gap=""),
                fontproperties=FONT_GEIST_BOLD, fontsize=7, color=MUTED,
                ha='left', va='center')
    ax.plot([6, 94], [y0 - 1.5, y0 - 1.5], color=NAVY, lw=0.5)

    # Rows
    y = y0 - 3.2
    row_h = 3.0
    for idx, (cid, name, reading, narr, fit) in enumerate(rows):
        # Section dividers
        if cid == "M1":
            ax.plot([6, 94], [y + 1.5, y + 1.5], color=HAIRLINE, lw=0.4)
            ax.text(50, y + 0.8, spaced("— MINIMAL EDITION —", gap=""),
                    fontproperties=FONT_GEIST_REG, fontsize=6.5, color=MUTED,
                    ha='center', va='center')
            y -= 1.6
        if cid == "U1":
            ax.plot([6, 94], [y + 1.5, y + 1.5], color=HAIRLINE, lw=0.4)
            ax.text(50, y + 0.8, spaced("— ULTRA-MINIMAL EDITION —", gap=""),
                    fontproperties=FONT_GEIST_REG, fontsize=6.5, color=MUTED,
                    ha='center', va='center')
            y -= 1.6

        # Alternating row tint (subtle)
        if idx % 2 == 0:
            ax.add_patch(plt.Rectangle((6, y - 1.2), 88, 2.6, facecolor="#F4F6F8", edgecolor='none', zorder=0))

        ax.text(cols_x[0], y, cid, fontproperties=FONT_GEIST_BOLD, fontsize=7.5, color=PULSE_CYAN, ha='left', va='center')
        ax.text(cols_x[1], y, name, fontproperties=FONT_GEIST_BOLD, fontsize=7.5, color=NAVY, ha='left', va='center')
        ax.text(cols_x[2], y, reading, fontproperties=FONT_GEIST_REG, fontsize=7.5, color=NAVY, ha='left', va='center')
        ax.text(cols_x[3], y, narr, fontproperties=FONT_GEIST_REG, fontsize=7, color=NAVY, ha='left', va='center')
        ax.text(cols_x[4], y, fit, fontproperties=FONT_GEIST_REG, fontsize=7, color=MUTED, ha='left', va='center')
        y -= row_h

    page_footer(ax, "Meaning matrix  ·  20 concepts decoded across 3 editions")
    commit(pdf, fig)

# ============================================================
# Page 7 — Recommendation & next steps
# ============================================================
def page_recommendation(pdf):
    fig, ax = new_page(pdf)
    page_header(ax, "05 · Recommendation", "Direction & Next Steps", "07")
    ytop = ax.get_ylim()[1]

    ax.text(6, ytop - 12, "Three directions worth deepening, each carrying a different brand claim.",
            fontproperties=FONT_ITALIANA, fontsize=12, color=NAVY,
            ha='left', va='top', style='italic')

    recs = [
        ("R1", "U6 · Pure Wordmark",
         "Cross-scene universal brand",
         "Like IBM / Sony / Google — the name IS the brand. Maximum versatility across contracts, splash screens, footers, app icons. Lowest risk, highest longevity.",
         "Best if you want a brand that ages well and never feels dated."),
        ("R2", "U7 · Square Cross",
         "Enterprise + compliance",
         "Box = boundary, cross = medicine. Speaks directly to the regulated nature of medical B2B. Swiss design feel that holds up on contract headers, audit reports, and procurement decks.",
         "Best if your buyers are hospital CIOs, government health bureaus, or insurance partners."),
        ("R3", "U5 · Arc + Dot",
         "Human-centered care",
         "Open arc = ongoing journey; cyan dot = pulse. Most narrative depth. Positions mysolmed as a long-term companion, not a transactional vendor.",
         "Best if your focus is chronic care, primary care, family doctors, or patient-centric SaaS."),
    ]

    y = ytop - 19
    card_h = 12
    for rid, name, claim, body, fit in recs:
        # card background
        ax.add_patch(plt.Rectangle((6, y - card_h + 2), 88, card_h, facecolor="#F4F6F8", edgecolor='none', zorder=0))
        # accent left bar
        ax.add_patch(plt.Rectangle((6, y - card_h + 2), 0.6, card_h, facecolor=PULSE_CYAN, edgecolor='none', zorder=1))
        # ID + name
        ax.text(10, y - 0.5, rid, fontproperties=FONT_GEIST_BOLD, fontsize=9, color=PULSE_CYAN, ha='left', va='top')
        ax.text(16, y - 0.5, name, fontproperties=FONT_BIG_BOLD, fontsize=12, color=NAVY, ha='left', va='top')
        # claim
        ax.text(16, y - 3.5, spaced(claim.upper(), gap=" "),
                fontproperties=FONT_GEIST_REG, fontsize=7, color=MUTED, ha='left', va='top')
        # body
        ax.text(16, y - 5, body,
                fontproperties=FONT_GEIST_REG, fontsize=8, color=NAVY, ha='left', va='top', wrap=True)
        # fit line
        ax.text(16, y - card_h + 3, "FIT — " + fit,
                fontproperties=FONT_GEIST_REG, fontsize=7, color=MUTED, ha='left', va='top', style='italic')
        y -= card_h + 1.5

    # Next steps block
    y -= 2
    ax.plot([6, 94], [y + 1.5, y + 1.5], color=NAVY, lw=0.6)
    ax.text(6, y - 0.5, spaced("NEXT STEPS", gap=""),
            fontproperties=FONT_GEIST_BOLD, fontsize=8, color=NAVY, ha='left', va='top')

    steps = [
        "1. Pick ONE direction (or tell me a different angle you want to push).",
        "2. I'll deliver 3-4 variants: pure-icon, pure-wordmark, reverse (dark bg), favicon, app icon.",
        "3. Output as SVG (vector, print-ready) + PNG + favicon set.",
        "4. Optional: business card / PPT cover / web header mockups.",
    ]
    y -= 3
    for s in steps:
        ax.text(8, y, s, fontproperties=FONT_GEIST_REG, fontsize=8.5, color=NAVY, ha='left', va='top')
        y -= 2.5

    page_footer(ax, "Pick a number, refine next.")
    commit(pdf, fig)

# ============================================================
# Main
# ============================================================
def build():
    print(f"Building {OUT_PDF.name} ...")
    with PdfPages(str(OUT_PDF)) as pdf:
        page_cover(pdf)
        page_philosophy(pdf)
        page_edition(
            pdf, "03",
            "02 · Edition I · Classic", "Clinical Architecture — 6 Concepts",
            "Original detailed marks: grids, ticks, dual rings, taglines.",
            LOGOS_CLASSIC / "overview_all_6.png",
            "Six concepts with full clinical detailing. Best for studying the design system before stripping down."
        )
        page_edition(
            pdf, "04",
            "03 · Edition II · Minimal", "Reduced Marks — 6 Concepts",
            "Strip the grid, ticks, and taglines. Mark + wordmark only.",
            LOGOS_MINIMAL / "overview_minimal.png",
            "Same six directions, minus decoration. More negative space, more logo-band feel."
        )
        page_edition(
            pdf, "05",
            "04 · Edition III · Ultra-Minimal", "Essential Marks — 8 Concepts",
            "Each mark is at most 3 visual primitives. Pure wordmark included.",
            LOGOS_ULTRA / "overview_ultra_min.png",
            "Pushed to the edge of restraint. Where Swiss design meets clinical instrument."
        )
        page_meaning(pdf)
        page_recommendation(pdf)

    print(f"\nDone. PDF: {OUT_PDF}")
    print(f"Size: {OUT_PDF.stat().st_size / 1024:.1f} KB")

if __name__ == "__main__":
    build()
