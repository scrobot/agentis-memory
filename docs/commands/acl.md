# ACL Commands

**Status: NOT SUPPORTED**

Agentis Memory supports only simple AUTH with `--requirepass`. Full ACL (Access Control Lists) is not implemented. These commands return `-ERR unsupported command '<name>'`.

---

## ACL CAT

**Syntax:** `ACL CAT [categoryname]`
**Return:** Array — list of ACL categories, or commands in a category
**Description:** Lists ACL categories or commands within a category.

## ACL SETUSER

**Syntax:** `ACL SETUSER username [rule [rule ...]]`
**Return:** Simple String — `OK`
**Description:** Creates or modifies an ACL user with specified permissions.

## ACL DELUSER

**Syntax:** `ACL DELUSER username [username ...]`
**Return:** Integer — number of users deleted
**Description:** Deletes one or more ACL users.

## ACL GETUSER

**Syntax:** `ACL GETUSER username`
**Return:** Array — user details (flags, passwords, commands, keys, channels)
**Description:** Returns the ACL rules for a specific user.

## ACL LIST

**Syntax:** `ACL LIST`
**Return:** Array — ACL rules for all users in config file format
**Description:** Lists all ACL users and their rules.

## ACL USERS

**Syntax:** `ACL USERS`
**Return:** Array — list of all usernames
**Description:** Lists all ACL usernames.

## ACL WHOAMI

**Syntax:** `ACL WHOAMI`
**Return:** Bulk String — the username of the current connection
**Description:** Returns the authenticated username for the current connection.

## ACL LOG

**Syntax:** `ACL LOG [count | RESET]`
**Return:** Array — recent ACL security events, or `OK` on RESET
**Description:** Shows recent ACL violations (denied commands, auth failures).

## ACL LOAD

**Syntax:** `ACL LOAD`
**Return:** Simple String — `OK`
**Description:** Reloads ACL rules from the configured ACL file.

## ACL SAVE

**Syntax:** `ACL SAVE`
**Return:** Simple String — `OK`
**Description:** Saves current ACL rules to the configured ACL file.

## ACL GENPASS

**Syntax:** `ACL GENPASS [bits]`
**Return:** Bulk String — random password
**Description:** Generates a secure random password for ACL use.

## ACL DRYRUN

**Syntax:** `ACL DRYRUN username command [arg [arg ...]]`
**Return:** Simple String — `OK` if allowed, or error message if denied
**Description:** Tests whether a user would be allowed to execute a command without actually executing it.
