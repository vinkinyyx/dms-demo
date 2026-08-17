# -*- coding: utf-8 -*-
"""
将 DMS 测试用例 Markdown 文档转换为 PDF
使用 reportlab platypus 直接构建，支持中文字体
"""
import re
from pathlib import Path
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm, mm
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle,
    PageBreak, KeepTogether
)
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.enums import TA_LEFT, TA_CENTER

SRC = Path(r"d:\Workspace\TRAE\DMS\docs\10_测试用例\DMS完整测试场景与测试案例_v3.11.0.md")
DST = Path(r"d:\Workspace\TRAE\DMS\docs\10_测试用例\DMS完整测试场景与测试案例_v3.11.0.pdf")

# 注册中文字体
FONT_REGULAR = "MSYH"
FONT_BOLD = "MSYHBD"
FONT_PATH_REGULAR = r"C:\Windows\Fonts\msyh.ttc"
FONT_PATH_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"

pdfmetrics.registerFont(TTFont(FONT_REGULAR, FONT_PATH_REGULAR, subfontIndex=0))
pdfmetrics.registerFont(TTFont(FONT_BOLD, FONT_PATH_BOLD, subfontIndex=0))

# 颜色
COLOR_H1 = colors.HexColor("#C00000")
COLOR_H2 = colors.HexColor("#1F4E78")
COLOR_H3 = colors.HexColor("#2E75B6")
COLOR_H4 = colors.HexColor("#305496")
COLOR_TABLE_HEADER_BG = colors.HexColor("#305496")
COLOR_TABLE_HEADER_TEXT = colors.white
COLOR_TABLE_GRID = colors.HexColor("#BFBFBF")
COLOR_TABLE_ROW_ALT = colors.HexColor("#F2F2F2")
COLOR_QUOTE = colors.HexColor("#595959")
COLOR_NORMAL = colors.HexColor("#262626")


def build_styles():
    styles = {}
    styles["h1"] = ParagraphStyle(
        "H1", fontName=FONT_BOLD, fontSize=20, leading=26,
        textColor=COLOR_H1, spaceBefore=18, spaceAfter=12, alignment=TA_LEFT
    )
    styles["h2"] = ParagraphStyle(
        "H2", fontName=FONT_BOLD, fontSize=16, leading=22,
        textColor=COLOR_H2, spaceBefore=16, spaceAfter=10, alignment=TA_LEFT
    )
    styles["h3"] = ParagraphStyle(
        "H3", fontName=FONT_BOLD, fontSize=13, leading=18,
        textColor=COLOR_H3, spaceBefore=12, spaceAfter=8, alignment=TA_LEFT
    )
    styles["h4"] = ParagraphStyle(
        "H4", fontName=FONT_BOLD, fontSize=11, leading=16,
        textColor=COLOR_H4, spaceBefore=10, spaceAfter=6, alignment=TA_LEFT
    )
    styles["body"] = ParagraphStyle(
        "Body", fontName=FONT_REGULAR, fontSize=9, leading=13,
        textColor=COLOR_NORMAL, spaceBefore=2, spaceAfter=2, alignment=TA_LEFT
    )
    styles["quote"] = ParagraphStyle(
        "Quote", fontName=FONT_REGULAR, fontSize=9, leading=13,
        textColor=COLOR_QUOTE, leftIndent=12, spaceBefore=2, spaceAfter=2,
        borderColor=colors.HexColor("#BFBFBF"), borderWidth=0, alignment=TA_LEFT
    )
    styles["table_header"] = ParagraphStyle(
        "TableHeader", fontName=FONT_BOLD, fontSize=8.5, leading=11,
        textColor=COLOR_TABLE_HEADER_TEXT, alignment=TA_CENTER
    )
    styles["table_cell"] = ParagraphStyle(
        "TableCell", fontName=FONT_REGULAR, fontSize=8, leading=11,
        textColor=COLOR_NORMAL, alignment=TA_LEFT
    )
    styles["table_cell_center"] = ParagraphStyle(
        "TableCellCenter", fontName=FONT_REGULAR, fontSize=8, leading=11,
        textColor=COLOR_NORMAL, alignment=TA_CENTER
    )
    return styles


def escape_html(text):
    """转义 HTML 特殊字符"""
    text = text.replace("&", "&amp;")
    text = text.replace("<", "&lt;")
    text = text.replace(">", "&gt;")
    return text


def md_inline_to_xml(text):
    """将 markdown 行内格式（粗体/代码）转为 reportlab XML"""
    text = escape_html(text)
    # 粗体 **text**
    text = re.sub(r"\*\*([^\*]+)\*\*", r"<b>\1</b>", text)
    # 行内代码 `text`
    text = re.sub(r"`([^`]+)`", r'<font face="Courier">\1</font>', text)
    return text


def parse_table_rows(lines):
    """解析 markdown 表格行，返回二维数组"""
    rows = []
    for ln in lines:
        ln = ln.strip()
        if not ln.startswith("|"):
            continue
        if re.match(r"^\|[\s\-:|]+\|$", ln):
            continue
        cells = [c.strip() for c in ln.strip("|").split("|")]
        rows.append(cells)
    return rows


def build_table(table_rows, styles, doc_width):
    """构建 reportlab Table"""
    if not table_rows:
        return None

    # 表头
    header = table_rows[0]
    # 数据行
    data_rows = table_rows[1:] if len(table_rows) > 1 else []

    # 列数
    n_cols = len(header)
    if n_cols == 0:
        return None

    # 构建单元格内容（Paragraph 以支持换行）
    table_data = []
    # 表头行
    header_row = [Paragraph(md_inline_to_xml(str(c)), styles["table_header"]) for c in header]
    table_data.append(header_row)
    # 数据行
    for row in data_rows:
        # 补齐列数
        while len(row) < n_cols:
            row.append("")
        row = row[:n_cols]
        # 第一列居中（子用例编号），其他列左对齐
        cell_row = []
        for idx, c in enumerate(row):
            if idx == 0:
                cell_row.append(Paragraph(md_inline_to_xml(str(c)), styles["table_cell_center"]))
            else:
                cell_row.append(Paragraph(md_inline_to_xml(str(c)), styles["table_cell"]))
        table_data.append(cell_row)

    # 列宽：根据内容粗略分配
    # 第一列（子用例编号）较窄，其他列平均
    if n_cols == 1:
        col_widths = [doc_width]
    elif n_cols == 2:
        col_widths = [doc_width * 0.18, doc_width * 0.82]
    elif n_cols == 3:
        col_widths = [doc_width * 0.12, doc_width * 0.38, doc_width * 0.50]
    elif n_cols == 4:
        col_widths = [doc_width * 0.10, doc_width * 0.25, doc_width * 0.30, doc_width * 0.35]
    elif n_cols == 5:
        col_widths = [doc_width * 0.10, doc_width * 0.22, doc_width * 0.22, doc_width * 0.23, doc_width * 0.23]
    else:
        # 6 列及以上
        first = doc_width * 0.10
        rest = (doc_width - first) / (n_cols - 1)
        col_widths = [first] + [rest] * (n_cols - 1)

    tbl = Table(table_data, colWidths=col_widths, repeatRows=1)
    tbl_style = TableStyle([
        # 表头
        ("BACKGROUND", (0, 0), (-1, 0), COLOR_TABLE_HEADER_BG),
        ("TEXTCOLOR", (0, 0), (-1, 0), COLOR_TABLE_HEADER_TEXT),
        ("FONTNAME", (0, 0), (-1, 0), FONT_BOLD),
        ("FONTSIZE", (0, 0), (-1, 0), 8.5),
        ("ALIGN", (0, 0), (-1, 0), "CENTER"),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        # 全表
        ("GRID", (0, 0), (-1, -1), 0.4, COLOR_TABLE_GRID),
        ("FONTNAME", (0, 1), (-1, -1), FONT_REGULAR),
        ("FONTSIZE", (0, 1), (-1, -1), 8),
        ("LEFTPADDING", (0, 0), (-1, -1), 4),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
    ])
    # 斑马纹
    for r in range(1, len(table_data)):
        if r % 2 == 0:
            tbl_style.add("BACKGROUND", (0, r), (-1, r), COLOR_TABLE_ROW_ALT)
    tbl.setStyle(tbl_style)
    return tbl


def add_page_number(canvas, doc):
    """页脚页码"""
    canvas.saveState()
    canvas.setFont(FONT_REGULAR, 8)
    canvas.setFillColor(colors.HexColor("#7F7F7F"))
    page_num = canvas.getPageNumber()
    canvas.drawCentredString(
        doc.pagesize[0] / 2, 1 * cm,
        f"DMS 完整测试场景与测试案例 v3.11.1  |  第 {page_num} 页"
    )
    canvas.restoreState()


def main():
    print(f"读取源文件: {SRC}")
    content = SRC.read_text(encoding="utf-8")
    lines = content.split("\n")
    print(f"总行数: {len(lines)}")

    styles = build_styles()

    # 使用横向 A4 以容纳宽表格
    doc = SimpleDocTemplate(
        str(DST),
        pagesize=landscape(A4),
        leftMargin=1.2 * cm, rightMargin=1.2 * cm,
        topMargin=1.2 * cm, bottomMargin=1.5 * cm,
        title="DMS 完整测试场景与测试案例 v3.11.1",
        author="DMS 项目组"
    )
    doc_width = doc.width
    print(f"文档可用宽度: {doc_width}")

    story = []
    table_count = 0
    i = 0
    while i < len(lines):
        ln = lines[i]
        stripped = ln.strip()

        # 空行
        if not stripped:
            story.append(Spacer(1, 3))
            i += 1
            continue

        # 水平分隔线
        if stripped == "---":
            story.append(Spacer(1, 6))
            i += 1
            continue

        # 标题
        if stripped.startswith("# "):
            story.append(Paragraph(md_inline_to_xml(stripped[2:]), styles["h1"]))
            story.append(Spacer(1, 4))
            i += 1
            continue
        if stripped.startswith("## "):
            # 大章节换页
            story.append(PageBreak())
            story.append(Paragraph(md_inline_to_xml(stripped[3:]), styles["h2"]))
            story.append(Spacer(1, 6))
            i += 1
            continue
        if stripped.startswith("### "):
            story.append(Paragraph(md_inline_to_xml(stripped[4:]), styles["h3"]))
            story.append(Spacer(1, 4))
            i += 1
            continue
        if stripped.startswith("#### "):
            story.append(Paragraph(md_inline_to_xml(stripped[5:]), styles["h4"]))
            story.append(Spacer(1, 3))
            i += 1
            continue

        # 引用块
        if stripped.startswith(">"):
            quote_text = stripped.lstrip(">").strip()
            if quote_text:
                story.append(Paragraph(md_inline_to_xml(quote_text), styles["quote"]))
            i += 1
            continue

        # 表格
        if stripped.startswith("|") and i + 1 < len(lines) and re.match(r"^\|[\s\-:|]+\|$", lines[i + 1].strip()):
            table_lines = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                table_lines.append(lines[i])
                i += 1
            table_rows = parse_table_rows(table_lines)
            if table_rows:
                table_count += 1
                tbl = build_table(table_rows, styles, doc_width)
                if tbl is not None:
                    story.append(Spacer(1, 4))
                    story.append(tbl)
                    story.append(Spacer(1, 6))
            continue

        # 普通段落
        story.append(Paragraph(md_inline_to_xml(stripped), styles["body"]))
        i += 1

    print(f"共构建 {table_count} 个表格")
    print(f"PDF 元素数: {len(story)}")
    print(f"生成 PDF: {DST}")
    doc.build(story, onFirstPage=add_page_number, onLaterPages=add_page_number)
    print("完成!")


if __name__ == "__main__":
    main()
