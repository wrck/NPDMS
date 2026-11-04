package cn.iocoder.yudao.server.deployment;

import cn.iocoder.yudao.module.pms.integration.deviceops.DacDeviceOpsGatewayProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Deployment-only service identity, using the existing gateway property contract. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "npdms.server-dac.enabled", havingValue = "true")
public class BackendDacIdentityConfiguration {
    public static final String ISSUER = "urn:npdms:server-dac";

    @Bean
    static BeanPostProcessor serverDacIdentity(Environment environment) {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                if (!(bean instanceof DacDeviceOpsGatewayProperties target)) return bean;
                if (!target.isEnabled()) throw new IllegalStateException("Server DAC integration must be enabled");
                if (target.getBearerToken() != null && !target.getBearerToken().isBlank()) {
                    throw new IllegalStateException("Existing DAC identity is configured; it was preserved and cannot be replaced automatically");
                }
                String endpoint = environment.getRequiredProperty("npdms.server-dac.endpoint");
                var uri = java.net.URI.create(endpoint);
                if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                        || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || !(uri.getPath() == null || uri.getPath().isEmpty())
                        || !endpoint.equals(target.getBaseUrl())) {
                    throw new IllegalStateException("Existing DAC endpoint differs from the approved HTTPS server DAC; configuration preserved");
                }
                var tokens = new Tokens(environment.getRequiredProperty("dac_service_private_key"),
                        environment.getRequiredProperty("DAC_NAMESPACE"));
                tokens.current(); // Fail startup if the encrypted identity is unusable.
                var proxy = new ProxyFactory(target);
                proxy.setProxyTargetClass(true);
                proxy.addAdvice((MethodInterceptor) invocation ->
                        invocation.getMethod().getName().equals("getBearerToken") && invocation.getArguments().length == 0
                                ? tokens.current() : invocation.proceed());
                Object result = proxy.getProxy();
                try {
                    java.nio.file.Files.writeString(java.nio.file.Path.of(environment.getRequiredProperty("npdms.server-dac.identity-ready-file")),
                            "ready");
                } catch (java.io.IOException failure) {
                    throw new IllegalStateException("Server DAC identity readiness cannot be recorded", failure);
                }
                return result;
            }
        };
    }

    static final class Tokens {
        final PrivateKey key;
        final String namespace;
        private volatile String cached;
        private volatile long renewAt;

        Tokens(String pem, String namespace) {
            if (!namespace.matches("[a-zA-Z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid DAC namespace");
            this.namespace = namespace;
            try {
                String encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
                key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
            } catch (Exception failure) {
                throw new IllegalStateException("Server DAC identity cannot be loaded", failure);
            }
        }

        synchronized String current() {
            long now = Instant.now().getEpochSecond();
            if (cached != null && now < renewAt) return cached;
            String header = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
            String claims = encode("{\"iss\":\"" + ISSUER + "\",\"sub\":\"npdms-dac-service\",\"aud\":\"device-ops\",\"iat\":"
                    + now + ",\"nbf\":" + (now - 30) + ",\"exp\":" + (now + 300)
                    + ",\"scope\":\"device-ops:collections:read device-ops:collections:execute\",\"device_ops_projects\":[\"*\"],\"device_ops_namespaces\":[\""
                    + namespace + "\"]}");
            String input = header + "." + claims;
            try {
                Signature signature = Signature.getInstance("SHA256withRSA");
                signature.initSign(key);
                signature.update(input.getBytes(StandardCharsets.US_ASCII));
                cached = input + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
                renewAt = now + 180;
                return cached;
            } catch (Exception failure) {
                throw new IllegalStateException("Server DAC identity signing failed", failure);
            }
        }

        static String encode(String value) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
