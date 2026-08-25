package cn.iocoder.yudao.module.pms.integration.governance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceBlocker;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardProviderApi;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CollectionGovernanceGuardProvider implements ProjectGovernanceGuardProviderApi {

    public static final String PROVIDER_CODE = "COLLECTION";
    private static final String FACT_VERSION = "COLLECTION_UNAVAILABLE_V1";

    private final CollectionGovernanceGuardProperties properties;

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public ProjectGovernanceProviderFact inspect(ProjectGovernanceGuardQuery query) {
        validateTenant(query);
        if (query.projectIds().isEmpty()) {
            return fact("EMPTY", List.of());
        }
        String reason = properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                ? "ENDPOINT_UNCONFIGURED" : "ENDPOINT_CONTRACT_UNVERIFIED";
        return fact(reason, List.of(new ProjectGovernanceBlocker("COLLECTION", "PROVIDER", "UNAVAILABLE",
                "PROVIDER_UNAVAILABLE", "采集守卫不可用")));
    }

    private static ProjectGovernanceProviderFact fact(String state, List<ProjectGovernanceBlocker> blockers) {
        return new ProjectGovernanceProviderFact(PROVIDER_CODE, FACT_VERSION, state, digest(state), blockers);
    }

    private static void validateTenant(ProjectGovernanceGuardQuery query) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("query tenant must match trusted tenant context");
        }
    }

    private static String digest(String state) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(state.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }
}
