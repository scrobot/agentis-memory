# Pub/Sub Commands

**Status: NOT SUPPORTED**

Agentis Memory does not implement Pub/Sub messaging. These commands return `-ERR unsupported command '<name>'`.

---

## SUBSCRIBE

**Syntax:** `SUBSCRIBE channel [channel ...]`
**Return:** Array per channel — `[subscribe, channel, count]`. Connection enters subscriber mode.
**Description:** Subscribes to one or more channels. Once subscribed, the connection can only receive messages and execute SUBSCRIBE/UNSUBSCRIBE/PSUBSCRIBE/PUNSUBSCRIBE/PING/QUIT.

## UNSUBSCRIBE

**Syntax:** `UNSUBSCRIBE [channel [channel ...]]`
**Return:** Array per channel — `[unsubscribe, channel, count]`
**Description:** Unsubscribes from one or more channels. Without arguments, unsubscribes from all.

## PUBLISH

**Syntax:** `PUBLISH channel message`
**Return:** Integer — number of clients that received the message
**Description:** Posts a message to a channel.

## PSUBSCRIBE

**Syntax:** `PSUBSCRIBE pattern [pattern ...]`
**Return:** Array per pattern — `[psubscribe, pattern, count]`
**Description:** Subscribes to channels matching glob-style patterns.

## PUNSUBSCRIBE

**Syntax:** `PUNSUBSCRIBE [pattern [pattern ...]]`
**Return:** Array per pattern — `[punsubscribe, pattern, count]`
**Description:** Unsubscribes from patterns. Without arguments, unsubscribes from all patterns.

## PUBSUB

**Subcommands:**
- `PUBSUB CHANNELS [pattern]` — list active channels
- `PUBSUB NUMSUB [channel ...]` — number of subscribers per channel
- `PUBSUB NUMPAT` — number of pattern subscriptions
