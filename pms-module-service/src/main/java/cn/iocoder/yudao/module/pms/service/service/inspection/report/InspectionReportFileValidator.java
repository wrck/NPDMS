package cn.iocoder.yudao.module.pms.service.service.inspection.report;

import org.apache.pdfbox.Loader;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Checks report bytes in memory, without logging content or retaining parser exception causes. */
public final class InspectionReportFileValidator {
    private static final int MAX_BYTES = 50 * 1024 * 1024;
    private static final int MAX_ENTRIES = 1024;
    private static final int MAX_ENTRY_BYTES = 20 * 1024 * 1024;
    private static final int MAX_XML_PART_BYTES = 8 * 1024 * 1024;
    private static final int RATIO_ALLOWANCE_BYTES = 64 * 1024;
    private static final int MAX_EXPANSION_RATIO = 100;
    private static final String CONTENT_TYPES = "[Content_Types].xml";
    private static final String WORD_DOCUMENT = "word/document.xml";
    private static final String TYPES_NAMESPACE = "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String WORD_MAIN_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final Set<String> WORD_NAMESPACES = Set.of(
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
            "http://purl.oclc.org/ooxml/wordprocessingml/main");

    public record Input(String formatCode, String fileName, String mediaType, byte[] content) { }

    public void validate(Input input) {
        if (input == null || input.content() == null || input.content().length == 0) {
            throw new IllegalArgumentException("报告文件不能为空");
        }
        if (input.content().length > MAX_BYTES) throw new IllegalArgumentException("报告文件不能超过50MiB");
        Format format = format(input);
        try {
            switch (format) {
                case PDF -> pdf(input.content());
                case DOC -> doc(input.content());
                case DOCX -> docx(input.content());
                case XML -> xml(new XmlInput(input.content(), new SafeXmlHandler()));
            }
        } catch (ResourceLimitException exceeded) {
            throw new IllegalArgumentException("报告文件超过解析资源限制");
        } catch (Exception invalid) {
            // Never expose parser messages, causes, uploaded content or filenames.
            throw new IllegalArgumentException("报告文件内容与指定格式不符或已损坏");
        }
    }

    private enum Format { PDF, DOCX, DOC, XML }

    private static Format format(Input input) {
        if (input.formatCode() == null || input.fileName() == null || input.mediaType() == null) {
            throw new IllegalArgumentException("报告格式、文件名和媒体类型不能为空");
        }
        Format format;
        try { format = Format.valueOf(input.formatCode()); }
        catch (IllegalArgumentException unsupported) { throw new IllegalArgumentException("仅支持PDF、DOCX、DOC和XML报告"); }
        String name = input.fileName();
        if (name.isBlank() || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("报告文件名无效");
        }
        int dot = name.lastIndexOf('.');
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (dot <= separator || !name.substring(dot + 1).equalsIgnoreCase(format.name())) {
            throw new IllegalArgumentException("报告扩展名与指定格式不符");
        }
        String mediaType = input.mediaType().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        boolean matches = switch (format) {
            case PDF -> mediaType.equals("application/pdf");
            case DOCX -> mediaType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case DOC -> mediaType.equals("application/msword");
            case XML -> mediaType.equals("application/xml") || mediaType.equals("text/xml");
        };
        if (!matches) throw new IllegalArgumentException("报告媒体类型与指定格式不符");
        return format;
    }

    private static void pdf(byte[] content) throws IOException {
        if (content.length < 5 || !new String(content, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new IOException();
        }
        // PDFBox 3 byte[] loading uses an in-memory source and the default memory-only stream cache.
        try (var document = Loader.loadPDF(content)) {
            if (document.getNumberOfPages() < 1) throw new IOException();
            document.getPage(0).getCOSObject();
        }
    }

    private static void doc(byte[] content) throws IOException {
        try (var filesystem = new POIFSFileSystem(new ByteArrayInputStream(content))) {
            var entry = filesystem.getRoot().getEntry("WordDocument");
            if (!(entry instanceof DocumentEntry document) || document.getSize() < 32) throw new IOException();
            // Check that the Word stream is readable, rather than accepting any OLE container.
            try (var stream = filesystem.createDocumentInputStream("WordDocument")) {
                byte[] fibBase = stream.readNBytes(32);
                if (fibBase.length != 32 || Byte.toUnsignedInt(fibBase[0]) != 0xEC
                        || Byte.toUnsignedInt(fibBase[1]) != 0xA5) throw new IOException();
            }
        }
    }

    private record ArchiveEntry(long compressedBytes, long expandedBytes) { }

    private static void docx(byte[] content) throws Exception {
        Map<String, ArchiveEntry> directory = directory(content);
        Map<String, byte[]> parts = new HashMap<>();
        Set<String> observed = new HashSet<>();
        byte[] buffer = new byte[8192];
        long total = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (observed.size() >= MAX_ENTRIES) throw new ResourceLimitException();
                ArchiveEntry declared = directory.get(entry.getName());
                if (declared == null || !observed.add(entry.getName())) throw new IOException();
                boolean keep = CONTENT_TYPES.equals(entry.getName()) || WORD_DOCUMENT.equals(entry.getName());
                ByteArrayOutputStream output = keep ? new ByteArrayOutputStream() : null;
                long expanded = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    total += read;
                    if (expanded > MAX_ENTRY_BYTES || total > MAX_BYTES
                            || (keep && expanded > MAX_XML_PART_BYTES)
                            || expanded > Math.max(RATIO_ALLOWANCE_BYTES, declared.compressedBytes() * MAX_EXPANSION_RATIO)) {
                        throw new ResourceLimitException();
                    }
                    if (entry.isDirectory() && read > 0) throw new IOException();
                    if (output != null) output.write(buffer, 0, read);
                }
                if (expanded != declared.expandedBytes() || entry.getCompressedSize() != declared.compressedBytes()) {
                    throw new IOException();
                }
                if (output != null) parts.put(entry.getName(), output.toByteArray());
                zip.closeEntry();
            }
        }
        if (!observed.equals(directory.keySet()) || !parts.containsKey(CONTENT_TYPES) || !parts.containsKey(WORD_DOCUMENT)) {
            throw new IOException();
        }
        TypesHandler types = new TypesHandler();
        xml(new XmlInput(parts.get(CONTENT_TYPES), types));
        if (!types.wordMain) throw new IOException();
        xml(new XmlInput(parts.get(WORD_DOCUMENT), new WordHandler()));
    }

    /** ZIP local streams alone accept missing or inconsistent central-directory records. */
    private static Map<String, ArchiveEntry> directory(byte[] content) throws IOException {
        if (content.length < 22) throw new IOException();
        ByteBuffer bytes = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        int end = -1;
        for (int index = content.length - 22; index >= Math.max(0, content.length - 22 - 65535); index--) {
            if (bytes.getInt(index) == 0x06054b50
                    && index + 22 + Short.toUnsignedInt(bytes.getShort(index + 20)) == content.length) {
                end = index;
                break;
            }
        }
        if (end < 0 || bytes.getShort(end + 4) != 0 || bytes.getShort(end + 6) != 0) throw new IOException();
        int count = Short.toUnsignedInt(bytes.getShort(end + 10));
        if (count > MAX_ENTRIES) throw new ResourceLimitException();
        if (count == 0 || Short.toUnsignedInt(bytes.getShort(end + 8)) != count) throw new IOException();
        long size = Integer.toUnsignedLong(bytes.getInt(end + 12));
        long offset = Integer.toUnsignedLong(bytes.getInt(end + 16));
        if (offset + size != end) throw new IOException();
        int cursor = Math.toIntExact(offset);
        Map<String, ArchiveEntry> entries = new HashMap<>();
        long expandedTotal = 0;
        for (int index = 0; index < count; index++) {
            if (cursor < 0 || cursor + 46 > end || bytes.getInt(cursor) != 0x02014b50) throw new IOException();
            int flags = Short.toUnsignedInt(bytes.getShort(cursor + 8));
            int method = Short.toUnsignedInt(bytes.getShort(cursor + 10));
            int nameLength = Short.toUnsignedInt(bytes.getShort(cursor + 28));
            int extraLength = Short.toUnsignedInt(bytes.getShort(cursor + 30));
            int commentLength = Short.toUnsignedInt(bytes.getShort(cursor + 32));
            int next = cursor + 46 + nameLength + extraLength + commentLength;
            if (next > end || (flags & 1) != 0 || (method != 0 && method != 8)
                    || bytes.getShort(cursor + 34) != 0) throw new IOException();
            long compressed = Integer.toUnsignedLong(bytes.getInt(cursor + 20));
            long expanded = Integer.toUnsignedLong(bytes.getInt(cursor + 24));
            long local = Integer.toUnsignedLong(bytes.getInt(cursor + 42));
            expandedTotal += expanded;
            if (expanded > MAX_ENTRY_BYTES || expandedTotal > MAX_BYTES) throw new ResourceLimitException();
            if (local + 30 > offset || bytes.getInt(Math.toIntExact(local)) != 0x04034b50) throw new IOException();
            String name = new String(content, cursor + 46, nameLength, StandardCharsets.UTF_8);
            if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":")
                    || name.codePoints().anyMatch(Character::isISOControl)) throw new IOException();
            for (String segment : name.split("/")) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) throw new IOException();
            }
            if (entries.put(name, new ArchiveEntry(compressed, expanded)) != null) throw new IOException();
            cursor = next;
        }
        if (cursor != end) throw new IOException();
        return entries;
    }

    private record XmlInput(byte[] content, SafeXmlHandler handler) { }

    private static void xml(XmlInput input) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setXIncludeAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        var reader = factory.newSAXParser().getXMLReader();
        reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        reader.setProperty("jdk.xml.maxElementDepth", "256");
        reader.setEntityResolver((publicId, systemId) -> { throw new SAXException(); });
        reader.setContentHandler(input.handler());
        reader.setErrorHandler(input.handler());
        reader.parse(new org.xml.sax.InputSource(new ByteArrayInputStream(input.content())));
        if (!input.handler().rootSeen) throw new SAXException();
    }

    private static class SafeXmlHandler extends DefaultHandler {
        private boolean rootSeen;
        protected int depth;
        @Override public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            rootSeen = true;
            depth++;
        }
        @Override public void endElement(String uri, String localName, String qName) { depth--; }
        @Override public void error(SAXParseException error) throws SAXException { throw new SAXException(); }
        @Override public void fatalError(SAXParseException error) throws SAXException { throw new SAXException(); }
    }

    private static final class TypesHandler extends SafeXmlHandler {
        private boolean wordMain;
        @Override public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);
            if (depth == 1 && (!TYPES_NAMESPACE.equals(uri) || !"Types".equals(localName))) throw new SAXException();
            if (depth == 2 && TYPES_NAMESPACE.equals(uri) && "Override".equals(localName)
                    && "/word/document.xml".equals(attributes.getValue("PartName"))) {
                if (wordMain || !WORD_MAIN_TYPE.equals(attributes.getValue("ContentType"))) throw new SAXException();
                wordMain = true;
            }
        }
    }

    private static final class WordHandler extends SafeXmlHandler {
        @Override public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);
            if (depth == 1 && (!WORD_NAMESPACES.contains(uri) || !"document".equals(localName))) throw new SAXException();
        }
    }

    private static final class ResourceLimitException extends RuntimeException { }
}
