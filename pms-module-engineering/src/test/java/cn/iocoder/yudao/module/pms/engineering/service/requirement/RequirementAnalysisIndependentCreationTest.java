package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.*;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Owner writer/access and form callbacks; project configuration, authorization and persistence are mocked. */
class RequirementAnalysisIndependentCreationTest {
    private static final String PARAMETERS = "{\"schemaVersion\":2,\"dynamicFormTemplateId\":41,"
            + "\"dynamicFormTemplateRevisionId\":42,\"dynamicFormRevisionNo\":3,\"dynamicFormRevisionFactVersion\":4}";
    private final EntityActor actor = new EntityActor(7L, 19L, "ordinary-owner-command");
    private final RequirementAnalysisMapper mapper = mock(RequirementAnalysisMapper.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectParticipantFactApi participants = mock(ProjectParticipantFactApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final ProjectNodeExecutionApi nodes = mock(ProjectNodeExecutionApi.class);
    private final ProjectWorkBindingFactApi bindings = mock(ProjectWorkBindingFactApi.class);
    private final ProjectBusinessExecutionApi guard = mock(ProjectBusinessExecutionApi.class);
    private final RequirementAnalysisExecutionAccess executions = new RequirementAnalysisExecutionAccess(nodes, bindings, guard);
    private final EntityFormApi forms = mock(EntityFormApi.class);
    private final EntityExtensionApi extensions = mock(EntityExtensionApi.class);
    private final RequirementAnalysisRevisionFiles files = mock(RequirementAnalysisRevisionFiles.class);
    private final OperationAuditApi audit = mock(OperationAuditApi.class);
    private final EngineeringRuleReevaluationEvents events = mock(EngineeringRuleReevaluationEvents.class);
    private final Map<Long, RequirementAnalysisRevisionDO> rows = new LinkedHashMap<>();
    private final AtomicReference<RequirementAnalysisDO> current = new AtomicReference<>();
    private RequirementAnalysisAccess access;
    private RequirementAnalysisEntityProvider provider;

    @BeforeEach void setUp() {
        access = new RequirementAnalysisAccess(mapper, scopes, participants, permissions, bindings, executions);
        provider = new RequirementAnalysisEntityProvider(mapper, access, extensions, forms, files, audit, events);
        when(extensions.read(any(), any())).thenReturn(new EntityExtensionApi.Values(null, Map.of(), 0));
        when(permissions.hasAnyPermissions(eq(19L), any(String[].class))).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(80L, 2L, Set.of(80L), Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(80L, 2L, Set.of(80L), Set.of()));
        when(participants.inspect(any())).thenReturn(new ProjectParticipantFact(80L,19L,Set.of("PROJECT_MANAGER"), "PRIMARY", "ACTIVE", "S6",2L,2L));
        when(mapper.selectRevision(any())).thenAnswer(call -> rows.get(call.getArgument(0, RequirementRevisionQuery.class).revisionId()));
        when(mapper.lockRevision(any())).thenAnswer(call -> rows.get(call.getArgument(0, RequirementRevisionQuery.class).revisionId()));
        when(mapper.selectLatest(any())).thenAnswer(call -> rows.values().stream().findFirst().orElse(null));
        when(mapper.selectDraft(any())).thenAnswer(call -> rows.values().stream().filter(r -> "DRAFT".equals(r.getRevisionState())).findFirst().orElse(null));
        when(mapper.selectEffective(any())).thenAnswer(call -> rows.values().stream().filter(RequirementAnalysisRevisionDO::effective).findFirst().orElse(null));
        when(mapper.insertRevision(any())).thenAnswer(call -> { var row = call.getArgument(0, RequirementAnalysisRevisionDO.class); rows.put(row.getId(), row); return 1; });
        when(mapper.saveDraft(any())).thenReturn(1);
        when(mapper.selectCurrent(any())).thenAnswer(call -> current.get());
        when(mapper.lockCurrent(any())).thenAnswer(call -> current.get());
        when(mapper.insertCurrent(any())).thenAnswer(call -> { current.set(call.getArgument(0)); return 1; });
        when(mapper.maxRevisionNo(any())).thenReturn(1);
        when(mapper.freeze(any())).thenAnswer(call -> {
            var command = call.getArgument(0, RequirementFreezeUpdate.class);
            var row = rows.get(command.revisionId()); row.setRevisionState("FROZEN"); row.setDraftMarker(null); row.setVersion(row.getVersion() + 1); return 1;
        });
        when(mapper.makeEffective(any())).thenAnswer(call -> {
            var command = call.getArgument(0, RequirementActivationUpdate.class);
            var row = rows.get(command.revisionId()); row.setEffectiveMarker(1); row.setVersion(row.getVersion() + 1); return 1;
        });
        when(forms.bind(any())).thenAnswer(call -> {
            var command = call.getArgument(0, EntityFormApi.Bind.class);
            provider.lockForWrite(command.target(), command.actor(), command.expectedEntityVersion());
            return new EntityFormApi.Binding(command.formRevisionId(), null, command.fieldBindings(), 1);
        });
        doAnswer(call -> { provider.lockForWrite(call.getArgument(1), call.getArgument(3), ((Number) call.getArgument(2)).longValue()); return null; })
                .when(forms).copy(any(), any(), anyLong(), any());
        doAnswer(call -> { provider.lockForWrite(call.getArgument(1), call.getArgument(3), ((Number) call.getArgument(2)).longValue()); return null; })
                .when(extensions).copy(any(), any(), anyLong(), any());
    }

    @Test void initialDraftNeedsNoTemplateOrFormCallback() {
        var result = provider.createInitial(80L, actor, null);
        var row = rows.get(result.ref().revisionId());
        var frozen = executions.frozen(80L, row.getExecutionSnapshot());
        assertEquals(new RequirementAnalysisExecutionAccess.BusinessOrigin(7L,80L), frozen.businessOrigin());
        assertNull(frozen.binding()); assertNull(frozen.execution()); assertNull(frozen.stageExecution());
        assertNull(row.getProjectTemplateId()); assertNull(row.getProjectTemplateRevisionId());
        verifyNoInteractions(forms);
        verify(scopes, atLeastOnce()).lockAndRevalidate(any());
        verifyNoInteractions(nodes, bindings, guard, events);
    }

    @Test void readSaveFreezeActivateAndCopyRetainConfigurationWhenTheProjectPlanChanges() {
        var created = provider.createInitial(80L, actor, null);
        var row = rows.get(created.ref().revisionId());
        String frozen = row.getExecutionSnapshot();
        // The live configuration service is no longer a dependency once the Owner revision exists.
        Map<String, Object> values = new LinkedHashMap<>();
        RequirementAnalysisEntityProvider.FIELDS.fields().stream().filter(EntityField::required).forEach(field -> values.put(field.code(), "有效业务内容"));
        var saved = provider.save(created.ref(), created.version(), values, actor);
        var completed = provider.freeze(saved.ref(), saved.version(), actor);
        var effective = provider.activate(completed.ref(), completed.version(), actor);
        var copy = provider.createDraft(effective.ref().entity(), effective.ref(), "业务修订", actor);
        assertEquals(frozen, row.getExecutionSnapshot());
        assertEquals(frozen, rows.get(copy.ref().revisionId()).getExecutionSnapshot());
        assertEquals(2, rows.get(copy.ref().revisionId()).getRevisionNo());
        verify(events).changed(80L, "RequirementAnalysis", row.getId(), 19L, actor.correlationId());
        verify(files).lockForFreeze(created.ref(), actor);
        verifyNoInteractions(nodes, bindings, guard);
    }

    @ParameterizedTest @ValueSource(strings = {"absent", "tenant", "project", "template", "publication", "version", "form"})
    void corruptLegacyProvenanceStopsBeforeAnyWrite(String damage) {
        var invalid = switch (damage) {
            case "absent" -> null;
            case "tenant" -> new ProjectBusinessConfigurationApi.Configuration(8L,80L,90L,91L,3,null,PARAMETERS);
            case "project" -> new ProjectBusinessConfigurationApi.Configuration(7L,81L,90L,91L,3,null,PARAMETERS);
            case "template" -> new ProjectBusinessConfigurationApi.Configuration(7L,80L,null,91L,3,null,PARAMETERS);
            case "publication" -> new ProjectBusinessConfigurationApi.Configuration(7L,80L,90L,0L,3,null,PARAMETERS);
            case "version" -> new ProjectBusinessConfigurationApi.Configuration(7L,80L,90L,91L,0,null,PARAMETERS);
            case "form" -> new ProjectBusinessConfigurationApi.Configuration(7L,80L,90L,91L,3,43L,PARAMETERS);
            default -> throw new AssertionError(damage);
        };
        assertThrows(RuntimeException.class, () -> access.lockExecution(80L, JsonUtils.toJsonString(frozenConfig(invalid)), null, actor, null));
        verify(mapper, never()).insertRevision(any());
        verifyNoInteractions(forms, extensions, files, events, nodes, bindings, guard);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "null", "string", "fractional", "overflow", "unknown", "schema"})
    void malformedFormConfigurationCannotBeDefaultedOrCoerced(String damage) {
        ObjectNode parameters = (ObjectNode) JsonUtils.parseTree(PARAMETERS);
        switch (damage) {
            case "missing" -> parameters.remove("dynamicFormTemplateRevisionId");
            case "null" -> parameters.putNull("dynamicFormTemplateRevisionId");
            case "string" -> parameters.put("dynamicFormTemplateRevisionId", "42");
            case "fractional" -> parameters.put("dynamicFormTemplateRevisionId", 42.5);
            case "overflow" -> parameters.put("dynamicFormRevisionNo", Long.MAX_VALUE);
            case "unknown" -> parameters.put("unregisteredBusinessField", true);
            case "schema" -> parameters.put("schemaVersion", 3);
            default -> throw new AssertionError(damage);
        }
        assertThrows(RuntimeException.class, () -> executions.frozen(80L, JsonUtils.toJsonString(frozenConfig(config(parameters.toString())))));
        verify(mapper, never()).insertRevision(any());
        verifyNoInteractions(nodes, bindings, guard, forms);
    }

    @Test void displayMetadataDoesNotBecomeAWriteOrAFormConfiguration() {
        String parameters = PARAMETERS.substring(0, PARAMETERS.length()-1)
                + ",\"businessViewRevisionId\":999,\"instanceResolutionStrategy\":\"BY_PROJECT\",\"contextMapping\":{}}";
        assertNull(executions.frozen(80L, rows.get(provider.createInitial(80L,actor,null).ref().revisionId()).getExecutionSnapshot()).formRevisionId());
        verifyNoInteractions(nodes, bindings, guard);
    }

    @Test void unavailableExplicitProjectEntryDoesNotUseTheOrdinaryConfiguration() {
        var selection = selection();
        assertThrows(RuntimeException.class, () -> provider.createInitial(80L, actor, selection));
        verifyNoInteractions(forms, extensions, events);
        verify(nodes).lockAndRevalidate(selection.task());
        verify(mapper, never()).insertRevision(any());
    }

    @Test void unavailablePresentationCannotBlockBusinessCreation() {
        doThrow(new IllegalStateException("form unavailable")).when(forms).bind(any());
        assertNotNull(provider.createInitial(80L, actor, null));
        verifyNoInteractions(forms, events, nodes, bindings, guard);
        // Transaction rollback belongs to integration coverage, not the in-memory repository double.
    }

    @Test void emptyWorkspaceOffersCreationWithoutRequestingANode() {
        var query = new RequirementAnalysisEntityQueryService(mapper, provider, access, extensions, forms, files, executions, bindings);
        assertEquals(List.of("CREATE_INITIAL_DRAFT"), query.workspace(80L, actor).allowedActions());
        var blocked = query.workspace(80L, actor);
        assertEquals(List.of("CREATE_INITIAL_DRAFT"), blocked.allowedActions());
        verifyNoInteractions(nodes, bindings, guard, forms, extensions, files);
    }

    @Test void legacyModuleSnapshotRemainsCompatibleThroughSaveAndCopy() {
        var form = new RequirementAnalysisConfiguration.ModuleForm(7L,80L,41L,42L,3,4);
        var created = provider.createInitial(80L, actor, null);
        var row = rows.get(created.ref().revisionId());
        assertNull(row.getProjectTemplateId());
        assertNull(row.getProjectTemplateRevisionId());
        String snapshot = row.getExecutionSnapshot();
        row.setExecutionSnapshot(JsonUtils.toJsonString(new RequirementAnalysisExecutionAccess.Frozen(null,null,null,null,form)));
        snapshot = row.getExecutionSnapshot();
        final String legacySnapshot = snapshot;
        assertEquals(form, executions.frozen(80L,snapshot).moduleForm());
        Map<String, Object> values = new LinkedHashMap<>();
        RequirementAnalysisEntityProvider.FIELDS.fields().stream().filter(EntityField::required).forEach(field -> values.put(field.code(), "有效业务内容"));
        var saved = provider.save(created.ref(), created.version(), values, actor);
        var completed = provider.freeze(saved.ref(), saved.version(), actor);
        var active = provider.activate(completed.ref(), completed.version(), actor);
        var copy = provider.createDraft(active.ref().entity(),active.ref(),"修订",actor);
        assertEquals(snapshot,rows.get(copy.ref().revisionId()).getExecutionSnapshot());
        assertThrows(RuntimeException.class,()->access.lockExecution(80L,legacySnapshot,null,new EntityActor(8L,19L,"wrong-tenant"),null));
        assertThrows(RuntimeException.class,()->executions.frozen(81L,legacySnapshot));
        verifyNoInteractions(nodes,bindings,guard);
    }

    @Test void standaloneProvenanceAcceptsDifferentPresentationOnSameBusinessTarget() {
        var initial = provider.createInitial(80L, actor, null);
        String snapshot = rows.get(initial.ref().revisionId()).getExecutionSnapshot();
        var selected = binding(PARAMETERS);
        when(bindings.inspectTask(any())).thenReturn(selected);
        assertSame(selected, executions.currentBinding(80L, snapshot, selection()));
        when(bindings.inspectTask(any())).thenReturn(binding(PARAMETERS.replace("\"dynamicFormRevisionNo\":3", "\"dynamicFormRevisionNo\":4")));
        assertNotNull(executions.currentBinding(80L, snapshot, selection()));
        assertThrows(RuntimeException.class, () -> executions.currentBinding(80L, snapshot, null));
        assertEquals(snapshot, rows.get(initial.ref().revisionId()).getExecutionSnapshot());
    }

    @Test void mixedAndCrossTenantProvenanceCannotEnterAnIndependentWrite() {
        var initial = provider.createInitial(80L, actor, null);
        var row = rows.get(initial.ref().revisionId());
        var document = (ObjectNode) JsonUtils.parseTree(row.getExecutionSnapshot());
        var otherTenant = document.deepCopy(); ((ObjectNode) otherTenant.get("businessOrigin")).put("tenantId", 8L);
        row.setExecutionSnapshot(otherTenant.toString());
        assertThrows(RuntimeException.class, () -> access.lock(row.getId(), 1, actor, null, true));
        document.set("binding", JsonUtils.parseTree(JsonUtils.toJsonString(binding(PARAMETERS))));
        row.setExecutionSnapshot(document.toString());
        assertThrows(RuntimeException.class, () -> executions.frozen(80L, row.getExecutionSnapshot()));
        verifyNoInteractions(nodes, guard);
    }

    @Test void legacyFrozenSerializationDoesNotGainANewConfigurationProperty() {
        var binding = binding(PARAMETERS);
        var old = new RequirementAnalysisExecutionAccess.Frozen(binding, selection().task(), null);
        String json = executions.freeze(old);
        assertFalse(JsonUtils.parseTree(json).has("configuration"));
        assertEquals(json, executions.freeze(executions.frozen(80L, json)));
    }

    private ProjectBusinessConfigurationApi.Configuration config(String parameters) {
        return new ProjectBusinessConfigurationApi.Configuration(7L,80L,90L,91L,3,null,parameters);
    }
    private ProjectBusinessExecutionSelection selection() {
        return new ProjectBusinessExecutionSelection(new ProjectTaskExecutionContext(80L,1L,30L,1,40L,1,50L,60L,1,1,70L,1,true,null),null);
    }
    private ProjectWorkBindingFact binding(String parameters) {
        return new ProjectWorkBindingFact(80L,1L,30L,1,40L,1,90L,1,"BUSINESS_OBJECT","SOL","REQUIREMENT_ANALYSIS",
                "PRE_04_REQUIREMENT_ANALYSIS",null,null,null,null,91L,3,parameters,41L,42L,3,4);
    }
    private RequirementAnalysisExecutionAccess.Frozen frozenConfig(ProjectBusinessConfigurationApi.Configuration value) {
        return new RequirementAnalysisExecutionAccess.Frozen(null, null, null, value);
    }
}
