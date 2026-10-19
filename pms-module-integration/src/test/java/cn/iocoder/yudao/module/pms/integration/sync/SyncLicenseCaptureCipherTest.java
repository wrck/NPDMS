package cn.iocoder.yudao.module.pms.integration.sync;
import org.junit.jupiter.api.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SyncLicenseCaptureCipherTest {
    static final String KEY="AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    SyncLicenseCaptureCipher cipher=new SyncLicenseCaptureCipher(KEY,"TEST-V1");
    @Test void compatibleEnvelopePreservesWhitespaceAndWipesTransientBuffers() throws Exception {
        char[] code="  synthetic-license\n合成许可".toCharArray();String original=new String(code);
        var encrypted=cipher.encrypt(new SyncLicenseCaptureCipher.Capture(1L,"201",code));assertTrue(java.util.stream.IntStream.range(0,code.length).allMatch(i->code[i]=='\0'));
        byte[] payload=Base64.getDecoder().decode(encrypted.ciphertext());byte[] aad="DPPMS-LICENSE|1|1|201".getBytes(StandardCharsets.UTF_8);
        var decoder=Cipher.getInstance("AES/GCM/NoPadding");decoder.init(Cipher.DECRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(KEY),"AES"),new GCMParameterSpec(128,payload,0,12));decoder.updateAAD(aad);
        byte[] actual=decoder.doFinal(payload,12,payload.length-12);assertEquals(original,new String(actual,StandardCharsets.UTF_8));
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(Base64.getDecoder().decode(KEY),"HmacSHA256"));mac.update(aad);mac.update((byte)0);
        assertEquals(HexFormat.of().formatHex(mac.doFinal(actual)),encrypted.codeMac());
        var repeated=cipher.encrypt(new SyncLicenseCaptureCipher.Capture(1L,"201",original.toCharArray()));assertEquals(encrypted.codeMac(),repeated.codeMac());assertNotEquals(encrypted.ciphertext(),repeated.ciphertext());
        Arrays.fill(actual,(byte)0);
    }
    @Test void failedEncryptionDoesNotExposeCodeOrLeaveCallerBuffer() {
        char[] code="SYNTHETIC-SECRET".repeat(30).toCharArray();var error=assertThrows(IllegalArgumentException.class,()->cipher.encrypt(new SyncLicenseCaptureCipher.Capture(1L,"201",code)));
        assertEquals("LICENSE_CAPTURE_ENCRYPTION_FAILED",error.getMessage());assertNull(error.getCause());assertEquals(0,code[0]);
        char[] unavailable="SYNTHETIC-SECRET".toCharArray();assertThrows(IllegalArgumentException.class,()->new SyncLicenseCaptureCipher("","TEST-V1").encrypt(new SyncLicenseCaptureCipher.Capture(1L,"201",unavailable)));assertEquals(0,unavailable[0]);
    }
    @Test void authenticationIsBoundToTenantAndSourceIdentity() {
        var first=cipher.encrypt(new SyncLicenseCaptureCipher.Capture(1L,"201","synthetic".toCharArray()));
        assertNotEquals(first.codeMac(),cipher.encrypt(new SyncLicenseCaptureCipher.Capture(2L,"201","synthetic".toCharArray())).codeMac());
        assertNotEquals(first.codeMac(),cipher.encrypt(new SyncLicenseCaptureCipher.Capture(1L,"202","synthetic".toCharArray())).codeMac());
    }
}
