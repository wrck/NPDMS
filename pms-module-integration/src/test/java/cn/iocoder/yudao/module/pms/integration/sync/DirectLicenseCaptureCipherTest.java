package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi.Envelope;
import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DirectLicenseCaptureCipherTest {
    final DirectLicenseCaptureCipher cipher=new DirectLicenseCaptureCipher(DirectLicenseHistoryCipherTest.KEY,"TEST-V1");
    final DirectLicenseHistoryCipher verifier=new DirectLicenseHistoryCipher(DirectLicenseHistoryCipherTest.KEY,"TEST-V1");
    Envelope encrypt(String text){return cipher.encrypt(new SyncLicenseCaptureCipher.Capture(1L,"472122",text==null?null:text.toCharArray()));}
    @Test void approvedEmptyStringHasNoPlaceholderAndUsesAuthenticatedEncryption() throws Exception {
        var first=encrypt("");var second=encrypt("");assertEquals(28,Base64.getDecoder().decode(first.ciphertext()).length);
        assertNotEquals(first.ciphertext(),second.ciphertext());assertEquals(first.codeMac(),second.codeMac());
        byte[] payload=Base64.getDecoder().decode(first.ciphertext());var independent=Cipher.getInstance("AES/GCM/NoPadding");
        independent.init(Cipher.DECRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(DirectLicenseHistoryCipherTest.KEY),"AES"),new GCMParameterSpec(128,payload,0,12));
        independent.updateAAD("DPPMS-LICENSE|1|1|472122".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(new byte[0],independent.doFinal(payload,12,payload.length-12));
        assertTrue(verifier.verifyContent(first,1L).empty());assertEquals(first.codeMac(),verifier.verifyStored(verifier.seal(first,1L),1L));
    }
    @Test void nullOverlengthAndLegacyEmptyRemainRejectedAndInputIsWiped() {
        assertThrows(IllegalArgumentException.class,()->encrypt(null));assertThrows(IllegalArgumentException.class,()->encrypt("x".repeat(257)));
        var old=new SyncLicenseCaptureCipher(DirectLicenseHistoryCipherTest.KEY,"TEST-V1");
        assertThrows(IllegalArgumentException.class,()->old.encrypt(new SyncLicenseCaptureCipher.Capture(1L,"472122",new char[0])));
        char[] input=" 合成许可 ".toCharArray();var captured=cipher.encrypt(new SyncLicenseCaptureCipher.Capture(1L,"472122",input));
        assertArrayEquals(new char[input.length],input);assertFalse(verifier.verifyContent(captured,1L).empty());
    }
    @Test void emptyCiphertextStillRejectsTamperingWrongMacAndWrongTenant() {
        var valid=encrypt("");var bytes=Base64.getDecoder().decode(valid.ciphertext());bytes[bytes.length-1]^=1;
        assertThrows(IllegalArgumentException.class,()->verifier.verify(new Envelope(valid.sourceKey(),Base64.getEncoder().encodeToString(bytes),valid.keyId(),valid.codeMac()),1L));
        assertThrows(IllegalArgumentException.class,()->verifier.verify(new Envelope(valid.sourceKey(),valid.ciphertext(),valid.keyId(),"0".repeat(64)),1L));
        assertThrows(IllegalArgumentException.class,()->verifier.verify(valid,2L));
    }
}
