# Stage 1: Build the application with Maven
FROM maven:3-eclipse-temurin-25-alpine AS builder
WORKDIR /build

COPY pom.xml .
# Download dependencies (cached unless pom changes)
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B && cp target/gateway-*.jar target/application.jar

# Stage 2: Extract layers from the fat JAR (jarmode)
FROM eclipse-temurin:25-jre-alpine AS extract
WORKDIR /builder

# Builder leaves a single JAR at target/application.jar
COPY --from=builder /build/target/application.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Stage 3: Runtime image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /application

# curl for healthchecks (Compose / K8s)
RUN apk add --no-cache curl

# Copy extracted layers (order matters for cache)
COPY --from=extract /builder/extracted/dependencies/ ./
COPY --from=extract /builder/extracted/spring-boot-loader/ ./
COPY --from=extract /builder/extracted/snapshot-dependencies/ ./
COPY --from=extract /builder/extracted/application/ ./

# Non-root user and ownership
RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -D appuser \
    && chown -R appuser:appgroup /application
USER 1000:1000

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "application.jar"]
