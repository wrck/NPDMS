package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="pms.integration.device-ops", name="enabled", havingValue="true")
public class DacDesktopConnectService {
    private final DacDesktopConnectStore store;
    public record Begin(String requestId, String nonceHash, String serverUrl, String webOrigin) { }
    public record Started(String requestId, long tenantId, int expiresInSeconds) { }
    public record Status(String product, int protocolVersion, String status, String serverUrl, String webOrigin,
                         int port, String publicKey, String instanceId, String signature) { }
    public record Proof(String nonce, Status status) {
        @Override public String toString() { return "DesktopConnectProof[redacted]"; }
    }
    public record Result(String state, Status status) { }
    public record Pending(long tenantId, long userId, Begin request, String state, Status status) { }

    public Started begin(Begin input, Long actor) {
        requireActor(actor);
        if (input == null || !hex(input.requestId(), 32) || !hex(input.nonceHash(), 64)
                || !validUrl(input.serverUrl(), false) || !validUrl(input.webOrigin(), true)) fail(HttpStatus.BAD_REQUEST);
        long tenant = TenantContextHolder.getRequiredTenantId();
        if (tenant <= 0) fail(HttpStatus.BAD_REQUEST);
        var pending = new Pending(tenant, actor, input, "pending", null);
        if (!store.create(input.requestId(), JsonUtils.toJsonString(pending))) fail(HttpStatus.CONFLICT);
        return new Started(input.requestId(), tenant, 90);
    }

    public Result poll(String id, Long actor) {
        var pending = owned(id, actor);
        return new Result(pending.state(), pending.status());
    }

    public void cancel(String id, Long actor) {
        requireActor(actor);
        String raw = read(id);
        var pending = parseOwned(raw, actor);
        // A tombstone preserves TTL and prevents a late proof from restoring cancelled trust.
        if (!store.replace(new DacDesktopConnectStore.Change(id, raw, JsonUtils.toJsonString(
                new Pending(pending.tenantId(), pending.userId(), pending.request(), "cancelled", null))))) {
            fail(HttpStatus.CONFLICT);
        }
    }

    public void accept(String id, Proof proof) {
        String raw = read(id);
        var pending = JsonUtils.parseObject(raw, Pending.class);
        if (pending.tenantId() != TenantContextHolder.getRequiredTenantId()) fail(HttpStatus.NOT_FOUND);
        if (proof == null || !hex(proof.nonce(), 64) || !validProof(pending.request(), proof)) fail(HttpStatus.UNAUTHORIZED);
        if ("confirmed".equals(pending.state()) && Objects.equals(pending.status(), proof.status())) return;
        if (!"pending".equals(pending.state())) fail(HttpStatus.CONFLICT);
        if (!store.replace(new DacDesktopConnectStore.Change(id, raw, JsonUtils.toJsonString(
                new Pending(pending.tenantId(), pending.userId(), pending.request(), "confirmed", proof.status()))))) {
            fail(HttpStatus.CONFLICT);
        }
    }

    private Pending owned(String id, Long actor) { requireActor(actor); return parseOwned(read(id), actor); }
    private Pending parseOwned(String raw, Long actor) {
        var pending = JsonUtils.parseObject(raw, Pending.class);
        if (pending.tenantId() != TenantContextHolder.getRequiredTenantId() || pending.userId() != actor.longValue()) fail(HttpStatus.NOT_FOUND);
        return pending;
    }
    private String read(String id) {
        if (!hex(id, 32)) fail(HttpStatus.BAD_REQUEST);
        String raw = store.get(id);
        if (raw == null) fail(HttpStatus.NOT_FOUND);
        return raw;
    }
    private static void requireActor(Long actor) { if (actor == null || actor <= 0) fail(HttpStatus.UNAUTHORIZED); }
    private static void fail(HttpStatus status) { throw new ResponseStatusException(status); }
    private static boolean hex(String value, int length) { return value != null && value.matches("[a-f0-9]{" + length + "}"); }
    private static boolean validUrl(String value, boolean origin) {
        try {
            if (value == null || value.length() > 2048 || value.matches("(?s).*[\\s\\\\\"].*")) return false;
            var uri = URI.create(value);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && uri.getPort() != 0 && uri.getPort() <= 65535
                    && (!origin || uri.getPath().isEmpty());
        } catch (RuntimeException failure) { return false; }
    }
    private static boolean validProof(Begin expected, Proof proof) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(proof.nonce().getBytes(StandardCharsets.UTF_8)));
            var s = proof.status();
            if (!hash.equals(expected.nonceHash()) || s == null || !"sdp-dac".equals(s.product())
                    || s.protocolVersion() != 3 || !"READY".equals(s.status()) || s.port() < 8280 || s.port() > 8289
                    || !expected.serverUrl().equals(s.serverUrl()) || !expected.webOrigin().equals(s.webOrigin())
                    || !hex(s.publicKey(), 64) || s.instanceId() == null
                    || !s.instanceId().matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                    || s.signature() == null || s.signature().length() != 88) return false;
            byte[] raw = HexFormat.of().parseHex("302a300506032b6570032100" + s.publicKey());
            var key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(raw));
            var verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            String message = "sdp-dac-discovery-v1\n" + proof.nonce() + "\n" + s.port() + "\n" + s.instanceId()
                    + "\n3\nREADY\n" + s.serverUrl() + "\n" + s.webOrigin();
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(s.signature()));
        } catch (Exception failure) { return false; }
    }
}
