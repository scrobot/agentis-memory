# LangChain

Add working memory to LangChain agents using Agentis Memory as a [tool](https://python.langchain.com/docs/concepts/tools/).

## Install

```bash
pip install langchain langchain-openai redis
```

## Define memory tools

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

## Use with an agent

```python
from langchain_openai import ChatOpenAI
from langchain.agents import create_tool_calling_agent, AgentExecutor
from langchain_core.prompts import ChatPromptTemplate

# Load the agent prompt
agent_prompt_text = open("docs/agent-prompt.md").read()
agent_prompt_text = agent_prompt_text.replace("{AGENTIS_HOST}", "localhost")
agent_prompt_text = agent_prompt_text.replace("{AGENTIS_PORT}", "6399")

prompt = ChatPromptTemplate.from_messages([
    ("system", agent_prompt_text),
    ("human", "{input}"),
    ("placeholder", "{agent_scratchpad}"),
])

llm = ChatOpenAI(model="gpt-4o")
tools = [mem_save, mem_query, mem_status, mem_delete]
agent = create_tool_calling_agent(llm, tools, prompt)
executor = AgentExecutor(agent=agent, tools=tools)

# The agent will autonomously save and search memory
result = executor.invoke({"input": "What do you remember about our test strategy?"})
print(result["output"])
```

## Use with LangGraph

```python
from langgraph.prebuilt import create_react_agent
from langchain_openai import ChatOpenAI

agent = create_react_agent(
    ChatOpenAI(model="gpt-4o"),
    tools=[mem_save, mem_query, mem_status, mem_delete],
    prompt=agent_prompt_text,
)

result = agent.invoke({"messages": [("human", "Remember that we use pytest with fixtures")]})
```

## With auth

```python
mem = redis.Redis(host="localhost", port=6399, password="mysecret", decode_responses=True)
```

## Notes

- Tool docstrings are what the model sees — keep them descriptive
- The agent prompt teaches the model _when_ to save and search; tools define _how_
- Works with any LangChain-compatible LLM (OpenAI, Anthropic, Google, local models)
