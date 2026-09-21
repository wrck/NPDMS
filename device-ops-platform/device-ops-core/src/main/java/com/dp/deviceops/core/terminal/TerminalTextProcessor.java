package com.dp.deviceops.core.terminal;

import com.dp.deviceops.core.model.ConnectionFailure;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Incrementally cleans terminal output and recognizes pagination and command prompts.
 */
public final class TerminalTextProcessor {

    public static final int DEFAULT_MAX_PAGES = 10_000;

    private static final int MAX_PENDING_LINE_CHARACTERS = 4_096;
    public static final Pattern DEFAULT_PAGER_PROMPT = Pattern.compile(
            "(?:[ \\t]*-{2,4}[ \\t]*more(?:[ \\t]*\\([^\\r\\n)]*\\))?[ \\t]*-{2,4}[ \\t]*"
                    + "|more:|press[ \\t]+any[ \\t]+key[ \\t]+to[ \\t]+continue"
                    + "|(?:[ \\t]*-+)?[ \\t]*more[ \\t]*-+"
                    + "|more[ \\t]*\\([^\\r\\n)]*\\)|按任意键继续)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final PromptMatcher commandPrompt;
    private final Pattern pagerPrompt;
    private final int maxPages;
    private final java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
    private final StringBuilder pending = new StringBuilder();

    private ByteBuffer undecoded = emptyByteBuffer();
    private EscapeState escapeState = EscapeState.NORMAL;
    private int pageCount;

    public TerminalTextProcessor(Pattern commandPrompt, int maxPages) {
        this(patternAtEnd(Objects.requireNonNull(commandPrompt, "commandPrompt")),
                DEFAULT_PAGER_PROMPT, maxPages);
    }

    public TerminalTextProcessor(PromptMatcher commandPrompt, int maxPages) {
        this(commandPrompt, DEFAULT_PAGER_PROMPT, maxPages);
    }

    public TerminalTextProcessor(PromptMatcher commandPrompt, Pattern pagerPrompt, int maxPages) {
        this.commandPrompt = Objects.requireNonNull(commandPrompt, "commandPrompt");
        this.pagerPrompt = Objects.requireNonNull(pagerPrompt, "pagerPrompt");
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages must be positive");
        }
        this.maxPages = maxPages;
    }

    public synchronized Decision accept(byte[] bytes, int length) {
        Objects.requireNonNull(bytes, "bytes");
        if (length < 0 || length > bytes.length) {
            throw new IllegalArgumentException("length is invalid");
        }
        decode(bytes, length, false);
        return decide(false);
    }

    /**
     * Completes UTF-8 decoding and releases the final unterminated line.
     */
    public synchronized Decision finish() {
        decode(new byte[0], 0, true);
        return decide(true);
    }

    private Decision decide(boolean flushPending) {
        boolean sendContinue = removePagerPromptAtTail();
        boolean promptReached = promptAtEnd();
        int emitLength = flushPending ? pending.length() : emitLength(sendContinue, promptReached);
        String output = emitLength == 0 ? "" : pending.substring(0, emitLength);
        if (emitLength > 0) {
            pending.delete(0, emitLength);
        }
        return new Decision(output, sendContinue, promptReached, pageCount);
    }

    private void decode(byte[] bytes, int length, boolean endOfInput) {
        ByteBuffer input = ByteBuffer.allocate(undecoded.remaining() + length);
        input.put(undecoded);
        input.put(bytes, 0, length);
        input.flip();

        CharBuffer characters = CharBuffer.allocate(Math.max(8, input.remaining() + 1));
        try {
            CoderResult result = decoder.decode(input, characters, endOfInput);
            if (result.isError()) {
                result.throwException();
            }
            if (endOfInput) {
                CoderResult flush = decoder.flush(characters);
                if (flush.isError()) {
                    flush.throwException();
                }
            }
        } catch (CharacterCodingException exception) {
            throw new IllegalStateException("terminal output is not valid UTF-8", exception);
        }
        ByteBuffer remainder = ByteBuffer.allocate(Math.max(4, input.remaining()));
        remainder.put(input);
        remainder.flip();
        undecoded = remainder;

        characters.flip();
        while (characters.hasRemaining()) {
            clean(characters.get());
        }
    }

    private void clean(char character) {
        switch (escapeState) {
            case NORMAL -> {
                if (character == '\u001B') {
                    escapeState = EscapeState.ESCAPE;
                } else if (character == '\u0000') {
                    // NUL is terminal padding; some devices append it to a pager prompt.
                } else if (character == '\b') {
                    if (!pending.isEmpty()) {
                        pending.deleteCharAt(pending.length() - 1);
                    }
                } else {
                    pending.append(character);
                }
            }
            case ESCAPE -> {
                if (character == '[') {
                    escapeState = EscapeState.CSI;
                } else if (character == ']') {
                    escapeState = EscapeState.OSC;
                } else {
                    escapeState = EscapeState.NORMAL;
                }
            }
            case CSI -> {
                if (character >= '\u0040' && character <= '\u007E') {
                    escapeState = EscapeState.NORMAL;
                }
            }
            case OSC -> {
                if (character == '\u0007') {
                    escapeState = EscapeState.NORMAL;
                } else if (character == '\u001B') {
                    escapeState = EscapeState.OSC_ESCAPE;
                }
            }
            case OSC_ESCAPE -> escapeState = character == '\\' ? EscapeState.NORMAL : EscapeState.OSC;
        }
    }

    private boolean removePagerPromptAtTail() {
        int terminalEnd = pending.length();
        while (terminalEnd > 0 && Character.isWhitespace(pending.charAt(terminalEnd - 1))) {
            terminalEnd--;
        }
        int lineStart = terminalEnd;
        while (lineStart > 0) {
            char previous = pending.charAt(lineStart - 1);
            if (previous == '\n' || previous == '\r') {
                break;
            }
            lineStart--;
        }
        if (lineStart == terminalEnd
                || !pagerPrompt.matcher(pending.subSequence(lineStart, terminalEnd)).matches()) {
            return false;
        }
        pending.delete(lineStart, pending.length());
        pageCount++;
        if (pageCount > maxPages) {
            throw new ConnectionFailure(
                    ConnectionFailure.Code.CONNECTION_CLOSED,
                    ConnectionFailure.Stage.EXECUTE,
                    "pagination limit exceeded");
        }
        return true;
    }

    private boolean promptAtEnd() {
        return commandPrompt.matches(pending);
    }

    private int emitLength(boolean pagerReached, boolean promptReached) {
        if (pagerReached || promptReached) {
            return pending.length();
        }
        int lastLineFeed = pending.lastIndexOf("\n");
        if (lastLineFeed >= 0) {
            return lastLineFeed + 1;
        }
        return Math.max(0, pending.length() - MAX_PENDING_LINE_CHARACTERS);
    }

    private static ByteBuffer emptyByteBuffer() {
        ByteBuffer empty = ByteBuffer.allocate(0);
        empty.flip();
        return empty;
    }

    private static PromptMatcher patternAtEnd(Pattern pattern) {
        return terminalText -> {
            int terminalEnd = terminalText.length();
            while (terminalEnd > 0 && Character.isWhitespace(terminalText.charAt(terminalEnd - 1))) {
                terminalEnd--;
            }
            Matcher matcher = pattern.matcher(terminalText.subSequence(0, terminalEnd));
            boolean reached = false;
            while (matcher.find()) {
                reached = matcher.end() == terminalEnd;
            }
            return reached;
        };
    }

    @FunctionalInterface
    public interface PromptMatcher {
        boolean matches(CharSequence terminalText);
    }

    public record Decision(
            String output,
            boolean sendContinue,
            boolean promptReached,
            int pageCount) {
    }

    private enum EscapeState {
        NORMAL,
        ESCAPE,
        CSI,
        OSC,
        OSC_ESCAPE
    }
}
