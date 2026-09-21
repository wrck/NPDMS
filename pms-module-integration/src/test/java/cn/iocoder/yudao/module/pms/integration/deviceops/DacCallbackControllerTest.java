package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DacCallbackControllerTest {
    private static final String KEY = "test-only-signature-material-32-bytes";
    private static final String CALLBACK = "a".repeat(64);
    private final DacCallbackService service = mock(DacCallbackService.class);
    private final DacCallbackController controller = new DacCallbackController(service, KEY);
    private final byte[] content = "{\"stdout\":\"configuration evidence\"}".getBytes(StandardCharsets.UTF_8);

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(7L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void authenticTerminalEvidenceIsStreamedToTheOwner() throws Exception {
        var ack = new DacCallbackService.Ack(CALLBACK, 19L, "ACKNOWLEDGED");
        when(service.receive(any(), anyString(), any())).thenAnswer(call -> {
            assertArrayEquals(content, ((java.io.InputStream) call.getArgument(2)).readAllBytes());
            return ack;
        });
        String raw = metadata(7L), now = Long.toString(Instant.now().getEpochSecond());
        assertEquals(ack, controller.receive(raw, file(content), now, CALLBACK, sign(raw, now)));
    }

    @Test void forgedSignatureCannotCreateAReceipt() throws Exception {
        String raw = metadata(7L), now = Long.toString(Instant.now().getEpochSecond());
        assertThrows(ResponseStatusException.class, () -> controller.receive(raw, file(content), now, CALLBACK, "00".repeat(32)));
        verifyNoInteractions(service);
    }

    @Test void validSignatureCannotCrossTenantOrReplayOutsideTimeWindow() throws Exception {
        String raw = metadata(8L), now = Long.toString(Instant.now().getEpochSecond());
        assertThrows(ResponseStatusException.class, () -> controller.receive(raw, file(content), now, CALLBACK, sign(raw, now)));
        String current = metadata(7L), stale = Long.toString(Instant.now().getEpochSecond() - 301);
        assertThrows(ResponseStatusException.class, () -> controller.receive(current, file(content), stale, CALLBACK, sign(current, stale)));
        verifyNoInteractions(service);
    }

    @Test void modifiedLogIsRejectedEvenWithAnAuthenticMetadataSignature() throws Exception {
        String raw = metadata(7L), now = Long.toString(Instant.now().getEpochSecond());
        byte[] changed = content.clone(); changed[10] ^= 1;
        assertThrows(ResponseStatusException.class, () -> controller.receive(raw, file(changed), now, CALLBACK, sign(raw, now)));
        verifyNoInteractions(service);
    }

    private String metadata(long tenant) throws Exception {
        return JsonUtils.toJsonString(new DacCallbackMetadata(tenant, CALLBACK, "task-1", "dac-1", "SUCCEEDED",
                1, 1, content.length, sha(content), null, "trace"));
    }
    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("log", "collection.json", "application/json", bytes);
    }
    private String sign(String raw, String timestamp) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "\n" + CALLBACK + "\n"
                + sha(raw.getBytes(StandardCharsets.UTF_8)) + "\n" + sha(content)).getBytes(StandardCharsets.UTF_8)));
    }
    private String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
