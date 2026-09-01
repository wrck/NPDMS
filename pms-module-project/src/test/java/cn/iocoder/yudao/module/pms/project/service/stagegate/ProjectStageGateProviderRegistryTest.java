package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectStageGateProviderRegistryTest {

    @Test
    void rejectsDuplicateProviderKey() {
        ProjectStageGateFactProviderApi first = provider(Set.of(ProjectStageGateFactProviderApi.PROVIDER_PROJ_TASK));
        ProjectStageGateFactProviderApi second = provider(Set.of(ProjectStageGateFactProviderApi.PROVIDER_PROJ_TASK));

        assertThrows(IllegalStateException.class,
                () -> new ProjectStageGateProviderRegistry(List.of(first, second)));
    }

    @Test
    void rejectsUnknownProviderAtRuntime() {
        ProjectStageGateProviderRegistry registry = new ProjectStageGateProviderRegistry(List.of(
                provider(Set.of(ProjectStageGateFactProviderApi.PROVIDER_PROJ_TASK))));

        assertThrows(IllegalStateException.class,
                () -> registry.lockAndRevalidate(ProjectStageGateFactProviderApi.PROVIDER_BPM_PROCESS, null));
    }

    private static ProjectStageGateFactProviderApi provider(Set<String> keys) {
        return new ProjectStageGateFactProviderApi() {
            @Override
            public Set<String> providerKeys() {
                return keys;
            }

            @Override
            public cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact lockAndRevalidate(
                    cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery query) {
                return null;
            }
        };
    }
}
