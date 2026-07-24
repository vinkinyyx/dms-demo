# 快速部署脚本 - 将本地修改部署到服务器
# 使用方式: .\quick-deploy.ps1

Write-Host "========================================" -ForegroundColor Green
Write-Host "DMS 快速部署脚本" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# 服务器信息
$server = "8.133.193.238"
$user = "root"
$password = "Welcomeyyx0616"
$remotePath = "/opt/dms"

Write-Host "服务器: $server" -ForegroundColor Yellow
Write-Host "路径: $remotePath" -ForegroundColor Yellow
Write-Host ""

# 第一步: 检查 git 状态
Write-Host "[1/6] 检查 git 状态..." -ForegroundColor Cyan
$gitStatus = git status --porcelain
if ($gitStatus) {
    Write-Host "有未提交的修改，请先提交: " -ForegroundColor Red
    Write-Host $gitStatus -ForegroundColor Red
    Write-Host ""
    Write-Host "请执行: git add . && git commit -m 'fix' && git push" -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "Git 状态干净" -ForegroundColor Green
}
Write-Host ""

# 第二步: 连接服务器并部署
Write-Host "[2/6] 连接到服务器并部署..." -ForegroundColor Cyan
Write-Host "请手动执行以下命令:" -ForegroundColor Yellow
Write-Host ""
Write-Host "ssh $user@$server" -ForegroundColor White
Write-Host ""
Write-Host "然后在服务器上执行:" -ForegroundColor Yellow
Write-Host "cd $remotePath" -ForegroundColor White
Write-Host "git pull origin main" -ForegroundColor White
Write-Host ""

Write-Host "[3/6] 部署后端测试环境..." -ForegroundColor Cyan
Write-Host "bash deploy-test-backend.sh" -ForegroundColor White
Write-Host ""

Write-Host "[4/6] 部署前端测试环境..." -ForegroundColor Cyan
Write-Host "bash deploy-test-frontend.sh" -ForegroundColor White
Write-Host ""

Write-Host "[5/6] 验证部署..." -ForegroundColor Cyan
Write-Host "测试环境地址:" -ForegroundColor Yellow
Write-Host "- 业务工作台: http://$server`:8083/" -ForegroundColor White
Write-Host "- 移动端登录: http://$server`:8083/mobile/login" -ForegroundColor White
Write-Host "- 后端 API: http://$server`:8082/" -ForegroundColor White
Write-Host ""

Write-Host "[6/6] 检查日志和数据库..." -ForegroundColor Cyan
Write-Host "检查后端容器日志:" -ForegroundColor Yellow
Write-Host "docker-compose -f docker-compose.test.yml logs --tail=100 dms-test-backend" -ForegroundColor White
Write-Host ""
Write-Host "检查数据库表:" -ForegroundColor Yellow
Write-Host "docker exec -it dms-postgres-test psql -U dms -d dms_test -c '\dt'" -ForegroundColor White
Write-Host ""
Write-Host "检查日志文件:" -ForegroundColor Yellow
Write-Host "docker exec -it dms-test-backend ls -la /app/logs/" -ForegroundColor White
Write-Host ""
Write-Host "将容器日志复制到服务器:" -ForegroundColor Yellow
Write-Host "cd $remotePath" -ForegroundColor White
Write-Host "mkdir -p logs-backup" -ForegroundColor White
Write-Host "docker cp dms-test-backend:/app/logs/. logs-backup/" -ForegroundColor White
Write-Host "ls -la logs-backup/" -ForegroundColor White
Write-Host ""

Write-Host "========================================" -ForegroundColor Green
Write-Host "部署说明" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "本次部署将更新:" -ForegroundColor Yellow
Write-Host "1. 移动端登录默认密码修复" -ForegroundColor White
Write-Host "2. 移动端路由配置修复" -ForegroundColor White
Write-Host "3. 登录日志功能" -ForegroundColor White
Write-Host "4. 完整的操作日志记录" -ForegroundColor White
Write-Host ""
Write-Host "验证清单:" -ForegroundColor Yellow
Write-Host "1. 移动端登录页默认密码是否为 Sh123456" -ForegroundColor White
Write-Host "2. 移动端登录后是否跳转到 /mobile/home" -ForegroundColor White
Write-Host "3. 后端日志文件是否在 /app/logs/ 目录下" -ForegroundColor White
Write-Host "4. 数据库中是否有 user_login_logs/operation_log/audit_logs 表" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Green
