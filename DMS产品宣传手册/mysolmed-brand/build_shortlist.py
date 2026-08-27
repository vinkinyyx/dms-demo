"""MySolMed shortlist PDF — 9 selected concepts + 5 excluded + top 3 picks.

English-only typography to avoid font issues; Chinese meanings replaced
with concise English so the whole PDF uses one consistent font system.
"""

from pathlib import Path as FilePath
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
from matplotlib.backends.backend_pdf import PdfPages
import matplotlib.image as mpimg

BRAND_DIR = FilePath(r"c:\Users\vinkin.yx.yu\文件\05_其他\DMS\mysolmed-brand")
FONT_DIR = FilePath(r"c:\Users\vinkin.yx.yu\.trae-cn\skills\canvas-design\canvas-fonts")

LOGOS_CLASSIC = BRAND_DIR / "logos"
LOGOS_ULTRA   = BRAND_DIR / "logos-ultra-min"
OUT_PDF       = BRAND_DIR / "MySolMed-Shortlist.pdf"

def load_font(name):
    return fm.FontProperties(fname=str(FONT_DIR / name))

def load_sys_font(path):
    return fm.FontProperties(fname=path)

FONT_BIG_BOLD   = load_font("BigShoulders-Bold.ttf")
FONT_GEIST_BOLD = load_font("GeistMono-Bold.ttf")
FONT_GEIST_REG  = load_font("GeistMono-Regular.ttf")
FONT_ITALIANA   = load_font("Italiana-Regular.ttf")
FONT_JURA_LIGHT = load_font("Jura-Light.ttf")
# Chinese font for any CJK text
FONT_CN = load_sys_font(r"C:\Windows\Fonts\msyh.ttc")

NAVY       = "#0B2545"
PULSE_CYAN = "#00B4D8"
SOL_AMBER  = "#E89B3C"
SOFT_WHITE = "#FAFBFC"
MUTED      = "#5A6573"
HAIRLINE   = "#E2E5EA"
TINT       = "#F4F6F8"

PAGE_W, PAGE_H = 8.27, 11.69  # A4 portrait

def new_page(bg=SOFT_WHITE):
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

def header(ax, eyebrow, title, page_no):
    ytop = ax.get_ylim()[1]
    ax.text(6, ytop - 3, spaced(eyebrow.upper(), gap=" "),
            fontproperties=FONT_GEIST_REG, fontsize=7, color=MUTED, ha='left', va='top')
    ax.text(6, ytop - 6.5, title,
            fontproperties=FONT_BIG_BOLD, fontsize=18, color=NAVY, ha='left', va='top')
    ax.plot([6, 94], [ytop - 9, ytop - 9], color=HAIRLINE, lw=0.6)
    ax.text(94, ytop - 3, page_no,
            fontproperties=FONT_GEIST_REG, fontsize=8, color=MUTED, ha='right', va='top')
    ax.text(94, ytop - 6.5, spaced("mysolmed"),
            fontproperties=FONT_GEIST_BOLD, fontsize=9, color=NAVY, ha='right', va='top')

def footer(ax, text):
    ax.plot([6, 94], [3, 3], color=HAIRLINE, lw=0.4)
    ax.text(6, 1.5, spaced(text),
            fontproperties=FONT_GEIST_REG, fontsize=6.5, color=MUTED, ha='left', va='center')
    ax.text(94, 1.5, spaced("SHORTLIST  ·  V1"),
            fontproperties=FONT_GEIST_REG, fontsize=6.5, color=MUTED, ha='right', va='center')

def text_cn(ax, x, y, s, fontsize=7.5, color=NAVY, ha='left', va='center', **kw):
    """Chinese text helper using msyh."""
    ax.text(x, y, s, fontproperties=FONT_CN, fontsize=fontsize, color=color,
            ha=ha, va=va, **kw)

# ============================================================
# Page 1 — Cover
# ============================================================
def page_cover(pdf):
    fig, ax = new_page()
    ytop = ax.get_ylim()[1]

    for cx, cy in [(4, ytop-4), (96, ytop-4), (4, 4), (96, 4)]:
        ax.plot([cx-1.2, cx+1.2], [cy, cy], color=MUTED, lw=0.6, alpha=0.5)
        ax.plot([cx, cx], [cy-1.2, cy+1.2], color=MUTED, lw=0.6, alpha=0.5)

    ax.text(50, ytop-22, spaced("SHORTLIST", gap="  "),
            fontproperties=FONT_GEIST_REG, fontsize=9, color=MUTED, ha='center', va='center')

    ax.text(50, ytop/2 + 4, spaced("mysolmed", gap="  "),
            fontproperties=FONT_JURA_LIGHT, fontsize=38, color=NAVY, ha='center', va='center')
    ax.add_patch(plt.Circle((67, ytop/2 + 2.5), 0.9, color=PULSE_CYAN))

    ax.plot([35, 65], [ytop/2 - 5, ytop/2 - 5], color=NAVY, lw=0.8)

    # bilingual subtitle (English on top, Chinese below)
    ax.text(50, ytop/2 - 9, "9 selected  ·  5 excluded  ·  3 recommendations",
            fontproperties=FONT_ITALIANA, fontsize=12, color=NAVY,
            ha='center', va='center', style='italic')
    text_cn(ax, 50, ytop/2 - 12.5, "9 个保留  ·  5 个淘汰  ·  3 个推荐方向",
            fontsize=10, color=MUTED, ha='center', va='center')

    ax.text(50, 14, spaced("ENTERPRISE  ·  MEDICAL  ·  MINIMAL", gap="  "),
            fontproperties=FONT_GEIST_BOLD, fontsize=8, color=NAVY, ha='center', va='center')
    ax.text(50, 11, spaced("V1  ·  2026", gap=" "),
            fontproperties=FONT_GEIST_REG, fontsize=7, color=MUTED, ha='center', va='center')

    commit(pdf, fig)

# ============================================================
# Page 2 — 9 selected logos in 3x3 grid
# ============================================================
def page_selected(pdf):
    fig, ax = new_page()
    header(ax, "01 · Selected", "9 Concepts Shortlisted", "02")
    ytop = ax.get_ylim()[1]

    ax.text(6, ytop - 12, "Each logo is shown with a one-line meaning. Pick by number.",
            fontproperties=FONT_ITALIANA, fontsize=10.5, color=NAVY,
            ha='left', va='top', style='italic')
    text_cn(ax, 6, ytop - 14.5, "每个 logo 附一行含义说明，按编号挑选。",
            fontsize=8, color=MUTED, ha='left', va='top')

    selected = [
        ("C1", "Pulse Monogram", LOGOS_CLASSIC / "logo1_pulse_monogram.png",
         "M + ECG pulse = 系统中的脉动"),
        ("C2", "Sol Cross",      LOGOS_CLASSIC / "logo2_sol_cross.png",
         "Cross + sun rays = 温暖与权威"),
        ("C3", "Hex Shield",     LOGOS_CLASSIC / "logo3_hex_shield.png",
         "Hexagon + M = 企业级护盾"),
        ("C5", "Axis Mark",      LOGOS_CLASSIC / "logo5_axis_mark.png",
         "Axes + M = 临床级精度"),
        ("U2", "M Stroke",       LOGOS_ULTRA / "u2_m_stroke.png",
         "One-line M = 一气呵成的方案"),
        ("U3", "Crosshair",      LOGOS_ULTRA / "u3_crosshair.png",
         "Ring + cross = 精准定位"),
        ("U5", "Arc + Dot",      LOGOS_ULTRA / "u5_arc_dot.png",
         "Open arc + dot = 开放持续"),
        ("U6", "Pure Wordmark",  LOGOS_ULTRA / "u6_pure_wordmark.png",
         "Typography only = 名字即承诺"),
        ("U7", "Square Cross",   LOGOS_ULTRA / "u7_square_cross.png",
         "Square + cross = 合规边界内医疗"),
    ]

    grid_top = ytop - 18
    cell_w = 29
    cell_h = 23
    x_starts = [6, 38, 70]
    y_starts = [grid_top - cell_h,
                grid_top - 2*cell_h - 2,
                grid_top - 3*cell_h - 4]

    for idx, (cid, name, img_path, meaning) in enumerate(selected):
        row = idx // 3
        col = idx % 3
        x0 = x_starts[col]
        y0 = y_starts[row]

        ax.add_patch(plt.Rectangle((x0, y0), cell_w, cell_h, facecolor=TINT, edgecolor='none', zorder=0))

        ix = fig.add_axes([
            (x0 + 1) / 100,
            (y0 + 7) / (100 / (PAGE_W/PAGE_H)),
            (cell_w - 2) / 100,
            (cell_h - 9) / (100 / (PAGE_W/PAGE_H))
        ])
        img = mpimg.imread(str(img_path))
        ix.imshow(img)
        ix.axis('off')

        ax.text(x0 + 1.5, y0 + cell_h - 1.5, cid,
                fontproperties=FONT_GEIST_BOLD, fontsize=8, color=PULSE_CYAN,
                ha='left', va='top')
        ax.text(x0 + cell_w - 1.5, y0 + cell_h - 1.5, name,
                fontproperties=FONT_GEIST_BOLD, fontsize=8, color=NAVY,
                ha='right', va='top')
        text_cn(ax, x0 + cell_w/2, y0 + 3, meaning,
                fontsize=6.5, color=NAVY, ha='center', va='center')

    footer(ax, "9 selected  ·  enterprise medical  ·  minimal")
    commit(pdf, fig)

# ============================================================
# Page 3 — Excluded + Top 3
# ============================================================
def page_excluded_and_top(pdf):
    fig, ax = new_page()
    header(ax, "02 · Cuts & Picks", "Excluded + Top 3", "03")
    ytop = ax.get_ylim()[1]

    # Excluded
    ax.text(6, ytop - 12, spaced("EXCLUDED  ·  5 CONCEPTS CUT", gap="  "),
            fontproperties=FONT_GEIST_BOLD, fontsize=8, color=MUTED, ha='left', va='top')
    ax.plot([6, 94], [ytop - 14, ytop - 14], color=HAIRLINE, lw=0.4)

    excluded = [
        ("C4", "Solution Blocks", "偏 DevOps 技术感，医疗隐喻弱"),
        ("C6", "Vital Wave",       "ECG 波形太花，不够企业级"),
        ("U1", "Dot Pulse",        "纯点抽象，企业级品牌感不足"),
        ("U4", "Dot Matrix",       "偏数据/中台，与 C4 方向重复"),
        ("U8", "M Alone",          "偏内容/媒体品牌，C 端感强"),
    ]

    y = ytop - 17
    for cid, name, reason in excluded:
        ax.add_patch(plt.Rectangle((6, y - 1.4), 88, 2.6, facecolor=TINT, edgecolor='none', zorder=0))
        ax.plot([6.5, 7.5], [y, y], color=SOL_AMBER, lw=1.6)
        ax.text(9, y, cid, fontproperties=FONT_GEIST_BOLD, fontsize=8, color=MUTED, ha='left', va='center')
        ax.text(15, y, name, fontproperties=FONT_GEIST_BOLD, fontsize=8, color=MUTED, ha='left', va='center')
        text_cn(ax, 30, y, reason, fontsize=7.5, color=NAVY, ha='left', va='center')
        y -= 3.2

    # Top 3
    y -= 2
    ax.text(6, y, spaced("TOP 3  ·  RECOMMENDED DIRECTIONS", gap="  "),
            fontproperties=FONT_GEIST_BOLD, fontsize=8, color=NAVY, ha='left', va='top')
    y -= 2.5
    ax.plot([6, 94], [y, y], color=NAVY, lw=0.8)
    y -= 3

    top3 = [
        ("R1", "U6 · Pure Wordmark",
         "Cross-scene universal brand",
         "像 IBM / Sony / Google — 名字即品牌。最大适应性：合同、启动屏、页脚、App icon 通吃。最低风险、最长寿命。",
         "适合：想要一个不过时、跨所有场景通用的品牌"),
        ("R2", "U7 · Square Cross",
         "Enterprise + compliance",
         "方框=边界/合规，十字=医疗。直击医疗 B2B 受监管的本质。瑞士设计感，合同抬头、审计报告、采购方案上最稳。",
         "适合：客户是医院 CIO、卫健委、医保/商保"),
        ("R3", "U5 · Arc + Dot",
         "Human-centered care",
         "开口弧=持续在路上，青点=脉动。叙事深度最强。把 mysolmed 定位为长期陪伴而非交易型供应商。",
         "适合：慢病管理、家庭医生、康复护理、患者中心 SaaS"),
    ]

    for rid, name, claim, body, fit in top3:
        card_h = 9
        ax.add_patch(plt.Rectangle((6, y - card_h + 1), 88, card_h, facecolor=TINT, edgecolor='none', zorder=0))
        ax.add_patch(plt.Rectangle((6, y - card_h + 1), 0.6, card_h, facecolor=PULSE_CYAN, edgecolor='none', zorder=1))
        ax.text(10, y - 0.5, rid, fontproperties=FONT_GEIST_BOLD, fontsize=8, color=PULSE_CYAN, ha='left', va='top')
        ax.text(15, y - 0.5, name, fontproperties=FONT_BIG_BOLD, fontsize=11, color=NAVY, ha='left', va='top')
        ax.text(15, y - 3, spaced(claim.upper(), gap=" "),
                fontproperties=FONT_GEIST_REG, fontsize=6.5, color=MUTED, ha='left', va='top')
        text_cn(ax, 15, y - 4.3, body, fontsize=7.5, color=NAVY, ha='left', va='top')
        text_cn(ax, 15, y - card_h + 2, "FIT — " + fit, fontsize=6.5, color=MUTED, ha='left', va='top')
        y -= card_h + 1.5

    y -= 1
    text_cn(ax, 6, y, "NEXT — 报一个编号（如 \"U6\"），我交付 SVG + favicon + App icon 全套。",
            fontsize=7.5, color=NAVY, ha='left', va='top')

    footer(ax, "5 excluded  ·  3 recommended  ·  pick one to deepen")
    commit(pdf, fig)

# ============================================================
# Main
# ============================================================
def build():
    print(f"Building {OUT_PDF.name} ...")
    with PdfPages(str(OUT_PDF)) as pdf:
        page_cover(pdf)
        page_selected(pdf)
        page_excluded_and_top(pdf)
    print(f"\nDone. PDF: {OUT_PDF}")
    print(f"Size: {OUT_PDF.stat().st_size / 1024:.1f} KB")

if __name__ == "__main__":
    build()
