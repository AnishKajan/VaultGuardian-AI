# Multi-stage build for Spring Boot application
FROM openjdk:17-jdk-slim as builder

# Install Maven
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    maven \
    curl \
    ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set working directory for build
WORKDIR /build

# Copy Maven files first (for better caching)
COPY pom.xml .

# Copy mvnw if it exists, otherwise we'll use system maven
COPY mvnw* ./

# Download dependencies (use maven instead of mvnw)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM openjdk:17-jdk-slim

# Install curl for health checks
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    curl \
    ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r vaultguardian && useradd -r -g vaultguardian vaultguardian

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /build/target/vaultguardian-ai-*.jar app.jar

# Change ownership to non-root user
RUN chown -R vaultguardian:vaultguardian /app

# Switch to non-root user
USER vaultguardian

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM options for production
ENV JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseG1GC -XX:+UseContainerSupport"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]