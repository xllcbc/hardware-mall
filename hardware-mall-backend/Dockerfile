# syntax=docker/dockerfile:1.6
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
# 构建阶段同样校验：Maven 依赖下载也走 JDK 信任库
COPY scripts/check-jdk-truststore.sh /usr/local/bin/check-jdk-truststore.sh
RUN sh /usr/local/bin/check-jdk-truststore.sh
COPY pom.xml .
RUN mvn -B -q dependency:resolve
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# 固定补丁版本：17.0.21+9 起 Temurin 已从信任库移除被泄的 eMudhra/Sectigo 根证书
# （CVE-2026-91403 / CVE-2026-114820，详见 docs/security/CVE-2026-91403.md）。
# 升级 JDK 时改这里的 tag；脚本会兜底，基础镜像若回退到未修复版本，构建直接失败。
FROM eclipse-temurin:17.0.21_9-jre-alpine
WORKDIR /app
COPY scripts/check-jdk-truststore.sh /usr/local/bin/check-jdk-truststore.sh
RUN sh /usr/local/bin/check-jdk-truststore.sh
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone
COPY --from=builder /build/target/hardware-mall-*.jar app.jar

RUN mkdir -p /var/log/hardware-mall && chmod 777 /var/log/hardware-mall
VOLUME ["/var/log/hardware-mall"]

ENV JAVA_OPTS="-Xms256m -Xmx384m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fs http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
