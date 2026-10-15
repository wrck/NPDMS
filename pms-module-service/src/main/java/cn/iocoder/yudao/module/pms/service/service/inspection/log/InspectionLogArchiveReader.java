package cn.iocoder.yudao.module.pms.service.service.inspection.log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads uploaded device logs in memory; content is neither logged nor written to the filesystem. */
public final class InspectionLogArchiveReader {
    private static final int MAX_BYTES = 50 * 1024 * 1024;
    private static final int MAX_ENTRIES = 1024;
    private static final Charset GB18030 = Charset.forName("GB18030");

    public record Input(String fileName, byte[] content) { }
    public record LogEntry(String fileName, String text) { }

    public List<LogEntry> read(Input input) {
        if (input == null || input.content() == null) throw new IllegalArgumentException("日志文件不能为空");
        if (input.content().length == 0) throw new IllegalArgumentException("日志文件不能为空");
        if (input.content().length > MAX_BYTES) throw new IllegalArgumentException("上传文件超过 50MiB 大小限制");
        String fileName = safeName(input.fileName());
        if (extension(fileName).equals("zip")) return archive(new Input(fileName, input.content()));
        requireLogExtension(fileName);
        return List.of(log(new Input(fileName, input.content())));
    }

    private static List<LogEntry> archive(Input input) {
        if (!zipSignature(input.content())) throw new IllegalArgumentException("文件扩展名与 ZIP 格式不符");
        if (!hasZipEnd(input.content())) throw new IllegalArgumentException("ZIP 文件损坏或格式无效");
        List<LogEntry> results = new ArrayList<>();
        long expandedBytes = 0;
        int entryCount = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(input.content()))) {
            ZipEntry entry;
            while ((entry = nextEntry(zip)) != null) {
                if (++entryCount > MAX_ENTRIES) throw new IllegalArgumentException("ZIP 条目超过 1024 个资源限制");
                String name = safeName(entry.getName());
                if (entry.isDirectory()) {
                    // Even directory entries must obey path validation and the expanded-byte limit.
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        expandedBytes += read;
                        requireExpandedSize(expandedBytes);
                        if (read > 0) throw new IllegalArgumentException("ZIP 目录条目不能包含文件内容");
                    }
                    zip.closeEntry();
                    continue;
                }
                if (extension(name).equals("zip")) throw new IllegalArgumentException("ZIP 内不允许嵌套压缩包");
                requireLogExtension(name);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expandedBytes += read;
                    requireExpandedSize(expandedBytes);
                    output.write(buffer, 0, read);
                }
                results.add(log(new Input(name, output.toByteArray())));
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("ZIP 文件损坏或格式无效");
        }
        if (results.isEmpty()) throw new IllegalArgumentException("ZIP 中没有可读取的日志文件");
        return List.copyOf(results);
    }

    private static ZipEntry nextEntry(ZipInputStream zip) throws IOException {
        try {
            return zip.getNextEntry();
        } catch (IllegalArgumentException invalidNameEncoding) {
            throw new IllegalArgumentException("ZIP 文件名编码无效");
        }
    }

    private static void requireExpandedSize(long bytes) {
        if (bytes > MAX_BYTES) throw new IllegalArgumentException("ZIP 展开累计超过 50MiB 大小限制");
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("日志文件名不能为空");
        String relative = name.replace('\\', '/');
        if (relative.startsWith("/") || relative.contains(":") || relative.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("日志文件名必须为安全相对路径");
        }
        String path = relative.endsWith("/") ? relative.substring(0, relative.length() - 1) : relative;
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("日志文件名包含不安全路径");
            }
        }
        return relative;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > name.lastIndexOf('/') ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static void requireLogExtension(String name) {
        if (!List.of("log", "txt").contains(extension(name))) {
            throw new IllegalArgumentException("只支持 .log、.txt 或包含这些日志的 .zip 文件");
        }
    }

    private static LogEntry log(Input input) {
        if (input.content().length == 0) throw new IllegalArgumentException("日志内容不能为空");
        if (compressedSignature(input.content())) throw new IllegalArgumentException("日志扩展名伪装了压缩包，禁止嵌套压缩包");
        if (binarySignature(input.content())) throw new IllegalArgumentException("日志扩展名伪装了二进制文件");
        for (byte value : input.content()) {
            if (value == 0) throw new IllegalArgumentException("日志含 NUL 字符，不能作为文本读取");
        }
        String text;
        try {
            text = decode(new Decoding(input.content(), StandardCharsets.UTF_8));
        } catch (CharacterCodingException invalidUtf8) {
            try {
                text = decode(new Decoding(input.content(), GB18030));
            } catch (CharacterCodingException invalidGb18030) {
                throw new IllegalArgumentException("日志编码无效，仅支持 UTF-8 或 GB18030 文本");
            }
        }
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        if (text.isBlank()) throw new IllegalArgumentException("日志内容不能为空");
        // ANSI ESC, backspace and form-feed are text terminal controls, retained for the DAC parser.
        if (text.codePoints().anyMatch(cp -> Character.isISOControl(cp)
                && cp != '\r' && cp != '\n' && cp != '\t' && cp != '\b' && cp != '\f' && cp != 0x1B)) {
            throw new IllegalArgumentException("日志含非文本控制字符，疑似二进制文件");
        }
        return new LogEntry(input.fileName(), text);
    }

    private record Decoding(byte[] content, Charset charset) { }

    private static String decode(Decoding decoding) throws CharacterCodingException {
        return decoding.charset().newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(decoding.content())).toString();
    }

    private static boolean zipSignature(byte[] bytes) {
        return startsWith(bytes, new int[] { 0x50, 0x4B, 0x03, 0x04 })
                || startsWith(bytes, new int[] { 0x50, 0x4B, 0x05, 0x06 });
    }

    private static boolean hasZipEnd(byte[] bytes) {
        // ZipInputStream alone accepts truncated archives with missing central-directory endings.
        int first = Math.max(0, bytes.length - 22 - 65535);
        for (int offset = bytes.length - 22; offset >= first; offset--) {
            if (bytes[offset] == 'P' && bytes[offset + 1] == 'K'
                    && bytes[offset + 2] == 5 && bytes[offset + 3] == 6) {
                int commentLength = Byte.toUnsignedInt(bytes[offset + 20]) | Byte.toUnsignedInt(bytes[offset + 21]) << 8;
                if (offset + 22 + commentLength == bytes.length) return true;
            }
        }
        return false;
    }

    private static boolean compressedSignature(byte[] bytes) {
        return zipSignature(bytes) || startsWith(bytes, new int[] { 0x1F, 0x8B })
                || startsWith(bytes, new int[] { 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C })
                || startsWith(bytes, new int[] { 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07 });
    }

    private static boolean binarySignature(byte[] bytes) {
        return startsWith(bytes, new int[] { 0x25, 0x50, 0x44, 0x46, 0x2D })
                || startsWith(bytes, new int[] { 0x89, 0x50, 0x4E, 0x47 })
                || startsWith(bytes, new int[] { 0xFF, 0xD8, 0xFF })
                || startsWith(bytes, new int[] { 0x47, 0x49, 0x46, 0x38, 0x37, 0x61 })
                || startsWith(bytes, new int[] { 0x47, 0x49, 0x46, 0x38, 0x39, 0x61 });
    }

    private static boolean startsWith(byte[] bytes, int[] signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if (Byte.toUnsignedInt(bytes[i]) != signature[i]) return false;
        return true;
    }
}
