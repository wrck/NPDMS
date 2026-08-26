package cn.iocoder.yudao.module.pms.platform.file;

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
import org.springframework.mock.web.MockMultipartFile;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileContentValidationTest {

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF"
            .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EICAR = ("X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-"
            + "ANTIVIRUS-TEST-FILE!$H+H*").getBytes(StandardCharsets.US_ASCII);

    @Test
    void validatesSizeDigestMediaTypeAndPassedScan() throws Exception {
        FileSecurityScanProvider scanner = ignored ->
                new FileSecurityScanResult("PASSED", "TEST", "1", null);
        FileContentPolicyService service = new FileContentPolicyService(
                new BoundedMultipartReader(), List.of(scanner));
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
                new BoundedMultipartReader(), List.of(scanner));

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
                new FileSecurityScanResult("PASSED", "TEST", "1", null)));
        assertThrows(RuntimeException.class, () -> passed.validate(command(
                PDF, "evidence.pdf", "application/pdf", "0".repeat(64), policy(1024L))));
        assertThrows(RuntimeException.class, () -> passed.validate(command(
                PDF, "evidence.png", "image/png", null, pngPolicy())));

        FileContentPolicyService rejected = new FileContentPolicyService(
                new BoundedMultipartReader(), List.of(ignored ->
                new FileSecurityScanResult("REJECTED", "TEST", "1", "MALWARE_FOUND")));
        assertThrows(RuntimeException.class, () -> rejected.validate(command(
                PDF, "evidence.pdf", "application/pdf", null, policy(1024L))));

        FileContentPolicyService missing = new FileContentPolicyService(
                new BoundedMultipartReader(), List.of());
        assertThrows(RuntimeException.class, () -> missing.validate(command(
                PDF, "evidence.pdf", "application/pdf", null, policy(1024L))));
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
