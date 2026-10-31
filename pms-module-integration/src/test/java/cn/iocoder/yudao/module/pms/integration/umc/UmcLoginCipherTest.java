package cn.iocoder.yudao.module.pms.integration.umc;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class UmcLoginCipherTest {
    @Test void matchesPinnedUmcStrEncForTwelveAsciiUnicodeAndMultiBlockKeyVectors() throws Exception {
        try(var stream=getClass().getResourceAsStream("/umc/login-vectors.json")) {
            assertNotNull(stream);
            var rows=JsonUtils.parseObject(stream.readAllBytes(),List.class);assertEquals(12,rows.size());
            for(Object row:rows) {
                var value=(Map<?,?>)row;
                var keys=((List<?>)value.get("keys")).stream().map(Object::toString).toArray(String[]::new);
                assertEquals(value.get("expected"),UmcLoginCipher.encrypt(value.get("value").toString(),keys));
            }
        }
    }
    @Test void sm3UsesUtf8AndKnownPublishedDigest() {
        assertEquals("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0",cn.hutool.crypto.SmUtil.sm3("abc"));
    }
    @Test void passwordAndUsernameUseDifferentChallengeKeyOrder() throws Exception {
        var challenge=new UmcLoginCipher.Challenge("1","123","456");
        var login=UmcLoginCipher.login(new UmcLoginCipher.Credentials("same","same",challenge));
        assertNotEquals(login.q(),login.w());assertEquals(cn.hutool.crypto.SmUtil.sm3("samesame456123"),login.smvalidate());
    }
}
