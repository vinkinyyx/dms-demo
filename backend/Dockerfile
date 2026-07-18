# =====================================================================
# DMS Backend Dockerfile - 多阶段构建
# 第一阶段：Maven + JDK 17 构建 jar
# 第二阶段：JRE 17 运行 jar
# 使用方式：docker compose build backend
# =====================================================================

# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先复制 pom 拉取依赖（利用 Docker layer cache）
COPY pom.xml .
RUN mvn -B dependency:go-offline -q || true

# 复制源码并打包
COPY src ./src
RUN mvn -B -DskipTests clean package -q

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 安装 curl 用于健康检查 + 设置时区
RUN apk add --no-cache curl tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 从构建阶段拷贝 jar
COPY --from=builder /build/target/dms-backend-*.jar app.jar

# JVM 参数
ENV JAVA_OPTS="-Xms256m -Xmx1024m -Duser.timezone=Asia/Shanghai"

# 暴露 Spring Boot 端口
EXPOSE 8080

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
