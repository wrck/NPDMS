package cn.iocoder.yudao.module.pms.integration.api.basesync;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Length framing distinguishes NULL, empty strings and separator characters. */
public final class SyncFingerprint {
    private SyncFingerprint() {}
    public static String of(List<?> fields) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            for(var field:fields) {
                if(field==null) digest.update(ByteBuffer.allocate(4).putInt(-1).array());
                else {
                    byte[] bytes=field.toString().getBytes(StandardCharsets.UTF_8);
                    digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                    digest.update(bytes);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
