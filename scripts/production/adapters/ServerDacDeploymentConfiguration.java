package com.dp.plat.deviceops.server.deployment;

import com.dp.plat.deviceops.adapter.web.parser.ParserControlProperties;
import com.dp.plat.deviceops.adapter.web.parser.concurrency.ParserWorkerMaintenance;
import com.dp.plat.deviceops.parser.runtime.model.*;
import com.dp.plat.deviceops.parser.runtime.port.ParserReleaseRepository;
import com.dp.plat.deviceops.parser.runtime.service.ParserReleaseService;
import com.dp.plat.deviceops.parser.semantic.ParserCoordinate;
import com.dp.plat.deviceops.parser.semantic.release.ParserReleaseBundleCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Deployment adapter: existing authentication and parser Owner services remain authoritative. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "npdms.server-dac.enabled", havingValue = "true")
public class ServerDacDeploymentConfiguration {
    @Bean
    JwtDecoder serverDacJwtDecoder(Environment environment) throws Exception {
        String pem = Files.readString(Path.of(environment.getRequiredProperty("npdms.server-dac.public-key")));
        String encoded = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        var key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        var decoder = NimbusJwtDecoder.withPublicKey(key).build();
        OAuth2TokenValidator<Jwt> audience = token -> token.getAudience().contains("device-ops")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "DAC audience required", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("urn:npdms:server-dac"), audience));
        return decoder;
    }

    @Bean
    ApplicationRunner serverDacParserInitialization(Environment environment, ParserReleaseRepository releases,
            ParserReleaseService service, ParserWorkerMaintenance workers, ParserControlProperties properties,
            TransactionTemplate transaction, Clock clock) {
        return arguments -> {
            var bundle = new ParserReleaseBundleCodec().decode(Path.of(environment.getRequiredProperty("npdms.server-dac.parser-directory")));
            var manifest = bundle.manifest();
            if (!"device-command-output".equals(manifest.logType()) || !"1.4.0".equals(manifest.releaseVersion())) {
                throw new IllegalStateException("Unexpected shipped server DAC parser release");
            }
            if (releases.findLogType(manifest.logType()).isEmpty()) {
                workers.heartbeat();
                transaction.executeWithoutResult(status -> {
                    if (releases.findLogType(manifest.logType()).isPresent()) return;
                    var now = clock.instant();
                    String id = "npdms-server-device-command-output-1.4.0";
                    releases.createLogType(new LogType(manifest.logType(), "设备命令输出", "服务端DAC安装包内置解析版本", now, now));
                    var coordinate = new ParserCoordinate(manifest.logType(), manifest.releaseVersion(), manifest.engineVersion(),
                            manifest.ruleVersion(), manifest.projectionVersion(), manifest.extension() == null ? null : manifest.extension().extensionId(),
                            manifest.extension() == null ? null : manifest.extension().extensionVersion());
                    service.saveDraft(new ParserRelease(id, manifest.logType(), manifest.releaseVersion(), ReleaseState.DRAFT, coordinate, 1, null, now, null), bundle);
                    if (!service.validate(id).passed()) throw new IllegalStateException("Server DAC parser verification failed");
                    service.publish(id);
                    service.activate(manifest.logType(), id, null, properties.getActivationMinimumCapableWorkers());
                });
            }
            // Never overwrite an operator's existing release selection or incomplete historical configuration.
            if (releases.findActive(manifest.logType()).isEmpty()) {
                throw new IllegalStateException("Existing DAC parser has no active release; preserved");
            }
            Files.writeString(Path.of(environment.getRequiredProperty("npdms.server-dac.ready-file")),
                    "ready");
        };
    }
}
