package cn.iocoder.yudao.module.pms.platform.service.collection;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class CollectionCredentialConfigurationTest {

    @Test
    void declaresRuntimeBeansAndSharesSecretProtector() throws Exception {
        assertBean("collectionClock");
        assertBean("collectionStringRedisTemplate", RedisConnectionFactory.class);
        assertBean("credentialTokenStore", StringRedisTemplate.class, CredentialSecretProtector.class);
        assertBean("credentialTokenService", Clock.class, CredentialTokenService.TokenStore.class);

        CollectionCredentialConfiguration configuration = new CollectionCredentialConfiguration();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CredentialSecretProtector protector = mock(CredentialSecretProtector.class);
        CredentialTokenService.TokenStore store = configuration.credentialTokenStore(redisTemplate, protector);
        Clock clock = configuration.collectionClock();
        CredentialTokenService service = configuration.credentialTokenService(clock, store);

        assertNotNull(service);
        assertSame(store, service.store());
        assertEquals(Clock.systemUTC().getZone(), clock.getZone());
    }

    private static void assertBean(String name, Class<?>... parameterTypes) throws Exception {
        Method method = CollectionCredentialConfiguration.class.getDeclaredMethod(name, parameterTypes);
        assertNotNull(method.getAnnotation(Bean.class));
    }
}
