package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectVerifiedOperationScope;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual Owner access and configuration reader; project authorization and persistence are test doubles. */
class RequirementAnalysisIndependentRevisionOperationTest {
    private final RequirementAnalysisMapper mapper = mock(RequirementAnalysisMapper.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectParticipantFactApi participants = mock(ProjectParticipantFactApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final ProjectNodeExecutionApi nodes = mock(ProjectNodeExecutionApi.class);
    private final ProjectWorkBindingFactApi bindings = mock(ProjectWorkBindingFactApi.class);
    private final ProjectBusinessExecutionApi guard = mock(ProjectBusinessExecutionApi.class);
    private final RequirementAnalysisExecutionAccess executions = new RequirementAnalysisExecutionAccess(nodes, bindings, guard);
    private final RequirementAnalysisAccess access = new RequirementAnalysisAccess(mapper, scopes, participants, permissions, bindings, executions);
    private final EntityActor actor = new EntityActor(1L, 9L, "independent");
    private final RequirementAnalysisRevisionDO row = new RequirementAnalysisRevisionDO();
    private final ProjectTaskExecutionContext origin = new ProjectTaskExecutionContext(20L, 1, 30L, 1, 40L, 1,
            50L, 60L, 1, 1, 70L, 1, false, null);

    @BeforeEach void setUp() {
        row.setId(80L); row.setTenantId(1L); row.setProjectId(20L); row.setEntityId(90L);
        row.setRevisionNo(1); row.setRevisionState("DRAFT"); row.setVersion(3);
        var binding = new ProjectWorkBindingFact(20L, 1, 30L, 1, 40L, 1, 100L, 1,
                "BUSINESS_OBJECT", "SOL", "REQUIREMENT_ANALYSIS", "PRE_04_REQUIREMENT_ANALYSIS",
                null, null, null, null, 101L, 1, "{}");
        row.setExecutionSnapshot(executions.freeze(binding, origin));
        when(mapper.selectRevision(any())).thenReturn(row);
        when(mapper.lockRevision(any())).thenReturn(row);
        when(mapper.selectDraft(any())).thenReturn(row);
        when(permissions.hasAnyPermissions(eq(9L), any(String[].class))).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 2L, Set.of(20L), Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L, 2L, Set.of(20L), Set.of()));
        when(participants.inspect(any())).thenReturn(new ProjectParticipantFact(20L, 9L, Set.of("PROJECT_MANAGER"),
                "PRIMARY", "ACTIVE", "S6", 2, 2L));
    }

    @Test void existingRevisionLocksCurrentOwnerPermissionAndRowButNotTheOriginNode() {
        String before = row.getExecutionSnapshot();
        assertSame(row, access.lock(80L, 3, actor, null, true));
        assertTrue(executions.canUseFrozenConfiguration(20L, before));
        verify(scopes).lockAndRevalidate(any());
        verify(participants).lockAndRevalidate(any());
        verify(mapper).lockRevision(any());
        verifyNoInteractions(nodes, bindings, guard);
        assertEquals(before, row.getExecutionSnapshot());
    }

    @ParameterizedTest @ValueSource(strings = {"permission", "scope", "manager", "version", "frozen"})
    void independentDoesNotMeanUnconditionallyWritable(String failure) {
        switch (failure) {
            case "permission" -> when(permissions.hasAnyPermissions(eq(9L), any(String[].class))).thenReturn(false);
            case "scope" -> when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L, 2L, Set.of(), Set.of(20L)));
            case "manager" -> when(participants.inspect(any())).thenReturn(null);
            case "version" -> row.setVersion(4);
            case "frozen" -> row.setRevisionState("FROZEN");
            default -> throw new AssertionError(failure);
        }
        assertThrows(RuntimeException.class, () -> access.lock(80L, 3, actor, null, true));
        verifyNoInteractions(nodes, bindings, guard);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "project", "owner", "form-target", "node"})
    void damagedProvenanceCannotBecomeAnIndependentConfiguration(String failure) {
        ObjectNode document = (ObjectNode) JsonUtils.parseTree(row.getExecutionSnapshot());
        ObjectNode binding = (ObjectNode) document.get("binding");
        switch (failure) {
            case "missing" -> document.remove("binding");
            case "project" -> binding.put("projectId", 21L);
            case "owner" -> binding.put("targetContextCode", "OTHER");
            case "form-target" -> binding.put("targetObjectKey", "OTHER");
            case "node" -> binding.put("projectTaskId", 31L);
            default -> throw new AssertionError(failure);
        }
        row.setExecutionSnapshot(document.toString());
        assertFalse(executions.canUseFrozenConfiguration(20L, row.getExecutionSnapshot()));
        assertThrows(RuntimeException.class, () -> access.lock(80L, 3, actor, null, true));
        verify(mapper, never()).lockRevision(any());
        verifyNoInteractions(nodes, bindings, guard);
    }

    @Test void suppliedOrVerifiedProjectEntryCannotDowngradeToIndependent() {
        var selected = new ProjectBusinessExecutionSelection(origin, null);
        assertThrows(RuntimeException.class, () -> access.lock(80L, 3, actor, selected, true));
        try (var verified = ProjectVerifiedOperationScope.open(new ProjectVerifiedOperationScope.Frame(1L, 9L, 20L,
                "SOL", "REQUIREMENT_ANALYSIS", "SOL.REQUIREMENT_ANALYSIS.SAVE", 1, "80", selected))) {
            assertThrows(RuntimeException.class, () -> access.lock(80L, 3, actor, null, true));
            assertThrows(RuntimeException.class, () -> access.lockExecution(20L, row.getExecutionSnapshot(), null));
        }
        verify(mapper, never()).lockRevision(any());
    }

    @Test void ordinaryWorkspaceOffersDraftActionsWithoutLookingUpANode() {
        var queries = queries();
        assertEquals(List.of("PATCH_FORM", "COMPLETE"), queries.workspace(20L, actor).draft().allowedActions());
        verifyNoInteractions(nodes, bindings, guard);
    }

    @Test void explicitlySelectedUnavailableWorkspaceDoesNotOfferIndependentActions() {
        assertTrue(queries().workspace(20L, actor, null, 30L).draft().allowedActions().isEmpty());
        verify(bindings).inspectTask(any());
        verifyNoInteractions(nodes, guard);
    }

    private RequirementAnalysisEntityQueryService queries() {
        var provider = mock(RequirementAnalysisEntityProvider.class);
        var extensions = mock(EntityExtensionApi.class);
        var files = mock(RequirementAnalysisRevisionFiles.class);
        when(provider.read(any(), any())).thenReturn(Map.of());
        when(extensions.read(any(), any())).thenReturn(new EntityExtensionApi.Values(null, Map.of(), 0));
        when(files.inspect(any(), any())).thenReturn(List.of());
        return new RequirementAnalysisEntityQueryService(mapper, provider, access, extensions, mock(EntityFormApi.class),
                files, executions, bindings);
    }
}
