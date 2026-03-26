package io.agentis.memory.command.zset;

import io.agentis.memory.resp.RespMessage;
import io.agentis.memory.store.StoreValue;

import java.util.TreeSet;

/**
 * Shared utilities for sorted-set command handlers.
 */
final class ZSetUtil {

    static final RespMessage WRONGTYPE = new RespMessage.Error(
            "WRONGTYPE Operation against a key holding the wrong kind of value");

    private ZSetUtil() {}

    /** Parses a score boundary string: "-inf", "+inf", "(N", "N". */
    static double parseScore(String s) {
        return switch (s.toLowerCase()) {
            case "-inf", "-infinity" -> Double.NEGATIVE_INFINITY;
            case "+inf", "+infinity", "inf", "infinity" -> Double.POSITIVE_INFINITY;
            default -> {
                if (s.startsWith("(")) {
                    yield Double.parseDouble(s.substring(1));
                }
                yield Double.parseDouble(s);
            }
        };
    }

    /** Returns true if the boundary string uses exclusive prefix '('. */
    static boolean isExclusive(String s) {
        return s.startsWith("(");
    }

    /** Formats a score for RESP output (no trailing .0 for integer scores). */
    static byte[] formatScore(double score) {
        if (score == Double.POSITIVE_INFINITY) return "+inf".getBytes();
        if (score == Double.NEGATIVE_INFINITY) return "-inf".getBytes();
        String s = (score == Math.floor(score) && !Double.isInfinite(score))
                ? Long.toString((long) score)
                : Double.toString(score);
        return s.getBytes();
    }

    /**
     * Adds or updates a member in the sorted set structures.
     * Returns true if the member was newly inserted (not just updated).
     */
    static boolean putMember(StoreValue.SortedSetValue sv, String member, double score) {
        Double old = sv.memberToScore().put(member, score);
        if (old != null) {
            // Remove from old score bucket
            TreeSet<String> bucket = sv.scoreToMembers().get(old);
            if (bucket != null) {
                bucket.remove(member);
                if (bucket.isEmpty()) {
                    sv.scoreToMembers().remove(old);
                }
            }
        }
        sv.scoreToMembers().computeIfAbsent(score, k -> new TreeSet<>()).add(member);
        return old == null;
    }

    /**
     * Removes a member from the sorted set structures.
     * Returns true if the member existed.
     */
    static boolean removeMember(StoreValue.SortedSetValue sv, String member) {
        Double score = sv.memberToScore().remove(member);
        if (score == null) return false;
        TreeSet<String> bucket = sv.scoreToMembers().get(score);
        if (bucket != null) {
            bucket.remove(member);
            if (bucket.isEmpty()) {
                sv.scoreToMembers().remove(score);
            }
        }
        return true;
    }
}
