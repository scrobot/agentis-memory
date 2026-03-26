# Agentis Memory Benchmark — Design Spec

## Overview

Бенчмарк-сьют для сравнения Agentis Memory с Redis, Dragonfly и Lux. Использует индустриальный стандарт `memtier_benchmark` для нагрузки и Python для визуализации.

Четыре сервера, один и тот же набор тестов, красивые графики на выходе.

## Принцип

Никакого самописного бенчмарка. memtier_benchmark — то, чем Redis, Dragonfly, Garnet и все остальные себя меряют. Он многопоточный, поддерживает RESP, выдаёт JSON с HDR гистограммами. Мы просто:

1. Поднимаем четыре сервера через Docker Compose
2. Прогоняем memtier_benchmark по каждому
3. Собираем JSON результаты
4. Генерим графики Python-скриптом

## Targets

| Server | Image | Port | Description |
|---|---|---|---|
| **Agentis Memory** | `agentis-memory:latest` (local build) | 6399 | Наш сервис. Java 26 + GraalVM. |
| **Redis** | `redis:7.4` | 6379 | Эталон. C, single-threaded. |
| **Dragonfly** | `docker.dragonflydb.io/dragonflydb/dragonfly:latest` | 6380 | C++, multi-threaded, shared-nothing, io_uring. Claims 25x Redis. |
| **Lux** | build from `github.com/lux-db/lux` | 6381 | Rust, multi-threaded, sharded. 200+ команд. MIT. Claims 3x Redis on SET. |

## Структура

```
benchmark/
├── docker-compose.yml          # поднимает 4 сервера + memtier
├── run.sh                      # оркестратор: запуск, сбор, визуализация
├── scenarios/                   # конфиги memtier для разных сценариев
│   ├── strings.cfg
│   ├── hashes.cfg
│   ├── lists.cfg
│   ├── sorted-sets.cfg
│   ├── sets.cfg
│   ├── mixed-workload.cfg
│   └── pipeline.cfg
├── results/                     # JSON output от memtier (gitignored)
│   ├── agentis/
│   ├── redis/
│   ├── dragonfly/
│   └── lux/
├── visualize/
│   ├── requirements.txt         # matplotlib, plotly, pandas, jinja2
│   ├── generate_report.py       # парсит JSON, генерит графики
│   ├── templates/
│   │   └── report.html.j2       # Jinja2 шаблон HTML отчёта
│   └── static/
│       └── style.css
├── reports/                     # сгенерированные отчёты (gitignored)
│   ├── report.html              # интерактивный HTML с plotly графиками
│   ├── throughput.png
│   ├── latency_p99.png
│   └── comparison.png
├── agentis-only/                # тесты MEMSAVE/MEMQUERY (наш custom скрипт)
│   ├── memsave_bench.py         # Python + redis-py, замеры MEMSAVE/MEMQUERY
│   └── recall_bench.py          # recall@K measurement
└── README.md                    # как запустить
```

## Docker Compose

```yaml
services:
  agentis-memory:
    build: ..
    ports:
      - "6399:6399"
    healthcheck:
      test: ["CMD", "redis-cli", "-p", "6399", "ping"]
      interval: 5s

  redis:
    image: redis:7.4
    ports:
      - "6379:6379"

  dragonfly:
    image: docker.dragonflydb.io/dragonflydb/dragonfly:latest
    ports:
      - "6380:6379"
    ulimits:
      memlock: -1

  lux:
    build:
      context: ./lux
      dockerfile: Dockerfile
    ports:
      - "6381:6379"
    # Dockerfile for Lux:
    # FROM rust:1.82 AS build
    # RUN git clone https://github.com/lux-db/lux.git /app && cd /app && cargo build --release
    # FROM debian:bookworm-slim
    # COPY --from=build /app/target/release/lux-server /usr/local/bin/
    # EXPOSE 6379
    # ENTRYPOINT ["lux-server", "--port", "6379"]

  memtier:
    image: redislabs/memtier_benchmark:latest
    depends_on:
      agentis-memory:
        condition: service_healthy
      redis:
        condition: service_started
      dragonfly:
        condition: service_started
    entrypoint: ["sleep", "infinity"]  # kept alive, run.sh execs into it
```

## Сценарии memtier

### 1. Strings (SET/GET)

```bash
memtier_benchmark \
  -s $HOST -p $PORT \
  --protocol=resp2 \
  --threads=4 --clients=50 \
  --requests=100000 \
  --ratio=1:10 \
  --data-size=256 \
  --key-pattern=R:R \
  --key-minimum=1 --key-maximum=1000000 \
  --json-out-file=results/strings.json
```

### 2. Hashes

```bash
memtier_benchmark \
  -s $HOST -p $PORT \
  --protocol=resp2 \
  --threads=4 --clients=50 \
  --requests=100000 \
  --command="HSET __key__ field1 __data__ field2 __data__" \
  --command-ratio=1 \
  --command="HGET __key__ field1" \
  --command-ratio=5 \
  --command="HGETALL __key__" \
  --command-ratio=2 \
  --data-size=128 \
  --key-pattern=R:R \
  --json-out-file=results/hashes.json
```

### 3. Lists

```bash
memtier_benchmark \
  -s $HOST -p $PORT \
  --protocol=resp2 \
  --threads=4 --clients=50 \
  --requests=100000 \
  --command="LPUSH __key__ __data__" \
  --command-ratio=3 \
  --command="LRANGE __key__ 0 9" \
  --command-ratio=5 \
  --command="LPOP __key__" \
  --command-ratio=2 \
  --data-size=128 \
  --json-out-file=results/lists.json
```

### 4. Sorted Sets

```bash
memtier_benchmark \
  -s $HOST -p $PORT \
  --protocol=resp2 \
  --threads=4 --clients=50 \
  --requests=100000 \
  --command="ZADD __key__ __key_index__ __data__" \
  --command-ratio=2 \
  --command="ZRANGE __key__ 0 9 WITHSCORES" \
  --command-ratio=5 \
  --command="ZSCORE __key__ __data__" \
  --command-ratio=3 \
  --data-size=64 \
  --json-out-file=results/sorted-sets.json
```

### 5. Sets

```bash
memtier_benchmark \
  -s $HOST -p $PORT \
  --protocol=resp2 \
  --threads=4 --clients=50 \
  --requests=100000 \
  --command="SADD __key__ __data__" \
  --command-ratio=3 \
  --command="SISMEMBER __key__ __data__" \
  --command-ratio=5 \
  --command="SMEMBERS __key__" \
  --command-ratio=2 \
  --data-size=64 \
  --json-out-file=results/sets.json
```

### 6. Mixed Workload

```bash
memtier_benchmark \
  -s $HOST -p $PORT \
  --protocol=resp2 \
  --threads=4 --clients=50 \
  --requests=200000 \
  --ratio=1:4 \
  --data-size=256 \
  --key-pattern=G:G \
  --key-minimum=1 --key-maximum=100000 \
  --json-out-file=results/mixed.json
```

### 7. Pipeline

Прогон strings сценария с разными pipeline depth:

```bash
for PIPELINE in 1 10 50 100; do
  memtier_benchmark \
    -s $HOST -p $PORT \
    --protocol=resp2 \
    --threads=4 --clients=50 \
    --requests=100000 \
    --ratio=1:10 --data-size=256 \
    --pipeline=$PIPELINE \
    --json-out-file=results/pipeline_${PIPELINE}.json
done
```

## run.sh — Оркестратор

```bash
#!/bin/bash
set -euo pipefail

SERVERS=("agentis-memory:6399" "redis:6379" "dragonfly:6380" "lux:6381")
SCENARIOS=(strings hashes lists sorted-sets sets mixed)
PIPELINES=(1 10 50 100)

echo "=== Agentis Memory Benchmark Suite ==="
echo "Targets: ${SERVERS[*]}"

# Warmup
for server in "${SERVERS[@]}"; do
  IFS=: read host port <<< "$server"
  echo "Warming up $host:$port..."
  docker compose exec memtier memtier_benchmark \
    -s $host -p $port --requests=10000 --threads=2 --clients=10 \
    --ratio=1:1 --data-size=64 --hide-histogram > /dev/null 2>&1
  # Flush after warmup
  docker compose exec memtier redis-cli -h $host -p $port FLUSHALL
done

# Run scenarios
for scenario in "${SCENARIOS[@]}"; do
  for server in "${SERVERS[@]}"; do
    IFS=: read host port <<< "$server"
    name=$(echo $host | tr '-' '_')
    echo "Running $scenario on $host:$port..."

    docker compose exec memtier memtier_benchmark \
      -s $host -p $port \
      $(cat scenarios/${scenario}.cfg) \
      --json-out-file=/results/${name}/${scenario}.json

    # Cleanup
    docker compose exec memtier redis-cli -h $host -p $port FLUSHALL
  done
done

# Pipeline benchmarks
for pipeline in "${PIPELINES[@]}"; do
  for server in "${SERVERS[@]}"; do
    IFS=: read host port <<< "$server"
    name=$(echo $host | tr '-' '_')
    echo "Running pipeline=$pipeline on $host:$port..."

    docker compose exec memtier memtier_benchmark \
      -s $host -p $port \
      --protocol=resp2 --threads=4 --clients=50 --requests=100000 \
      --ratio=1:10 --data-size=256 --pipeline=$pipeline \
      --json-out-file=/results/${name}/pipeline_${pipeline}.json

    docker compose exec memtier redis-cli -h $host -p $port FLUSHALL
  done
done

echo "=== Generating report ==="
cd visualize && python generate_report.py ../results/ ../reports/

echo "Report: reports/report.html"
```

## Визуализация (Python)

### requirements.txt

```
matplotlib>=3.9
plotly>=5.24
pandas>=2.2
jinja2>=3.1
kaleido>=0.2    # для экспорта plotly в PNG
```

### generate_report.py — что генерит

**Входные данные:** директория `results/` с JSON файлами от memtier по каждому серверу и сценарию.

**Графики:**

1. **Throughput Bar Chart** (per scenario)
   - X: сценарий (strings, hashes, lists, ...)
   - Y: ops/sec
   - 4 бара рядом: Agentis (синий), Redis (красный), Dragonfly (зелёный), Lux (оранжевый)
   - Подписи значений на барах

2. **Latency Comparison** (per scenario)
   - Grouped bar chart: p50, p95, p99 для каждого сервера
   - Или line chart: percentile на X, latency(ms) на Y, 3 линии

3. **Pipeline Scaling**
   - X: pipeline depth (1, 10, 50, 100)
   - Y: ops/sec
   - 4 линии — как каждый сервер масштабируется с пайплайнингом

4. **Latency Distribution (CDF)**
   - Per scenario: cumulative distribution function
   - 4 кривые — какой сервер какой percentile держит

5. **Summary Heatmap**
   - Rows: сценарии
   - Columns: серверы
   - Цвет: ratio vs Redis (>1 = быстрее, <1 = медленнее)
   - Число внутри ячейки: "0.76x" или "1.2x"

6. **Agentis-Only Section** (MEMSAVE/MEMQUERY)
   - MEMSAVE latency vs text size (bar chart)
   - MEMQUERY latency vs corpus size (line chart)
   - Recall@K (bar chart)

**Формат вывода:**
- `report.html` — интерактивный HTML с plotly графиками (hover, zoom)
- `reports/*.png` — статические PNG для README / презентаций
- Console summary — таблица с ключевыми цифрами

### HTML отчёт (Jinja2 шаблон)

Красивый standalone HTML:
- Metadata: дата, версии серверов, hardware, параметры теста
- Секция на каждый сценарий с графиком + таблицей цифр
- Summary heatmap вверху
- Agentis-only секция внизу
- Тёмная тема, responsive

## Agentis-Only Benchmarks

memtier не знает про MEMSAVE/MEMQUERY. Для них — отдельные Python-скрипты.

### memsave_bench.py

```python
import redis
import time
import statistics

r = redis.Redis(host='localhost', port=6399)

# MEMSAVE latency (sync part)
for text_size in [100, 500, 2000, 10000]:
    text = "word " * (text_size // 5)
    latencies = []
    for i in range(1000):
        key = f"bench:{i}"
        start = time.perf_counter_ns()
        r.execute_command("MEMSAVE", key, text)
        end = time.perf_counter_ns()
        latencies.append((end - start) / 1e6)  # ms

    print(f"MEMSAVE {text_size} chars: "
          f"p50={statistics.median(latencies):.3f}ms "
          f"p95={sorted(latencies)[int(0.95*len(latencies))]:.3f}ms "
          f"p99={sorted(latencies)[int(0.99*len(latencies))]:.3f}ms")

# Indexation time (poll MEMSTATUS)
for text_size in [100, 2000, 10000]:
    text = "word " * (text_size // 5)
    key = f"indexbench:{text_size}"
    r.execute_command("MEMSAVE", key, text)
    start = time.perf_counter_ns()
    while True:
        status = r.execute_command("MEMSTATUS", key)
        if status[0] == b"indexed":
            break
        time.sleep(0.001)
    elapsed = (time.perf_counter_ns() - start) / 1e6
    print(f"Indexation {text_size} chars: {elapsed:.1f}ms")

# MEMQUERY latency vs corpus size
for corpus_size in [100, 500, 1000, 5000]:
    # populate (assume already indexed from above or separate setup)
    latencies = []
    for _ in range(100):
        start = time.perf_counter_ns()
        r.execute_command("MEMQUERY", "bench", "search query about something", "10")
        end = time.perf_counter_ns()
        latencies.append((end - start) / 1e6)

    print(f"MEMQUERY (corpus={corpus_size}, top-10): "
          f"p50={statistics.median(latencies):.3f}ms "
          f"p99={sorted(latencies)[int(0.99*len(latencies))]:.3f}ms")
```

### recall_bench.py

```python
# Populate N texts via MEMSAVE, wait for indexation
# For each test query:
#   1. MEMQUERY → get top-K results
#   2. Embed query via ONNX locally, brute-force cosine against all chunks
#   3. Recall@K = |intersection| / K
# Average recall across all queries
```

Зависимости: `redis-py`, `onnxruntime`, `numpy`, `sentence-transformers` (для ground truth).

## Запуск

```bash
cd benchmark

# Полный прогон
./run.sh

# Только визуализация (из уже собранных results/)
cd visualize && python generate_report.py ../results/ ../reports/

# Только Agentis-specific
cd agentis-only && python memsave_bench.py
cd agentis-only && python recall_bench.py
```

## Критерий готовности

1. `./run.sh` поднимает все 4 сервера, прогоняет все сценарии, генерит `reports/report.html`
2. HTML отчёт содержит все графики: throughput, latency, pipeline scaling, CDF, heatmap
3. PNG экспорт для README
4. Agentis-only скрипты выдают MEMSAVE/MEMQUERY latency и recall@K
5. Никаких ошибок при прогоне (все команды поддержаны)
