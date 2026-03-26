# HyperLogLog Commands

**Status: NOT SUPPORTED**

Agentis Memory does not implement HyperLogLog. These commands return `-ERR unsupported command '<name>'`.

---

## PFADD

**Syntax:** `PFADD key [element [element ...]]`
**Return:** Integer — 1 if the internal representation was altered, 0 otherwise
**Description:** Adds elements to a HyperLogLog data structure for approximate cardinality counting.

## PFCOUNT

**Syntax:** `PFCOUNT key [key ...]`
**Return:** Integer — approximate cardinality (number of unique elements). With multiple keys, returns the cardinality of the union.
**Description:** Returns the approximate number of unique elements observed via PFADD.

## PFMERGE

**Syntax:** `PFMERGE destkey sourcekey [sourcekey ...]`
**Return:** Simple String — `OK`
**Description:** Merges multiple HyperLogLog values into a single one (union).

## PFDEBUG

**Syntax:** `PFDEBUG subcommand key`
**Description:** Internal debugging command. Not documented for public use.
