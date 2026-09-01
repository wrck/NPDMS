package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 固定providerKey到唯一Owner实现的注册表。 */
@Component
public class ProjectStageGateProviderRegistry {

    private final Map<String, ProjectStageGateFactProviderApi> providers;

    public ProjectStageGateProviderRegistry(List<ProjectStageGateFactProviderApi> implementations) {
        Map<String, ProjectStageGateFactProviderApi> resolved = new LinkedHashMap<>();
        for (ProjectStageGateFactProviderApi implementation : implementations) {
            if (implementation == null || implementation.providerKeys() == null
                    || implementation.providerKeys().isEmpty()) {
                throw new IllegalStateException("stage gate provider keys must not be empty");
            }
            for (String providerKey : implementation.providerKeys()) {
                if (providerKey == null || providerKey.isBlank()
                        || resolved.putIfAbsent(providerKey, implementation) != null) {
                    throw new IllegalStateException("duplicate or blank stage gate provider: " + providerKey);
                }
            }
        }
        this.providers = Map.copyOf(resolved);
    }

    public boolean hasProvider(String providerKey) {
        return providers.containsKey(providerKey);
    }

    public ProjectStageGateFact lockAndRevalidate(String providerKey, ProjectStageGateFactQuery query) {
        ProjectStageGateFactProviderApi provider = providers.get(providerKey);
        if (provider == null) {
            throw new IllegalStateException("stage gate provider unavailable: " + providerKey);
        }
        ProjectStageGateFact fact = provider.lockAndRevalidate(query);
        if (fact == null || !providerKey.equals(fact.providerKey())) {
            throw new IllegalStateException("stage gate provider returned mismatched fact: " + providerKey);
        }
        return fact;
    }
}
