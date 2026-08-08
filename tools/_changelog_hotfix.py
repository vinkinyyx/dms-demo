from pathlib import Path
p=Path('CHANGELOG.md')
s=p.read_text(encoding='utf-8')
needle='- 修复入库、销售出库、经销商画像等页面少量中文标签和时间字段展示。\n'
add=needle+'- 修复接口调用日志公共列表 render 单元格为空的问题，恢复方向、方法、结果、时间等列内容；重写日志页中文文案，并隐藏未实现的导出按钮。\n- 新增 V71，修复接口日志状态筛选标签为“状态”并修正状态字典 500 文案错字。\n'
if '接口调用日志公共列表 render 单元格为空' not in s:
    s=s.replace(needle, add)
p.write_text(s, encoding='utf-8', newline='')
print('changelog updated')
