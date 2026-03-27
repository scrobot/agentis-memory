# Vercel AI SDK

Give your AI agent working memory by defining Agentis Memory commands as [Vercel AI SDK tools](https://sdk.vercel.ai/docs/ai-sdk-core/tools-and-tool-calling).

## Install

```bash
npm install ai @ai-sdk/openai redis zod
```

## Define memory tools

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

## Use with generateText

Include the [agent prompt](https://github.com/scrobot/agentis-memory/blob/main/docs/agent-prompt.md) as the system message so the model knows how to use its memory.

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

console.log(text);
```

## Use with streamText

```typescript
import { streamText } from "ai";

const result = streamText({
  model: openai("gpt-4o"),
  system: agentPrompt,
  tools: memoryTools,
  maxSteps: 10,
  prompt: "Save that our API uses rate limiting at 100 req/min",
});

for await (const chunk of result.textStream) {
  process.stdout.write(chunk);
}
```

## With auth

```typescript
const mem = createClient({ url: "redis://:mysecret@localhost:6399" });
```

## Notes

- The model will autonomously call `memSave` and `memQuery` based on the system prompt
- Set `maxSteps` high enough for the model to save/search and then respond (typically 5–10)
- Memory tools work with any model provider (OpenAI, Anthropic, Google, etc.)
