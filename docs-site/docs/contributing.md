# Contributing

Contributions are welcome! Here's how to get started.

## Development setup

Requirements:

- Java 25+ (GraalVM for native binary)
- Docker (for integration tests via Testcontainers)

```bash
git clone https://github.com/scrobot/agentis-memory.git
cd agentis-memory

# Run unit tests
./gradlew test

# Run integration tests
./gradlew integrationTest

# Build
./gradlew build

# Build native binary (requires GraalVM)
./gradlew nativeCompile
```

The ONNX model files must be in the `models/` directory (not checked into git due to size).

## Before submitting a PR

1. **Open an issue first** for non-trivial changes — let's discuss the approach before you invest time
2. **Write tests** — unit tests for logic, integration tests for command behavior
3. **Run the full test suite** — `./gradlew test integrationTest`
4. **Keep PRs focused** — one feature or fix per PR

## Project structure

```
src/main/java/io/agentis/memory/
├── AgentisMemory.java          # Entry point
├── config/ServerConfig.java    # CLI args + config file
├── resp/                       # RESP protocol (decoder, encoder, server)
├── command/                    # Command handlers
│   ├── kv/                     # Standard Redis commands
│   ├── mem/                    # MEMSAVE, MEMQUERY, MEMDEL, MEMSTATUS
│   ├── list/                   # List commands
│   ├── zset/                   # Sorted set commands
│   └── server/                 # BGSAVE, HELLO, etc.
├── store/                      # KV store, entry, persistence
└── vector/                     # Embedder, HNSW index, chunker
```

## Adding a new command

1. Create a class implementing `CommandHandler` in the appropriate package
2. Annotate with `@Singleton`
3. Implement `handle(ClientConnection conn, List<byte[]> args)` and `name()`
4. The command is auto-registered via Avaje Inject — no manual wiring needed

## License

By contributing, you agree that your contributions will be licensed under the Apache-2.0 License.
