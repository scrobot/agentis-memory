# ─── Stage 1: Build Lux from source ──────────────────────────────────────────
FROM rust:1.82-slim AS build

# Install git and build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    pkg-config \
    libssl-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN git clone --depth 1 https://github.com/lux-db/lux.git .

RUN cargo build --release

# ─── Stage 2: Minimal runtime ─────────────────────────────────────────────────
FROM debian:bookworm-slim AS runtime

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    redis-tools \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/release/lux-server /usr/local/bin/lux-server

EXPOSE 6379

ENTRYPOINT ["lux-server", "--port", "6379", "--bind", "0.0.0.0"]
