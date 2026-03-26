# Bitmap Commands

**Status: NOT SUPPORTED**

Agentis Memory does not implement bitmap operations. These commands return `-ERR unsupported command '<name>'`.

Bitmaps in Redis are not a separate data type — they are string values manipulated at the bit level.

---

## SETBIT

**Syntax:** `SETBIT key offset value`
**Return:** Integer — the original bit value at the offset (0 or 1)
**Description:** Sets or clears the bit at offset in the string value stored at key. The string is auto-grown to accommodate the offset.

## GETBIT

**Syntax:** `GETBIT key offset`
**Return:** Integer — the bit value at offset (0 or 1). Returns 0 if key does not exist or offset exceeds string length.
**Description:** Returns the bit value at the given offset in the string.

## BITCOUNT

**Syntax:** `BITCOUNT key [start end [BYTE|BIT]]`
**Return:** Integer — number of bits set to 1 in the range. Without range, counts all bits.
**Description:** Counts the number of set bits (population count) in a string. BYTE (default) or BIT specifies whether start/end are byte or bit offsets.

## BITOP

**Syntax:** `BITOP AND|OR|XOR|NOT destkey key [key ...]`
**Return:** Integer — the size of the destination string in bytes
**Description:** Performs bitwise operations between strings and stores the result. NOT takes exactly one source key.

## BITPOS

**Syntax:** `BITPOS key bit [start [end [BYTE|BIT]]]`
**Return:** Integer — position of the first bit set to the specified value (0 or 1). Returns -1 if not found.
**Description:** Returns the position of the first 0 or 1 bit in a string.

## BITFIELD

**Syntax:** `BITFIELD key [GET encoding offset | SET encoding offset value | INCRBY encoding offset increment | OVERFLOW WRAP|SAT|FAIL] ...`
**Return:** Array — results of each sub-operation
**Description:** Treats a string as an array of bits and provides atomic read, write, and increment operations on arbitrary bit fields.

## BITFIELD_RO

**Syntax:** `BITFIELD_RO key [GET encoding offset ...]`
**Return:** Array — results of GET operations only
**Description:** Read-only variant of BITFIELD.
