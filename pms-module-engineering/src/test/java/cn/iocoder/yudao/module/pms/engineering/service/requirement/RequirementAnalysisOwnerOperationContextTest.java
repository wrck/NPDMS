package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationCommand;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationControlScope;
import org.springframework.beans.factory.ObjectProvider;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementRevisionQuery;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOwnerOperationScope;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectVerifiedOperationScope;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Real Owner commands/provider/access/execution bridge, with repository and platform-callback test doubles.
 * This is not a Spring transaction, actual form/file service or database integration test.
 */
@SuppressWarnings("try")
class RequirementAnalysisOwnerOperationContextTest {
    @Test void allFourOperationsCarryTheirOwnIdentityInBothNodeKinds() {
        for (boolean stage : new boolean[]{false, true}) {
            for (String action : List.of("CREATE", "SAVE", "COMPLETE", "COPY")) {
                var f = new Fixture(stage, action);
                try (var verified = ProjectVerifiedOperationScope.open(f.frame(action))) {
                    var result = f.invoke(action);
                    assertNotNull(result);
                    assertFalse(f.checked.isEmpty(), action);
                    assertTrue(f.checked.stream().allMatch(r -> r.operationCode().equals("SOL.REQUIREMENT_ANALYSIS." + action)));
                    if (Set.of("CREATE", "COPY").contains(action)) {
                        var destination = result.ref().revisionId().toString();
                        assertNotEquals("40", destination);
                        assertTrue(f.checked.stream().anyMatch(r -> destination.equals(r.objectId())), "new revision callbacks must be checked");
                        if ("COPY".equals(action)) verify(f.files).copy(eq(f.source.revisionRef()), eq(result.ref()), eq(f.actor));
                    } else {
                        assertTrue(f.checked.stream().allMatch(r -> "40".equals(r.objectId())));
                    }
                    assertThrows(IllegalStateException.class, () -> f.request("40"), "Owner scope must close before returning to executor");
                }
            }
        }
    }

    @Test void declaredCommandCannotBorrowAnotherOperationOrRevision() {
        var f = new Fixture(false, "SAVE");
        try (var verified = ProjectVerifiedOperationScope.open(f.frame("COMPLETE"))) {
            assertThrows(IllegalStateException.class, () -> f.invoke("SAVE"));
            verify(f.mapper, never()).saveDraft(any());
        }
        var other = new RequirementAnalysisRevisionDO();
        other.setId(41L); other.setEntityId(100L); other.setProjectId(3L); other.setTenantId(1L);
        other.setRevisionState("DRAFT"); other.setRevisionNo(2); other.setVersion(1);
        f.rows.put(41L, other);
        try (var verified = ProjectVerifiedOperationScope.open(f.frame("SAVE"))) {
            assertThrows(IllegalStateException.class, () -> f.commands.save(other.revisionRef(), 1,
                    new RequirementAnalysisEntityCommands.Patch(Map.of(), null, 0, null, f.selection), f.actor, "other"));
        }
    }

    @Test void versionCallbacksRetainSelectedNodeInsteadOfRevertingToOrigin() {
        for (boolean stage : new boolean[]{false, true}) {
            var f = new Fixture(stage, "COPY");
            var origin = binding(stage, 14L);
            var old = selection(stage, 14L);
            f.source.setExecutionSnapshot(JsonUtils.toJsonString(new RequirementAnalysisExecutionAccess.Frozen(origin, old.task(), old.stage())));
            try (var verified = ProjectVerifiedOperationScope.open(f.frame("COPY"))) {
                f.invoke("COPY");
                assertTrue(f.checked.stream().allMatch(r -> f.selection.equals(r.selection())));
                if (stage) verify(f.bindings, never()).inspectStage(new ProjectWorkBindingStageFactQuery(3L, 14L, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
                else verify(f.bindings, never()).inspectTask(new ProjectWorkBindingTaskFactQuery(3L, 14L, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
            }
        }
    }

    @Test void failingCallbackReleasesDerivedTargetsAndDeclaration() {
        var f = new Fixture(false, "COPY");
        doThrow(new IllegalStateException("form copy failed")).when(f.forms).copy(any(), any(), anyInt(), any());
        try (var verified = ProjectVerifiedOperationScope.open(f.frame("COPY"))) {
            assertThrows(IllegalStateException.class, () -> f.invoke("COPY"));
            assertThrows(IllegalStateException.class, () -> f.request("40"));
            for (var request : f.checked) assertFalse(request.ownerProof().matches(1L, 2L, request));
        }
        // Database rollback is deliberately not asserted against mocked repositories.
    }

    @Test void failedInsertDoesNotRegisterOrInitializeAnUncreatedRevision() {
        for (String action : List.of("CREATE", "COPY")) {
            var f = new Fixture(false, action);
            doReturn(0).when(f.mapper).insertRevision(any());
            try (var verified = ProjectVerifiedOperationScope.open(f.frame(action))) {
                assertThrows(RuntimeException.class, () -> f.invoke(action));
                verifyNoInteractions(f.forms, f.extensions, f.files);
                assertThrows(IllegalStateException.class, () -> f.request("40"));
            }
        }
    }

    @Test void actualAdapterCarriesTheStableCommandTraceThroughAllOwnerCallbacks() {
        TenantContextHolder.setTenantId(1L);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
            for (boolean stage : new boolean[]{false, true}) {
                for (String action : List.of("CREATE", "SAVE", "COMPLETE", "COPY")) {
                    var f = new Fixture(stage, action);
                    doReturn(true).when(f.access).isManager(eq(3L), any());
                    var adapter = new RequirementAnalysisOperationCommandAdapter(of(f.commands), of(f.access));
                    String code = "SOL.REQUIREMENT_ANALYSIS." + action;
                    String kind = stage ? "STAGE" : "TASK";
                    String key = "PROJECT_OP:" + DigestUtil.sha256Hex(code + ":" + kind + ":4:retry-key");
                    var facts = new ArrayList<PlatformCommandExecutionApi.SuccessFacts>();
                    doAnswer(call -> {
                        var result = call.<Supplier<EntityVersionProvider.Revision>>getArgument(3).get();
                        facts.add(call.<java.util.function.Function<EntityVersionProvider.Revision,
                                PlatformCommandExecutionApi.SuccessFacts>>getArgument(4).apply(result));
                        return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, result);
                    }).when(f.idempotency).execute(any(), anyString(), eq(EntityVersionProvider.Revision.class), any(), any());
                    var input = JsonUtils.parseTree(action.equals("SAVE")
                            ? "{\"values\":{},\"expectedExtensionVersion\":0}" : "{}");
                    String originalInput = input.toString();
                    var request = new ProjectOperationCommand(3L, kind, 4L, f.selection,
                            action.equals("CREATE") ? null : "40", 1, "v1", input, "retry-key");
                    try (var verified = ProjectVerifiedOperationScope.open(f.frame(action))) {
                        var result = adapter.invoke(code, request);
                        assertNotNull(result);
                        assertEquals(originalInput, input.toString());
                        assertEquals(1, facts.size());
                        assertEquals(key, facts.getFirst().correlationId());
                        assertTrue(facts.getFirst().businessEvents() == null || facts.getFirst().businessEvents().isEmpty());
                        verify(f.audit, atLeastOnce()).record(eq(1L), eq(2L), eq(key), anyString(), anyString(),
                                anyString(), anyString(), anyMap());
                        if (action.equals("COMPLETE")) verify(f.events).changed(3L, "RequirementAnalysis", 40L, 2L, key);
                        else verifyNoInteractions(f.events);
                        assertFalse(f.checked.isEmpty());
                        assertTrue(f.checked.stream().allMatch(write -> write.operationCode().equals(code)));
                        assertThrows(IllegalStateException.class, () -> f.request("40"));
                    }
                    assertNull(ProjectVerifiedOperationScope.current());
                    assertNull(f.request("40").ownerProof());
                }
            }
        } finally { TenantContextHolder.clear(); }
    }

    @Test void onlyAuditedRequirementCommandsDeclareProjectEntryOnlyControl() {
        var provider = new RequirementAnalysisOperationProvider();
        var scopes = provider.controlScopes();
        assertEquals(provider.operations().stream().map(operation -> operation.operationCode()).collect(java.util.stream.Collectors.toSet()), scopes.keySet());
        assertEquals(4, scopes.size());
        assertTrue(scopes.values().stream().allMatch(scope -> scope == ProjectOperationControlScope.PROJECT_ENTRY_ONLY));
        assertThrows(UnsupportedOperationException.class, scopes::clear);
    }

    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> of(T instance) {
        ObjectProvider<T> result = mock(ObjectProvider.class);
        when(result.getObject()).thenReturn(instance);
        return result;
    }

    private static final class Fixture {
        final EntityActor actor = new EntityActor(1L, 2L, "unit-test");
        final ProjectBusinessExecutionSelection selection;
        final ProjectWorkBindingFact binding;
        final RequirementAnalysisMapper mapper = mock(RequirementAnalysisMapper.class);
        final ProjectNodeExecutionApi nodes = mock(ProjectNodeExecutionApi.class);
        final ProjectWorkBindingFactApi bindings = mock(ProjectWorkBindingFactApi.class);
        final EntityFormApi forms = mock(EntityFormApi.class);
        final EntityExtensionApi extensions = mock(EntityExtensionApi.class);
        final EntityVersionApi versions = mock(EntityVersionApi.class);
        final RequirementAnalysisRevisionFiles files = mock(RequirementAnalysisRevisionFiles.class);
        final OperationAuditApi audit = mock(OperationAuditApi.class);
        final EngineeringRuleReevaluationEvents events = mock(EngineeringRuleReevaluationEvents.class);
        final PlatformCommandExecutionApi idempotency = mock(PlatformCommandExecutionApi.class);
        final Map<Long, RequirementAnalysisRevisionDO> rows = new LinkedHashMap<>();
        final AtomicReference<RequirementAnalysisDO> current = new AtomicReference<>();
        final List<ProjectBusinessExecutionApi.WriteRequest> checked = new ArrayList<>();
        final RequirementAnalysisExecutionAccess executions;
        final RequirementAnalysisAccess access;
        final RequirementAnalysisEntityProvider provider;
        final RequirementAnalysisEntityCommands commands;
        final RequirementAnalysisRevisionDO source;

        Fixture(boolean stage, String action) {
            selection = selection(stage, 4L); binding = binding(stage, 4L);
            ProjectBusinessExecutionApi guard = request -> {
                assertNotNull(request.ownerProof(), "old four-argument call must not reappear");
                assertTrue(request.ownerProof().matches(actor.tenantId(), actor.userId(), request));
                assertEquals(selection, request.selection()); checked.add(request);
            };
            executions = new RequirementAnalysisExecutionAccess(nodes, bindings, guard);
            access = spy(new RequirementAnalysisAccess(mapper, mock(ProjectScopeApi.class), mock(ProjectParticipantFactApi.class),
                    mock(PermissionApi.class), bindings, executions, null));
            // Stub external project-authorization collaborators only, not the execution/target checks under test.
            doNothing().when(access).requireRead(anyLong(), any(), anyBoolean());
            doNothing().when(access).lockScope(anyLong(), any());
            provider = new RequirementAnalysisEntityProvider(mapper, access, extensions, forms, files,
                    audit, events);
            commands = new RequirementAnalysisEntityCommands(provider, access, versions, extensions, idempotency);
            when(nodes.inspect(any())).thenReturn(selection.task());
            when(nodes.inspectStage(any())).thenReturn(selection.stage());
            when(nodes.lockAndRevalidate(any())).thenAnswer(i -> i.getArgument(0));
            when(nodes.lockAndRevalidateStage(any())).thenAnswer(i -> i.getArgument(0));
            when(bindings.inspectTask(any())).thenReturn(binding);
            when(bindings.inspectStage(any())).thenReturn(binding);
            when(bindings.lockAndRevalidate(any())).thenReturn(binding);
            when(bindings.lockAndRevalidateStage(any())).thenReturn(binding);
            when(mapper.selectRevision(any())).thenAnswer(i -> rows.get(i.getArgument(0, RequirementRevisionQuery.class).revisionId()));
            when(mapper.lockRevision(any())).thenAnswer(i -> rows.get(i.getArgument(0, RequirementRevisionQuery.class).revisionId()));
            when(mapper.selectCurrent(any())).thenAnswer(i -> current.get());
            when(mapper.lockCurrent(any())).thenAnswer(i -> current.get());
            when(mapper.selectEffective(any())).thenAnswer(i -> rows.values().stream().filter(RequirementAnalysisRevisionDO::effective).findFirst().orElse(null));
            when(mapper.selectDraft(any())).thenAnswer(i -> rows.values().stream().filter(r -> "DRAFT".equals(r.getRevisionState())).findFirst().orElse(null));
            when(mapper.selectLatest(any())).thenAnswer(i -> rows.values().stream().findFirst().orElse(null));
            when(mapper.maxRevisionNo(any())).thenReturn(1);
            when(mapper.insertRevision(any())).thenAnswer(i -> { var row = i.getArgument(0, RequirementAnalysisRevisionDO.class); rows.put(row.getId(), row); return 1; });
            when(mapper.saveDraft(any())).thenReturn(1);
            when(mapper.insertCurrent(any())).thenAnswer(i -> { current.set(i.getArgument(0)); return 1; });
            when(mapper.updateCurrent(any())).thenAnswer(i -> { current.set(i.getArgument(0)); return 1; });
            source = new RequirementAnalysisRevisionDO();
            source.setId(40L); source.setEntityId(100L); source.setProjectId(3L); source.setTenantId(1L);
            source.setRevisionState("COPY".equals(action) ? "FROZEN" : "DRAFT"); source.setVersion(1); source.setRevisionNo(1);
            source.setExecutionSnapshot(JsonUtils.toJsonString(new RequirementAnalysisExecutionAccess.Frozen(binding, selection.task(), selection.stage())));
            Map<String, Object> values = new LinkedHashMap<>();
            RequirementAnalysisEntityProvider.FIELDS.fields().stream().filter(EntityField::required).forEach(field -> values.put(field.code(), "completed"));
            RequirementAnalysisEntityProvider.FIELDS.write(source, values);
            if (!"CREATE".equals(action)) rows.put(40L, source);
            if ("COPY".equals(action)) source.setEffectiveMarker(1);
            when(mapper.freeze(any())).thenAnswer(i -> {
                source.setRevisionState("FROZEN"); source.setDraftMarker(null); source.setVersion(source.getVersion() + 1);
                source.setFrozenBy(actor.userId()); source.setFrozenAt(LocalDateTime.now()); return 1;
            });
            when(mapper.makeEffective(any())).thenAnswer(i -> { source.setEffectiveMarker(1); source.setVersion(source.getVersion() + 1); return 1; });
            when(idempotency.<EntityVersionProvider.Revision>execute(any(), anyString(), eq(EntityVersionProvider.Revision.class), any(), any()))
                    .thenAnswer(i -> new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                            i.<Supplier<EntityVersionProvider.Revision>>getArgument(3).get()));
            when(versions.create(any(), any(), any(), any())).thenAnswer(i -> provider.createDraft(i.getArgument(0),i.getArgument(1),i.getArgument(2),i.getArgument(3)));
            when(versions.complete(any(), anyInt(), any())).thenAnswer(i -> {
                RevisionRef ref = i.getArgument(0); int version = i.getArgument(1); EntityActor owner = i.getArgument(2);
                // Same callback sequence as EntityVersionService; the platform itself is not under test here.
                provider.lockForWrite(EntityDataRef.revision(ref), owner, version);
                var frozen = provider.freeze(ref, version, owner);
                return provider.activate(ref, frozen.version(), owner);
            });
            when(forms.bind(any())).thenAnswer(i -> {
                var command = i.getArgument(0, EntityFormApi.Bind.class);
                provider.lockForWrite(command.target(), command.actor(), command.expectedEntityVersion());
                return new EntityFormApi.Binding(command.formRevisionId(), null, command.fieldBindings(), 1);
            });
            doAnswer(i -> { provider.lockForWrite(i.getArgument(1), i.getArgument(3), i.getArgument(2)); return null; })
                    .when(forms).copy(any(), any(), anyInt(), any());
            doAnswer(i -> { provider.lockForWrite(i.getArgument(1), i.getArgument(3), i.getArgument(2)); return null; })
                    .when(extensions).copy(any(), any(), anyInt(), any());
            doAnswer(i -> {
                RevisionRef destination = i.getArgument(1);
                access.lock(destination.revisionId(), rows.get(destination.revisionId()).getVersion(), actor, null, true);
                return null;
            }).when(files).copy(any(), any(), any());
        }
        ProjectVerifiedOperationScope.Frame frame(String action) {
            return new ProjectVerifiedOperationScope.Frame(1L,2L,3L,"SOL","REQUIREMENT_ANALYSIS",
                    "SOL.REQUIREMENT_ANALYSIS."+action,1,"CREATE".equals(action)?null:"40",selection);
        }
        EntityVersionProvider.Revision invoke(String action) {
            return switch(action) {
                case "CREATE" -> commands.create(new RequirementAnalysisEntityCommands.Create(3L,selection),actor,"create");
                case "SAVE" -> commands.save(source.revisionRef(),1,new RequirementAnalysisEntityCommands.Patch(Map.of(),null,0,null,selection),actor,"save");
                case "COMPLETE" -> commands.complete(source.revisionRef(),1,new RequirementAnalysisEntityCommands.Action(null,selection),actor,"complete");
                case "COPY" -> commands.copy(source.revisionRef(),1,new RequirementAnalysisEntityCommands.Action("revision",selection),actor,"copy");
                default -> throw new IllegalArgumentException(action);
            };
        }
        ProjectBusinessExecutionApi.WriteRequest request(String id) {
            return ProjectOwnerOperationScope.writeRequest(1L,2L,3L,"SOL","REQUIREMENT_ANALYSIS",id,selection);
        }
    }
    private static ProjectBusinessExecutionSelection selection(boolean stage, Long node) {
        return stage ? new ProjectBusinessExecutionSelection(null,new ProjectStageExecutionContext(3L,1,node,1,5L,1,6L,7L,1,1,true))
                : new ProjectBusinessExecutionSelection(new ProjectTaskExecutionContext(3L,1,node,1,5L,1,6L,7L,1,1,8L,1,true,null),null);
    }
    private static ProjectWorkBindingFact binding(boolean stage, Long node) {
        var target = ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS;
        return new ProjectWorkBindingFact(3L,1,stage?null:node,stage?null:1,5L,1,9L,1,
                target.workBindingTypeCode(),target.targetContextCode(),target.targetObjectType(),target.targetObjectKey(),
                null,null,null,null,10L,1,"{}",11L,12L,1,1,stage?node:null,stage?1:null);
    }
}
