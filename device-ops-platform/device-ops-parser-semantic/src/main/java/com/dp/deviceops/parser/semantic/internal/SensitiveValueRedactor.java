package com.dp.deviceops.parser.semantic.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SensitiveValueRedactor {

    private static final Pattern SECRET_MARKERS = Pattern.compile("(?i)\\[REDACTED]|\\*{4,}");
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(password|passphrase|secret|community|private[-_ ]?key|credential).*");
    private static final Pattern PASSWORD_COMMAND = Pattern.compile(
            "(?im)(\\bpassword\\s+(?:(?:encrypted-password|simple|cipher|hash)\\s+)?)(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|\\S+)");
    private static final Pattern COMMUNITY_COMMAND = Pattern.compile(
            "(?im)(\\b(?:snmp-agent\\s+)?community\\s+(?:(?:read|write|simple|cipher)\\s+)*)(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|\\S+)");
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?im)(\\b(?:password|passphrase|secret|community|private[- ]?key|credential)\\b\\s*"
                    + "(?::|=|\\s)\\s*)(?!(?:(?:encrypted-password|simple|cipher|hash|read|write)\\s+)*"
                    + "\\[REDACTED])(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|\\S+)");

    private static final Pattern ENHANCED_PREFIX = Pattern.compile(
            "(?i)\\b(?:password|passphrase|secret|community|private[-_ ]?key|credential)\\b"
                    + "(?:[\\t ]*[:=][\\t ]*|[\\t ]+(?:(?:encrypted-password|simple|cipher|hash|read|write)[\\t ]+)*)");
    private static final Pattern ENHANCED_BARE_KEY = Pattern.compile(
            "(?i)[\\t ]*(?:password|passphrase|secret|community|private[-_ ]?key|credential)[\\t ]*");
    private static final Pattern PRIVATE_KEY_BEGIN = Pattern.compile(
            "(?i)-----BEGIN ((?:[A-Z0-9-]+ )*PRIVATE KEY)-----");
    private static final Pattern PRIVATE_KEY_END = Pattern.compile(
            "(?i)-----END ((?:[A-Z0-9-]+ )*PRIVATE KEY)-----");
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");

    private final boolean enhanced;

    public SensitiveValueRedactor() {
        this(false);
    }

    public SensitiveValueRedactor(boolean enhanced) {
        this.enhanced = enhanced;
    }

    public String redactText(String value) {
        return redactTextWithCount(value).value();
    }

    public RedactedText redactTextWithCount(String value) {
        if (enhanced) {
            return redactEnhancedText(value == null ? "" : value, new EnhancedContext());
        }
        RedactedText password = replace(PASSWORD_COMMAND, value == null ? "" : value);
        RedactedText community = replace(COMMUNITY_COMMAND, password.value());
        RedactedText assignment = replace(ASSIGNMENT, community.value());
        return new RedactedText(assignment.value(), Math.addExact(password.redactedValueCount(),
                Math.addExact(community.redactedValueCount(), assignment.redactedValueCount())));
    }

    public boolean isSensitiveKey(String value) {
        return value != null && SENSITIVE_KEY.matcher(value).matches();
    }

    public Sanitized sanitize(Object value) {
        if (value instanceof String text) {
            RedactedText sanitized = redactTextWithCount(text);
            return new Sanitized(sanitized.value(), sanitized.redactedValueCount() > 0
                    || SECRET_MARKERS.matcher(text).find());
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            boolean redacted = false;
            EnhancedContext context = enhanced ? new EnhancedContext() : null;
            for (Object item : list) {
                if (enhanced && item instanceof String text) {
                    RedactedText safe = redactEnhancedText(text, context);
                    copy.add(safe.value());
                    redacted |= safe.redactedValueCount() > 0 || SECRET_MARKERS.matcher(text).find();
                } else {
                    Sanitized nested = sanitize(item);
                    copy.add(nested.value());
                    redacted |= nested.redacted();
                    context = enhanced ? new EnhancedContext() : null;
                }
            }
            return new Sanitized(List.copyOf(copy), redacted);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            boolean redacted = false;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Sanitized nested = sanitize(entry.getValue());
                if (SECRET_MARKERS.matcher(key).find() || isSensitiveKey(key)) {
                    copy.put(key, "[REDACTED]");
                    redacted = true;
                } else {
                    copy.put(key, nested.value());
                    redacted |= nested.redacted();
                }
            }
            return new Sanitized(copy, redacted);
        }
        return new Sanitized(value, false);
    }

    public record Sanitized(Object value, boolean redacted) {
    }

    public record RedactedText(String value, int redactedValueCount) {
    }

    RedactedLines redactLinesWithCount(List<String> lines) {
        EnhancedContext context = new EnhancedContext();
        List<String> safeLines = new ArrayList<>(lines.size());
        int count = 0;
        for (String line : lines) {
            RedactedText safe = enhanced ? redactEnhancedText(line, context) : redactTextWithCount(line);
            safeLines.add(safe.value());
            count = Math.addExact(count, safe.redactedValueCount());
        }
        return new RedactedLines(List.copyOf(safeLines), count);
    }

    private static RedactedText redactEnhancedText(String text, EnhancedContext context) {
        StringBuilder result = new StringBuilder(text.length());
        Matcher breaks = LINE_BREAK.matcher(text);
        int offset = 0;
        int count = 0;
        while (breaks.find()) {
            RedactedText line = redactEnhancedLine(text.substring(offset, breaks.start()), context);
            result.append(line.value()).append(breaks.group());
            count = Math.addExact(count, line.redactedValueCount());
            offset = breaks.end();
        }
        RedactedText line = redactEnhancedLine(text.substring(offset), context);
        return new RedactedText(result.append(line.value()).toString(),
                Math.addExact(count, line.redactedValueCount()));
    }

    private static RedactedText redactEnhancedLine(String line, EnhancedContext context) {
        Matcher begin = PRIVATE_KEY_BEGIN.matcher(line);
        if (context.privateKeyType != null || begin.find()) {
            if (context.privateKeyType == null) {
                context.privateKeyType = begin.group(1);
            }
            Matcher end = PRIVATE_KEY_END.matcher(line);
            while (end.find()) {
                if (end.group(1).equalsIgnoreCase(context.privateKeyType)) {
                    context.privateKeyType = null;
                    break;
                }
            }
            context.sensitiveIndent = -1;
            return redactWholeLine(line);
        }
        if (context.sensitiveIndent >= 0) {
            if (line.isBlank()) {
                return new RedactedText(line, 0);
            }
            if (indentWidth(line) > context.sensitiveIndent) {
                return redactWholeLine(line);
            }
            context.sensitiveIndent = -1;
        }
        Matcher prefix = ENHANCED_PREFIX.matcher(line);
        StringBuilder result = new StringBuilder(line.length());
        int offset = 0;
        int count = 0;
        while (prefix.find(offset)) {
            int valueStart = prefix.end();
            result.append(line, offset, valueStart);
            if (valueStart == line.length()) {
                context.sensitiveIndent = indentWidth(line);
                return new RedactedText(result.toString(), count);
            }
            int valueEnd = quotedValueEnd(line, valueStart);
            if (valueEnd < 0) {
                // Without an explicit quoted boundary the entire remaining value is sensitive.
                valueEnd = line.length();
            }
            String value = line.substring(valueStart, valueEnd);
            result.append("[REDACTED]");
            if (!value.equals("[REDACTED]")) {
                count++;
            }
            offset = valueEnd;
        }
        result.append(line, offset, line.length());
        if (ENHANCED_BARE_KEY.matcher(line).matches()) {
            context.sensitiveIndent = indentWidth(line);
        }
        return new RedactedText(result.toString(), count);
    }

    private static int quotedValueEnd(String line, int start) {
        char quote = line.charAt(start);
        if (quote != '\'' && quote != '"') {
            return -1;
        }
        for (int index = start + 1; index < line.length(); index++) {
            if (line.charAt(index) == '\\') {
                index++;
            } else if (line.charAt(index) == quote) {
                return index + 1;
            }
        }
        return -1;
    }

    private static RedactedText redactWholeLine(String line) {
        if (line.isBlank()) {
            return new RedactedText(line, 0);
        }
        int indent = 0;
        while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) {
            indent++;
        }
        String safe = line.substring(0, indent) + "[REDACTED]";
        return new RedactedText(safe, safe.equals(line) ? 0 : 1);
    }

    private static int indentWidth(String line) {
        int width = 0;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '\t') {
                width += 8 - width % 8;
            } else if (Character.isWhitespace(character)) {
                width++;
            } else {
                break;
            }
        }
        return width;
    }

    record RedactedLines(List<String> lines, int redactedValueCount) {
    }

    private static final class EnhancedContext {
        private String privateKeyType;
        private int sensitiveIndent = -1;
    }

    private static RedactedText replace(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        StringBuilder result = new StringBuilder(value.length());
        int count = 0;
        while (matcher.find()) {
            String replacement = matcher.group(1) + "[REDACTED]";
            if (!matcher.group().equals(replacement)) {
                count++;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return new RedactedText(result.toString(), count);
    }
}
