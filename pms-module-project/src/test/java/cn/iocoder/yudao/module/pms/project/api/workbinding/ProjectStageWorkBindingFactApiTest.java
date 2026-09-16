package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectWorkBindingFactMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectStageWorkBindingFactApiTest {
    final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    final ProjectWorkBindingFactMapper tasks = mock(ProjectWorkBindingFactMapper.class);
    final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    final ProjectWorkBindingFactApi api = new ProjectWorkBindingFactApiImpl(projects,tasks,graph);
    final ProjectWorkBindingStageFactQuery query = new ProjectWorkBindingStageFactQuery(9L,90L,ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS);
    ProjectStageExecutionContractDO contract;
    ProjectStageInstanceDO stage;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(1L); project.setVersion(1);
        project.setLifecycleTemplateId(700L); project.setLifecycleTemplateRevisionId(701L); project.setLifecycleTemplateRevisionNo(1);
        when(projects.selectById(9L)).thenReturn(project);
        when(projects.selectByIdForUpdate(9L)).thenReturn(project);
        stage = new ProjectStageInstanceDO(); stage.setId(90L); stage.setTenantId(1L); stage.setProjectId(9L);
        stage.setCode("CUSTOM_PREP"); stage.setGraphVersion(2L); stage.setVersion(3);
        var binding = new TemplateExecutionSnapshot.BindingContract();
        binding.setType("BUSINESS_OBJECT"); binding.setTargetContextCode("SOL");
        binding.setTargetObjectType("REQUIREMENT_ANALYSIS"); binding.setTargetObjectKey("PRE_04_REQUIREMENT_ANALYSIS");
        binding.setParameters(JsonUtils.parseTree("{\"schemaVersion\":2,\"dynamicFormTemplateId\":800,\"dynamicFormTemplateRevisionId\":801,\"dynamicFormRevisionNo\":2,\"dynamicFormRevisionFactVersion\":3}"));
        var node = new TemplateExecutionSnapshot.StageContract(); node.setNodeKey("stage:independent"); node.setCode("CUSTOM_PREP"); node.setBinding(binding);
        var snapshot = new TemplateExecutionSnapshot(); snapshot.setStages(List.of(node));
        contract = new ProjectStageExecutionContractDO(); contract.setId(99L); contract.setTenantId(1L); contract.setProjectId(9L);
        contract.setStageId(90L); contract.setGraphVersion(2L); contract.setSourceNodeKey(node.getNodeKey());
        contract.setBindingVersion(4); contract.setBindingType(binding.getType());
        contract.setBindingSnapshot(JsonUtils.toJsonString(binding)); contract.setDefinitionSnapshot(JsonUtils.toJsonString(snapshot));
        when(graph.selectStages(any())).thenReturn(List.of(stage));
        when(graph.selectStagesForUpdate(any())).thenReturn(List.of(stage));
        when(graph.selectContracts(any())).thenReturn(List.of(contract));
    }

    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void resolvesTheFrozenStageBindingAndOwnerFormWithoutTaskIdentityOrAssetLookup() {
        var result = api.inspectStage(query);
        assertEquals(90L,result.projectStageId()); assertEquals(3,result.projectStageVersion());
        assertNull(result.projectTaskId()); assertNull(result.projectTaskVersion());
        assertNull(result.sourceDefinitionVersion());
        assertEquals(99L,result.executionContractId()); assertEquals(4,result.contractVersion());
        assertEquals(801L,result.dynamicFormTemplateRevisionId()); assertEquals(3,result.dynamicFormRevisionFactVersion());
        verifyNoInteractions(tasks);
    }

    @Test void lockRevalidatesCompletedStageFactsAndRejectsStaleExpectedVersions() {
        stage.setStatus("DONE");
        var request = new ProjectWorkBindingStageFactRevalidationQuery(9L,90L,99L,3,4,1,query.target());
        assertEquals(api.inspectStage(query),api.lockAndRevalidateStage(request));
        var order = inOrder(projects, graph);
        order.verify(projects).selectByIdForUpdate(9L);
        order.verify(graph).selectStagesForUpdate(any());
        assertEquals("DONE", stage.getStatus());
        stage.setVersion(4);
        assertThrows(RuntimeException.class, () -> api.lockAndRevalidateStage(request));
        verify(projects, times(2)).selectByIdForUpdate(9L);
        verifyNoInteractions(tasks);
    }

    @Test void foreignTenantCannotLockAnotherProjectsStageFacts() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(RuntimeException.class, () -> api.lockAndRevalidateStage(
                new ProjectWorkBindingStageFactRevalidationQuery(9L,90L,99L,3,4,1,query.target())));
        verify(graph, never()).selectStagesForUpdate(any());
    }

    @Test void changedProjectVersionCannotUseAnOldStageBindingProof() {
        assertThrows(RuntimeException.class, () -> api.lockAndRevalidateStage(
                new ProjectWorkBindingStageFactRevalidationQuery(9L,90L,99L,3,4,0,query.target())));
        verify(graph, never()).selectStagesForUpdate(any());
    }

    @Test void changedSnapshotOrForeignTenantNeverFallsBackToAnotherNode() {
        contract.setBindingSnapshot("{}");
        assertThrows(RuntimeException.class, () -> api.inspectStage(query));
        TenantContextHolder.setTenantId(2L);
        assertThrows(RuntimeException.class, () -> api.inspectStage(query));
        verifyNoInteractions(tasks);
    }

    @Test void missingOrAmbiguousStageContractIsNotReplacedByTaskBinding() {
        when(graph.selectContracts(any())).thenReturn(List.of());
        assertThrows(RuntimeException.class, () -> api.inspectStage(query));
        when(graph.selectContracts(any())).thenReturn(List.of(contract,contract));
        assertThrows(RuntimeException.class, () -> api.inspectStage(query));
        verifyNoInteractions(tasks);
    }
}
