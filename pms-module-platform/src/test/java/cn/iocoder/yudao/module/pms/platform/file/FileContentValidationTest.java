package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.file.FileSecurityScanProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileSecurityScanResult;
import cn.iocoder.yudao.module.pms.platform.service.file.BoundedMultipartReader;
import cn.iocoder.yudao.module.pms.platform.service.file.ClamAvFileSecurityScanProvider;
import cn.iocoder.yudao.module.pms.platform.service.file.FileContentPolicyService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileContentValidationCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.ValidatedFileContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

import java.io.DataInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_MEDIA_TYPE_INVALID;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SECURITY_SCAN_REJECTED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileContentValidationTest {

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Test
    void acceptsGeneratedSanitizedInspectionJsonByContentAndScansIt() {
        byte[] content = sanitizedInspectionJson();
        AtomicInteger scans = new AtomicInteger();
        FileSecurityScanProvider scanner = scan -> {
            scans.incrementAndGet();
            assertArrayEquals(content, scan.validatedContent());
            assertEquals("application/json", scan.detectedMediaType());
            return new FileSecurityScanResult("PASSED", "TEST", "1", null);
        };
        var service = new FileContentPolicyService(new BoundedMultipartReader(), List.of(scanner), true);

        var result = service.validate(command(content, "sanitized-inspection.json",
                "application/json", null, jsonPolicy()));

        assertEquals("application/json", result.mediaType());
        assertEquals("json", result.extension());
        assertArrayEquals(content, result.content());
        assertEquals("PASSED", result.scanStatusCode());
        assertEquals(1, scans.get());
    }

    @Test
    void rejectsInvalidJsonAndPlainTextDeclaredAsJson() {
        var service = new FileContentPolicyService(new BoundedMultipartReader(), List.of(), false);
        for (String content : List.of("{\"schemaVersion\":\"1.0.0\",\"commandBlocks\":[}",
                "display version\nSerial number: [REDACTED]")) {
            var failure = assertThrows(ServiceException.class, () -> service.validate(command(
                    content.getBytes(StandardCharsets.UTF_8), "sanitized-inspection.json",
                    "application/json", null, jsonPolicy())));

            assertEquals(FILE_MEDIA_TYPE_INVALID.getCode(), failure.getCode());
        }
    }

    @Test
    void rejectsConcatenatedJsonDocuments() {
        byte[] content = (new String(sanitizedInspectionJson(), StandardCharsets.UTF_8)
                + "\n{\"schemaVersion\":\"1.0.0\",\"commandBlocks\":[]}")
                .getBytes(StandardCharsets.UTF_8);
        var service = new FileContentPolicyService(new BoundedMultipartReader(), List.of(), false);

        var failure = assertThrows(ServiceException.class, () -> service.validate(command(
                content, "sanitized-inspection.json", "application/json", null, jsonPolicy())));

        assertEquals(FILE_MEDIA_TYPE_INVALID.getCode(), failure.getCode());
    }

    @Test
    void rejectsValidJsonWhenPolicyDoesNotAllowJson() {
        var service = new FileContentPolicyService(new BoundedMultipartReader(), List.of(), false);

        var failure = assertThrows(ServiceException.class, () -> service.validate(command(
                sanitizedInspectionJson(), "sanitized-inspection.json", "application/json", null, policy(1024L))));

        assertEquals(FILE_MEDIA_TYPE_INVALID.getCode(), failure.getCode());
    }

    @Test
    void rejectsValidJsonWithMismatchedFilenameExtension() {
        var service = new FileContentPolicyService(new BoundedMultipartReader(), List.of(), false);

        var failure = assertThrows(ServiceException.class, () -> service.validate(command(
                sanitizedInspectionJson(), "sanitized-inspection.txt", "application/json", null, jsonPolicy())));

        assertEquals(FILE_MEDIA_TYPE_INVALID.getCode(), failure.getCode());
    }

    @Test
    void rejectsValidJsonWhenSecurityScanRejectsIt() {
        AtomicInteger scans = new AtomicInteger();
        FileSecurityScanProvider scanner = ignored -> {
            scans.incrementAndGet();
            return new FileSecurityScanResult("REJECTED", "TEST", "1", "MALWARE_FOUND");
        };
        var service = new FileContentPolicyService(new BoundedMultipartReader(), List.of(scanner), true);

        var failure = assertThrows(ServiceException.class, () -> service.validate(command(
                sanitizedInspectionJson(), "sanitized-inspection.json", "application/json", null, jsonPolicy())));

        assertEquals(FILE_SECURITY_SCAN_REJECTED.getCode(), failure.getCode());
        assertEquals(1, scans.get());
    }

    private static byte[] sanitizedInspectionJson() {
        return JsonUtils.toJsonString(Map.of("schemaVersion", "1.0.0", "commandBlocks", List.of(Map.of(
                "commandIndex", 1, "commandText", "display version", "status", "SUCCESS",
                "stdout", "设备巡检模拟日志\nSerial number: [REDACTED]\n", "stderr", "",
                "truncated", false, "exitCode", 0)))).getBytes(StandardCharsets.UTF_8);
    }

    private static FileBusinessObjectPolicyFact jsonPolicy() {
        return new FileBusinessObjectPolicyFact(true, 1L, "MUTABLE", "SINGLE", Set.of("INSPECTION_LOG"),
                Set.of("application/json"), 1024L, "INTERNAL");
    }

    @Test
    void acceptsGeneratedDocxByContainerContent() throws Exception {
        byte[] content;
        try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("割接平台文档生成回归样例");
            document.write(output);
            content = output.toByteArray();
        }
        var service = new FileContentPolicyService(new BoundedMultipartReader(), List.of(), false);
        var result = service.validate(command(content, "plan.docx", DOCX, null, docxPolicy()));
        assertEquals(DOCX, result.mediaType());
        assertEquals("docx", result.extension());
        assertArrayEquals(content, result.content());
    }

    @Test
    void rejectsRenamedZipSpreadsheetAndTruncatedContainer() throws Exception {
        var service = new FileContentPolicyService(new BoundedMultipartReader(), List.of(), false);
        byte[] zip;
        try (var output = new ByteArrayOutputStream(); var archive = new ZipOutputStream(output)) {
            archive.putNextEntry(new ZipEntry("unrelated.txt"));
            archive.write("not a Word document".getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
            archive.finish();
            zip = output.toByteArray();
        }
        byte[] spreadsheet;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet("sample").createRow(0).createCell(0).setCellValue("not Word");
            workbook.write(output);
            spreadsheet = output.toByteArray();
        }
        for (byte[] content : List.of(zip, spreadsheet, new byte[] {'P', 'K', 3, 4, 0, 0})) {
            var failure = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                    () -> service.validate(command(content, "plan.docx", DOCX, null, docxPolicy())));
            assertEquals(cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_MEDIA_TYPE_INVALID.getCode(), failure.getCode());
        }
    }

    private static FileBusinessObjectPolicyFact docxPolicy() {
        return new FileBusinessObjectPolicyFact(true, 1L, "MUTABLE", "SINGLE", Set.of("FULL_PLAN"),
                Set.of(DOCX), 52_428_800L, "INTERNAL");
    }

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF"
            .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EICAR = ("X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-"
            + "ANTIVIRUS-TEST-FILE!$H+H*").getBytes(StandardCharsets.US_ASCII);

    @Test
    void validatesSizeDigestMediaTypeAndPassedScan() throws Exception {
        FileSecurityScanProvider scanner = ignored ->
                new FileSecurityScanResult("PASSED", "TEST", "1", null);
        FileContentPolicyService service = new FileContentPolicyService(
                new BoundedMultipartReader(), List.of(scanner), true);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PDF));

        ValidatedFileContent result = service.validate(command(PDF, "evidence.pdf",
                "application/pdf", sha256, policy(52_428_800L)));

        assertArrayEquals(PDF, result.content());
        assertEquals(PDF.length, result.sizeBytes());
        assertEquals(sha256, result.sha256());
        assertEquals("application/pdf", result.mediaType());
        assertEquals("pdf", result.extension());
    }

    @Test
    void rejectsContentLargerThanFiftyMegabytesBeforeScan() {
        byte[] oversized = new byte[52_428_801];
        FileSecurityScanProvider scanner = ignored -> {
            throw new AssertionError("scanner must not be called");
        };
        FileContentPolicyService service = new FileContentPolicyService(
                new BoundedMultipartReader(), List.of(scanner), true);

        assertThrows(RuntimeException.class, () -> service.validate(command(
                oversized, "evidence.pdf", "application/pdf", null, policy(52_428_800L))));
    }

    @Test
    void readsExactlyFiftyMegabytesWithinBound() {
        byte[] content = new byte[52_428_800];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.bin", "application/octet-stream", content);

        byte[] result = new BoundedMultipartReader().read(file, 52_428_800L);

        assertEquals(52_428_800, result.length);
    }

    @Test
    void rejectsDigestMediaTypeAndScanFailures() {
        FileContentPolicyService passed = new FileContentPolicyService(
                new BoundedMultipartReader(), List.of(ignored ->
                new FileSecurityScanResult("PASSED", "TEST", "1", null)), true);
        assertThrows(RuntimeException.class, () -> passed.validate(command(
                PDF, "evidence.pdf", "application/pdf", "0".repeat(64), policy(1024L))));
        assertThrows(RuntimeException.class, () -> passed.validate(command(
                PDF, "evidence.png", "image/png", null, pngPolicy())));

        FileContentPolicyService rejected = new FileContentPolicyService(
                new BoundedMultipartReader(), List.of(ignored ->
                new FileSecurityScanResult("REJECTED", "TEST", "1", "MALWARE_FOUND")), true);
        assertThrows(RuntimeException.class, () -> rejected.validate(command(
                PDF, "evidence.pdf", "application/pdf", null, policy(1024L))));

        FileContentPolicyService skippedByEnabledProvider = new FileContentPolicyService(
                new BoundedMultipartReader(), List.of(ignored ->
                new FileSecurityScanResult("SKIPPED", null, null, null)), true);
        assertThrows(RuntimeException.class, () -> skippedByEnabledProvider.validate(command(
                PDF, "evidence.pdf", "application/pdf", null, policy(1024L))));

        FileContentPolicyService missing = new FileContentPolicyService(
                new BoundedMultipartReader(), List.of(), true);
        assertThrows(RuntimeException.class, () -> missing.validate(command(
                PDF, "evidence.pdf", "application/pdf", null, policy(1024L))));
    }

    @Test
    void skipsSecurityProviderWhenScanningIsDisabled() {
        FileSecurityScanProvider scanner = ignored -> {
            throw new AssertionError("scanner must not be called");
        };
        FileContentPolicyService service = new FileContentPolicyService(
                new BoundedMultipartReader(), List.of(scanner), false);

        ValidatedFileContent result = service.validate(command(
                PDF, "evidence.pdf", "application/pdf", null, policy(1024L)));

        assertEquals("SKIPPED", result.scanStatusCode());
        assertNull(result.scanProviderCode());
        assertNull(result.scanProviderVersion());
    }

    @Test
    void clamAvAdapterUsesVersionAndInstreamProtocol() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Void> daemon = CompletableFuture.runAsync(() -> {
                try {
                    respondToVersion(server.accept());
                    respondToScan(server.accept(), PDF);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });
            ClamAvFileSecurityScanProvider provider = new ClamAvFileSecurityScanProvider(
                    "127.0.0.1", server.getLocalPort(), 1000, 3000);

            FileSecurityScanResult result = provider.scan(new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileSecurityScanCommand(
                    PDF, "evidence.pdf", "application/pdf", "application/pdf", "a".repeat(64)));

            assertEquals("PASSED", result.resultCode());
            assertEquals("CLAMAV", result.providerCode());
            assertEquals("ClamAV 1.4.3", result.providerVersion());
            daemon.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void clamAvAdapterRejectsNonCanonicalOkResponse() throws Exception {
        FileSecurityScanResult result = scanWithStubResponses("ClamAV 1.4.3", "garbage OK");

        assertEquals("ERROR", result.resultCode());
        assertEquals("INVALID_SCAN_RESPONSE", result.reasonCode());
    }

    @Test
    void clamAvAdapterRejectsInvalidVersionResponse() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Void> daemon = CompletableFuture.runAsync(() -> {
                try (Socket socket = server.accept()) {
                    assertArrayEquals("zVERSION\0".getBytes(StandardCharsets.US_ASCII),
                            socket.getInputStream().readNBytes(9));
                    socket.getOutputStream().write("not-clamav\0".getBytes(StandardCharsets.US_ASCII));
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });
            ClamAvFileSecurityScanProvider provider = new ClamAvFileSecurityScanProvider(
                    "127.0.0.1", server.getLocalPort(), 1000, 3000);

            FileSecurityScanResult result = provider.scan(
                    new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileSecurityScanCommand(
                            PDF, "evidence.pdf", "application/pdf", "application/pdf", "a".repeat(64)));

            assertEquals("ERROR", result.resultCode());
            assertEquals("PROVIDER_UNAVAILABLE", result.reasonCode());
            daemon.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "NPDMS_CLAMAV_IT", matches = "true")
    void realClamAvRejectsEicar() {
        int port = Integer.parseInt(System.getenv().getOrDefault("NPDMS_CLAMAV_PORT", "13310"));
        ClamAvFileSecurityScanProvider provider = new ClamAvFileSecurityScanProvider(
                "127.0.0.1", port, 3000, 30000);

        FileSecurityScanResult result = provider.scan(
                new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileSecurityScanCommand(
                        EICAR, "eicar.com", "application/octet-stream", "application/octet-stream",
                        "a".repeat(64)));

        assertEquals("REJECTED", result.resultCode());
        assertEquals("MALWARE_FOUND", result.reasonCode());
    }

    private static void respondToVersion(Socket socket) throws IOException {
        try (socket) {
            byte[] command = socket.getInputStream().readNBytes(9);
            assertArrayEquals("zVERSION\0".getBytes(StandardCharsets.US_ASCII), command);
            socket.getOutputStream().write("ClamAV 1.4.3\0".getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static void respondToScan(Socket socket, byte[] expected) throws IOException {
        try (socket; DataInputStream input = new DataInputStream(socket.getInputStream())) {
            assertArrayEquals("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII), input.readNBytes(10));
            int length = input.readInt();
            assertEquals(expected.length, length);
            assertArrayEquals(expected, input.readNBytes(length));
            assertEquals(0, input.readInt());
            socket.getOutputStream().write("stream: OK\0".getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static FileSecurityScanResult scanWithStubResponses(String versionResponse,
                                                                 String scanResponse) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Void> daemon = CompletableFuture.runAsync(() -> {
                try (Socket versionSocket = server.accept()) {
                    assertArrayEquals("zVERSION\0".getBytes(StandardCharsets.US_ASCII),
                            versionSocket.getInputStream().readNBytes(9));
                    versionSocket.getOutputStream().write(
                            (versionResponse + "\0").getBytes(StandardCharsets.US_ASCII));
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
                try {
                    respondToScanWithResponse(server.accept(), PDF, scanResponse);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });
            ClamAvFileSecurityScanProvider provider = new ClamAvFileSecurityScanProvider(
                    "127.0.0.1", server.getLocalPort(), 1000, 3000);
            FileSecurityScanResult result = provider.scan(
                    new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileSecurityScanCommand(
                            PDF, "evidence.pdf", "application/pdf", "application/pdf", "a".repeat(64)));
            daemon.get(3, TimeUnit.SECONDS);
            return result;
        }
    }

    private static void respondToScanWithResponse(Socket socket, byte[] expected,
                                                   String response) throws IOException {
        try (socket; DataInputStream input = new DataInputStream(socket.getInputStream())) {
            assertArrayEquals("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII), input.readNBytes(10));
            int length = input.readInt();
            assertEquals(expected.length, length);
            assertArrayEquals(expected, input.readNBytes(length));
            assertEquals(0, input.readInt());
            socket.getOutputStream().write((response + "\0").getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static FileContentValidationCommand command(byte[] content, String fileName,
                                                        String mediaType, String digest,
                                                        FileBusinessObjectPolicyFact policy) {
        return new FileContentValidationCommand(
                new MockMultipartFile("file", fileName, mediaType, content), fileName,
                content.length, mediaType, digest, policy);
    }

    private static FileBusinessObjectPolicyFact policy(long maxBytes) {
        return new FileBusinessObjectPolicyFact(
                true, 1L, "MUTABLE", "SINGLE", Set.of("CUSTOMER_DELAY_EVIDENCE"),
                Set.of("application/pdf"), maxBytes, "INTERNAL");
    }

    private static FileBusinessObjectPolicyFact pngPolicy() {
        return new FileBusinessObjectPolicyFact(
                true, 1L, "MUTABLE", "SINGLE", Set.of("CUSTOMER_DELAY_EVIDENCE"),
                Set.of("image/png"), 1024L, "INTERNAL");
    }
}
