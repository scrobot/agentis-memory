# Modular Transport Layer — Design Spec

## Статус: Future Work

Реализовать после завершения шагов 2-7 (все команды, persistence, GraalVM). Требует работающей и протестированной базы.

## Идея

Разбить проект на Gradle-модули. Сетевой/transport слой — за интерфейсом. Три реализации: Netty, Loom, Reactive. Всё остальное (80%+ кода) — общий core. Выбор transport — через конфигурацию при сборке или запуске.

## Модульная структура

```
agentis-memory/
├── core/                          # 80% кода — общий для всех transport-ов
│   ├── build.gradle.kts
│   └── src/main/java/io/agentis/memory/
│       ├── command/               # CommandRouter, все CommandHandler-ы
│       │   ├── CommandHandler.java
│       │   ├── CommandRouter.java
│       │   ├── kv/                # SET, GET, HSET, LPUSH, ZADD, ...
│       │   └── mem/               # MEMSAVE, MEMQUERY, MEMDEL, MEMSTATUS
│       ├── store/                 # KvStore, Entry, ExpiryManager, EvictionManager
│       ├── vector/                # Embedder, Chunker, HnswIndex, VectorEngine
│       ├── persistence/           # AofWriter, AofReader, SnapshotWriter, SnapshotReader
│       ├── config/                # ServerConfig
│       └── transport/             # ИНТЕРФЕЙСЫ (не реализации)
│           ├── TransportServer.java
│           ├── RespParser.java
│           └── RespWriter.java
│
├── transport-netty/               # Текущая реализация
│   ├── build.gradle.kts           # depends on :core, io.netty:netty-all
│   └── src/main/java/io/agentis/memory/transport/netty/
│       ├── NettyTransportServer.java
│       ├── NettyRespDecoder.java
│       ├── NettyRespEncoder.java
│       └── NettyCommandDispatcher.java
│
├── transport-loom/                # Virtual Threads реализация
│   ├── build.gradle.kts           # depends on :core, zero extra deps
│   └── src/main/java/io/agentis/memory/transport/loom/
│       ├── LoomTransportServer.java
│       ├── StreamRespParser.java
│       └── StreamRespWriter.java
│
├── transport-reactive/            # Vert.x + Mutiny реализация
│   ├── build.gradle.kts           # depends on :core, io.vertx:vertx-core, io.smallrye.reactive:mutiny
│   └── src/main/java/io/agentis/memory/transport/reactive/
│       ├── VertxTransportServer.java
│       ├── ReactiveRespParser.java
│       └── ReactiveRespWriter.java
│
├── app/                           # Точка входа, собирает всё вместе
│   ├── build.gradle.kts           # depends on :core + выбранный transport
│   └── src/main/java/io/agentis/memory/
│       └── AgentisMemory.java     # main(), выбор transport из конфига
│
├── benchmark/                     # memtier + визуализация
│   └── ...
│
└── settings.gradle.kts            # include(":core", ":transport-netty", ":transport-loom", ...)
```

## Transport Interface (core модуль)

```java
// core/src/main/java/io/agentis/memory/transport/TransportServer.java

public interface TransportServer extends AutoCloseable {

    /**
     * Start accepting connections.
     * Blocking — returns only when server is shut down.
     */
    void start() throws Exception;

    /**
     * Graceful shutdown.
     * Stop accepting, drain in-flight, close.
     */
    void shutdown();
}
```

```java
// core/src/main/java/io/agentis/memory/transport/ClientConnection.java

public interface ClientConnection {
    /**
     * Read next RESP command from the client.
     * Returns null on disconnect.
     */
    List<byte[]> readCommand() throws IOException;

    /**
     * Write RESP response to the client.
     */
    void writeResponse(RespMessage message) throws IOException;

    /**
     * Flush buffered output.
     */
    void flush() throws IOException;

    /**
     * Get client address (for logging).
     */
    String remoteAddress();

    /**
     * Store per-connection state (auth status, client name, etc.)
     */
    <T> void setAttribute(String key, T value);
    <T> T getAttribute(String key);
}
```

```java
// core/src/main/java/io/agentis/memory/transport/ConnectionHandler.java

/**
 * Shared connection handling logic. Used by all transport implementations.
 * This is where 80% of the "networking" logic lives — auth, dispatch, AOF, logging.
 */
@Singleton
public class ConnectionHandler {
    private final CommandRouter router;
    private final AofWriter aofWriter;
    private final ServerConfig config;

    public void handle(ClientConnection conn) {
        try {
            // Auth gate
            if (config.requirepass() != null) {
                var cmd = conn.readCommand();
                if (!isAuth(cmd)) {
                    conn.writeResponse(RespMessage.error("NOAUTH Authentication required"));
                    return;
                }
                // process AUTH
                var result = router.dispatch("AUTH", cmd);
                conn.writeResponse(result);
                conn.flush();
            }

            // Command loop
            while (true) {
                var args = conn.readCommand();
                if (args == null) break;

                var result = router.dispatch(conn, args);
                conn.writeResponse(result);
                conn.flush();

                if (aofWriter != null && isWriteCommand(args)) {
                    aofWriter.append(args);
                }
            }
        } catch (IOException e) {
            // disconnect, normal
        }
    }
}
```

## Transport реализации

### Netty (transport-netty)

```java
public class NettyTransportServer implements TransportServer {
    // Текущий код, адаптированный к интерфейсу.
    // RespDecoder/RespEncoder остаются, но CommandDispatcher вызывает ConnectionHandler.
    // Netty pipeline: RespDecoder → bridge → ConnectionHandler → RespEncoder
}
```

### Loom (transport-loom)

```java
public class LoomTransportServer implements TransportServer {
    private final ServerConfig config;
    private final ConnectionHandler handler;
    private ServerSocket serverSocket;

    public void start() throws Exception {
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(config.bind(), config.port()));

        while (!serverSocket.isClosed()) {
            var socket = serverSocket.accept();
            Thread.startVirtualThread(() -> {
                var conn = new SocketClientConnection(socket);
                handler.handle(conn);
            });
        }
    }
}
```

### Reactive (transport-reactive)

```java
public class VertxTransportServer implements TransportServer {
    private final Vertx vertx;
    private final ConnectionHandler handler;

    public void start() {
        vertx.createNetServer()
            .connectHandler(socket -> {
                var conn = new VertxClientConnection(socket);
                // В reactive контексте ConnectionHandler.handle() запускается
                // на worker thread (executeBlocking) так как содержит blocking reads.
                // Или адаптируется под reactive API — зависит от глубины интеграции.
                vertx.executeBlocking(() -> { handler.handle(conn); return null; });
            })
            .listen(config.port(), config.bind());
    }
}
```

## Выбор transport при запуске

```bash
# Через CLI флаг
./agentis-memory --transport netty     # дефолт
./agentis-memory --transport loom
./agentis-memory --transport reactive

# Или через конфиг
transport loom
```

## Gradle сборка

```kotlin
// settings.gradle.kts
rootProject.name = "agentis-memory"
include(":core", ":transport-netty", ":transport-loom", ":transport-reactive", ":app", ":benchmark")

// app/build.gradle.kts
dependencies {
    implementation(project(":core"))

    // Включить все три — выбор в runtime
    implementation(project(":transport-netty"))
    implementation(project(":transport-loom"))
    implementation(project(":transport-reactive"))

    // ИЛИ: собрать отдельные бинарники
    // В этом случае — три отдельных app модуля или Gradle feature variants
}
```

Для GraalVM native-image — лучше собирать отдельный бинарник под каждый transport (чтобы не тащить Netty reflection configs в Loom-бинарник):

```kotlin
// Три Gradle tasks:
// nativeCompileNetty — app + core + transport-netty
// nativeCompileLoom — app + core + transport-loom (самый маленький бинарник)
// nativeCompileReactive — app + core + transport-reactive
```

## Что попадает в core (общий код)

| Компонент | Строк (оценка) | Описание |
|---|---|---|
| CommandRouter | ~100 | Dispatch, auth gate, error handling |
| 76 CommandHandler-ов | ~3000 | Все Redis + custom команды |
| KvStore + Entry | ~200 | ConcurrentHashMap, multi-type values |
| ExpiryManager | ~80 | Active + lazy expiry |
| EvictionManager | ~60 | volatile-lru |
| Embedder | ~150 | ONNX Runtime inference |
| Chunker | ~80 | Sentence splitting |
| HnswIndex | ~120 | jvector wrapper |
| VectorEngine | ~150 | Async indexation pipeline |
| AofWriter/Reader | ~200 | Persistence |
| SnapshotWriter/Reader | ~200 | KV + HNSW snapshots |
| ServerConfig | ~100 | CLI + conf parsing |
| ConnectionHandler | ~80 | Shared connection logic |
| RespMessage | ~50 | Sealed interface + records |
| **Total core** | **~4600** | **~90% всего кода** |

| Компонент | Строк | Описание |
|---|---|---|
| transport-netty | ~400 | Decoder, encoder, server, dispatcher |
| transport-loom | ~200 | ServerSocket, InputStream parser, writer |
| transport-reactive | ~350 | Vert.x server, reactive parser, writer |

## Бенчмарк-интеграция

benchmark/ модуль должен уметь тестировать все три transport-а:

```bash
# В docker-compose — три инстанса Agentis Memory с разными transport-ами
# + Redis + Dragonfly + Lux = итого 7 серверов (или выбирать подмножество)
./run.sh --targets agentis-netty,agentis-loom,agentis-reactive,redis,dragonfly,lux
```

Это позволит увидеть не только "Java vs C vs C++ vs Rust", но и "Netty vs Loom vs Reactive" внутри Java.

## Порядок миграции

1. Выделить `core/` модуль — вынести всё кроме Netty-специфичного кода
2. Определить интерфейсы `TransportServer`, `ClientConnection`
3. Обернуть текущий Netty код в `transport-netty/` — адаптировать к интерфейсам
4. Все тесты должны проходить (ничего не сломано)
5. Реализовать `transport-loom/` (~200 строк)
6. Реализовать `transport-reactive/` (~350 строк)
7. Прогнать бенчмарк по всем трём
8. Выбрать дефолт по результатам

**Шаги 1-4 — безопасный рефакторинг, не ломает ничего.**
**Шаги 5-6 — новый код, не трогает существующий.**
