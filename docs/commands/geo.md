# Geo Commands

**Status: NOT SUPPORTED**

Agentis Memory does not implement geospatial indexing. These commands return `-ERR unsupported command '<name>'`.

Geo commands internally use sorted sets — members are stored with geohash-encoded scores.

---

## GEOADD

**Syntax:** `GEOADD key [NX|XX] [CH] longitude latitude member [longitude latitude member ...]`
**Return:** Integer — number of elements added (or changed if CH)
**Description:** Adds geospatial items (longitude, latitude, name) to a sorted set.

## GEODIST

**Syntax:** `GEODIST key member1 member2 [M|KM|FT|MI]`
**Return:** Bulk String — distance as string, or nil if either member does not exist
**Description:** Returns the distance between two members. Default unit: meters.

## GEOHASH

**Syntax:** `GEOHASH key member [member ...]`
**Return:** Array — Geohash strings for each member, or nil for missing members
**Description:** Returns Geohash representation of positions.

## GEOPOS

**Syntax:** `GEOPOS key member [member ...]`
**Return:** Array — `[[longitude, latitude], ...]` or nil for missing members
**Description:** Returns the longitude and latitude of members.

## GEORADIUS

**Syntax:** `GEORADIUS key longitude latitude radius M|KM|FT|MI [WITHCOORD] [WITHDIST] [WITHHASH] [COUNT count [ANY]] [ASC|DESC] [STORE key] [STOREDIST key]`
**Return:** Array — matching members with optional coordinates/distances
**Description:** Finds members within a given radius of a point. Deprecated in favor of GEOSEARCH.

## GEORADIUSBYMEMBER

**Syntax:** `GEORADIUSBYMEMBER key member radius M|KM|FT|MI [WITHCOORD] [WITHDIST] [WITHHASH] [COUNT count [ANY]] [ASC|DESC] [STORE key] [STOREDIST key]`
**Return:** Array — matching members
**Description:** Like GEORADIUS but uses a member's position as center. Deprecated in favor of GEOSEARCH.

## GEOSEARCH

**Syntax:** `GEOSEARCH key FROMMEMBER member|FROMLONLAT longitude latitude BYRADIUS radius M|KM|FT|MI|BYBOX width height M|KM|FT|MI [ASC|DESC] [COUNT count [ANY]] [WITHCOORD] [WITHDIST] [WITHHASH]`
**Return:** Array — matching members
**Description:** Search for members within a radius or bounding box from a point or member.

## GEOSEARCHSTORE

**Syntax:** `GEOSEARCHSTORE destination source FROMMEMBER member|FROMLONLAT longitude latitude BYRADIUS radius M|KM|FT|MI|BYBOX width height M|KM|FT|MI [ASC|DESC] [COUNT count [ANY]] [STOREDIST]`
**Return:** Integer — number of members stored
**Description:** Like GEOSEARCH but stores results in a destination key.
