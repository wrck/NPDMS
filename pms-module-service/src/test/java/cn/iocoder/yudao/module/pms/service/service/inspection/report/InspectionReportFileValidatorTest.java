package cn.iocoder.yudao.module.pms.service.service.inspection.report;

import cn.iocoder.yudao.module.pms.service.service.inspection.report.InspectionReportFileValidator.Input;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InspectionReportFileValidatorTest {
    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String WORD_NAMESPACE = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String CONTENT_TYPES = """
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Override PartName="/word/document.xml"
                ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
            """;
    private static final String WORD_XML = "<w:document xmlns:w=\"" + WORD_NAMESPACE + "\"><w:body/></w:document>";
    private final InspectionReportFileValidator validator = new InspectionReportFileValidator();

    @Test
    void acceptsRealPdfDocxAndXmlFromTheirActualParsers() throws Exception {
        assertDoesNotThrow(() -> validator.validate(new Input("PDF", "报告.PDF", "application/pdf", pdf(1))));
        byte[] word;
        try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("中文巡检结果<&>");
            document.write(output);
            word = output.toByteArray();
        }
        assertDoesNotThrow(() -> validator.validate(new Input("DOCX", "报告.docx", DOCX_MIME, word)));
        assertDoesNotThrow(() -> validator.validate(new Input("XML", "报告.xml", "application/xml; charset=UTF-8",
                utf8("<?xml version=\"1.0\"?><inspectionReport><result>通过</result></inspectionReport>"))));
        assertDoesNotThrow(() -> validator.validate(new Input("XML", "报告.xml", "text/xml", utf8("<report/>"))));
    }

    @Test
    void rejectsExtensionMimeAndActualContentMismatch() throws Exception {
        rejects(new Input("PDF", "report.xml", "application/pdf", pdf(1)));
        rejects(new Input("PDF", "report.pdf", "text/xml", pdf(1)));
        rejects(new Input("PDF", "report.pdf", "application/pdf", utf8("<report/>")));
        rejects(new Input("PDF", "report.pdf", "application/pdf", utf8("%PDF-1.7\nprivate-upload-marker")));
        rejects(new Input("PDF", "report.pdf", "application/pdf", pdf(0)));
        rejects(new Input("DOCX", "report.docx", DOCX_MIME, utf8("PK private-upload-marker")));
        rejects(new Input("XML", "report.xml", "application/xml", utf8("private-upload-marker")));
        rejects(new Input("XML", "report.xml", "application/xml", new byte[0]));
        rejects(new Input("HTML", "report.html", "text/html", utf8("<html/>")));
    }

    @Test
    void rejectsOleContainersWithoutARealWordStream() throws Exception {
        for (String name : new String[] { "Workbook", "WordDocument" }) {
            byte[] content;
            try (var filesystem = new POIFSFileSystem(); var output = new ByteArrayOutputStream()) {
                filesystem.createDocument(new ByteArrayInputStream(utf8("private-upload-marker")), name);
                filesystem.writeFilesystem(output);
                content = output.toByteArray();
            }
            rejects(new Input("DOC", "report.doc", "application/msword", content));
        }
    }

    @Test
    void rejectsDtdExternalEntitiesAndMalformedXmlWithoutLeakingParserDetails() {
        for (String document : new String[] {
                "<!DOCTYPE report [<!ENTITY secret SYSTEM 'file:///private-upload-marker'>]><report>&secret;</report>",
                "<!DOCTYPE report SYSTEM 'https://private-upload-marker.invalid/report.dtd'><report/>",
                "<!DOCTYPE report [<!ENTITY value 'private-upload-marker'>]><report>&value;</report>",
                "<report><private-upload-marker></report>",
                "<?xml version='1.0'?>" }) {
            IllegalArgumentException error = rejects(new Input("XML", "report.xml", "application/xml", utf8(document)));
            assertEquals("报告文件内容与指定格式不符或已损坏", error.getMessage());
            assertNull(error.getCause());
        }
    }

    @Test
    void acceptsStrictWordNamespaceAndRejectsOtherOfficeRootsAndContentTypes() throws Exception {
        assertDoesNotThrow(() -> validator.validate(docxInput(archive(parts(CONTENT_TYPES,
                WORD_XML.replace(WORD_NAMESPACE, "http://purl.oclc.org/ooxml/wordprocessingml/main"))))));
        rejects(docxInput(archive(parts(CONTENT_TYPES, "<workbook xmlns='urn:spreadsheet'/>"))));
        rejects(docxInput(archive(parts(CONTENT_TYPES.replace("wordprocessingml.document", "spreadsheetml.sheet"), WORD_XML))));
        rejects(docxInput(archive(Map.of("word/document.xml", utf8(WORD_XML)))));
        rejects(docxInput(archive(Map.of("[Content_Types].xml", utf8(CONTENT_TYPES)))));
        rejects(docxInput(archive(parts(CONTENT_TYPES, "<!DOCTYPE w:document [<!ENTITY x SYSTEM 'file:///private-upload-marker'>]>" + WORD_XML))));
    }

    @Test
    void rejectsTruncatedAndInconsistentZipDirectoryAndDuplicateParts() throws Exception {
        byte[] valid = archive(parts(CONTENT_TYPES, WORD_XML));
        rejects(docxInput(Arrays.copyOf(valid, valid.length - 22)));
        byte[] wrongCount = valid.clone();
        ByteBuffer.wrap(wrongCount).order(ByteOrder.LITTLE_ENDIAN).putShort(wrongCount.length - 22 + 10, (short) 3);
        rejects(docxInput(wrongCount));
        byte[] duplicate = archive(Map.of("[Content_Types].xml", utf8(CONTENT_TYPES), "word/document.xml", utf8(WORD_XML),
                "word/xxxxxxxx.xml", utf8(WORD_XML)));
        byte[] from = utf8("word/xxxxxxxx.xml");
        byte[] to = utf8("word/document.xml");
        for (int index = 0; index <= duplicate.length - from.length; index++) {
            if (Arrays.equals(duplicate, index, index + from.length, from, 0, from.length)) {
                System.arraycopy(to, 0, duplicate, index, to.length);
            }
        }
        rejects(docxInput(duplicate));
    }

    @Test
    void rejectsZipEntryCountsDeclaredOversizeAndHighExpansionUsingSmallArchives() throws Exception {
        Map<String, byte[]> many = parts(CONTENT_TYPES, WORD_XML);
        for (int index = 0; index < 1023; index++) many.put("word/empty-" + index, new byte[0]);
        assertEquals("报告文件超过解析资源限制", rejects(docxInput(archive(many))).getMessage());

        byte[] declaredOversize = archive(parts(CONTENT_TYPES, WORD_XML));
        ByteBuffer directory = ByteBuffer.wrap(declaredOversize).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index < declaredOversize.length - 46; index++) {
            if (directory.getInt(index) == 0x02014b50) {
                directory.putInt(index + 24, 21 * 1024 * 1024);
                break;
            }
        }
        assertEquals("报告文件超过解析资源限制", rejects(docxInput(declaredOversize)).getMessage());

        Map<String, byte[]> compressed = parts(CONTENT_TYPES, WORD_XML);
        compressed.put("word/padding.bin", new byte[96 * 1024]);
        byte[] small = archive(compressed);
        assertEquals("报告文件超过解析资源限制", rejects(docxInput(small)).getMessage());
    }

    private IllegalArgumentException rejects(InspectionReportFileValidator.Input input) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> validator.validate(input));
        assertNull(error.getCause());
        return error;
    }

    private static InspectionReportFileValidator.Input docxInput(byte[] content) {
        return new Input("DOCX", "report.docx", DOCX_MIME, content);
    }

    private static byte[] pdf(int pages) throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (int index = 0; index < pages; index++) document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private static Map<String, byte[]> parts(String types, String document) {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", utf8(types));
        parts.put("word/document.xml", utf8(document));
        return parts;
    }

    private static byte[] archive(Map<String, byte[]> entries) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] utf8(String content) { return content.getBytes(StandardCharsets.UTF_8); }
}
