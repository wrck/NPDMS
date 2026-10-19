package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi.Envelope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** Compatible with the original controlled Python capture and the unchanged Asset cipher. */
@Component
final class SyncLicenseCaptureCipher {
    private final SecretKeySpec key;private final String keyId;private final SecureRandom random=new SecureRandom();
    SyncLicenseCaptureCipher(@Value("${pms.license-history.master-key:}") String encoded,@Value("${pms.license-history.key-id:V1}") String keyId) {
        if(keyId==null || !keyId.matches("[A-Za-z0-9._-]{1,64}"))throw new IllegalArgumentException("LICENSE_KEY_ID_INVALID");
        this.keyId=keyId;if(encoded==null || encoded.isBlank()){key=null;return;}
        byte[] decoded=null;
        try {decoded=Base64.getDecoder().decode(encoded);if(decoded.length!=32)throw new IllegalArgumentException();key=new SecretKeySpec(decoded,"AES");}
        catch(RuntimeException failure){throw new IllegalArgumentException("LICENSE_MASTER_KEY_INVALID");}
        finally {if(decoded!=null)Arrays.fill(decoded,(byte)0);}
    }
    void requireAvailable(){if(key==null)throw new IllegalStateException("LICENSE_MASTER_KEY_UNAVAILABLE");}
    record Capture(Long tenantId,String sourceKey,char[] code) {}
    Envelope encrypt(Capture input) {
        byte[] plaintext=null,aad=null,nonce=new byte[12],encrypted=null,macBytes=null;
        ByteBuffer buffer=null;
        try {
            requireAvailable();
            if(input==null || input.tenantId()==null || input.tenantId()<=0 || input.sourceKey()==null || !input.sourceKey().matches("[1-9][0-9]*")
                    || input.code()==null || input.code().length==0 || Character.codePointCount(input.code(),0,input.code().length)>256)throw new IllegalArgumentException();
            buffer=StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(input.code()));plaintext=new byte[buffer.remaining()];buffer.get(plaintext);
            aad=("DPPMS-LICENSE|"+input.tenantId()+"|1|"+input.sourceKey()).getBytes(StandardCharsets.UTF_8);random.nextBytes(nonce);
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));cipher.updateAAD(aad);encrypted=cipher.doFinal(plaintext);
            var mac=Mac.getInstance("HmacSHA256");mac.init(key);mac.update(aad);mac.update((byte)0);macBytes=mac.doFinal(plaintext);
            var payload=ByteBuffer.allocate(nonce.length+encrypted.length).put(nonce).put(encrypted).array();
            return new Envelope(input.sourceKey(),Base64.getEncoder().encodeToString(payload),keyId,HexFormat.of().formatHex(macBytes));
        }catch(Exception failure){throw new IllegalArgumentException("LICENSE_CAPTURE_ENCRYPTION_FAILED");}
        finally {
            if(input!=null && input.code()!=null)Arrays.fill(input.code(),'\0');
            if(plaintext!=null)Arrays.fill(plaintext,(byte)0);if(aad!=null)Arrays.fill(aad,(byte)0);Arrays.fill(nonce,(byte)0);
            if(encrypted!=null)Arrays.fill(encrypted,(byte)0);if(macBytes!=null)Arrays.fill(macBytes,(byte)0);
            if(buffer!=null && buffer.hasArray())Arrays.fill(buffer.array(),(byte)0);
        }
    }
}
