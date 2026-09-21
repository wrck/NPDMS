package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/pms/integration/device-ops/callbacks")
@ConditionalOnProperty(prefix = "pms.integration.device-ops", name = "enabled", havingValue = "true")
public class DacCallbackController {
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private final DacCallbackService service;
    private final byte[] signingKey;

    public DacCallbackController(DacCallbackService service,
            @Value("${pms.integration.device-ops.callback-signing-key:}") String key) {
        this.service = service;
        signingKey = key.getBytes(StandardCharsets.UTF_8);
        if (signingKey.length < 32) throw new IllegalArgumentException("DAC_CALLBACK_SIGNING_KEY_REQUIRED");
    }

    @PostMapping(consumes = "multipart/form-data")
    @PermitAll // Service authentication is the HMAC below; never a browser/user token.
    public DacCallbackService.Ack receive(@RequestPart("metadata") String raw,
            @RequestPart("log") MultipartFile file,
            @RequestHeader("X-DAC-Timestamp") String timestamp,
            @RequestHeader("X-DAC-Nonce") String nonce,
            @RequestHeader("X-DAC-Signature") String signature) throws Exception {
        if (raw.length() > 8192 || file.getSize() < 1 || file.getSize() > MAX_BYTES) reject();
        long sentAt;
        try { sentAt = Long.parseLong(timestamp); }
        catch (NumberFormatException invalid) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
        if (sentAt < Instant.now().getEpochSecond() - 300 || sentAt > Instant.now().getEpochSecond() + 300) reject();
        DacCallbackMetadata metadata = JsonUtils.parseObjectQuietly(raw, DacCallbackMetadata.class);
        if (metadata == null || metadata.tenantId() == null
                || !metadata.tenantId().equals(TenantContextHolder.getRequiredTenantId())
                || metadata.callbackId() == null || !metadata.callbackId().matches("[a-f0-9]{64}")
                || !metadata.callbackId().equals(nonce) || metadata.platformTaskId() == null
                || !metadata.platformTaskId().matches("[a-zA-Z0-9-]{1,64}")
                || metadata.externalTaskId() == null || metadata.externalTaskId().isBlank() || metadata.externalTaskId().length() > 128
                || metadata.sha256() == null || !metadata.sha256().matches("[a-f0-9]{64}")
                || metadata.sequence() != 1 || metadata.resultVersion() != 1
                || metadata.sizeBytes() != file.getSize()
                || metadata.externalStatus() == null
                || !Set.of("SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "TIMED_OUT", "CANCELLED", "SECURITY_EXCEPTION")
                    .contains(metadata.externalStatus())) reject();
        String metaDigest = sha(raw.getBytes(StandardCharsets.UTF_8));
        String canonical = timestamp + "\n" + nonce + "\n" + metaDigest + "\n" + metadata.sha256();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
        byte[] supplied;
        try { supplied = HexFormat.of().parseHex(signature); }
        catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
        if (!MessageDigest.isEqual(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)), supplied)) reject();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            long count = 0;
            for (int read; (read = input.read(buffer)) != -1;) {
                count += read;
                if (count > metadata.sizeBytes()) reject();
                digest.update(buffer, 0, read);
            }
            if (count != metadata.sizeBytes()) reject();
        }
        if (!HexFormat.of().formatHex(digest.digest()).equals(metadata.sha256())) reject();
        try (InputStream input = file.getInputStream()) {
            return service.receive(metadata, sha((metaDigest + "\n" + metadata.sha256())
                    .getBytes(StandardCharsets.UTF_8)), input);
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static void reject() { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "DAC_CALLBACK_REJECTED"); }
}
