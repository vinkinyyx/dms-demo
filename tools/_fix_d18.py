from pathlib import Path
p=Path('.memory/layers/layer4-decisions.md')
s=p.read_text(encoding='utf-8', errors='replace')
idx=s.rfind('## D18:')
section='''## D18: 全站日期时间格式化与日志报文复制（2026-08-08）

| 属性 | 值 |
|------|----|
| 状态 | 生效中 |
| 背景 | 多个列表/详情页直接渲染 ISO 时间；日志/报文页面缺少复制入口；历史迁移编码导致中文按钮显示为问号。 |
| 决策 | 前后台分别维护统一 `format.js`；公共表格/报表默认用 `formatAuto`；所有日志展示区提供复制按钮；历史中文标签通过稳定 key 和 V69/V70 修复。 |
| 影响 | 新增页面必须复用格式化工具和复制交互；日志对象缺失必须降级处理；中文文案不得硬编码为 ASCII 问号。 |
| 验证 | 三端构建通过，测试环境 E2E 与日志/画像 smoke 通过。 |
'''
if idx != -1:
    s=s[:idx]+section+'\n'
else:
    s=s.rstrip()+'\n\n'+section+'\n'
p.write_text(s, encoding='utf-8', newline='')
print(repr(p.read_text(encoding='utf-8')[idx if idx!=-1 else -600:]))
