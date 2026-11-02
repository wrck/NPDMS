package cn.iocoder.yudao.module.system.service.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** New account credentials are encrypted; existing unmarked records remain readable. */
@Component
public class MailCredentialCipher {
    private static final String PREFIX = "ENCv1:";
    private final SecureRandom random = new SecureRandom();
    @Value("${pms.mail.credential-key:}") private String key;

    public String encrypt(String value) {
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, bytes, nonce.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception ex) { throw new IllegalStateException("邮箱密码加密配置无效", ex); }
    }

    public String decrypt(String value) {
        if (value == null || !value.startsWith(PREFIX)) return value;
        try {
            byte[] bytes = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (bytes.length < 29) throw new IllegalArgumentException("invalid ciphertext");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, Arrays.copyOf(bytes, 12)));
            return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("邮箱密码解密失败", ex); }
    }

    private SecretKeySpec secretKey() {
        byte[] bytes = Base64.getDecoder().decode(key);
        if (bytes.length != 32) throw new IllegalStateException("邮箱密码加密密钥必须为 32 字节");
        return new SecretKeySpec(bytes, "AES");
    }
}
