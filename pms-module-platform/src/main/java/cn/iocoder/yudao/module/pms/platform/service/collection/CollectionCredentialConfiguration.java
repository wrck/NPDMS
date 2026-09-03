package cn.iocoder.yudao.module.pms.platform.service.collection;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class CollectionCredentialConfiguration {

    @Bean
    public Clock collectionClock() {
        return Clock.systemUTC();
    }

    @Bean
    public StringRedisTemplate collectionStringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public CredentialTokenService.TokenStore credentialTokenStore(
            StringRedisTemplate collectionStringRedisTemplate,
            CredentialSecretProtector credentialSecretProtector) {
        return new RedisCredentialTokenStore(
                collectionStringRedisTemplate,
                "pms:collection:credential-token:",
                credentialSecretProtector);
    }

    @Bean
    public CredentialTokenService credentialTokenService(
            Clock collectionClock,
            CredentialTokenService.TokenStore credentialTokenStore) {
        return new CredentialTokenService(collectionClock, credentialTokenStore);
    }
}
