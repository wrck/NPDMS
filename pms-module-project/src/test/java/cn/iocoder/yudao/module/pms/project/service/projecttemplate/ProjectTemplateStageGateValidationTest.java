package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessDefinitionFact;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectTemplateStageGateValidationTest {

    @Test
    void acceptsS0ToS3ExitGatesWithRegisteredOwnersAndFlowableDefinition() {
        ProjectTemplateServiceImpl service = service();
        TemplateDefinitionContent content = content();

        List<String> failures = service.validateStageGateOwners(content, 7L);

        assertTrue(failures.isEmpty(), () -> "阶段Gate Owner校验应通过，实际：" + failures);
    }

    @Test
    void rejectsMissingExitGate() {
        ProjectTemplateServiceImpl service = service();
        TemplateDefinitionContent content = content();
        content.getGates().removeIf(gate -> "S2".equals(gate.getStageCode()));

        List<String> failures = service.validateStageGateOwners(content, 7L);

        assertTrue(failures.stream().anyMatch(value -> value.contains("S2") && value.contains("EXIT Gate")));
    }

    private static ProjectTemplateServiceImpl service() {
        ProjectStageGateFactProviderApi provider = mock(ProjectStageGateFactProviderApi.class);
        when(provider.providerKeys()).thenReturn(Set.of(
                ProjectStageGateFactProviderApi.PROVIDER_PROJ_TASK,
                ProjectStageGateFactProviderApi.PROVIDER_PROJ_MILESTONE,
                ProjectStageGateFactProviderApi.PROVIDER_PROJ_STATE,
                ProjectStageGateFactProviderApi.PROVIDER_ACC_DELIVERABLE,
                ProjectStageGateFactProviderApi.PROVIDER_BPM_APPROVAL,
                ProjectStageGateFactProviderApi.PROVIDER_BPM_PROCESS));
        ProjectStageGateProcessOwnerApi processOwner = mock(ProjectStageGateProcessOwnerApi.class);
        when(processOwner.inspectDefinitionKey(any())).thenReturn(
                new ProjectStageGateProcessDefinitionFact("def:3", "gate-approval", "审批", true));
        ProjectTemplateServiceImpl service = new ProjectTemplateServiceImpl();
        ReflectionTestUtils.setField(service, "stageGateProviderRegistry",
                new ProjectStageGateProviderRegistry(List.of(provider)));
        ReflectionTestUtils.setField(service, "stageGateProcessOwnerApi", processOwner);
        return service;
    }

    private static TemplateDefinitionContent content() {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        List<TemplateDefinitionContent.GateDef> gates = new ArrayList<>();
        for (int stage = 0; stage <= 3; stage++) {
            TemplateDefinitionContent.GateDef gate = new TemplateDefinitionContent.GateDef();
            gate.setGateCode("G-S" + stage);
            gate.setStageCode("S" + stage);
            gate.setGateType(TemplateDefinitionContent.GATE_TYPE_EXIT);
            TemplateDefinitionContent.GateRef task = new TemplateDefinitionContent.GateRef();
            task.setRefType(TemplateDefinitionContent.REF_TYPE_TASK);
            task.setRefCode("T-S" + stage);
            gate.setReferences(new ArrayList<>(List.of(task)));
            gates.add(gate);
        }
        TemplateDefinitionContent.GateRef process = new TemplateDefinitionContent.GateRef();
        process.setRefType(TemplateDefinitionContent.REF_TYPE_APPROVAL);
        process.setRefCode("gate-approval");
        gates.getFirst().getReferences().add(process);
        content.setGates(gates);
        return content;
    }
}
