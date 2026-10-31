package cn.iocoder.yudao.module.pms.integration.umc;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.HexFormat;

/** UMC e.js strEnc wire format: UTF-16BE blocks, zero fill, successive DES keys. */
final class UmcLoginCipher {
    private UmcLoginCipher() { }

    record Challenge(String c, String cn, String cp) { }
    record Credentials(String username, String password, Challenge challenge) {
        @Override public String toString() { return "UMC credentials [redacted]"; }
    }
    record Login(String q, String w, String smvalidate) {
        @Override public String toString() { return "UMC login proof [redacted]"; }
    }

    static Login login(Credentials input) throws Exception {
        var c = input.challenge();
        return new Login(encrypt(input.username(), new String[]{c.cn(), c.cp(), c.c()}),
                encrypt(input.password(), new String[]{c.cp(), c.cn(), c.c()}),
                cn.hutool.crypto.SmUtil.sm3(input.username() + input.password() + c.cp() + c.cn()));
    }

    static String encrypt(String value, String[] keys) throws Exception {
        var result = new StringBuilder();
        for (int offset = 0; offset < value.length(); offset += 4) {
            byte[] block = block(value, offset);
            try {
                for (String key : keys) {
                    for (int index = 0; index < key.length(); index += 4) {
                        byte[] material = block(key, index);
                        try {
                            var cipher = Cipher.getInstance("DES/ECB/NoPadding");
                            // e.js groups the 56 selected key bits by reversed byte columns;
                            // remap its PC-1 ordering before using the standard JCE DES engine.
                            byte[] mapped = legacyKey(material);
                            try { cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(mapped, "DES")); }
                            finally { Arrays.fill(mapped, (byte) 0); }
                            byte[] previous = block;
                            block = cipher.doFinal(previous);
                            Arrays.fill(previous, (byte) 0);
                        } finally { Arrays.fill(material, (byte) 0); }
                    }
                }
                result.append(HexFormat.of().withUpperCase().formatHex(block));
            } finally { Arrays.fill(block, (byte) 0); }
        }
        return result.toString();
    }

    private static byte[] legacyKey(byte[] original) {
        int[] pc1={57,49,41,33,25,17,9,1,58,50,42,34,26,18,10,2,59,51,43,35,27,19,11,3,60,52,44,36,
                63,55,47,39,31,23,15,7,62,54,46,38,30,22,14,6,61,53,45,37,29,21,13,5,28,20,12,4};
        byte[] key=new byte[8];
        for(int i=0;i<56;i++) {
            int from=(7-i%8)*8+i/8;
            int bit=(original[from/8] >>> (7-from%8)) & 1;
            int to=pc1[i]-1;
            key[to/8] |= (byte)(bit << (7-to%8));
        }
        return key;
    }

    private static byte[] block(String value, int start) {
        byte[] bytes = new byte[8];
        for (int i = 0; i < 4 && start + i < value.length(); i++) {
            char ch = value.charAt(start + i);
            bytes[i * 2] = (byte) (ch >>> 8);
            bytes[i * 2 + 1] = (byte) ch;
        }
        return bytes;
    }
}
