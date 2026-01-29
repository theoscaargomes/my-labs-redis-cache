FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copia arquivos do Maven
COPY pom.xml .
COPY src ./src

# Build da aplicação (pulando testes para build mais rápido)
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests && \
    mv target/*.jar app.jar

# Stage final - imagem mínima
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copia JAR do builder
COPY --from=builder /app/app.jar .

# Expõe porta da aplicação
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Executa aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]
