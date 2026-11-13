#################################################################
# Stage 1: Build
#################################################################
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

# Cache do wrapper e dependencias
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Compila o .jar sem rodar os testes (eles rodam no CI)
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

#################################################################
# Stage 2: Runtime
#################################################################
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY --from=build /app/target/oficina-execution-service-*.jar app.jar

# Agente do OpenTelemetry (ADR 002): traces, métricas e logs sem dependência no código
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
RUN chmod 644 /app/opentelemetry-javaagent.jar

EXPOSE 8084

ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
