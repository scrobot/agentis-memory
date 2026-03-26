# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM ghcr.io/graalvm/jdk-community:26 AS builder

WORKDIR /workspace

# Copy Gradle wrapper and build files first for layer caching
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle.kts gradle.properties ./

RUN chmod +x gradlew

# Pre-fetch dependencies; surface failures clearly instead of silently swallowing them
RUN ./gradlew dependencies --no-daemon -q || echo "WARNING: dependency pre-fetch failed (will retry during build)"

# Copy source and models
COPY src/ src/
COPY models/ models/

# Build the distribution (tests run against the container, not inside the build)
RUN ./gradlew installDist --no-daemon -x test

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Use the JRE-only variant to avoid shipping the full JDK (~1 GB) in the runtime image.
# Java 26 is required for --enable-preview and jdk.incubator.vector; GraalVM CE 26
# is currently the only widely-available Java 26 image. Switch to eclipse-temurin:26-jre
# once it reaches Docker Hub stable.
FROM ghcr.io/graalvm/jre-community:26 AS runtime

WORKDIR /app

# Copy the distribution produced by installDist
COPY --from=builder /workspace/build/install/agentis-memory/ ./

# Copy models so the JVM finds them at ./models/ (adjacent directory lookup path)
COPY --from=builder /workspace/models/ ./models/

# Data directory for AOF + snapshots
RUN mkdir -p /data

EXPOSE 6399

# Bind to 0.0.0.0 so the port is reachable from outside the container.
# The application default is 127.0.0.1 (loopback-only); this override is
# intentional and required for Docker networking.
# The generated start script injects applicationDefaultJvmArgs
# (--enable-preview, --add-modules jdk.incubator.vector) automatically.
ENTRYPOINT ["bin/agentis-memory", "--port", "6399", "--bind", "0.0.0.0", "--data-dir", "/data"]
