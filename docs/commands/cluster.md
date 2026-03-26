# Cluster Commands

**Status: NOT SUPPORTED**

Agentis Memory is a single-node service and does not implement Redis Cluster. These commands return `-ERR unsupported command '<name>'`.

---

## CLUSTER INFO

**Syntax:** `CLUSTER INFO`
**Return:** Bulk String — cluster state information
**Description:** Returns information about the Redis Cluster state.

## CLUSTER NODES

**Syntax:** `CLUSTER NODES`
**Return:** Bulk String — node list in cluster nodes format
**Description:** Returns the cluster configuration as seen by the node.

## CLUSTER MEET

**Syntax:** `CLUSTER MEET ip port [cluster-bus-port]`
**Return:** Simple String — `OK`
**Description:** Connects two nodes to form a cluster.

## CLUSTER ADDSLOTS

**Syntax:** `CLUSTER ADDSLOTS slot [slot ...]`
**Return:** Simple String — `OK`
**Description:** Assigns hash slots to the current node.

## CLUSTER DELSLOTS

**Syntax:** `CLUSTER DELSLOTS slot [slot ...]`
**Return:** Simple String — `OK`

## CLUSTER SETSLOT

**Syntax:** `CLUSTER SETSLOT slot IMPORTING|MIGRATING|NODE|STABLE [node-id]`
**Return:** Simple String — `OK`

## CLUSTER REPLICATE

**Syntax:** `CLUSTER REPLICATE node-id`
**Return:** Simple String — `OK`

## CLUSTER FAILOVER

**Syntax:** `CLUSTER FAILOVER [FORCE|TAKEOVER]`
**Return:** Simple String — `OK`

## CLUSTER RESET

**Syntax:** `CLUSTER RESET [HARD|SOFT]`
**Return:** Simple String — `OK`

## CLUSTER SLOTS

**Syntax:** `CLUSTER SLOTS`
**Return:** Array — slot ranges and associated nodes. Deprecated in favor of CLUSTER SHARDS.

## CLUSTER SHARDS

**Syntax:** `CLUSTER SHARDS`
**Return:** Array — shard information.

## CLUSTER KEYSLOT

**Syntax:** `CLUSTER KEYSLOT key`
**Return:** Integer — the hash slot for the given key.

## CLUSTER COUNTKEYSINSLOT

**Syntax:** `CLUSTER COUNTKEYSINSLOT slot`
**Return:** Integer — number of keys in the slot.

## CLUSTER GETKEYSINSLOT

**Syntax:** `CLUSTER GETKEYSINSLOT slot count`
**Return:** Array — up to `count` key names in the slot.

## CLUSTER MYID

**Syntax:** `CLUSTER MYID`
**Return:** Bulk String — the node ID.

## CLUSTER LINKS

**Syntax:** `CLUSTER LINKS`
**Return:** Array — cluster bus links information.

## READONLY

**Syntax:** `READONLY`
**Return:** Simple String — `OK`
**Description:** Enables read queries on a replica node in cluster mode.

## READWRITE

**Syntax:** `READWRITE`
**Return:** Simple String — `OK`
**Description:** Disables READONLY mode, restoring default behavior.
