package cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliveryconfiguration.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.domain.template.DeliveryDefinitionKind;
import cn.iocoder.yudao.module.pms.project.domain.template.DeliveryDefinitionReference;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessProviderRegistry;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Revision;
import java.util.function.Supplier;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PM-03: dangling, tenant, disabled, cycle and unavailable Owner failures never mean unconfigured. */
class DeliveryDefinitionResolverTest {
    DeliveryDefinitionRevisionMapper revisions = mock(DeliveryDefinitionRevisionMapper.class);
    DeliveryDefinitionReferenceMapper references = mock(DeliveryDefinitionReferenceMapper.class);
    ProjectStageGateProviderRegistry providers = mock(ProjectStageGateProviderRegistry.class);
    TaskBusinessObjectProvider survey = owner("SOL", "SITE_SURVEY", Set.of("SURVEY_CONFIRMED", "SURVEY_ARCHIVED"));
    TaskBusinessObjectProvider acceptance = owner("ACC", "ACCEPTANCE", Set.of("REPORT_EFFECTIVE"));
    BusinessViewQueryApi views = mock(BusinessViewQueryApi.class);
    TaskBusinessProviderRegistry catalog = new TaskBusinessProviderRegistry(List.of(survey, acceptance));
    DeliveryDefinitionResolver resolver = new DeliveryDefinitionResolver(revisions, references,
            views, providers, mock(ProjectStageGateProcessOwnerApi.class), catalog);
    @BeforeEach void setup() { TenantContextHolder.setTenantId(7L); when(references.selectReferences(any())).thenReturn(List.of()); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void missingForeignDraftAndDisabledTargetsRejected() {
        assertThrows(ServiceException.class, () -> resolve(1L));
        var row = row(1L); row.setTenantId(8L); when(revisions.selectById(1L)).thenReturn(row);
        assertThrows(ServiceException.class, () -> resolve(1L));
        row.setTenantId(7L); row.setRevisionState("DRAFT"); assertThrows(ServiceException.class, () -> resolve(1L));
        row.setRevisionState("PUBLISHED"); row.setDisabledAt(LocalDateTime.now()); assertThrows(ServiceException.class, () -> resolve(1L));
    }
    @Test void selfAndTransitiveCyclesRejected() {
        when(revisions.selectById(1L)).thenReturn(row(1L)); when(revisions.selectById(2L)).thenReturn(row(2L));
        when(references.selectReferences(any())).thenAnswer(call -> {
            var query = (cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.query.DeliveryDefinitionReferencesQuery)call.getArgument(0);
            var edge = new DeliveryDefinitionReferenceDO(); edge.setReferenceKey("next");
            edge.setTargetRevisionId(query.ownerRevisionId() == 1L ? 2L : 1L); return List.of(edge);
        });
        assertThrows(ServiceException.class, () -> resolve(1L));
        assertThrows(ServiceException.class, () -> resolver.resolve(List.of(new DeliveryDefinitionReference("self",1L)),1L,false));
    }
    @Test void providerMustExistAndStableVersionRecheckedUnderLock() {
        var row = row(1L); when(revisions.selectById(1L)).thenReturn(row); when(revisions.lockRevision(any())).thenReturn(row);
        assertThrows(ServiceException.class, () -> resolve(1L));
        when(providers.hasProvider("PROJ_TASK")).thenReturn(true);
        var result = resolver.resolve(List.of(new DeliveryDefinitionReference("rule",1L)),null,true);
        assertEquals(1, result.size()); assertTrue(result.get(1L).definition().payload().has("parameters"));
        verify(revisions).lockRevision(any());
        var stale = row(1L); stale.setVersion(8); when(revisions.lockRevision(any())).thenReturn(stale);
        assertThrows(ServiceException.class, () -> resolver.resolve(List.of(new DeliveryDefinitionReference("rule",1L)),null,true));
    }
    @Test void independentBusinessRulesPublishUsingOnlyDeclaredMetadata() {
        for (String code : List.of("SURVEY_CONFIRMED", "SURVEY_ARCHIVED", "REPORT_EFFECTIVE")) {
            var row = row(1L); row.setRevisionState("DRAFT"); row.setPublishedAt(null); row.setPayload(fact(code));
            when(revisions.selectById(1L)).thenReturn(row);
            when(revisions.lockIdentity(any())).thenReturn(List.of(row));
            when(revisions.publish(any())).thenReturn(1);
            assertEquals(1L, service().publish(1L, 0, code));
        }
        verify(revisions, times(3)).publish(any());
        verifyNoInteractions(providers, views);
        verifyMetadataOnly();
    }

    @Test void unknownFactCannotPublishEvenInsideAnyRule() {
        var row = row(1L); row.setRevisionState("DRAFT"); row.setPublishedAt(null);
        row.setPayload("{\"operator\":\"ANY\",\"rules\":[" + fact("SURVEY_CONFIRMED") + "," + fact("UNKNOWN") + "]}");
        when(revisions.selectById(1L)).thenReturn(row);
        when(revisions.lockIdentity(any())).thenReturn(List.of(row));
        assertThrows(ServiceException.class, () -> service().publish(1L, 0, "unknown"));
        verify(revisions, never()).publish(any());
        verifyMetadataOnly();
    }

    @Test void exactTaskBindingSupportsNestedFactsAndBothBusinessHosts() {
        for (String binding : List.of("BUSINESS_OBJECT", "BUSINESS_COMPONENT")) {
            Revision task = bound(DeliveryDefinitionKind.TASK, binding, "SOL", "SITE_SURVEY",
                    "{\"operator\":\"ALL\",\"rules\":[" + fact("SURVEY_CONFIRMED") + ","
                            + "{\"operator\":\"ANY\",\"rules\":[" + fact("SURVEY_ARCHIVED") + "]}]}");
            assertEquals(3, resolver.resolveDefinition(task, true).size());
        }
        assertEquals(3, resolver.resolveDefinition(bound(DeliveryDefinitionKind.TASK, "BUSINESS_OBJECT",
                "ACC", "ACCEPTANCE", fact("REPORT_EFFECTIVE")), true).size());
        verifyMetadataOnly();
    }

    @Test void ownerTypeMismatchAndUnregisteredOwnerCannotBorrowOtherFacts() {
        for (String[] target : List.of(new String[]{"ACC", "ACCEPTANCE"}, new String[]{"SOL", "OTHER"},
                new String[]{"OTHER", "SITE_SURVEY"})) {
            Revision task = bound(DeliveryDefinitionKind.TASK, "BUSINESS_OBJECT", target[0], target[1],
                    fact("SURVEY_CONFIRMED"));
            assertThrows(ServiceException.class, () -> resolver.resolveDefinition(task, true));
        }
        // Same-named declarations are scoped to an exact Owner/type, not globally merged.
        when(acceptance.completionFactCodes()).thenReturn(Set.of("SURVEY_CONFIRMED"));
        assertEquals(3, resolver.resolveDefinition(bound(DeliveryDefinitionKind.TASK, "BUSINESS_OBJECT",
                "ACC", "ACCEPTANCE", fact("SURVEY_CONFIRMED")), true).size());
        Revision mixed = bound(DeliveryDefinitionKind.TASK, "BUSINESS_OBJECT", "ACC", "ACCEPTANCE",
                "{\"operator\":\"ANY\",\"rules\":[" + fact("SURVEY_CONFIRMED") + "," + fact("SURVEY_ARCHIVED") + "]}");
        assertThrows(ServiceException.class, () -> resolver.resolveDefinition(mixed, true));
        verifyMetadataOnly();
    }

    @Test void stageNativeAndUnsupportedHostsDoNotAdvertiseTaskFactRuntime() {
        for (String binding : List.of("BUSINESS_OBJECT", "STAGE_NATIVE")) {
            Revision stage = bound(DeliveryDefinitionKind.STAGE, binding, "SOL", "SITE_SURVEY", fact("SURVEY_CONFIRMED"));
            assertThrows(ServiceException.class, () -> resolver.resolveDefinition(stage, true));
        }
        for (String binding : List.of("TASK_NATIVE", "DYNAMIC_FORM", "COMPOSITE", "APPROVAL")) {
            Revision task = bound(DeliveryDefinitionKind.TASK, binding, "SOL", "SITE_SURVEY", fact("SURVEY_CONFIRMED"));
            assertThrows(ServiceException.class, () -> resolver.resolveDefinition(task, true));
        }
        verifyMetadataOnly();
    }

    @Test void absentDefaultAndAmbiguousCatalogsFailClosed() {
        assertFalse(new TaskBusinessProviderRegistry(List.of()).supportsCompletionFact("SURVEY_CONFIRMED"));
        TaskBusinessObjectProvider legacy = mock(TaskBusinessObjectProvider.class, CALLS_REAL_METHODS);
        assertEquals(Set.of(), legacy.completionFactCodes());
        assertFalse(new TaskBusinessProviderRegistry(List.of(legacy)).supportsCompletionFact("SURVEY_CONFIRMED"));
        var duplicate = owner("SOL", "SITE_SURVEY", Set.of("SURVEY_CONFIRMED"));
        var ambiguous = new TaskBusinessProviderRegistry(List.of(survey, duplicate));
        assertFalse(ambiguous.supportsCompletionFact("SURVEY_CONFIRMED"));
        assertFalse(ambiguous.supportsCompletionFact("SOL", "SITE_SURVEY", "SURVEY_CONFIRMED"));
        assertFalse(catalog.supportsCompletionFact(null));
        assertFalse(catalog.supportsCompletionFact(null, "SITE_SURVEY", "SURVEY_CONFIRMED"));
    }

    @Test void disabledPublishedHistoryReadsWithoutCatalogOrBusinessObjectCalls() {
        var row = row(1L); row.setDisabledAt(LocalDateTime.now()); row.setPayload(fact("REMOVED_FACT"));
        when(revisions.selectById(1L)).thenReturn(row);
        clearInvocations(survey, acceptance);
        assertEquals("REMOVED_FACT", service().get(1L).payload().path("parameters").path("factCode").asText());
        verifyNoInteractions(survey, acceptance, providers, views);
        verify(revisions, never()).publish(any());
    }

    private DeliveryDefinitionService service() {
        var commands = mock(DeliveryConfigurationCommands.class);
        when(commands.execute(anyString(), anyString(), any(), eq(Long.class), any())).thenAnswer(call -> {
            Supplier<Long> operation = call.getArgument(4); return operation.get();
        });
        return new DeliveryDefinitionService(revisions, references, commands, resolver);
    }

    private Revision bound(DeliveryDefinitionKind kind, String binding, String owner, String type, String rule) {
        var work = row(2L); work.setDefinitionKind("WORK_BINDING");
        work.setPayload("{\"bindingType\":\"" + binding + "\",\"instanceResolutionStrategy\":\"REFERENCE_EXISTING\","
                + "\"contextMapping\":{}" + (binding.endsWith("_NATIVE") ? "" : ",\"businessViewRevisionId\":20,"
                + "\"targetContextCode\":\"" + owner + "\",\"targetObjectType\":\"" + type + "\",\"targetObjectKey\":\"PROJECT\"") + "}");
        var permission = row(3L); permission.setDefinitionKind("PERMISSION_POLICY");
        permission.setPayload("{\"requiredActions\":[\"QUERY\"]}");
        var completion = row(4L); completion.setPayload(rule);
        for (var row : List.of(work, permission, completion)) when(revisions.selectById(row.getId())).thenReturn(row);
        doAnswer(call -> {
            var query = (cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.query.DeliveryDefinitionByIdQuery) call.getArgument(0);
            return List.of(work, permission, completion).stream().filter(row -> row.getId().equals(query.revisionId())).findFirst().orElseThrow();
        }).when(revisions).lockRevision(any());
        var view = mock(cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision.class);
        when(view.id()).thenReturn(20L); when(view.ownerContext()).thenReturn(owner); when(view.entityType()).thenReturn(type);
        when(view.publishedAt()).thenReturn(LocalDateTime.now()); when(view.version()).thenReturn(0);
        when(view.contextSchema()).thenReturn(JsonUtils.parseTree("{\"properties\":{}}"));
        when(views.getRevision(any())).thenReturn(view);
        when(views.lockAndRevalidateAll(any())).thenReturn(List.of(view));
        return new Revision(10L, kind, "NODE", 1L, "DRAFT", 1,
                JsonUtils.parseTree("{\"name\":\"Node\",\"workBinding\":\"work\",\"permissionPolicy\":\"permission\",\"completionRule\":\"completion\""
                        + (kind == DeliveryDefinitionKind.STAGE ? ",\"stageCode\":\"S1\",\"start\":true,\"terminal\":true" : "") + "}"),
                List.of(new DeliveryDefinitionReference("work", 2L), new DeliveryDefinitionReference("permission", 3L),
                        new DeliveryDefinitionReference("completion", 4L)), null, null, 0);
    }

    private static String fact(String code) {
        return "{\"predicate\":\"BUSINESS_FACT\",\"parameters\":{\"factCode\":\"" + code + "\",\"quantifier\":\"ALL\"}}";
    }
    private static TaskBusinessObjectProvider owner(String owner, String type, Set<String> codes) {
        var provider = mock(TaskBusinessObjectProvider.class);
        when(provider.ownerContext()).thenReturn(owner); when(provider.objectType()).thenReturn(type);
        when(provider.completionFactCodes()).thenReturn(codes); return provider;
    }
    private void verifyMetadataOnly() {
        for (var provider : List.of(survey, acceptance)) {
            verify(provider, atLeast(0)).ownerContext(); verify(provider, atLeast(0)).objectType();
            verify(provider, atLeast(0)).completionFactCodes(); verifyNoMoreInteractions(provider);
        }
    }

    private void resolve(Long id) { resolver.resolve(List.of(new DeliveryDefinitionReference("rule",id)),null,false); }
    private DeliveryDefinitionRevisionDO row(Long id) {
        var row = new DeliveryDefinitionRevisionDO(); row.setId(id); row.setTenantId(7L); row.setRevisionState("PUBLISHED");
        row.setPublishedAt(LocalDateTime.now()); row.setVersion(0); row.setRevisionNo(1L); row.setSchemaVersion(1);
        row.setDefinitionKind("COMPLETION_RULE"); row.setDefinitionCode("RULE");
        row.setPayload("{\"predicate\":\"TASK_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}"); return row;
    }
}
