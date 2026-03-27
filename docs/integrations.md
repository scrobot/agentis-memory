# Integrations

How to wire Agentis Memory into your application. Pick your stack — all examples connect to `localhost:6399` (default).

Any Redis client library works. The custom commands (`MEMSAVE`, `MEMQUERY`, `MEMSTATUS`, `MEMDEL`) are sent as raw Redis commands.

## Python (redis-py)

```python
import redis

mem = redis.Redis(host="localhost", port=6399, decode_responses=True)

# Save to semantic memory
mem.execute_command("MEMSAVE", "myagent:fact:stack", "Project uses FastAPI with PostgreSQL")

# Search by meaning
results = mem.execute_command("MEMQUERY", "myagent", "what database does the project use", "5")
# → [["myagent:fact:stack", "Project uses FastAPI with PostgreSQL", "0.94"], ...]

# Check indexing status
status = mem.execute_command("MEMSTATUS", "myagent:fact:stack")
# → ["indexed", 1, 384, 1711451234567]

# Delete
mem.execute_command("MEMDEL", "myagent:fact:stack")

# Standard Redis commands work too
mem.set("myagent:session:id", "abc-123")
mem.hset("myagent:config", mapping={"model": "gpt-4", "temperature": "0.7"})
```

## TypeScript / Node.js (ioredis)

```typescript
import Redis from "ioredis";

const mem = new Redis({ host: "localhost", port: 6399 });

// Save
await mem.call("MEMSAVE", "myagent:pref:style", "User prefers functional style over OOP");

// Search
const results = await mem.call("MEMQUERY", "myagent", "coding preferences", "5");

// Status
const status = await mem.call("MEMSTATUS", "myagent:pref:style");

// Delete
await mem.call("MEMDEL", "myagent:pref:style");

// Standard commands
await mem.set("myagent:session:id", "abc-123");
await mem.hset("myagent:config", { model: "gpt-4", temperature: "0.7" });
```

## Vercel AI SDK

Define memory as tools so the model calls them autonomously. Include the [agent prompt](agent-prompt.md) in your system message.

```typescript
import { createClient } from "redis";
import { tool } from "ai";
import { z } from "zod";

const mem = createClient({ url: "redis://localhost:6399" });
await mem.connect();

export const memoryTools = {
  memSave: tool({
    description: "Save a fact or observation to working memory for later retrieval",
    parameters: z.object({
      key: z.string().describe("Key in format namespace:category:topic"),
      text: z.string().describe("The fact or observation to remember"),
    }),
    execute: async ({ key, text }) => {
      await mem.sendCommand(["MEMSAVE", key, text]);
      return "Saved to memory.";
    },
  }),

  memQuery: tool({
    description: "Search working memory by meaning to find relevant prior knowledge",
    parameters: z.object({
      namespace: z.string().describe("Namespace to search, or ALL for everything"),
      query: z.string().describe("Natural language search query"),
      count: z.number().default(5).describe("Max results to return (1-1000)"),
    }),
    execute: async ({ namespace, query, count }) => {
      const results = await mem.sendCommand(["MEMQUERY", namespace, query, String(count)]);
      return results;
    },
  }),

  memStatus: tool({
    description: "Check if a memory entry has been indexed and is searchable",
    parameters: z.object({
      key: z.string().describe("Key to check"),
    }),
    execute: async ({ key }) => {
      return await mem.sendCommand(["MEMSTATUS", key]);
    },
  }),

  memDelete: tool({
    description: "Delete a key from working memory",
    parameters: z.object({
      key: z.string().describe("Key to delete"),
    }),
    execute: async ({ key }) => {
      const removed = await mem.sendCommand(["MEMDEL", key]);
      return removed === 1 ? "Deleted." : "Key not found.";
    },
  }),
};
```

Usage with `generateText`:

```typescript
import { generateText } from "ai";
import { openai } from "@ai-sdk/openai";
import { readFileSync } from "fs";

const agentPrompt = readFileSync("docs/agent-prompt.md", "utf-8")
  .replace("{AGENTIS_HOST}", "localhost")
  .replace("{AGENTIS_PORT}", "6399");

const { text } = await generateText({
  model: openai("gpt-4o"),
  system: agentPrompt,
  tools: memoryTools,
  maxSteps: 10,
  prompt: "What do you remember about our deployment process?",
});
```

## LangChain (Python)

```python
from langchain_core.tools import tool
import redis

mem = redis.Redis(host="localhost", port=6399, decode_responses=True)


@tool
def mem_save(key: str, text: str) -> str:
    """Save a fact or observation to working memory.
    Key format: namespace:category:topic
    Categories: fact, obs, pref, fix, ctx, policy"""
    mem.execute_command("MEMSAVE", key, text)
    return "Saved."


@tool
def mem_query(namespace: str, query: str, count: int = 5) -> list:
    """Search working memory by meaning.
    Use ALL as namespace for cross-agent search.
    Returns [[key, text, score], ...] sorted by relevance."""
    return mem.execute_command("MEMQUERY", namespace, query, str(count))


@tool
def mem_status(key: str) -> list:
    """Check indexing status of a memory entry.
    Returns [status, chunk_count, dimensions, timestamp]."""
    return mem.execute_command("MEMSTATUS", key)


@tool
def mem_delete(key: str) -> str:
    """Delete a key from working memory."""
    removed = mem.execute_command("MEMDEL", key)
    return "Deleted." if removed == 1 else "Key not found."
```

Usage with an agent:

```python
from langchain_openai import ChatOpenAI
from langchain.agents import create_tool_calling_agent, AgentExecutor
from langchain_core.prompts import ChatPromptTemplate

agent_prompt = open("docs/agent-prompt.md").read()
agent_prompt = agent_prompt.replace("{AGENTIS_HOST}", "localhost").replace("{AGENTIS_PORT}", "6399")

prompt = ChatPromptTemplate.from_messages([
    ("system", agent_prompt),
    ("human", "{input}"),
    ("placeholder", "{agent_scratchpad}"),
])

llm = ChatOpenAI(model="gpt-4o")
tools = [mem_save, mem_query, mem_status, mem_delete]
agent = create_tool_calling_agent(llm, tools, prompt)
executor = AgentExecutor(agent=agent, tools=tools)

executor.invoke({"input": "What do you remember about our test strategy?"})
```

## Go (go-redis)

```go
package main

import (
	"context"
	"fmt"

	"github.com/redis/go-redis/v9"
)

func main() {
	mem := redis.NewClient(&redis.Options{Addr: "localhost:6399"})
	ctx := context.Background()

	// Save
	mem.Do(ctx, "MEMSAVE", "myagent:fact:stack", "Service uses Go with Chi router").Result()

	// Search
	results, _ := mem.Do(ctx, "MEMQUERY", "myagent", "what framework", "5").Result()
	fmt.Println(results)

	// Status
	status, _ := mem.Do(ctx, "MEMSTATUS", "myagent:fact:stack").Result()
	fmt.Println(status)

	// Delete
	mem.Do(ctx, "MEMDEL", "myagent:fact:stack").Result()

	// Standard commands
	mem.Set(ctx, "myagent:session:id", "abc-123", 0)
	mem.HSet(ctx, "myagent:config", "model", "gpt-4")
}
```

## Java (Jedis)

```java
import redis.clients.jedis.Jedis;

try (var mem = new Jedis("localhost", 6399)) {
    // Save
    mem.sendCommand(() -> "MEMSAVE".getBytes(),
        "myagent:fact:stack".getBytes(),
        "Project uses Java 26 with GraalVM".getBytes());

    // Search
    var results = mem.sendCommand(() -> "MEMQUERY".getBytes(),
        "myagent".getBytes(),
        "what language".getBytes(),
        "5".getBytes());

    // Status
    var status = mem.sendCommand(() -> "MEMSTATUS".getBytes(),
        "myagent:fact:stack".getBytes());

    // Delete
    mem.sendCommand(() -> "MEMDEL".getBytes(),
        "myagent:fact:stack".getBytes());

    // Standard commands
    mem.set("myagent:session:id", "abc-123");
    mem.hset("myagent:config", "model", "gpt-4");
}
```

## redis-cli

```bash
# Semantic memory
redis-cli -p 6399 MEMSAVE "myagent:fact:stack" "Project uses Rust with Actix"
redis-cli -p 6399 MEMQUERY myagent "web framework" 5
redis-cli -p 6399 MEMSTATUS "myagent:fact:stack"
redis-cli -p 6399 MEMDEL "myagent:fact:stack"

# Standard commands
redis-cli -p 6399 SET "myagent:session:id" "abc-123"
redis-cli -p 6399 HSET "myagent:config" model gpt-4
redis-cli -p 6399 INFO
redis-cli -p 6399 DBSIZE
```

## Raw TCP (bash, zero dependencies)

For environments where `redis-cli` is not available:

```bash
AGENTIS_HOST=localhost
AGENTIS_PORT=6399

# Helper: send a RESP command and read the response
agentis_cmd() {
  exec 3<>/dev/tcp/$AGENTIS_HOST/$AGENTIS_PORT
  local argc=$#
  printf "*${argc}\r\n" >&3
  for arg in "$@"; do
    printf "\$${#arg}\r\n${arg}\r\n" >&3
  done
  local response=""
  while IFS= read -t 2 -r line <&3; do
    response+="$line"$'\n'
  done
  exec 3>&-
  echo "$response"
}

# Usage
agentis_cmd MEMSAVE "myagent:fact:db" "Project uses PostgreSQL 16"
agentis_cmd MEMQUERY "myagent" "database" "5"
agentis_cmd PING
```

## With AUTH

If the server requires a password (`--requirepass`), authenticate before sending commands:

```python
# Python
mem = redis.Redis(host="localhost", port=6399, password="mysecret", decode_responses=True)
```

```typescript
// TypeScript
const mem = new Redis({ host: "localhost", port: 6399, password: "mysecret" });
```

```bash
# redis-cli
redis-cli -p 6399 -a mysecret MEMSAVE "myagent:fact:db" "Uses PostgreSQL"
```

```bash
# Raw TCP — send AUTH first
agentis_cmd AUTH "mysecret"
agentis_cmd MEMSAVE "myagent:fact:db" "Uses PostgreSQL"
```
