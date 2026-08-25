package cn.iocoder.yudao.module.pms.project.service.projectgovernance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.config.ProjectGovernanceGuardProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectGovernanceGuardTokenService {

    private static final String TOKEN_VERSION = "g1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HMAC_SHA256_LENGTH = 32;
    private static final Set<String> SUPPORTED_ACTIONS = Set.of(
            "ROLLBACK", "EXCEPTION_CLOSE", "REOPEN");

    private final ProjectGovernanceGuardProperties properties;

    public String issue(GuardClaims claims) {
        validateClaims(claims);
        byte[] payload = JsonUtils.toJsonByte(claims);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        return TOKEN_VERSION + "." + encodedPayload + "." + encode(sign(signingInput(encodedPayload)));
    }

    public GuardClaims verify(String token) {
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !TOKEN_VERSION.equals(parts[0])
                || parts[1].isBlank() || parts[2].isBlank()) {
            throw invalidToken();
        }
        try {
            byte[] payload = decodeCanonical(parts[1], null);
            byte[] expected = sign(signingInput(parts[1]));
            byte[] actual = decodeCanonical(parts[2], HMAC_SHA256_LENGTH);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw invalidToken();
            }
            GuardClaims claims = JsonUtils.parseObject(payload, GuardClaims.class);
            validateClaims(claims);
            return claims;
        } catch (IllegalArgumentException ex) {
            throw invalidToken();
        } catch (RuntimeException ex) {
            if ("invalid project governance guard token".equals(ex.getMessage())) {
                throw ex;
            }
            throw invalidToken();
        }
    }

    private byte[] sign(byte[] input) {
        String signingKey = properties.getSigningKey();
        if (signingKey == null || signingKey.isBlank()) {
            throw new IllegalStateException("project governance guard signing key is not configured");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(input);
        } catch (Exception ex) {
            throw new IllegalStateException("project governance guard token signing failed", ex);
        }
    }

    private static byte[] signingInput(String encodedPayload) {
        return (TOKEN_VERSION + "." + encodedPayload).getBytes(StandardCharsets.US_ASCII);
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decodeCanonical(String value, Integer expectedLength) {
        byte[] decoded = Base64.getUrlDecoder().decode(value);
        if ((expectedLength != null && decoded.length != expectedLength)
                || !encode(decoded).equals(value)) {
            throw invalidToken();
        }
        return decoded;
    }

    private static void validateClaims(GuardClaims claims) {
        if (claims == null || claims.tenantId() == null
                || claims.projectId() == null || claims.projectId() <= 0
                || claims.projectVersion() == null || claims.projectVersion() < 0
                || claims.treeRootProjectId() == null || claims.treeVersion() == null
                || claims.treeVersion() <= 0 || !SUPPORTED_ACTIONS.contains(claims.action())
                || claims.providerFacts() == null
                || claims.providerFacts().size() != ProjectGovernanceProviderRegistry.REQUIRED_PROVIDERS.size()
                || claims.providerFacts().stream().anyMatch(ProjectGovernanceGuardTokenService::invalidProviderFact)
                || !claims.providerFacts().stream().map(ProjectGovernanceGuardResult.ProviderVersion::provider)
                .toList().equals(ProjectGovernanceProviderRegistry.REQUIRED_PROVIDERS.stream().sorted().toList())
                || claims.checkedAt() == null) {
            throw invalidToken();
        }
    }

    private static boolean invalidProviderFact(ProjectGovernanceGuardResult.ProviderVersion fact) {
        return fact == null || blank(fact.provider()) || blank(fact.factVersion())
                || blank(fact.watermark()) || blank(fact.factDigest());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static IllegalArgumentException invalidToken() {
        return new IllegalArgumentException("invalid project governance guard token");
    }

    public record GuardClaims(Long tenantId, Long projectId, String action,
                              Integer projectVersion, Long treeRootProjectId,
                              Long treeVersion,
                              List<ProjectGovernanceGuardResult.ProviderVersion> providerFacts,
                              LocalDateTime checkedAt) {
        public GuardClaims {
            providerFacts = providerFacts == null ? null : providerFacts.stream()
                    .sorted(Comparator.comparing(ProjectGovernanceGuardResult.ProviderVersion::provider,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .toList();
        }
    }
}
