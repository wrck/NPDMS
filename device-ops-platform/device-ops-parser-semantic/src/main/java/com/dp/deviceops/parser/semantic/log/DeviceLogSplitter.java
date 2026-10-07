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
    private static final Pattern TERMINAL_ESCAPE = Pattern.compile("\\x1B(?:\\[[0-?]*[ -/]*[@-~]|][^\\x07]*(?:\\x07|\\x1B\\\\))");
    private static final Pattern OTHER_SECRET = Pattern.compile(
            "(?im)(\\b(?:api[-_ ]?key|access[-_ ]?token|token|authorization|pre[-_ ]?shared[-_ ]?key|key-string|key)\\b(?:[\\t ]*[:=][\\t ]*|[\\t ]+))[^\\r\\n]+"
                    + "|(?im)(\\b(?:snmp-server|snmp-agent)\\s+[^\\r\\n]*?\\b(?:auth|authentication|priv|privacy)\\s+)[^\\r\\n]+");
    private final SensitiveValueRedactor redactor = new SensitiveValueRedactor(true);

    public Result split(String input) {
        if (input == null || input.isBlank() || input.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new SplitException("LOG_SIZE_INVALID");
        String clean = TERMINAL_ESCAPE.matcher(input.replace("\u0000", "")).replaceAll("").replaceAll(".?\u0008", "");
        List<String> sessions = sessions(clean);
        var contents = new LinkedHashMap<String, StringBuilder>();
        int redacted = 0;
        var safeSource = new StringBuilder();
        for (int index = 0; index < sessions.size(); index++) {
            String session = sessions.get(index);
            var serials = new LinkedHashSet<String>();
            session.lines().forEach(line -> {
                var match = SERIAL.matcher(line);
                if (match.matches()) serials.add(match.group(1).toUpperCase(Locale.ROOT));
            });
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
