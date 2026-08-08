from pathlib import Path
changelog_entry = """## v3.8.11 (2026-08-08) - 全站日期格式、日志复制与中文标签修复

### 修复
- 统一业务前台与后台管理端日期时间展示：新增 `formatDateTime`、`formatDate`、`formatAuto`，公共列表、报表和详情默认按 `YYYY-MM-DD HH:mm:ss` 渲染，避免直接显示 ISO 原始字符串。
- 业务前台接口调用日志详情增加请求头、请求体、响应头、响应体、错误信息复制按钮；后台 API 日志与审计日志也提供报文复制能力。
- 后台日志文件读取在 MinIO 对象缺失或不可用时降级为空内容，避免复制按钮返回 500。
- 修复经销商画像行操作按钮被硬编码为 `????` 的问题，恢复为“查看画像”，保留 KPI、月度达成、返利、合同、库存等页签入口。
- 修复入库、销售出库、经销商画像等页面少量中文标签和时间字段展示。

### 数据
- 保持已执行的 V68 不变，新增 V69/V70 修复历史编码损坏造成的按钮与字典标签问号。
- 平台默认与租户覆盖继续共用 `platform_button_configs`，以 `tenant_id IS NULL / NOT NULL` 区分。

### 验证
- 后端 Maven package、业务前台 Vite build、后台管理端 Vite build 均通过。
- 测试环境已部署：业务前台 `http://8.133.193.238:8083/`，后台 `http://8.133.193.238:8083/admin/`。
- `tools/_e2e_v389_final.py` 通过；额外 smoke 验证画像按钮返回“查看画像”、后台 API/审计日志可访问、请求报文复制接口返回 200。

---
"""
readme_entry = """## v3.8.11（2026-08-08）

- 统一全站日期时间格式，默认展示为 `YYYY-MM-DD HH:mm:ss`，避免 ISO 字符串和毫秒后缀。
- 前台接口日志、后台 API 日志、后台审计日志均提供一键复制报文能力。
- 修复经销商画像按钮乱码，恢复“查看画像”以及 KPI、合同、返利等页签入口。
- 新增 V69/V70 数据迁移，修复历史中文按钮和字典标签。
- 测试环境已完成部署，并通过 E2E 与关键接口 smoke。

"""
for name, entry, next_marker in [
    ('CHANGELOG.md', changelog_entry, '## v3.8.10'),
    ('README.md', readme_entry, '## v3.8.10'),
]:
    p = Path(name)
    s = p.read_text(encoding='utf-8', errors='replace')
    start = s.find('## v3.8.11')
    end = s.find(next_marker)
    if start != -1 and end != -1:
        s = s[:start] + entry + s[end:]
    elif '## v3.8.11' not in s:
        s = entry + s
    if name == 'README.md':
        s = s.replace('**当前版本**: v3.8.9', '**当前版本**: v3.8.11')
        s = s.replace('**最后更新**: 2026-08-07', '**最后更新**: 2026-08-08')
    p.write_text(s, encoding='utf-8', newline='')
    print(name, 'updated', p.read_bytes()[:120])
