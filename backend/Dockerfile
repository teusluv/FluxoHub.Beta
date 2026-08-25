# =============================================================================
# Multi-stage Dockerfile para o backend Spring Boot
#
# Estágio 1 (builder): compila o JAR com Maven
# Estágio 2 (runtime): imagem mínima com JRE 21 Alpine
#
# O uso de layers do Spring Boot (habilitado no pom.xml) garante que a
# camada de dependências seja cacheada no Docker, tornando rebuilds muito
# mais rápidos em CI — apenas a camada de código da aplicação é reconstruída.
# =============================================================================

# ---------- Estágio 1: Build ----------
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copiar POM e baixar dependências primeiro (aproveita cache do Docker)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copiar código-fonte e compilar
COPY src ./src
RUN mvn package -DskipTests -q

# Extrair layers do Spring Boot para otimizar a imagem final
RUN java -Djarmode=layertools -jar target/*.jar extract

# ---------- Estágio 2: Runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Usuário não-root por segurança
RUN addgroup -S podgroup && adduser -S poduser -G podgroup
USER poduser

# Copiar layers na ordem correta (menos voláteis primeiro = melhor cache)
COPY --from=builder /build/dependencies/ ./
COPY --from=builder /build/spring-boot-loader/ ./
COPY --from=builder /build/snapshot-dependencies/ ./
COPY --from=builder /build/application/ ./

# Health check interno do container
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]
