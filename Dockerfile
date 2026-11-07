# ==============================================================================
# Stage 1: Build stage com Eclipse Temurin JDK 25 e Gradle Wrapper
# ==============================================================================
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

# Copiar apenas arquivos de dependência primeiro para aproveitar cache de camadas
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Baixar dependências
RUN ./gradlew dependencies --no-daemon

# Copiar código fonte e recursos
COPY src src

# Compilar e gerar o bootJar ignorando execução de testes durante o empacotamento
RUN ./gradlew bootJar --no-daemon -x test

# ==============================================================================
# Stage 2: Runtime Image minimalista com Eclipse Temurin JRE 25
# ==============================================================================
FROM eclipse-temurin:25-jre

WORKDIR /app

# Criar usuário não-root para segurança
RUN groupadd -r appgroup && useradd -r -g appgroup -u 10001 appuser

# Copiar o JAR gerado do estágio builder
COPY --from=builder --chown=appuser:appgroup /workspace/build/libs/card-receivables-engine-*.jar app.jar

USER appuser

EXPOSE 8080

# Otimizações para ambiente de produção: Virtual Threads ativadas e Gerenciamento de RAM
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]

