#!/bin/bash
# 使用后端容器里的 Java 生成 bcrypt hash，然后更新 admin 密码
set -e
echo "=== 用 Java 生成 Sh123456 的 bcrypt hash ==="

# 用 docker exec 让后端容器执行 groovy 内嵌 spring-security-crypto
docker exec dms-backend sh -c 'cd /app && unzip -q -o app.jar -d /tmp/app 2>/dev/null; ls /tmp/app/BOOT-INF/lib | grep -i spring-security-crypto | head -1' || true

# 直接用 Python bcrypt（更简单）
docker exec dms-postgres apk add --no-cache python3 py3-pip 2>&1 | tail -2 || true
docker exec dms-postgres python3 -c "
import subprocess
try:
    import bcrypt
except ImportError:
    subprocess.run(['pip3', 'install', 'bcrypt', '--break-system-packages', '-q'], check=True)
    import bcrypt
pw = 'Sh123456'.encode()
h = bcrypt.hashpw(pw, bcrypt.gensalt(rounds=10)).decode()
print('HASH=' + h)
" > /tmp/hashout.txt 2>&1 || cat /tmp/hashout.txt

echo "=== 使用 htpasswd 方式（更通用）==="
# 直接在 backend 容器（有 openjdk）里跑一个 spring-security bcrypt
docker run --rm eclipse-temurin:17-jre-alpine sh -c "
apk add --no-cache openjdk17 >/dev/null 2>&1 || true
cat > /tmp/Bc.java <<'EOF'
import java.lang.reflect.*;
import java.io.*;
public class Bc {
  public static void main(String[] a) throws Exception {
    // 简单实现 bcrypt 太复杂，直接用固定盐生成
    System.out.println(\"use spring boot inside app.jar\");
  }
}
EOF
"

# 最靠谱的方案：直接在后端容器执行 Spring Boot bcrypt
docker exec dms-backend sh -c 'cat > /tmp/gen.sh <<"EOF"
cd /app
mkdir -p /tmp/hashgen
cd /tmp/hashgen
cp /app/app.jar .
# 用后端 jar 里的 BCryptPasswordEncoder
java -cp app.jar -Dloader.main=org.springframework.security.crypto.bcrypt.BCrypt org.springframework.boot.loader.launch.PropertiesLauncher 2>&1 | head -5
EOF
'

# 更简单：用 Python 通过 pip 安装 bcrypt 库
apt-get install -y python3 python3-pip 2>&1 | tail -1 || true
pip3 install bcrypt --break-system-packages -q 2>&1 | tail -1 || pip3 install bcrypt -q 2>&1 | tail -1

python3 -c "
import bcrypt
pw = b'Sh123456'
h = bcrypt.hashpw(pw, bcrypt.gensalt(rounds=10)).decode()
print(h)
" > /tmp/newhash.txt

NEW_HASH=$(cat /tmp/newhash.txt)
echo "New hash: $NEW_HASH"

echo "=== 更新数据库 ==="
docker exec dms-postgres psql -U dms -d dms -c "UPDATE users SET password_hash='$NEW_HASH', must_change_password=false, login_fail_count=0, locked_until=NULL WHERE username='admin';"

echo "=== 验证登录 ==="
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantCode":"default","username":"admin","password":"Sh123456"}'
echo
