# Agentis Memory — Development Guidelines

## Build & Configuration

### Requirements
- **Java 26** (set in `gradle.properties` as `javaVersion=26`). GraalVM JDK 26 is required for `nativeCompile`; a standard JDK 26 suffices for local development and testing.
- All compile and test tasks require `--enable-preview` and `--add-modules jdk.incubator.vector`. These are pre-configured in `build.gradle.kts` — no manual flags needed.
- ONNX model artifacts must be present in `models/` (already committed: `model.onnx`, `tokenizer.json`, `tokenizer_config.json`). The `Embedder` loads them from this directory during local development. The native binary embeds them via `-H:IncludeResources=models/.*`.

### Common Gradle Tasks
```bash
./gradlew build              # compile + test (non-docker)
./gradlew test               # non-docker tests only (default)
./gradlew integrationTest    # docker-tagged tests (requires Docker daemon)
./gradlew run                # start server with default config (port 6399)
./gradlew nativeCompile      # build GraalVM native binary → build/native/nativeCompile/agentis-memory
```

### Running the Server
```bash
# JVM mode (development)
./gradlew run --args="--port 6399 --bind 127.0.0.1"

# Native binary
./build/native/nativeCompile/agentis-memory --port 6399 --data-dir ./data

# Docker
docker compose up
```

Optional config file `agentis-memory.conf` (Redis-style key=value, see `agentis-memory.conf.example`):
```
port 6399
bind 127.0.0.1
max-memory 512mb
aof-fsync everysec
```
CLI flags override config file values.

### Key Defaults
| Parameter | Default | Note |
|---|---|---|
| `--port` | `6399` | Intentionally not 6379 to avoid Redis conflicts |
| `--bind` | `127.0.0.1` | Localhost only; use `0.0.0.0` explicitly for network |
| `--data-dir` | `./data` | AOF + KV snapshots + HNSW snapshots |
| `--max-memory` | `256mb` | KV value bytes only; total RSS is higher (see design spec) |
| `--embedding-threads` | `2` | ONNX inference thread pool size |

---

## Testing

### Test Categories
- **Default (`./gradlew test`)**: all tests _without_ the `@Tag("docker")` annotation. Includes unit tests and in-process integration tests. Runs in ~12s.
- **Docker (`./gradlew integrationTest`)**: tests tagged `@Tag("docker")` (e.g. `DockerIntegrationTest`). Requires a running Docker daemon. Uses Testcontainers to build the image and start the server.

### Unit Tests (command/store/vector layer)
Instantiate components directly — no DI container needed:

```java
class SetCommandTest {
    private KvStore kvStore;
    private SetCommand cmd;

    @BeforeEach
    void setUp() {
        kvStore = new KvStore(new ServerConfig());
        cmd = new SetCommand(kvStore);
    }

    @Test
    void setsValueAndReturnsOk() {
        RespMessage result = cmd.handle(null, args("SET", "mykey", "myvalue"));
        assertInstanceOf(RespMessage.SimpleString.class, result);
        assertEquals("OK", ((RespMessage.SimpleString) result).value());
        assertArrayEquals("myvalue".getBytes(StandardCharsets.UTF_8), kvStore.get("mykey"));
    }

    private List<byte[]> args(String... parts) {
        return Arrays.stream(parts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
```

Key points:
- `ctx` (first arg to `handle`) is `null` in unit tests — only used at the Netty pipeline layer.
- `args()` helper converts `String...` to `List<byte[]>` — replicate this in every new command test.
- `new ServerConfig()` produces a zero-arg default config (all defaults). Override fields directly: `config.port = 6398`.
- Use `assertInstanceOf(RespMessage.Error.class, result)` to check error responses without casting.

### Integration Tests (embedded server)
Use avaje-inject `BeanScope` to wire the full server with a custom config bean, start it on an alternate port, then connect via Jedis:

```java
@BeforeAll
static void setup() {
    ServerConfig config = new ServerConfig();
    config.port = 6398; // avoid collision with any real Redis or other test servers

    scope = BeanScope.builder()
            .bean(ServerConfig.class, config)
            .build();

    server = scope.get(RespServer.class);
    executor = Executors.newSingleThreadExecutor();
    executor.submit(() -> server.start());

    jedis = new Jedis("127.0.0.1", config.port);
    await().atMost(Duration.ofSeconds(5))
           .pollInterval(Duration.ofMillis(200))
           .until(() -> { try { return "PONG".equals(jedis.ping()); } catch (Exception e) { return false; } });
}

@AfterAll
static void tearDown() {
    if (jedis != null) jedis.close();
    if (server != null) server.shutdown();
    if (executor != null) executor.shutdownNow();
    if (scope != null) scope.close();
}
```

- Each integration test class should use a **unique port** to avoid cross-test interference when tests run in parallel.
- Awaitility is the standard way to wait for async behavior (TTL expiry, server startup, MEMSAVE indexation).

### Adding a New Command Test
1. Create `src/test/java/io/agentis/memory/command/kv/<CommandName>Test.java` (package-private class, no `public`).
2. Follow the unit test pattern above: `new KvStore(new ServerConfig())` + direct command instantiation.
3. Cover: happy path, missing args (expect `RespMessage.Error`), edge cases (expired keys, invalid numbers, etc).
4. Run: `./gradlew test --tests "io.agentis.memory.command.kv.<CommandName>Test"`.

### Async Assertions
```java
await().atMost(Duration.ofSeconds(2))
       .pollDelay(Duration.ofMillis(1100))
       .until(() -> store.get("temp") == null);
```
Awaitility is already on the test classpath (`awaitility` in `gradle.properties`).

---

## Code Style & Architecture

### Package Structure
```
io.agentis.memory
├── AgentisMemory          # main(), startup, shutdown hook (SIGTERM/SIGINT)
├── config/ServerConfig    # CLI args + conf file, plain POJO with public fields
├── resp/                  # Netty pipeline: RespDecoder → CommandDispatcher → RespEncoder
│   └── RespMessage        # sealed interface: SimpleString, Error, RespInteger, BulkString, Array
├── command/
│   ├── CommandHandler     # interface: handle(ctx, args) → RespMessage; name(); aliases()
│   ├── CommandRouter      # maps command name → handler; aliases() registered automatically
│   └── kv/                # one class per command, @Singleton + @Inject constructor
├── store/
│   ├── KvStore            # ConcurrentHashMap<String, Entry>; set/get/delete/exists/ttl
│   ├── Entry              # record: value(byte[]), createdAt, expireAt(-1=none), hasVectorIndex
│   └── ExpiryManager      # background lazy + active TTL expiry
└── vector/
    ├── Chunker             # sentence splitter, ~200-300 token chunks with 1-sentence overlap
    ├── Embedder            # ONNX Runtime inference, all-MiniLM-L6-v2, batched
    └── HnswIndex           # jvector HNSW, cosine similarity, namespace post-filter (K×3 overfetch)
```

### DI: avaje-inject
- All singleton beans use `@Singleton` (Jakarta) + `@Inject` on constructor.
- `annotationProcessor("io.avaje:avaje-inject-generator")` generates the wiring at compile time — no runtime reflection. After adding a new `@Singleton` class, a clean build regenerates the wiring.
- Tests bypass DI entirely for unit tests. For integration tests use `BeanScope.builder().bean(ServerConfig.class, customConfig).build()`.

### RespMessage
`RespMessage` is a `sealed interface` with these permitted records:
- `SimpleString(String value)` → `+OK\r\n`
- `Error(String message)` → `-ERR ...\r\n`
- `RespInteger(long value)` → `:1\r\n`
- `BulkString(byte[] value)` / `BulkString(String value)` → `$N\r\n...\r\n`; `null` value → nil bulk string `$-1\r\n`
- `Array(List<RespMessage> elements)` → `*N\r\n...`; `null` list → nil array

### KvStore API
```java
void   set(String key, byte[] value, long ttlSeconds)  // ttlSeconds=-1 for no expiry
byte[] get(String key)                                  // returns null if missing or expired
long   delete(String... keys)                           // returns count deleted
boolean exists(String key)
long   ttl(String key)                                  // -1=no expiry, -2=missing/expired
```

### Custom Commands (MEMSAVE / MEMQUERY)
- `MEMSAVE key value`: KV write is **synchronous** (returns `+OK` immediately); chunking → embedding → HNSW indexation runs **asynchronously** in background. Use `MEMSTATUS key` to poll.
- `MEMQUERY namespace query K`: K must be 1–1000. Namespace `ALL` searches entire index. Fewer than K results is normal for sparse namespaces (not an error).
- Namespace = prefix before first `:` in the key. Keys without `:` belong to `default`.

### Logging
Logback (`src/main/resources/logback.xml`). Use SLF4J:
```java
private static final Logger log = LoggerFactory.getLogger(MyClass.class);
```

---

## Project-Specific Notes

### Design Spec
Full architecture, protocol spec, memory accounting, persistence format, and security model:
`docs/superpowers/specs/2026-03-26-agentis-memory-design.md`

### Snapshot Binary Format
`[magic: 4 bytes "AGMM"][version: uint32][entry_count: uint64][entries...]`
Unknown version → refuse to load with a clear error. Forward compatibility is explicit.

### Memory Accounting
`--max-memory` covers KV value bytes only. Actual RSS ≈ `--max-memory` + (chunk_count × 1.5 KB) + 300 MB baseline (ONNX ~200 MB + Netty/JVM ~100 MB).

### Redis Insight Compatibility
`INFO`, `SCAN`, `DBSIZE`, `TYPE`, `CLIENT SETNAME/INFO`, `CONFIG GET`, `COMMAND` are all implemented (some as stubs) specifically to support Redis Insight. Do not remove these even if they seem unused.

### Known JVM Warnings at Startup
The following warnings are expected and benign during local dev and test runs:
- `sun.misc.Unsafe::allocateMemory` — Netty internal, will be addressed upstream.
- `java.lang.System::load` — ONNX Runtime native library loading, requires `--enable-native-access=ALL-UNNAMED` to suppress.
- `Using incubator modules: jdk.incubator.vector` — expected until Vector API graduates.
