package cn.iocoder.yudao.server.deployment;

import cn.iocoder.yudao.module.pms.integration.deviceops.DacDeviceOpsGatewayProperties;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Synthetic credentials stay in memory; the only written value is readiness. */
public class BackendIdentityProbe {
    public static void main(String[] args) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(3072);
        var pair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----";
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("probe", Map.of(
                "npdms.server-dac.endpoint", "https://172.17.73.104:8443",
                "DAC_NAMESPACE", "npdms", "dac_service_private_key", pem,
                "npdms.server-dac.identity-ready-file", Path.of(args[0], "identity-ready").toString(),
                "RELEASE_ID", "synthetic-probe")));
        var bean = new DacDeviceOpsGatewayProperties(); bean.setEnabled(true);
        bean.setBaseUrl("https://172.17.73.104:8443");
        var processor = BackendDacIdentityConfiguration.serverDacIdentity(env);
        var proxy = (DacDeviceOpsGatewayProperties) processor.postProcessAfterInitialization(bean, "gateway");
        String token = proxy.getBearerToken();
        var parts = token.split("\\.");
        var verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(pair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        if (!verifier.verify(Base64.getUrlDecoder().decode(parts[2]))) throw new AssertionError("Signature invalid");
        if (!token.equals(proxy.getBearerToken())) throw new AssertionError("Cache invalid");
        if (!bean.getBaseUrl().equals(proxy.getBaseUrl())) throw new AssertionError("Owner endpoint changed");
        for (String endpoint : new String[]{"http://172.17.73.104:8443", "https://other.invalid:8443"}) {
            bean.setBaseUrl(endpoint);
            try { processor.postProcessAfterInitialization(bean, "gateway"); throw new AssertionError("Endpoint accepted"); }
            catch (IllegalStateException expected) { }
        }
        bean.setBaseUrl("https://172.17.73.104:8443"); bean.setBearerToken("synthetic-existing-identity");
        try { processor.postProcessAfterInitialization(bean, "gateway"); throw new AssertionError("Identity replaced"); }
        catch (IllegalStateException expected) { }
        System.out.println("PASS signed service JWT, token cache, owner endpoint preservation, HTTP/foreign endpoint refusal, existing identity preservation");
    }
}
