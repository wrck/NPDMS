package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DacDesktopConnectServiceTest {
    private final Map<String, String> records = new HashMap<>();
    private final DacDesktopConnectStore store = mock(DacDesktopConnectStore.class);
    private final DacDesktopConnectService service = new DacDesktopConnectService(store);
    private static final String ID = "1".repeat(32), NONCE = "a".repeat(64);
    private static final String SERVER = "http://10.210.0.11:59191", ORIGIN = "http://10.210.0.11:19191";
    private KeyPair keys;
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        when(store.create(anyString(), anyString())).thenAnswer(call -> records.putIfAbsent(call.getArgument(0), call.getArgument(1)) == null);
        when(store.get(anyString())).thenAnswer(call -> records.get(call.getArgument(0)));
        when(store.replace(any())).thenAnswer(call -> {
            DacDesktopConnectStore.Change change = call.getArgument(0);
            return records.replace(change.id(), change.before(), change.after());
        });
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    private DacDesktopConnectService.Begin beginInput() throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(NONCE.getBytes(StandardCharsets.UTF_8)));
        return new DacDesktopConnectService.Begin(ID, hash, SERVER, ORIGIN);
    }
    private DacDesktopConnectService.Proof proof(String nonce, String server, int port) throws Exception {
        byte[] encoded = keys.getPublic().getEncoded();
        String pub = HexFormat.of().formatHex(Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));
        String instance = "01234567-1234-1234-1234-012345678901";
        var signature = Signature.getInstance("Ed25519"); signature.initSign(keys.getPrivate());
        signature.update(("npdms-dac-discovery-v1\n"+nonce+"\n"+port+"\n"+instance+"\n3\nREADY\n"+server+"\n"+ORIGIN).getBytes(StandardCharsets.UTF_8));
        return new DacDesktopConnectService.Proof(nonce, new DacDesktopConnectService.Status("npdms-dac", 3, "READY",
                server, ORIGIN, port, pub, instance, Base64.getEncoder().encodeToString(signature.sign())));
    }
    @Test void confirmsOnlySignedChallengeAndDoesNotStoreNonceOrPrivateKey() throws Exception {
        assertEquals(90, service.begin(beginInput(), 11L).expiresInSeconds());
        assertEquals("pending", service.poll(ID, 11L).state());
        var proof = proof(NONCE, SERVER, 8289);
        service.accept(ID, proof);
        assertEquals(proof.status(), service.poll(ID, 11L).status());
        assertFalse(records.get(ID).contains(NONCE));
        service.accept(ID, proof); // Exact redelivery is idempotent.
        assertEquals("confirmed", service.poll(ID, 11L).state());
    }
    @Test void rejectsOtherOwnerTenantAndAnonymous() throws Exception {
        service.begin(beginInput(), 11L);
        assertThrows(ResponseStatusException.class, () -> service.poll(ID, 12L));
        assertThrows(ResponseStatusException.class, () -> service.cancel(ID, 12L));
        assertThrows(ResponseStatusException.class, () -> service.poll(ID, null));
        TenantContextHolder.setTenantId(2L);
        assertThrows(ResponseStatusException.class, () -> service.poll(ID, 11L));
        assertThrows(ResponseStatusException.class, () -> service.accept(ID, proof(NONCE, SERVER, 8280)));
        assertEquals(1, records.size());
    }
    @Test void rejectsWrongNonceEnvironmentAndPortEvenWhenValidlySigned() throws Exception {
        service.begin(beginInput(), 11L);
        for (var proof : List.of(proof("b".repeat(64), SERVER, 8280), proof(NONCE, "http://other:59191", 8280), proof(NONCE, SERVER, 48182))) {
            assertThrows(ResponseStatusException.class, () -> service.accept(ID, proof));
        }
        assertEquals("pending", service.poll(ID, 11L).state());
    }
    @Test void rejectsForgedSignatureAndDoesNotOverwriteConfirmation() throws Exception {
        service.begin(beginInput(), 11L);
        var original = proof(NONCE, SERVER, 8280);
        var s = original.status();
        var forged = new DacDesktopConnectService.Status(s.product(), 3, s.status(), SERVER, ORIGIN, 8281, s.publicKey(), s.instanceId(), s.signature());
        assertThrows(ResponseStatusException.class, () -> service.accept(ID, new DacDesktopConnectService.Proof(NONCE, forged)));
        service.accept(ID, original);
        keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        assertThrows(ResponseStatusException.class, () -> service.accept(ID, proof(NONCE, SERVER, 8280)));
        assertEquals(s, service.poll(ID, 11L).status());
    }
    @Test void lateProofCannotRestoreCancelledOrExpiredRequest() throws Exception {
        service.begin(beginInput(), 11L);
        service.cancel(ID, 11L);
        assertThrows(ResponseStatusException.class, () -> service.accept(ID, proof(NONCE, SERVER, 8280)));
        assertEquals("cancelled", service.poll(ID, 11L).state());
        records.clear(); // Simulate Redis TTL expiry without a runtime Redis or database.
        assertThrows(ResponseStatusException.class, () -> service.accept(ID, proof(NONCE, SERVER, 8280)));
        assertThrows(ResponseStatusException.class, () -> service.poll(ID, 11L));
    }
    @Test void concurrentCancelCannotBeOverwrittenByProof() throws Exception {
        service.begin(beginInput(), 11L);
        doReturn(false).when(store).replace(any());
        assertThrows(ResponseStatusException.class, () -> service.accept(ID, proof(NONCE, SERVER, 8280)));
        assertEquals("pending", service.poll(ID, 11L).state());
    }
    @Test void rejectsCollisionAndInvalidRegistrationBeforeWriting() throws Exception {
        assertThrows(ResponseStatusException.class, () -> service.begin(beginInput(), null));
        assertThrows(ResponseStatusException.class, () -> service.begin(new DacDesktopConnectService.Begin(ID, NONCE, SERVER, ORIGIN+"/path"), 11L));
        assertTrue(records.isEmpty());
        service.begin(beginInput(), 11L);
        assertThrows(ResponseStatusException.class, () -> service.begin(beginInput(), 11L));
    }
}
