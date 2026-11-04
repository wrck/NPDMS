package com.dp.plat.deviceops.server.deployment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.oauth2.jwt.JwtException;

public class DacIdentityProbe {
    static String token(PrivateKey key, String issuer, String audience, long expires) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes());
        String claims = encoder.encodeToString(("{\"iss\":\"" + issuer + "\",\"aud\":\"" + audience
                + "\",\"sub\":\"synthetic-test\",\"exp\":" + expires + "}").getBytes());
        String input = header + "." + claims;
        var signature = Signature.getInstance("SHA256withRSA"); signature.initSign(key); signature.update(input.getBytes());
        return input + "." + encoder.encodeToString(signature.sign());
    }
    public static void main(String[] args) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(3072);
        var pair = generator.generateKeyPair();
        Path publicFile = Path.of(args[0], "synthetic-public.pem");
        Files.writeString(publicFile, "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----");
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("probe", Map.of("npdms.server-dac.public-key", publicFile.toString())));
        var decoder = new ServerDacDeploymentConfiguration().serverDacJwtDecoder(env);
        long future = Instant.now().getEpochSecond() + 300;
        decoder.decode(token(pair.getPrivate(), "urn:npdms:server-dac", "device-ops", future));
        var invalid = new String[]{
                token(pair.getPrivate(), "wrong-issuer", "device-ops", future),
                token(pair.getPrivate(), "urn:npdms:server-dac", "wrong-audience", future),
                token(pair.getPrivate(), "urn:npdms:server-dac", "device-ops", future - 900),
                token(generator.generateKeyPair().getPrivate(), "urn:npdms:server-dac", "device-ops", future)};
        for (String value : invalid) {
            try { decoder.decode(value); throw new AssertionError("Invalid JWT accepted"); }
            catch (JwtException expected) { }
        }
        System.out.println("PASS valid signature and issuer/audience/expiry/wrong-key refusal");
    }
}
