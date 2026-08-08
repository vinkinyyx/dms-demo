from pathlib import Path
root = Path(r'D:\Workspace\TRAE\DMS')
# README targeted replacements (file is mojibake in many sections, so only touch known ASCII address lines safely)
p = root/'README.md'
s = p.read_text(encoding='utf-8', errors='replace')
s = s.replace('**正式环境**: PC http://8.133.193.238:8081/，移动端 http://8.133.193.238:8081/mobile/login',
              '**正式环境**: 业务前台 http://8.133.193.238:8081/login ｜ PC 工作台 http://8.133.193.238:8081/ ｜ 移动端 http://8.133.193.238:8081/mobile/login ｜ 平台后台 http://8.133.193.238:8081/admin/')
s = s.replace('**测试环境**: PC http://8.133.193.238:8083/，移动端 http://8.133.193.238:8083/mobile/login',
              '**测试环境**: 业务前台 http://8.133.193.238:8083/login ｜ PC 工作台 http://8.133.193.238:8083/ ｜ 移动端 http://8.133.193.238:8083/mobile/login ｜ 平台后台 http://8.133.193.238:8083/admin/')
s = s.replace('| 业务工作台 | http://8.133.193.238:8081/ |', '| 业务前台登录 | http://8.133.193.238:8081/login |\n| 业务工作台 | http://8.133.193.238:8081/ |')
s = s.replace('| 后台管理 | http://8.133.193.238:8081/admin |', '| 平台后台 | http://8.133.193.238:8081/admin/ |')
s = s.replace('| 移动端 H5 登录 | http://8.133.193.238:8081/mobile/login |', '| 移动端 H5 登录 | http://8.133.193.238:8081/mobile/login |\n| 平台 API | http://8.133.193.238:8080/api/admin/auth/login |')
s = s.replace('| 业务工作台 | http://8.133.193.238:8083/ |', '| 业务前台登录 | http://8.133.193.238:8083/login |\n| 业务工作台 | http://8.133.193.238:8083/ |')
s = s.replace('| 后台管理 | http://8.133.193.238:8083/admin |', '| 平台后台 | http://8.133.193.238:8083/admin/ |')
p.write_text(s, encoding='utf-8', newline='\n')

# package description
p = root/'package.json'
s = p.read_text(encoding='utf-8')
s = s.replace('**正式环境**: PC http://8.133.193.238:8081/，移动端 http://8.133.193.238:8081/mobile/login **测试环境**: PC http://8.133.193.238:8083/，移动端 http://8.133.193.238:8083/mobile/login',
              '**正式环境**: 业务前台 http://8.133.193.238:8081/login ｜ PC 工作台 http://8.133.193.238:8081/ ｜ 移动端 http://8.133.193.238:8081/mobile/login ｜ 平台后台 http://8.133.193.238:8081/admin/ **测试环境**: 业务前台 http://8.133.193.238:8083/login ｜ PC 工作台 http://8.133.193.238:8083/ ｜ 移动端 http://8.133.193.238:8083/mobile/login ｜ 平台后台 http://8.133.193.238:8083/admin/')
p.write_text(s, encoding='utf-8', newline='\n')

# Platform architecture doc
p = root/'docs/11_平台后台/02_架构与多租户隔离设计.md'
s = p.read_text(encoding='utf-8')
s = s.replace('| 平台后台 | `admin-vue` | `/admin/login` | 平台后台管理员使用 |', '| 平台后台 | `admin-vue` | `/admin/`（未登录自动进入登录态） | 平台后台管理员使用；API 走 `/api/admin/auth/login` |')
p.write_text(s, encoding='utf-8', newline='\n')
print('patched README, package.json, platform doc')
