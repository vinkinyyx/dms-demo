from pathlib import Path
layer2_section = """
## 日期、日志与中文标签规范（v3.8.11）

- 所有页面展示日期/时间字段必须通过统一格式化工具处理，禁止直接渲染 ISO 字符串、毫秒值或带 `Z` 的原始 UTC 文本。
- 日期只显示到天时使用 `YYYY-MM-DD`；日期时间默认使用 `YYYY-MM-DD HH:mm:ss`。
- 所有展示请求报文、响应报文、审计变更、错误堆栈或长文本日志的地方，必须提供一键复制按钮。
- 后台日志文件/对象存储缺失时应降级为空内容或友好提示，不能让复制按钮返回 500。
- 中文按钮、字典和菜单标签禁止以 `?`、`????` 或替换字符落库；历史损坏按稳定 `button_key`/字典编码修复。
- 经销商画像行内“查看画像”是进入 KPI、月度达成、返利、合同、库存等页签的入口，任何布局优化都不得删除。

"""
d18 = """
## D18: 全站日期时间格式化与日志报文复制（2026-08-08）

| 属性 | 值 |
|------|----|
| 状态 | 生效中 |
| 背景 | 多个列表/详情页直接渲染 ISO 时间；日志/报文页面缺少复制入口；历史迁移编码导致中文按钮显示为问号。 |
| 决策 | 前后台分别维护统一 `format.js`；公共表格/报表默认用 `formatAuto`；所有日志展示区提供复制按钮；历史中文标签通过稳定 key 和 V69/V70 修复。 |
| 影响 | 新增页面必须复用格式化工具和复制交互；日志对象缺失必须降级处理；中文文案不得硬编码为 ASCII 问号。 |
| 验证 | 三端构建通过，测试环境 E2E 与日志/画像 smoke 通过。 |

"""
for name, marker, section in [
    ('.memory/layers/layer2-conventions.md', '## 日期、日志与中文标签规范（v3.8.11）', layer2_section),
    ('.memory/layers/layer4-decisions.md', '## D18:', d18),
]:
    p = Path(name)
    s = p.read_text(encoding='utf-8', errors='replace')
    idx = s.find(marker)
    if idx != -1:
        next_idx = s.find('\n## ', idx + len(marker))
        if next_idx != -1:
            s = s[:idx] + section.strip() + '\n\n' + s[next_idx+1:]
        else:
            s = s[:idx] + section.strip() + '\n'
    else:
        s = s.rstrip() + '\n' + section
    p.write_text(s, encoding='utf-8', newline='')
    print(name, p.read_bytes()[:120])
