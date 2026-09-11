package cn.iocoder.yudao.module.pms.project.service.stagebusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.*;
import cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectStageBusinessQueryServiceTest {
    private final ProjectManualCreationService projects = mock(ProjectManualCreationService.class);
    private final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    private final BusinessViewQueryApi views = mock(BusinessViewQueryApi.class);
    private final StageBusinessViewProvider owner = mock(StageBusinessViewProvider.class);
    private final ProjectStageBusinessQueryService service = new ProjectStageBusinessQueryService(projects, graph, views, List.of(owner));
    private final ProjectAccessActor actor = new ProjectAccessActor(1L, 7L);
    private ProjectStageInstanceDO stage;
    private ProjectStageExecutionContractDO contract;

    @BeforeEach void setup() {
        var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE");
        when(projects.getProject(9L, actor)).thenReturn(project);
        stage = new ProjectStageInstanceDO(); stage.setId(90L); stage.setProjectId(9L); stage.setTenantId(1L);
        stage.setStageCode("S4"); stage.setGraphVersion(1L); stage.setDefinitionRevisionId(10L);
        contract = new ProjectStageExecutionContractDO(); contract.setId(99L); contract.setProjectId(9L); contract.setStageId(90L);
        contract.setTenantId(1L); contract.setGraphVersion(1L); contract.setDefinitionRevisionId(10L);
        contract.setWorkBindingRevisionId(2L); contract.setPermissionPolicyRevisionId(3L); contract.setCompletionRuleRevisionId(4L);
        contract.setBindingVersion(1);
        binding("STAGE_NATIVE");
        when(graph.selectStages(any())).thenReturn(List.of(stage)); when(graph.selectContracts(any())).thenReturn(List.of(contract));
        when(owner.ownerContext()).thenReturn("SOL"); when(owner.objectType()).thenReturn("REQUIREMENT_ANALYSIS");
        when(owner.inspectStage(any())).thenReturn(new StageBusinessViewProvider.Result(Set.of("QUERY", "PATCH_FORM")));
        when(views.getRevision(any())).thenReturn(view("PUBLISHED"));
    }
    private void binding(String type) {
        contract.setBindingType(type);
        String binding = "{\"bindingType\":\"" + type + "\",\"businessViewRevisionId\":\"88\",\"targetContextCode\":\"SOL\",\"targetObjectType\":\"REQUIREMENT_ANALYSIS\",\"targetObjectKey\":\"PRE_04_REQUIREMENT_ANALYSIS\",\"instanceResolutionStrategy\":\"CREATE_ON_FIRST_ACTION\"}";
        contract.setBindingSnapshot(binding);
        contract.setDefinitionSnapshot("[{\"definition\":{\"id\":2,\"definitionKind\":\"WORK_BINDING\",\"schemaVersion\":1,\"payload\":" + binding + "}},"
                + "{\"definition\":{\"id\":3,\"definitionKind\":\"PERMISSION_POLICY\",\"schemaVersion\":1,\"payload\":{\"requiredActions\":[\"QUERY\"]}}},"
                + "{\"definition\":{\"id\":4,\"definitionKind\":\"COMPLETION_RULE\",\"schemaVersion\":1,\"payload\":{}}}]");
    }
    private BusinessViewRevision view(String status) {
        return new BusinessViewRevision(88L, "REQUIREMENT_ANALYSIS", "REQ", 1L, "SOL", BusinessViewComponentProvider.ViewSource.PAGE,
                "PROJ_REQUIREMENT_ANALYSIS", "1", null, JsonUtils.parseTree("{}"), JsonUtils.parseTree("[]"),
                "QUERY", "COMMAND", "PERMISSION", null, null, 1, status, Set.of());
    }
    @Test void readsStageNativeFromFrozenContractWithoutGuessingByStageCode() {
        var result = service.getContext(9L, "S4", actor);
        assertEquals("STAGE_NATIVE", result.bindingType()); assertEquals(90L, result.stageId()); assertNull(result.recoverableError());
        verifyNoInteractions(views, owner);
    }
    @Test void resolvesExactViewAndPassesStageIdentityOnlyToOwner() {
        binding("BUSINESS_OBJECT");
        var result = service.getContext(9L, "S4", actor);
        assertEquals(88L, result.businessView().id()); assertFalse(result.readonly());
        verify(views).getRevision(new BusinessViewQueryApi.Query(88L, BusinessViewQueryApi.Purpose.HISTORICAL_REFERENCE));
        verify(owner).inspectStage(new StageBusinessViewProvider.Context(1L, 7L, 9L, 90L, "PRE_04_REQUIREMENT_ANALYSIS", "CREATE_ON_FIRST_ACTION"));
    }
    @Test void disabledViewCannotGrantWriteActions() {
        binding("BUSINESS_OBJECT"); when(views.getRevision(any())).thenReturn(view("DISABLED"));
        var result = service.getContext(9L, "S4", actor);
        assertTrue(result.readonly()); assertEquals(Set.of("QUERY"), result.ownerActions());
    }
    @Test void rejectsForeignOrStaleStageContract() {
        contract.setTenantId(2L);
        assertEquals("STAGE_CONTRACT_STALE", service.getContext(9L, "S4", actor).recoverableError());
        verifyNoInteractions(views, owner);
    }
    @Test void doesNotResolveLatestWhenFrozenBindingDisagrees() {
        contract.setBindingSnapshot("{}");
        assertEquals("STAGE_BINDING_SNAPSHOT_MISMATCH", service.getContext(9L, "S4", actor).recoverableError());
        verifyNoInteractions(views, owner);
    }
    @Test void missingOrAmbiguousCurrentContractIsNotNativeFallback() {
        when(graph.selectContracts(any())).thenReturn(List.of(contract, contract));
        assertEquals("STAGE_CONTRACT_NOT_FROZEN", service.getContext(9L, "S4", actor).recoverableError());
        verifyNoInteractions(views, owner);
    }
    @Test void ownerDenialNeverReturnsAUsableView() {
        binding("BUSINESS_OBJECT"); when(owner.inspectStage(any())).thenReturn(new StageBusinessViewProvider.Result(Set.of()));
        var result = service.getContext(9L, "S4", actor);
        assertEquals("OWNER_CONTEXT_FORBIDDEN", result.recoverableError()); assertNull(result.businessView());
    }
    @Test void projectScopeDenialPrecedesGraphLookup() {
        when(projects.getProject(9L, actor)).thenThrow(new IllegalArgumentException("forbidden"));
        assertThrows(IllegalArgumentException.class, () -> service.getContext(9L, "S4", actor)); verifyNoInteractions(graph, views, owner);
    }
    @Test void readEndpointDoesNotPretendToExecuteCreateOnEnter() {
        binding("BUSINESS_OBJECT");
        contract.setBindingSnapshot(contract.getBindingSnapshot().replace("CREATE_ON_FIRST_ACTION", "CREATE_ON_ENTER"));
        contract.setDefinitionSnapshot(contract.getDefinitionSnapshot().replace("CREATE_ON_FIRST_ACTION", "CREATE_ON_ENTER"));
        assertEquals("STAGE_ENTER_COMMAND_NOT_CONNECTED", service.getContext(9L, "S4", actor).recoverableError());
        verify(owner, never()).inspectStage(any());
    }
}
