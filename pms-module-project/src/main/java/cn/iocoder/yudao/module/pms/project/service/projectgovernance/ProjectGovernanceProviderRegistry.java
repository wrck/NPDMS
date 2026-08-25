package cn.iocoder.yudao.module.pms.project.service.projectgovernance;

import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceBlocker;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardProviderApi;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProjectGovernanceProviderRegistry {

    public static final List<String> REQUIRED_PROVIDERS = List.of(
            "PROJECT_TREE", "BPM_APPROVAL", "PROJECT_TASK",
            "CUTOVER", "INSPECTION");

    private final Map<String, ProjectGovernanceGuardProviderApi> providers;

    public ProjectGovernanceProviderRegistry(List<ProjectGovernanceGuardProviderApi> providerList) {
        Map<String, ProjectGovernanceGuardProviderApi> indexed = new LinkedHashMap<>();
        for (ProjectGovernanceGuardProviderApi provider : providerList) {
            String code = provider.providerCode();
            if (code == null || code.isBlank() || indexed.putIfAbsent(code.trim(), provider) != null) {
                throw new IllegalStateException("duplicate or invalid project governance provider");
            }
        }
        this.providers = Map.copyOf(indexed);
    }

    public List<ProjectGovernanceProviderFact> inspectAll(ProjectGovernanceGuardQuery query) {
        List<ProjectGovernanceProviderFact> facts = new ArrayList<>(REQUIRED_PROVIDERS.size());
        for (String providerCode : REQUIRED_PROVIDERS) {
            facts.add(inspect(providerCode, query));
        }
        return facts.stream().sorted(Comparator.comparing(ProjectGovernanceProviderFact::provider)).toList();
    }

    private ProjectGovernanceProviderFact inspect(String providerCode, ProjectGovernanceGuardQuery query) {
        ProjectGovernanceGuardProviderApi provider = providers.get(providerCode);
        if (provider == null) {
            return unavailable(providerCode, "MISSING");
        }
        try {
            ProjectGovernanceProviderFact fact = provider.inspect(query);
            if (fact == null || !providerCode.equals(fact.provider())) {
                return unavailable(providerCode, "INVALID_RESULT");
            }
            return fact;
        } catch (RuntimeException ex) {
            return unavailable(providerCode, "QUERY_FAILED");
        }
    }

    private static ProjectGovernanceProviderFact unavailable(String providerCode, String reason) {
        String canonical = providerCode + "|" + reason;
        return new ProjectGovernanceProviderFact(providerCode, "PROVIDER_UNAVAILABLE_V1",
                reason, digest(canonical), List.of(new ProjectGovernanceBlocker(
                "GUARD_PROVIDER", providerCode, "UNAVAILABLE", "PROVIDER_UNAVAILABLE",
                "治理守卫提供方不可用")));
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }
}
