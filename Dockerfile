# ─── Stage 1: Build native binary ────────────────────────────────────────────
FROM ghcr.io/graalvm/native-image-community:25 AS builder

WORKDIR /workspace

# Install findutils (provides xargs, required by gradlew)
RUN microdnf install -y findutils && microdnf clean all

# Copy Gradle wrapper and build files first for layer caching
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle.kts gradle.properties ./

RUN chmod +x gradlew

# Pre-fetch dependencies
RUN ./gradlew dependencies --no-daemon -q || echo "WARNING: dependency pre-fetch failed (will retry during build)"

# Copy source and models
COPY src/ src/
COPY models/ models/

# Build native binary
RUN ./gradlew nativeCompile --no-daemon -x test

# ─── Stage 2: Minimal runtime ───────────────────────────────────────────────
# Ubuntu 24.04 LTS provides GLIBC 2.39, required by Oracle GraalVM 25 native binaries (GLIBC 2.38+)
FROM ubuntu:24.04 AS runtime

RUN apt-get update && apt-get install -y --no-install-recommends \
    libstdc++6 zlib1g \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the native binary
COPY --from=builder /workspace/build/native/nativeCompile/agentis-memory ./agentis-memory

# Copy models (needed at runtime for ONNX inference)
COPY --from=builder /workspace/models/ ./models/

# Data directory for AOF + snapshots
RUN mkdir -p /data

EXPOSE 6399

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s \
    CMD exec 3<>/dev/tcp/localhost/6399 && printf '*1\r\n$4\r\nPING\r\n' >&3 && read -t 2 line <&3 && [[ "$line" == *PONG* ]]

CMD ["./agentis-memory", "--port", "6399", "--bind", "0.0.0.0", "--data-dir", "/data"]
