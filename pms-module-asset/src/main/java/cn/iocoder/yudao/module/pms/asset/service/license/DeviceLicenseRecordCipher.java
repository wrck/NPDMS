package cn.iocoder.yudao.module.pms.asset.service.license;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/** Validates source-side AES-256-GCM envelopes. Plaintext never leaves this component. */
@Component
public class DeviceLicenseRecordCipher {
    private final SecretKeySpec key;
    private final String keyId;

    public DeviceLicenseRecordCipher(@Value("${pms.license-history.master-key:}") String encodedKey,
                                @Value("${pms.license-history.key-id:V1}") String keyId) {
        if (keyId == null || !keyId.matches("[A-Za-z0-9._-]{1,64}")) fail("LICENSE_KEY_ID_INVALID");
        this.keyId = keyId;
        if (encodedKey == null || encodedKey.isBlank()) { key = null; return; }
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(encodedKey); }
        catch (IllegalArgumentException failure) { throw new IllegalStateException("LICENSE_MASTER_KEY_INVALID"); }
        try {
            if (decoded.length != 32) fail("LICENSE_MASTER_KEY_INVALID");
            key = new SecretKeySpec(decoded,"AES");
        } finally { Arrays.fill(decoded,(byte)0); }
    }

    public String verify(cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi.Envelope envelope, Long tenantId) {
        // Fixed initial-capture namespace; a shared contract license is independent of project membership.
        String projectKey = "1";
        if (key == null) fail("LICENSE_MASTER_KEY_UNAVAILABLE");
        if (envelope == null || !keyId.equals(envelope.keyId())
                || envelope.sourceKey() == null || !envelope.sourceKey().matches("[1-9][0-9]*")
                || tenantId == null || tenantId <= 0 || projectKey == null || !projectKey.matches("[1-9][0-9]*")
                || envelope.codeMac() == null || !envelope.codeMac().matches("[a-f0-9]{64}")
                || envelope.ciphertext() == null || envelope.ciphertext().length() > 1536)
            fail("LICENSE_ENVELOPE_INVALID");
        byte[] payload;
        try { payload = Base64.getDecoder().decode(envelope.ciphertext()); }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("LICENSE_ENVELOPE_INVALID"); }
        byte[] plaintext = null;
        byte[] expectedMac = null;
        byte[] actualMac = null;
        byte[] aad = aad(tenantId,projectKey,envelope.sourceKey());
        try {
            if (payload.length < 28 || payload.length > 1052) fail("LICENSE_ENVELOPE_INVALID");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,payload,0,12));
            cipher.updateAAD(aad);
            plaintext = cipher.doFinal(payload,12,payload.length-12);
            var decoder = StandardCharsets.UTF_8.newDecoder();
            var decoded = decoder.decode(ByteBuffer.wrap(plaintext));
            try { if (!decoded.hasRemaining() || decoded.codePoints().count() > 256) fail("LICENSE_SOURCE_CODE_TOO_LONG"); }
            finally { if (decoded.hasArray()) Arrays.fill(decoded.array(),'\0'); }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key); mac.update(aad); mac.update((byte)0);
            expectedMac = mac.doFinal(plaintext);
            actualMac = HexFormat.of().parseHex(envelope.codeMac());
            if (!MessageDigest.isEqual(expectedMac,actualMac)) fail("LICENSE_ENVELOPE_AUTHENTICATION_FAILED");
            return envelope.codeMac();
        } catch (GeneralSecurityException | java.nio.charset.CharacterCodingException failure) {
            // Provider errors must not include plaintext, ciphertext or source secrets in public/logged causes.
            throw new IllegalArgumentException("LICENSE_ENVELOPE_AUTHENTICATION_FAILED");
        } finally {
            Arrays.fill(payload,(byte)0); Arrays.fill(aad,(byte)0);
            if (plaintext != null) Arrays.fill(plaintext,(byte)0);
            if (expectedMac != null) Arrays.fill(expectedMac,(byte)0);
            if (actualMac != null) Arrays.fill(actualMac,(byte)0);
        }
    }
    static byte[] aad(Long tenantId,String projectKey,String sourceKey) {
        return ("DPPMS-LICENSE|"+tenantId+"|"+projectKey+"|"+sourceKey).getBytes(StandardCharsets.UTF_8);
    }
    public String seal(cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi.Envelope envelope, Long tenantId) {
        verify(envelope,tenantId);
        return cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(new Sealed("AES-256-GCM",envelope));
    }
    public String verifyStored(String sealed, Long tenantId) {
        Sealed value;
        try { value=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(sealed,Sealed.class); }
        catch (RuntimeException failure) { throw new IllegalArgumentException("LICENSE_ENVELOPE_INVALID"); }
        if(value==null || !"AES-256-GCM".equals(value.algorithm())) fail("LICENSE_ENVELOPE_INVALID");
        return verify(value.envelope(),tenantId);
    }
    public record Sealed(String algorithm,cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi.Envelope envelope) {}
    private static void fail(String code) { throw new IllegalArgumentException(code); }
}
