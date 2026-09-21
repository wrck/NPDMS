package com.dp.deviceops.core.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateful output redactor that evaluates every rule against the same unmodified raw buffer.
 * Credential copies and pending raw characters are cleared when the redactor is closed.
 */
final class StreamingOutputRedactor implements AutoCloseable {

    private static final String REPLACEMENT = "[REDACTED]";
    private static final String[] KEY_PREFIXES = {
            "password", "passphrase", "private_key", "private-key", "private key"
    };
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(password|passphrase|private[_ -]?key)\\s*[:=]\\s*\\S+");
    private static final Pattern INCOMPLETE_KEY_VALUE = Pattern.compile(
            "(?i)(password|passphrase|private[_ -]?key)\\s*(?:[:=]\\s*)?$");

    private final char[] secret;
    private final char[] passphrase;
    private final StringBuilder pending = new StringBuilder();
    private boolean flushed;
    private boolean closed;

    StreamingOutputRedactor(char[] secret, char[] passphrase) {
        this.secret = copy(secret);
        this.passphrase = copy(passphrase);
    }

    String accept(String content) {
        ensureOpen();
        if (flushed) {
            throw new IllegalStateException("streaming output redactor is already flushed");
        }
        if (content != null && !content.isEmpty()) {
            pending.append(content);
        }
        return drain(false);
    }

    String flush() {
        ensureOpen();
        if (flushed) {
            return "";
        }
        String safe = drain(true);
        flushed = true;
        return safe;
    }

    private String drain(boolean emitAll) {
        if (pending.isEmpty()) {
            return "";
        }
        List<Interval> sensitive = mergedSensitiveIntervals();
        int stableEnd = emitAll ? pending.length() : stableEnd(sensitive);
        if (stableEnd == 0) {
            return "";
        }
        StringBuilder safe = new StringBuilder();
        int cursor = 0;
        for (Interval interval : sensitive) {
            if (interval.start() >= stableEnd) {
                break;
            }
            if (interval.end() > stableEnd) {
                throw new IllegalStateException("stable output boundary intersects sensitive content");
            }
            safe.append(pending, cursor, interval.start()).append(REPLACEMENT);
            cursor = interval.end();
        }
        safe.append(pending, cursor, stableEnd);
        retainSuffix(stableEnd);
        return safe.toString();
    }

    private List<Interval> mergedSensitiveIntervals() {
        List<Interval> intervals = new ArrayList<>();
        addExactIntervals(intervals, secret);
        addExactIntervals(intervals, passphrase);
        Matcher keyValue = KEY_VALUE.matcher(pending);
        while (keyValue.find()) {
            intervals.add(new Interval(keyValue.start(), keyValue.end()));
        }
        if (intervals.isEmpty()) {
            return List.of();
        }
        intervals.sort(Comparator.comparingInt(Interval::start).thenComparingInt(Interval::end));
        List<Interval> merged = new ArrayList<>();
        Interval current = intervals.getFirst();
        for (int index = 1; index < intervals.size(); index++) {
            Interval next = intervals.get(index);
            if (next.start() <= current.end()) {
                current = new Interval(current.start(), Math.max(current.end(), next.end()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private void addExactIntervals(List<Interval> intervals, char[] token) {
        if (token.length == 0 || pending.length() < token.length) {
            return;
        }
        int lastStart = pending.length() - token.length;
        for (int start = 0; start <= lastStart; start++) {
            if (matches(pending, start, token, token.length)) {
                intervals.add(new Interval(start, start + token.length));
            }
        }
    }

    private int stableEnd(List<Interval> sensitive) {
        int stableEnd = pending.length();
        stableEnd = Math.min(stableEnd, exactPartialStart(secret));
        stableEnd = Math.min(stableEnd, exactPartialStart(passphrase));
        stableEnd = Math.min(stableEnd, incompleteKeyValueStart());
        stableEnd = Math.min(stableEnd, activeKeyValueStart());
        boolean changed;
        do {
            changed = false;
            for (Interval interval : sensitive) {
                if (interval.start() < stableEnd && interval.end() > stableEnd) {
                    stableEnd = interval.start();
                    changed = true;
                }
            }
        } while (changed);
        return stableEnd;
    }

    private int exactPartialStart(char[] token) {
        if (token.length < 2) {
            return pending.length();
        }
        int maximum = Math.min(pending.length(), token.length - 1);
        for (int length = maximum; length > 0; length--) {
            int start = pending.length() - length;
            if (matches(pending, start, token, length)) {
                return start;
            }
        }
        return pending.length();
    }

    private int incompleteKeyValueStart() {
        Matcher incomplete = INCOMPLETE_KEY_VALUE.matcher(pending);
        int start = incomplete.find() ? incomplete.start() : pending.length();
        int partialLength = longestKeyPrefixSuffix();
        return Math.min(start, pending.length() - partialLength);
    }

    private int activeKeyValueStart() {
        Matcher keyValue = KEY_VALUE.matcher(pending);
        int start = pending.length();
        while (keyValue.find()) {
            if (keyValue.end() == pending.length()
                    && !Character.isWhitespace(pending.charAt(pending.length() - 1))) {
                start = Math.min(start, keyValue.start());
            }
        }
        return start;
    }

    private int longestKeyPrefixSuffix() {
        int longest = 0;
        for (String key : KEY_PREFIXES) {
            int maximum = Math.min(pending.length(), key.length() - 1);
            for (int length = maximum; length > longest; length--) {
                int start = pending.length() - length;
                if (regionMatchesIgnoreCase(pending, start, key, length)) {
                    longest = length;
                    break;
                }
            }
        }
        return longest;
    }

    private void retainSuffix(int stableEnd) {
        int retainedLength = pending.length() - stableEnd;
        if (retainedLength == 0) {
            clear(pending);
            return;
        }
        char[] retained = new char[retainedLength];
        pending.getChars(stableEnd, pending.length(), retained, 0);
        clear(pending);
        pending.append(retained);
        Arrays.fill(retained, '\0');
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        Arrays.fill(secret, '\0');
        Arrays.fill(passphrase, '\0');
        clear(pending);
        closed = true;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("streaming output redactor is closed");
        }
    }

    private static char[] copy(char[] value) {
        return value == null ? new char[0] : Arrays.copyOf(value, value.length);
    }

    private static boolean matches(CharSequence value, int start, char[] token, int length) {
        for (int offset = 0; offset < length; offset++) {
            if (value.charAt(start + offset) != token[offset]) {
                return false;
            }
        }
        return true;
    }

    private static boolean regionMatchesIgnoreCase(
            CharSequence value, int start, String candidate, int length) {
        for (int offset = 0; offset < length; offset++) {
            if (Character.toLowerCase(value.charAt(start + offset))
                    != Character.toLowerCase(candidate.charAt(offset))) {
                return false;
            }
        }
        return true;
    }

    private static void clear(StringBuilder value) {
        Objects.requireNonNull(value, "value");
        for (int index = 0; index < value.length(); index++) {
            value.setCharAt(index, '\0');
        }
        value.setLength(0);
    }

    private record Interval(int start, int end) {
    }
}
