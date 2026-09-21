package com.dp.deviceops.parser.semantic.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative lexical boundaries for the enhanced KV parser, not command-specific rules. */
final class EnhancedKeyValueLexer {

    private static final Pattern KEY = Pattern.compile("[\\p{L}\\p{N}_][\\p{L}\\p{N} _./()\\[\\]%-]*");
    private static final Pattern TIME = Pattern.compile("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b");
    private static final Pattern EVENT = Pattern.compile(
            "^(?:<\\d+>|\\[?(?:TRACE|DEBUG|INFO|NOTICE|WARN(?:ING)?|ERROR|CRIT(?:ICAL)?|ALERT|EMERG|FATAL)\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern URL = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://");
    private static final Pattern WORD = Pattern.compile("\\S+");
    private static final Pattern WIDE_SPACE = Pattern.compile("\\s{2,}|\\t");

    private EnhancedKeyValueLexer() {
    }

    static List<Pair> parse(String text) {
        String content = text.strip();
        int first = firstDelimiter(content);
        if (first < 0 || nonFieldPrefix(content, first) || !validKey(content.substring(0, first).strip())) {
            return List.of();
        }
        List<Pair> pairs = new ArrayList<>();
        String key = content.substring(0, first).strip();
        int valueStart = first + 1;
        char quote = 0;
        for (int index = valueStart; index < content.length(); index++) {
            char current = content.charAt(index);
            if (quote != 0) {
                if (current == quote && (index == 0 || content.charAt(index - 1) != '\\')) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (current != ':' && current != '=') {
                continue;
            }
            int tokenStart = index;
            while (tokenStart > valueStart && !Character.isWhitespace(content.charAt(tokenStart - 1))) {
                tokenStart--;
            }
            int tokenEnd = index;
            while (tokenEnd < content.length() && !Character.isWhitespace(content.charAt(tokenEnd))) {
                tokenEnd++;
            }
            String token = content.substring(tokenStart, tokenEnd);
            if (content.startsWith("://", index) || URL.matcher(token).find()
                    || (token.chars().filter(c -> c == ':').count() >= 2
                    && token.matches("[0-9a-fA-F:.]+(?:%[^/]+)?(?:/\\d{1,3})?"))) {
                continue;
            }
            int keyStart = nextKeyStart(content, valueStart, index);
            if (keyStart < 0) {
                continue;
            }
            pairs.add(new Pair(key, content.substring(valueStart, keyStart).strip()));
            key = content.substring(keyStart, index).strip();
            valueStart = index + 1;
        }
        pairs.add(new Pair(key, content.substring(valueStart).strip()));
        return List.copyOf(pairs);
    }

    static boolean plainContinuation(String content) {
        return !content.isBlank() && firstDelimiter(content) < 0
                && !content.contains("|") && !content.contains("\t")
                && !WIDE_SPACE.matcher(content.strip()).find()
                && !content.stripLeading().matches("(?:[-*+]|\\d+[.)])\\s+.*")
                && content.codePoints().anyMatch(Character::isLetter)
                && !EVENT.matcher(content.stripLeading()).find();
    }

    private static int firstDelimiter(String content) {
        for (int index = 0; index < content.length(); index++) {
            if (content.charAt(index) == ':' || content.charAt(index) == '=') {
                return index;
            }
        }
        return -1;
    }

    private static boolean nonFieldPrefix(String content, int delimiter) {
        boolean alignedCounter = content.matches("(?i)(?:error|warning|info)[ \\t]{2,}[\\p{L}\\p{N}_ -]+\\s{2,}[:=].*");
        if (URL.matcher(content).find() || (EVENT.matcher(content).find() && !alignedCounter)) {
            return true;
        }
        Matcher time = TIME.matcher(content);
        if (time.find() && time.start() < delimiter) {
            return true;
        }
        String token = content.split("\\s+", 2)[0].replaceFirst("(?:%[^/]+)?(?:/\\d{1,3})?$", "");
        return token.chars().filter(character -> character == ':').count() >= 2
                && token.matches("[0-9a-fA-F:.%]+")
                && token.substring(0, token.indexOf(':')).matches("[0-9a-fA-F]{0,4}");
    }

    private static boolean validKey(String key) {
        return KEY.matcher(key).matches() && key.codePoints().anyMatch(Character::isLetter);
    }

    private static int nextKeyStart(String content, int valueStart, int delimiter) {
        String prefix = content.substring(valueStart, delimiter);
        Matcher words = WORD.matcher(prefix);
        List<Integer> starts = new ArrayList<>();
        while (words.find()) {
            starts.add(words.start());
        }
        // A value must precede the next key. This also keeps "first: second" intact.
        if (starts.size() < 2) {
            return -1;
        }
        Matcher wide = WIDE_SPACE.matcher(prefix);
        int wideStart = -1;
        while (wide.find()) {
            if (!prefix.substring(0, wide.start()).isBlank() && wide.end() < prefix.length()) {
                wideStart = wide.end();
            }
        }
        if (wideStart >= 0 && validKey(prefix.substring(wideStart).strip())) {
            return valueStart + wideStart;
        }
        // Single-space multiword labels need a visible boundary. Prefer a capitalized
        // label suffix, otherwise a single final word; never infer arbitrary prose keys.
        for (int index = starts.size() - 1; index >= 1; index--) {
            int start = starts.get(index);
            if (Character.isUpperCase(prefix.charAt(start)) && validKey(prefix.substring(start).strip())) {
                return valueStart + start;
            }
        }
        int start = starts.getLast();
        return validKey(prefix.substring(start).strip()) ? valueStart + start : -1;
    }

    record Pair(String key, String value) {
    }
}
