# -*- coding: utf-8 -*-
"""使用 Word COM 读取真实页数（需安装 Microsoft Word）"""
import os
import sys

try:
    import win32com.client as win32
except ImportError:
    print("需要安装 pywin32: pip install pywin32")
    sys.exit(1)

files = [
    r"d:\Workspace\TRAE\DMS\软著申请材料\01_申请表\计算机软件著作权登记申请表_DMS经销商管理系统V1.0.docx",
    r"d:\Workspace\TRAE\DMS\软著申请材料\02_源代码\源程序_DMS经销商管理系统V1.0_前30页后30页.docx",
    r"d:\Workspace\TRAE\DMS\软著申请材料\03_用户手册\DMS经销商管理系统V1.0用户操作手册.docx",
    r"d:\Workspace\TRAE\DMS\软著申请材料\04_其他证明材料\附属材料1_软件功能说明书.docx",
    r"d:\Workspace\TRAE\DMS\软著申请材料\04_其他证明材料\附属材料2_原创性声明.docx",
    r"d:\Workspace\TRAE\DMS\软著申请材料\04_其他证明材料\附属材料3_身份证明粘贴模板.docx",
    r"d:\Workspace\TRAE\DMS\软著申请材料\04_其他证明材料\附属材料4_代理委托书模板.docx",
    r"d:\Workspace\TRAE\DMS\软著申请材料\04_其他证明材料\附属材料5_提交材料清单与注意事项.docx",
]

word = win32.Dispatch("Word.Application")
word.Visible = False
word.DisplayAlerts = False

try:
    for f in files:
        if not os.path.exists(f):
            print(f"NOT FOUND: {f}")
            continue
        doc = word.Documents.Open(f, ReadOnly=True)
        # 强制重新分页
        doc.Repaginate()
        pages = doc.ComputeStatistics(2)  # 2 = wdStatisticPages
        words = doc.ComputeStatistics(0)  # 0 = wdStatisticWords
        chars = doc.ComputeStatistics(3)  # 3 = wdStatisticCharacters
        print(f"{os.path.basename(f)}: {pages} 页, {words} 词, {chars} 字符")
        doc.Close(False)
finally:
    word.Quit()
