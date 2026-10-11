package com.dp.deviceops.parser.semantic.log;

import com.dp.deviceops.parser.semantic.internal.SensitiveValueRedactor;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Splits complete terminal sessions by a serial found in their output, never by hostname alone. */
public final class DeviceLogSplitter {
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final Pattern COMMAND = Pattern.compile(
            "^\\s*((?:[\\w.()/:-]+[>#])|(?:<[^>\\r\\n]+>)|(?:\\[[^]\\r\\n]+]))\\s*(?:show|sh|display)\\s+.+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERIAL = Pattern.compile(
            "^\\s*(?:(?:chassis|system|device)\\s+(?:serial(?:\\s+number)?|s/n|sn)|serial\\s+number|processor\\s+board\\s+id|esn|sn|s/n)(?:\\s*[:=#]\\s*|\\s+)([A-Za-z0-9][A-Za-z0-9._-]{0,127})\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROMPT = Pattern.compile(
            "^\\s*(?:[\\w.()/:-]+[>#]|<[^>\\r\\n]+>|\\[[^]\\r\\n]+])\\s*$");
    private static final Pattern TERMINAL_ESCAPE = Pattern.compile("\\x1B(?:\\[[0-?]*[ -/]*[@-~]|][^\\x07]*(?:\\x07|\\x1B\\\\))");
    private static final Pattern OTHER_SECRET = Pattern.compile(
            "(?im)(\\b(?:api[-_ ]?key|access[-_ ]?token|token|authorization|pre[-_ ]?shared[-_ ]?key|key-string|key)\\b(?:[\\t ]*[:=][\\t ]*|[\\t ]+))[^\\r\\n]+"
                    + "|(?im)(\\b(?:snmp-server|snmp-agent)\\s+[^\\r\\n]*?\\b(?:auth|authentication|priv|privacy)\\s+)[^\\r\\n]+");
    private final SensitiveValueRedactor redactor = new SensitiveValueRedactor(true);

    public static Set<String> serialNumbers(String text) {
        var serials=new LinkedHashSet<String>();
        if (text!=null) text.lines().forEach(line -> {
            var match=SERIAL.matcher(line);
            if (match.matches()) serials.add(match.group(1).toUpperCase(Locale.ROOT));
        });
        return Collections.unmodifiableSet(serials);
    }

    public Result split(String input) {
        if (input == null || input.isBlank() || input.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new SplitException("LOG_SIZE_INVALID");
        String clean = TERMINAL_ESCAPE.matcher(input.replace("\u0000", "")).replaceAll("").replaceAll(".?\u0008", "");
        List<String> sessions = new ArrayList<>();
        for (String session : sessions(clean)) sessions.addAll(splitSamePrompt(session));
        var contents = new LinkedHashMap<String, StringBuilder>();
        int redacted = 0;
        var safeSource = new StringBuilder();
        for (int index = 0; index < sessions.size(); index++) {
            String session = sessions.get(index);
            var serials = serialNumbers(session);
            if (serials.size() != 1)
                throw new SplitException(serials.isEmpty() ? "DEVICE_SERIAL_MISSING" : "DEVICE_SERIAL_AMBIGUOUS", index + 1);
            var safe = redactor.redactTextWithCount(session);
            redacted = Math.addExact(redacted, safe.redactedValueCount());
            var secret = OTHER_SECRET.matcher(safe.value());
            var protectedText = new StringBuilder();
            while (secret.find()) {
                String prefix = secret.group(1) == null ? secret.group(2) : secret.group(1);
                secret.appendReplacement(protectedText, java.util.regex.Matcher.quoteReplacement(prefix+"[REDACTED]"));
                redacted++;
            }
            secret.appendTail(protectedText);
            String text = protectedText.toString().endsWith("\n") ? protectedText.toString() : protectedText + "\n";
            contents.computeIfAbsent(serials.iterator().next(), ignored -> new StringBuilder()).append(text);
            safeSource.append(text);
        }
        return new Result(safeSource.toString(), redacted, contents.entrySet().stream()
                .map(e -> new DeviceLog(e.getKey(), e.getValue().toString())).toList());
    }

    private static List<String> sessions(String input) {
        var result = new ArrayList<String>();
        var current = new StringBuilder();
        String prompt = null;
        for (String line : input.split("\\R", -1)) {
            var command = COMMAND.matcher(line);
            if (command.matches()) {
                String next = command.group(1);
                if (prompt != null && !prompt.equals(next)) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                prompt = next;
            }
            current.append(line).append('\n');
        }
        if (!current.toString().isBlank()) result.add(current.toString());
        return result;
    }

    private static List<String> splitSamePrompt(String session) {
        if (serialNumbers(session).size() <= 1) return List.of(session);
        var blocks = new ArrayList<String>();
        var current = new StringBuilder();
        for (String line : session.split("\\R", -1)) {
            if (COMMAND.matcher(line).matches() && !current.isEmpty()) {
                blocks.add(current.toString());
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (!current.isEmpty()) blocks.add(current.toString());
        var identities = new ArrayList<String>();
        for (String block : blocks) {
            var serials = serialNumbers(block);
            if (serials.size() > 1) throw new SplitException("DEVICE_SERIAL_AMBIGUOUS");
            identities.add(serials.isEmpty() ? null : serials.iterator().next());
        }
        var resolved = new ArrayList<>(identities);
        var following = new String[identities.size()];
        String next = null;
        for (int index = identities.size() - 1; index >= 0; index--) {
            following[index] = next;
            if (identities.get(index) != null) next = identities.get(index);
        }
        String previous = null;
        for (int index = 0; index < blocks.size(); index++) {
            if (identities.get(index) != null) {
                previous = identities.get(index);
                continue;
            }
            next = following[index];
            boolean hasOutput = blocks.get(index).lines().anyMatch(line -> !line.isBlank()
                    && !COMMAND.matcher(line).matches() && !PROMPT.matcher(line).matches());
            // Real output without a serial needs matching anchors on both sides. Empty terminal
            // prompts/abbreviated commands may precede the next complete identifying command.
            if (hasOutput && (previous == null || !previous.equals(next)))
                throw new SplitException("DEVICE_SERIAL_AMBIGUOUS");
            resolved.set(index, next == null ? previous : next);
        }
        var result = new ArrayList<String>();
        current.setLength(0);
        String identity = null;
        for (int index = 0; index < blocks.size(); index++) {
            if (identity != null && !identity.equals(resolved.get(index))) {
                result.add(current.toString());
                current.setLength(0);
            }
            identity = resolved.get(index);
            current.append(blocks.get(index));
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    public record DeviceLog(String serialNumber, String content) { }
    public record Result(String sanitizedSource, int redactedValueCount, List<DeviceLog> devices) {
        public Result { devices = List.copyOf(devices); }
    }
    public static final class SplitException extends IllegalArgumentException {
        private final String code;
        private final Integer block;
        public SplitException(String code) { this(code, null); }
        public SplitException(String code, Integer block) {
            super(code + (block == null ? "" : ": block " + block)); this.code = code; this.block = block;
        }
        public String code() { return code; }
        public Integer block() { return block; }
    }
}
