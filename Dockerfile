# =========================
# ETAPA 1 — COMPILAÇÃO
# =========================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copia o projeto inteiro
COPY . .

# Compila o Spring Boot
RUN mvn clean package -DskipTests

# =========================
# ETAPA 2 — EXECUÇÃO
# =========================
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copia o JAR gerado pelo Maven
COPY --from=build /app/target/*.jar app.jar

# Porta usada pelo Render
EXPOSE 8080

# Inicia o Spring Boot
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar --server.port=${PORT:-8080} --server.address=0.0.0.0"]
