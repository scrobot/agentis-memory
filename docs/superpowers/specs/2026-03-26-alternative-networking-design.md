# Alternative Networking Architectures — Design Spec

## Контекст

Текущая архитектура использует Netty (event loop, non-blocking I/O, callback pipeline). Это работает, но код сложный: `ByteToMessageDecoder`, `ChannelHandlerContext`, pipeline конфигурация, reflection-тяжёлый для GraalVM.

Здесь описаны два альтернативных подхода, каждый из которых может заменить Netty в RESP Protocol Layer.

---

## Вариант A: Project Loom (Virtual Threads)

### Идея

Убрать Netty. Один `ServerSocket`, на каждое входящее соединение — virtual thread. Внутри — обычный блокирующий `InputStream`/`OutputStream`. Код читается линейно, как синхронный.

### Архитектура

```
┌───────────────────────────────────────────┐
│         ServerSocket :6399                │
│    accept() → Thread.startVirtualThread() │
├───────────────────────────────────────────┤
│         ConnectionHandler (per client)    │
│                                           │
│   InputStream → RespParser.readCommand()  │  ← blocking read, linear code
│   CommandRouter.dispatch(command)          │
│   OutputStream → RespWriter.write(result) │  ← blocking write
│                                           │
│   loop until disconnect                   │
└───────────────────────────────────────────┘
```

### Как выглядит код

```java
// Server startup — вся "сетевая инфраструктура"
void start() {
    var server = new ServerSocket(config.port());
    server.bind(new InetSocketAddress(config.bind(), config.port()));

    while (!stopped) {
        var socket = server.accept();
        Thread.startVirtualThread(() -> handleConnection(socket));
    }
}

// Connection handler — линейный, блокирующий, понятный
void handleConnection(Socket socket) {
    try (var in = socket.getInputStream();
         var out = socket.getOutputStream()) {

        var parser = new RespParser(in);    // читает из стрима
        var writer = new RespWriter(out);   // пишет в стрим

        if (config.requirepass() != null) {
            // первая команда должна быть AUTH
            var cmd = parser.readCommand();
            if (!isAuth(cmd)) {
                writer.write(RespMessage.error("NOAUTH Authentication required"));
                return;
            }
        }

        while (!socket.isClosed()) {
            var command = parser.readCommand();  // blocking read
            if (command == null) break;          // client disconnected

            var result = router.dispatch(command);
            writer.write(result);                // blocking write

            if (aofWriter != null && command.isWrite()) {
                aofWriter.append(command);
            }
        }
    } catch (IOException e) {
        // client disconnected, normal
    }
}
```

```java
// RESP Parser — чистый InputStream, никаких ByteBuf
class RespParser {
    private final InputStream in;
    private final byte[] buf = new byte[65536];

    Command readCommand() throws IOException {
        int type = in.read();  // blocking!
        return switch (type) {
            case '*' -> readArray();
            case '$' -> readBulkString();
            case '+' -> readSimpleString();
            case -1  -> null;  // EOF
            default  -> throw new ProtocolException("Unknown type: " + (char) type);
        };
    }

    private byte[] readBulkString() throws IOException {
        int len = readInt();
        if (len == -1) return null;
        byte[] data = in.readNBytes(len);  // blocking, exact count
        in.readNBytes(2);                   // \r\n
        return data;
    }
    // ...
}
```

### Pipelining

Redis-клиенты шлют несколько команд подряд без ожидания ответа. С блокирующим I/O это работает автоматически: `readCommand()` читает следующую команду из буфера сокета, `write()` пишет ответ. TCP буферизация на уровне OS делает своё дело. Для агрессивного pipelining (1000 команд за раз) может быть чуть медленнее чем Netty (который обрабатывает весь буфер в одном event loop цикле), но для нашего use-case (агентская память, не high-frequency trading) — достаточно.

Оптимизация: `BufferedOutputStream` с `flush()` после каждой команды. Или batch flush — накапливать ответы и flush раз в N команд или по таймеру.

### Плюсы

- **Радикально проще код.** ~150 строк вместо ~500 (Netty decoder/encoder/handler/pipeline/bootstrap). Никаких `ChannelHandlerContext`, `ByteBuf`, `SimpleChannelInboundHandler`.
- **Zero dependencies.** Нет Netty (4MB jar, reflection-тяжёлый). Всё на `java.net.*` и `java.io.*`.
- **GraalVM native-image проще.** Нет Netty reflection configs. `ServerSocket` и virtual threads — полностью поддерживаются нативно.
- **Легко дебажить.** Stack traces линейные, breakpoints работают как ожидается. Нет callback hell.
- **Масштабируется.** Миллион virtual threads — не проблема. Каждый клиент = свой поток, OS не потеет.

### Минусы

- **Pipeline throughput.** Netty обрабатывает батч команд за один event loop tick (zero syscall overhead между ними). Blocking I/O — syscall на каждый read. Для 99% use-cases незаметно, но на микробенчмарках Netty может быть быстрее.
- **Нет io_uring.** Netty 4.2 поддерживает io_uring на Linux — самый быстрый способ делать I/O. `ServerSocket` использует epoll/kqueue, что чуть медленнее.
- **Нет встроенного backpressure.** Netty имеет `writeAndFlush()` с `ChannelFuture` и `isWritable()`. С blocking I/O — если клиент медленно читает, virtual thread просто заблокируется на `write()`. Это нормально (thread дешёвый), но нет явного контроля.

### Оценка сложности миграции

1. Удалить: `RespDecoder.java`, `RespEncoder.java`, `RespServer.java`, `CommandDispatcher.java` (~500 строк)
2. Создать: `RespParser.java` (InputStream-based), `RespWriter.java` (OutputStream-based), `ConnectionHandler.java`, обновить `AgentisMemory.java` (~300 строк)
3. CommandRouter, KvStore, VectorEngine — **не меняются**
4. Тесты: integration тесты не меняются (Jedis подключается к порту, ему всё равно что внутри)
5. Убрать dependency: `io.netty:netty-all`

**Scope: ~2 дня работы.**

---

## Вариант B: Reactive (Project Reactor / Mutiny)

### Идея

Заменить Netty callbacks на reactive streams. Декларативный пайплайн обработки: `Flux`/`Mono` (Reactor) или `Uni`/`Multi` (Mutiny). Код описывает ЧТО, а не КАК. Backpressure встроен. Ресурсы утилизируются оптимально.

### Какой фреймворк

Два реалистичных варианта:

**a) Reactor Netty** (Spring ecosystem) — Netty под капотом, но API реактивный. Используется в Spring WebFlux и R2DBC. Зрелый, огромное комьюнити. Но: тяжёлый, Spring-гравитация.

**b) Vert.x** — event-driven reactive toolkit. Использует Netty внутри, но API гораздо проще. `Vert.x TCP Server` + reactive pipeline. Легковесный, без Spring. Есть Redis protocol parser. **Рекомендую этот.**

**c) SmallRye Mutiny** (Quarkus ecosystem) — reactive types `Uni`/`Multi` как альтернатива Reactor. Проще чем Reactor, менее церемониальный. Обычно используется с Vert.x.

### Архитектура (Vert.x)

```
┌──────────────────────────────────────────────┐
│         Vert.x TCP Server :6399              │
│         Event Loop (Netty inside)            │
├──────────────────────────────────────────────┤
│         NetSocket handler (per connection)   │
│                                              │
│   socket.handler(buffer →                    │
│     RespParser.parse(buffer)                 │
│       .flatMap(cmd → router.dispatch(cmd))   │
│       .map(result → RespEncoder.encode())    │
│       .subscribe(bytes → socket.write(bytes))│
│   )                                          │
└──────────────────────────────────────────────┘
```

### Как выглядит код

```java
// Server startup
var vertx = Vertx.vertx();
vertx.createNetServer()
    .connectHandler(this::handleConnection)
    .listen(config.port(), config.bind());

// Connection handler
void handleConnection(NetSocket socket) {
    var parser = new RespStreamParser();  // stateful parser

    socket.handler(buffer -> {
        parser.feed(buffer)              // push bytes into parser
            .onItem().transformToMulti(commands -> Multi.createFrom().iterable(commands))
            .onItem().transformToUniAndMerge(cmd ->
                router.dispatch(cmd)      // returns Uni<RespMessage>
                    .onItem().transform(RespEncoder::encode)
            )
            .subscribe().with(
                encoded -> socket.write(encoded),
                err -> socket.close()
            );
    });
}
```

```java
// CommandRouter — reactive dispatch
class CommandRouter {
    Uni<RespMessage> dispatch(Command cmd) {
        var handler = handlers.get(cmd.name());
        if (handler == null) {
            return Uni.createFrom().item(RespMessage.error("ERR unknown command '" + cmd.name() + "'"));
        }

        // Sync commands (GET, SET) — wrap in Uni
        if (handler.isSync()) {
            return Uni.createFrom().item(() -> handler.handle(cmd.args()));
        }

        // Async commands (MEMSAVE) — naturally reactive
        return handler.handleAsync(cmd.args());
    }
}
```

```java
// MEMSAVE — naturally async
class MemSaveCommand {
    Uni<RespMessage> handleAsync(List<byte[]> args) {
        String key = new String(args.get(0));
        String value = new String(args.get(1));

        // Store in KV synchronously
        kvStore.set(key, args.get(1), -1);

        // Trigger async indexation — returns immediately
        return vectorEngine.indexAsync(key, value)
            .onItemOrFailure().transform((v, err) -> {
                if (err != null) log.error("Indexation failed", err);
                return null;  // fire-and-forget
            })
            .onItem().transform(v -> RespMessage.ok())
            // Respond OK immediately, don't wait for indexation
            .ifNoItem().after(Duration.ZERO).recoverWithItem(RespMessage.ok());
    }
}

// Actually simpler: indexation is fire-and-forget
Uni<RespMessage> handleAsync(List<byte[]> args) {
    kvStore.set(key, value, -1);
    vectorEngine.indexAsync(key, value).subscribe().with(v -> {}, err -> log.error("", err));
    return Uni.createFrom().item(RespMessage.ok());
}
```

### Плюсы

- **MEMSAVE/MEMQUERY становятся нативно-асинхронными.** Сейчас у нас `CompletableFuture` / `ExecutorService` для async indexation. В reactive — это `Uni`/`Multi`, композиция, cancellation, retry, timeout — всё из коробки.
- **Backpressure.** Если HNSW search медленный, reactive pipeline автоматически замедлит чтение из сокета. Не нужен ручной flow control.
- **Vert.x event loop = Netty performance.** Та же производительность что сейчас, но API чище.
- **Vert.x + GraalVM** — хорошо поддерживается (Quarkus/Vert.x — основной стек для GraalVM native).
- **Worker pool для CPU-bound.** ONNX inference можно вынести на Vert.x worker pool: `vertx.executeBlocking(() -> embedder.embed(text))` — возвращает `Uni`, не блокирует event loop.

### Минусы

- **Reactive learning curve.** Если команда не привычна к reactive — дебаг сложнее, stack traces нечитаемые, ментальная модель другая.
- **Overkill для sync операций.** GET/SET — это `map.get(key)`. Оборачивать в `Uni` — overhead по allocation. Для 90% команд reactive не нужен.
- **Vert.x зависимость.** Vert.x тащит Netty + Jackson + свой event loop. Не zero-dependency.
- **RESP parser.** Vert.x не имеет встроенного RESP парсера. Нужно написать `RecordParser`-based RESP decoder (или портировать наш текущий). Есть community-пакет `vertx-redis-client`, но он клиент, не сервер.

### Оценка сложности миграции

1. Добавить dependency: `io.vertx:vertx-core`
2. Удалить: `RespDecoder.java`, `RespEncoder.java`, `RespServer.java`, `CommandDispatcher.java`
3. Создать: `VertxRespServer.java`, `RespStreamParser.java` (RecordParser-based), обновить `CommandRouter` для reactive dispatch
4. Адаптировать `VectorEngine.indexAsync()` на Vert.x / Mutiny
5. KvStore, Chunker, Embedder, HnswIndex — **не меняются**
6. Тесты: integration тесты не меняются

**Scope: ~3-4 дня работы.**

---

## Сравнение трёх подходов

| Аспект | Netty (текущий) | Loom (Virtual Threads) | Reactive (Vert.x) |
|---|---|---|---|
| **Строк кода (networking)** | ~500 | ~300 | ~400 |
| **Dependencies** | netty-all (4MB) | Zero | vertx-core (3MB) |
| **GraalVM native** | Сложно (reflection) | Просто | Средне (Vert.x поддержан) |
| **Pipeline perf** | Максимум (batch в event loop) | Хорошо (OS TCP буферы) | Максимум (event loop) |
| **io_uring** | Да (Netty 4.2) | Нет | Да (Vert.x/Netty) |
| **Код читаемость** | Низкая (callbacks) | Высокая (линейный) | Средняя (reactive chains) |
| **Debuggability** | Сложно | Просто | Средне |
| **Async MEMSAVE** | Manual (ExecutorService) | Manual (ExecutorService) | Нативно (Uni/Multi) |
| **Backpressure** | Встроен (Netty) | Нет (thread блокируется) | Встроен (Reactive Streams) |
| **Concurrency model** | Event loop (few threads) | Thread-per-connection (millions) | Event loop (few threads) |
| **Миграция** | Текущий | ~2 дня | ~3-4 дня |
| **Risk** | Proven | Low (stdlib only) | Medium (new framework) |

## Рекомендация

**Для Agentis Memory — Loom (вариант A).**

Причины:
1. Код станет радикально проще — а это ключевое для проекта на ранней стадии
2. Zero dependencies — идеально для "как Whisper" философии
3. GraalVM native-image без Netty reflection hell
4. Java 26 virtual threads — это не эксперимент, они stable с Java 21
5. Performance достаточна — агенты не делают миллион RPS

Reactive (вариант B) стоит рассмотреть если:
- Появится потребность в streaming (долгие MEMQUERY по большим корпусам)
- Нужен backpressure на уровне команд
- Команда уже знает reactive

---

## Решение

Эта спека — для будущей работы. Текущая Netty-архитектура работает. Миграция на Loom или Reactive — отдельный step, когда появится свободное время или когда Netty reflection станет блокером для GraalVM.
