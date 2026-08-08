from pathlib import Path
p=Path('README.md')
s=p.read_text(encoding='utf-8')
s=s.replace('**当前版本**: v3.8.7','**当前版本**: v3.8.9')
s=s.replace('**最后更新**: 2026-08-06','**最后更新**: 2026-08-07')
marker='---\n\n'
idx=s.index(marker)+len(marker)
section='''## v3.8.9 列表页规范全站收口（2026-08-07）
- 列表页统一规则：搜索字段走布局配置；行内按钮超过 1 个即折叠到“更多 ▾”；查询/重置固定展示。
- 租户能力：新增“列表页配置”，可维护当前租户搜索字段与按钮显示；新增“角色权限”，可按角色勾选菜单、按钮、接口权限。
- 数据层：保留平台默认 + 租户覆盖双层模型；新增 `tenant_filter_configs`，按钮继续使用 `platform_button_configs` 的 `tenant_id IS NULL / NOT NULL` 唯一键。
- 修正点：经销商画像保留“查看画像”且移除未实现的导入/导出/新增；销售订单补齐驳回、取消动作桥接。
- 验证：后端 Maven package 通过，业务前端 Vite build 通过，测试环境已部署到 V66。

'''
if '## v3.8.9 列表页规范全站收口' not in s:
    s=s[:idx]+section+s[idx:]
p.write_text(s, encoding='utf-8', newline='\n')
