package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.PlatformDynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.PlatformDynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstanceValueUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionPublishUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormTemplateVersionUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicFormCommandServiceTest {

    private static final DynamicFormCommands.Actor ACTOR = new DynamicFormCommands.Actor(1L, 7L);
    private static final String CONF = "{\"form\":{}}";
    private static final String RULES = "[{\"type\":\"input\",\"field\":\"subject\"},"
            + "{\"type\":\"PmsFileArtifact\",\"field\":\"evidence\"}]";

    @Mock private DynamicFormTemplateMapper templateMapper;
    @Mock private DynamicFormTemplateRevisionMapper revisionMapper;
    @Mock private PlatformDynamicFormInstanceMapper instanceMapper;
    @Mock private DynamicFormSchemaService schemaService;
    @Mock private DynamicFormActionProjection actionProjection;
    @Mock private DynamicFormQueryService queryService;
    @Mock private PlatformCommandExecutionApi commandExecutionApi;
    @Mock private OperationAuditApi operationAuditApi;
    @Mock private TransactionTemplate transactionTemplate;

    private DynamicFormCommandService service;
    private final List<PlatformCommandExecutionApi.SuccessFacts> successFacts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DynamicFormCommandService(templateMapper, revisionMapper, instanceMapper, schemaService,
                actionProjection, queryService, commandExecutionApi, operationAuditApi, transactionTemplate);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        });
        stubNewExecution();
        lenient().when(actionProjection.templateActions(anyLong(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(Set.of());
        lenient().when(actionProjection.revisionActions(anyLong(), any())).thenReturn(Set.of());
        lenient().when(actionProjection.instanceActions(anyLong(), anyLong())).thenReturn(Set.of());
    }

    @Test
    void createTemplateCreatesDisabledIdentityAndRevisionOneDraftInOneExecution() {
        AtomicReference<DynamicFormTemplateDO> template = new AtomicReference<>();
        AtomicReference<DynamicFormTemplateRevisionDO> revision = new AtomicReference<>();
        when(templateMapper.insert(any())).thenAnswer(invocation -> {
            template.set(invocation.getArgument(0));
            return 1;
        });
        when(revisionMapper.insert(any())).thenAnswer(invocation -> {
            revision.set(invocation.getArgument(0));
            return 1;
        });
        when(templateMapper.selectByRow(any())).thenAnswer(ignored -> template.get());
        when(queryService.toTemplate(eq(ACTOR.userId()), any())).thenAnswer(invocation ->
                templateView(invocation.<DynamicFormTemplateDO>getArgument(1), revision.get()));

        DynamicFormViews.Template result = service.createTemplate(new DynamicFormCommands.CreateTemplate(
                ACTOR, "create-template-1", "  TEMPLATE_A  ", "  示例模板  ", " GENERAL ", "  说明  "));

        assertEquals("TEMPLATE_A", template.get().getTemplateCode());
        assertEquals("示例模板", template.get().getTemplateName());
        assertEquals("GENERAL", template.get().getCategoryCode());
        assertEquals("说明", template.get().getDescription());
        assertEquals("DISABLED", template.get().getAvailabilityCode());
        assertEquals(0, template.get().getVersion());
        assertEquals(template.get().getId(), revision.get().getTemplateId());
        assertEquals(1, revision.get().getRevisionNo());
        assertEquals("DRAFT", revision.get().getStatusCode());
        assertEquals(1, revision.get().getDraftMarker());
        assertEquals("{}", revision.get().getFormConfJson());
        assertEquals("[]", revision.get().getFormRulesJson());
        assertEquals(DynamicFormSchemaService.ENGINE_CODE, revision.get().getEngineCode());
        assertEquals(result.templateId(), template.get().getId());
        verify(actionProjection, atLeastOnce()).require(ACTOR.userId(), DynamicFormActionProjection.TEMPLATE_MANAGE);
        assertEquals(revision.get().getId(),
                JsonUtils.parseTree(successFacts.getFirst().detailSnapshot()).get("draftRevisionId").asLong());
        assertSafe(successFacts.getFirst().detailSnapshot(), "form_conf_json", "form_rules_json", "说明");
    }

    @Test
    void createTemplateWithoutManagePermissionHasNoBusinessWrite() {
        doThrow(exception(FORBIDDEN)).when(actionProjection)
                .require(ACTOR.userId(), DynamicFormActionProjection.TEMPLATE_MANAGE);

        assertThrows(ServiceException.class, () -> service.createTemplate(new DynamicFormCommands.CreateTemplate(
                ACTOR, "create-template-forbidden", "T", "模板", "GENERAL", null)));

        verify(templateMapper, never()).insert(any());
        verify(revisionMapper, never()).insert(any());
    }

    @Test
    void createRevisionCopiesCurrentPublishedPayloadAndNeverUpdatesTheSource() {
        DynamicFormTemplateDO template = template(11L, 3, "DISABLED", 21L);
        DynamicFormTemplateRevisionDO source = publishedRevision(21L, 11L, 4, 6);
        AtomicReference<DynamicFormTemplateRevisionDO> inserted = new AtomicReference<>();
        when(templateMapper.selectForUpdate(any())).thenReturn(template);
        when(revisionMapper.selectDraftForUpdate(any())).thenReturn(null);
        when(revisionMapper.selectCurrentPublishedForUpdate(any())).thenReturn(source);
        when(revisionMapper.insert(any())).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        });
        when(revisionMapper.selectByRow(any())).thenAnswer(ignored -> inserted.get());
        when(queryService.toRevision(eq(ACTOR.userId()), any())).thenAnswer(invocation ->
                revisionView(invocation.getArgument(1)));

        DynamicFormViews.Revision result = service.createRevision(new DynamicFormCommands.CreateRevision(
                ACTOR, 11L, 3, "create-revision-1"));

        assertEquals(5, inserted.get().getRevisionNo());
        assertEquals(source.getId(), inserted.get().getSourceRevisionId());
        assertEquals(source.getFormConfJson(), inserted.get().getFormConfJson());
        assertEquals(source.getFormRulesJson(), inserted.get().getFormRulesJson());
        assertEquals("DRAFT", inserted.get().getStatusCode());
        assertEquals(1, inserted.get().getDraftMarker());
        assertEquals(inserted.get().getId(), result.revisionId());
        verify(revisionMapper, never()).updateDraftIfMatch(any());
        verify(revisionMapper, never()).publishIfMatch(any());
    }

    @Test
    void patchDraftReplacesSchemaWithCasAndAuditsOnlyOrderedFieldKeys() {
        DynamicFormTemplateDO template = template(11L, 3, "DISABLED", 21L);
        DynamicFormTemplateRevisionDO draft = draftRevision(22L, 11L, 2, 5);
        when(revisionMapper.selectByRow(any())).thenReturn(draft);
        when(templateMapper.selectForUpdate(any())).thenReturn(template);
        when(revisionMapper.selectForUpdate(any())).thenReturn(draft);
        when(revisionMapper.updateDraftIfMatch(any())).thenReturn(1);
        when(schemaService.parseAndValidate(any(), any(), any(), any(), any()))
                .thenReturn(new DynamicFormSchemaService.SchemaFields(List.of("subject"), List.of("evidence")));
        when(queryService.toRevision(eq(ACTOR.userId()), any())).thenAnswer(invocation ->
                revisionView(invocation.getArgument(1)));

        service.patchRevision(new DynamicFormCommands.PatchRevision(ACTOR, 22L, 5,
                JsonUtils.parseTree(CONF), JsonUtils.parseTree(RULES), DynamicFormSchemaService.ENGINE_CODE,
                DynamicFormSchemaService.DESIGNER_VERSION, DynamicFormSchemaService.RENDERER_VERSION,
                "patch-revision-1"));

        ArgumentCaptor<DynamicFormTemplateRevisionDO> update =
                ArgumentCaptor.forClass(DynamicFormTemplateRevisionDO.class);
        verify(revisionMapper).updateDraftIfMatch(update.capture());
        assertEquals(5, update.getValue().getVersion());
        assertEquals(CONF, update.getValue().getFormConfJson());
        assertEquals(RULES, update.getValue().getFormRulesJson());
        Map<String, ?> audit = captureSuccessAudit();
        assertEquals(List.of("subject"), audit.get("ordinaryFieldKeys"));
        assertEquals(List.of("evidence"), audit.get("fileFieldKeys"));
        assertFalse(audit.containsKey("formConfJson"));
        assertFalse(audit.containsKey("formRulesJson"));
    }

    @Test
    void publishedRevisionCannotUseEitherRevisionUpdatePath() {
        DynamicFormTemplateRevisionDO published = publishedRevision(21L, 11L, 1, 4);
        when(revisionMapper.selectByRow(any())).thenReturn(published);
        when(templateMapper.selectForUpdate(any())).thenReturn(template(11L, 2, "DISABLED", 21L));
        when(revisionMapper.selectForUpdate(any())).thenReturn(published);
        when(schemaService.parseAndValidate(any(), any(), any(), any(), any()))
                .thenReturn(new DynamicFormSchemaService.SchemaFields(List.of("subject"), List.of()));

        assertThrows(ServiceException.class, () -> service.patchRevision(new DynamicFormCommands.PatchRevision(
                ACTOR, 21L, 4, JsonUtils.parseTree(CONF), JsonUtils.parseTree("[]"),
                DynamicFormSchemaService.ENGINE_CODE, DynamicFormSchemaService.DESIGNER_VERSION,
                DynamicFormSchemaService.RENDERER_VERSION, "patch-published")));
        reset(commandExecutionApi);
        stubNewExecution();
        assertThrows(ServiceException.class, () -> service.publishRevision(
                new DynamicFormCommands.PublishRevision(ACTOR, 21L, 4, "publish-published")));

        verify(revisionMapper, never()).updateDraftIfMatch(any());
        verify(revisionMapper, never()).publishIfMatch(any());
    }

    @Test
    void publishMovesOnlyExpectedDraftThenSwitchesTemplatePointer() {
        DynamicFormTemplateDO template = template(11L, 8, "DISABLED", 20L);
        DynamicFormTemplateRevisionDO draft = draftRevision(22L, 11L, 2, 5);
        DynamicFormTemplateRevisionDO published = publishedRevision(22L, 11L, 2, 6);
        DynamicFormTemplateDO updated = template(11L, 9, "DISABLED", 22L);
        when(revisionMapper.selectByRow(any())).thenReturn(draft, published);
        when(templateMapper.selectForUpdate(any())).thenReturn(template);
        when(revisionMapper.selectForUpdate(any())).thenReturn(draft);
        when(revisionMapper.publishIfMatch(any())).thenReturn(1);
        when(templateMapper.updateIfMatch(any())).thenReturn(1);
        when(templateMapper.selectByRow(any())).thenReturn(updated);
        when(schemaService.parseAndValidate(any(), any(), any(), any(), any()))
                .thenReturn(new DynamicFormSchemaService.SchemaFields(List.of("subject"), List.of("evidence")));
        when(queryService.toRevision(eq(ACTOR.userId()), any())).thenReturn(revisionView(published));

        DynamicFormViews.PublishResult result = service.publishRevision(
                new DynamicFormCommands.PublishRevision(ACTOR, 22L, 5, "publish-1"));

        InOrder order = inOrder(revisionMapper, templateMapper);
        order.verify(revisionMapper).publishIfMatch(any(DynamicFormRevisionPublishUpdate.class));
        order.verify(templateMapper).updateIfMatch(any(DynamicFormTemplateVersionUpdate.class));
        ArgumentCaptor<DynamicFormTemplateVersionUpdate> pointer =
                ArgumentCaptor.forClass(DynamicFormTemplateVersionUpdate.class);
        verify(templateMapper).updateIfMatch(pointer.capture());
        assertTrue(pointer.getValue().updateCurrentPublishedRevisionId());
        assertEquals(22L, pointer.getValue().currentPublishedRevisionId());
        assertEquals(22L, result.revision().revisionId());
        assertSafe(successFacts.getFirst().detailSnapshot(), CONF, RULES, "subject", "evidence");
    }

    @Test
    void enableAndDisableUseIndependentCasTargets() {
        DynamicFormTemplateDO enabled = template(11L, 4, "ENABLED", 21L);
        DynamicFormTemplateDO disabled = template(11L, 5, "DISABLED", 21L);
        when(templateMapper.selectForUpdate(any())).thenReturn(disabled, enabled);
        when(templateMapper.updateIfMatch(any())).thenReturn(1);
        when(templateMapper.selectByRow(any())).thenReturn(enabled, disabled);
        when(queryService.toTemplate(eq(ACTOR.userId()), any())).thenAnswer(invocation ->
                templateView(invocation.getArgument(1)));

        service.setAvailability(new DynamicFormCommands.SetAvailability(
                ACTOR, 11L, 5, "ENABLED", "enable-1"));
        service.setAvailability(new DynamicFormCommands.SetAvailability(
                ACTOR, 11L, 4, "DISABLED", "disable-1"));

        ArgumentCaptor<DynamicFormTemplateVersionUpdate> updates =
                ArgumentCaptor.forClass(DynamicFormTemplateVersionUpdate.class);
        verify(templateMapper, org.mockito.Mockito.times(2)).updateIfMatch(updates.capture());
        assertEquals(List.of("ENABLED", "DISABLED"), updates.getAllValues().stream()
                .map(DynamicFormTemplateVersionUpdate::availabilityCode).toList());
        assertTrue(updates.getAllValues().stream().allMatch(DynamicFormTemplateVersionUpdate::updateAvailabilityCode));
    }

    @Test
    void availabilityRejectsANewCommandThatDoesNotRepresentAStateTransition() {
        when(templateMapper.selectForUpdate(any())).thenReturn(template(11L, 4, "ENABLED", 21L));

        ServiceException failure = assertThrows(ServiceException.class, () -> service.setAvailability(
                new DynamicFormCommands.SetAvailability(ACTOR, 11L, 4, "ENABLED", "enable-again")));

        assertEquals(DYNAMIC_FORM_VERSION_CONFLICT.getCode(), failure.getCode());
        verify(templateMapper, never()).updateIfMatch(any());
    }

    @Test
    void createInstanceFreezesCurrentPublishedRevisionAndRevalidatesPermissionAfterLocks() {
        DynamicFormTemplateDO template = template(11L, 6, "ENABLED", 21L);
        DynamicFormTemplateRevisionDO published = publishedRevision(21L, 11L, 3, 4);
        AtomicReference<PlatformDynamicFormInstanceDO> inserted = new AtomicReference<>();
        when(revisionMapper.selectByRow(any())).thenReturn(published);
        when(templateMapper.selectForUpdate(any())).thenReturn(template);
        when(revisionMapper.selectForUpdate(any())).thenReturn(published);
        when(instanceMapper.insert(any())).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        });

        DynamicFormViews.InstanceCreated result = service.createInstance(new DynamicFormCommands.CreateInstance(
                ACTOR, 21L, 6, "  实例A  ", "create-instance-1"));

        assertEquals("实例A", inserted.get().getInstanceName());
        assertEquals("PLATFORM", inserted.get().getOwnerContext());
        assertEquals("MANUAL_DYNAMIC_FORM", inserted.get().getObjectType());
        assertEquals(String.valueOf(inserted.get().getId()), inserted.get().getObjectId());
        assertEquals(21L, inserted.get().getTemplateRevisionId());
        assertEquals(3, inserted.get().getTemplateRevisionNo());
        assertEquals(CONF, published.getFormConfJson());
        assertEquals("{}", inserted.get().getValueJson());
        assertEquals(result.instanceId(), inserted.get().getId());
        verify(actionProjection, org.mockito.Mockito.atLeast(2))
                .require(ACTOR.userId(), DynamicFormActionProjection.INSTANCE_CREATE);
    }

    @Test
    void patchInstanceMergesFalseZeroNullAndEmptyValuesAndUsesSafeAudit() {
        PlatformDynamicFormInstanceDO instance = instance(31L, 21L, 7);
        instance.setValueJson("{\"kept\":\"yes\",\"zero\":9}");
        DynamicFormTemplateRevisionDO revision = publishedRevision(21L, 11L, 1, 4);
        when(instanceMapper.selectForUpdate(any())).thenReturn(instance);
        when(revisionMapper.selectByRow(any())).thenReturn(revision);
        when(schemaService.parseAndValidate(any(), any(), any(), any(), any())).thenReturn(
                new DynamicFormSchemaService.SchemaFields(
                        List.of("kept", "flag", "zero", "nothing", "text", "items"), List.of("evidence")));
        when(instanceMapper.updateValueIfMatch(any())).thenReturn(1);

        DynamicFormViews.InstancePatchResult result = service.patchInstance(new DynamicFormCommands.PatchInstance(
                ACTOR, 31L, 7, JsonUtils.parseTree(
                "{\"flag\":false,\"zero\":0,\"nothing\":null,\"text\":\"\",\"items\":[]}"),
                "patch-instance-1"));

        ArgumentCaptor<DynamicFormInstanceValueUpdate> update =
                ArgumentCaptor.forClass(DynamicFormInstanceValueUpdate.class);
        verify(instanceMapper).updateValueIfMatch(update.capture());
        assertEquals("yes", JsonUtils.parseTree(update.getValue().valueJson()).get("kept").asText());
        assertFalse(JsonUtils.parseTree(update.getValue().valueJson()).get("flag").asBoolean());
        assertEquals(0, JsonUtils.parseTree(update.getValue().valueJson()).get("zero").asInt());
        assertTrue(JsonUtils.parseTree(update.getValue().valueJson()).get("nothing").isNull());
        assertEquals("", JsonUtils.parseTree(update.getValue().valueJson()).get("text").asText());
        assertTrue(JsonUtils.parseTree(update.getValue().valueJson()).get("items").isArray());
        assertEquals(List.of("flag", "items", "nothing", "text", "zero"), result.changedFieldKeys());
        Map<String, ?> audit = captureSuccessAudit();
        assertEquals(result.changedFieldKeys(), audit.get("changedFieldKeys"));
        assertFalse(audit.containsKey("values"));
        assertFalse(audit.containsKey("valueJson"));
    }

    @Test
    void patchInstanceRejectsFileAndUnknownFieldsBeforeCas() {
        PlatformDynamicFormInstanceDO instance = instance(31L, 21L, 7);
        when(instanceMapper.selectForUpdate(any())).thenReturn(instance);
        when(revisionMapper.selectByRow(any())).thenReturn(publishedRevision(21L, 11L, 1, 4));
        when(schemaService.parseAndValidate(any(), any(), any(), any(), any())).thenReturn(
                new DynamicFormSchemaService.SchemaFields(List.of("subject"), List.of("evidence")));

        assertThrows(ServiceException.class, () -> service.patchInstance(new DynamicFormCommands.PatchInstance(
                ACTOR, 31L, 7, JsonUtils.parseTree("{\"evidence\":[]}"), "patch-file")));
        assertThrows(ServiceException.class, () -> service.patchInstance(new DynamicFormCommands.PatchInstance(
                ACTOR, 31L, 7, JsonUtils.parseTree("{\"unknown\":1}"), "patch-unknown")));

        verify(instanceMapper, never()).updateValueIfMatch(any());
    }

    @Test
    void staleCasAndDeniedPermissionHaveNoSuccessMutation() {
        when(templateMapper.selectForUpdate(any())).thenReturn(template(11L, 5, "DISABLED", 21L));
        assertThrows(ServiceException.class, () -> service.patchTemplate(new DynamicFormCommands.PatchTemplate(
                ACTOR, 11L, 4, DynamicFormCommands.FieldPatch.present("新名称"),
                DynamicFormCommands.FieldPatch.absent(), DynamicFormCommands.FieldPatch.absent(), "patch-stale")));
        verify(templateMapper, never()).updateIfMatch(any());

        reset(templateMapper);
        doThrow(exception(FORBIDDEN)).when(actionProjection)
                .require(ACTOR.userId(), DynamicFormActionProjection.TEMPLATE_PUBLISH);
        assertThrows(ServiceException.class, () -> service.setAvailability(new DynamicFormCommands.SetAvailability(
                ACTOR, 11L, 5, "ENABLED", "enable-forbidden")));
        verify(templateMapper, never()).updateIfMatch(any());
    }

    @Test
    void idempotencyConflictAndInProgressNeverExecuteBusinessWrite() {
        doReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.CONFLICT, null),
                        new PlatformCommandExecutionApi.ExecutionResult<>(
                                PlatformCommandExecutionApi.Decision.IN_PROGRESS, null))
                .when(commandExecutionApi).execute(any(), any(), any(), any(), any());

        assertThrows(ServiceException.class, () -> service.createRevision(new DynamicFormCommands.CreateRevision(
                ACTOR, 11L, 3, "revision-conflict")));
        assertThrows(ServiceException.class, () -> service.createRevision(new DynamicFormCommands.CreateRevision(
                ACTOR, 11L, 3, "revision-progress")));

        verifyNoInteractions(templateMapper, revisionMapper, instanceMapper);
    }

    @Test
    void completedIdempotencyReplayReturnsFrozenResponseWithoutBusinessWrite() {
        DynamicFormViews.Template replayed = new DynamicFormViews.Template(11L, "TEMPLATE_11", "模板11",
                "GENERAL", null, "DISABLED", 0, null, null, null, Set.of(), null, null);
        doReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, replayed))
                .when(commandExecutionApi).execute(any(), any(), any(), any(), any());

        DynamicFormViews.Template result = service.createTemplate(new DynamicFormCommands.CreateTemplate(
                ACTOR, "create-template-replay", "TEMPLATE_11", "模板11", "GENERAL", null));

        assertEquals(replayed, result);
        verifyNoInteractions(templateMapper, revisionMapper, instanceMapper);
        assertTrue(successFacts.isEmpty());
    }

    private void stubNewExecution() {
        lenient().when(commandExecutionApi.execute(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Object> operation = invocation.getArgument(3);
                    @SuppressWarnings("unchecked")
                    Function<Object, PlatformCommandExecutionApi.SuccessFacts> factory = invocation.getArgument(4);
                    Object response = operation.get();
                    successFacts.add(factory.apply(response));
                    return new PlatformCommandExecutionApi.ExecutionResult<>(
                            PlatformCommandExecutionApi.Decision.NEW, response);
                });
    }

    private DynamicFormTemplateDO template(Long id, int version, String availability, Long publishedRevisionId) {
        DynamicFormTemplateDO row = new DynamicFormTemplateDO();
        row.setId(id);
        row.setTenantId(ACTOR.tenantId());
        row.setTemplateCode("TEMPLATE_" + id);
        row.setTemplateName("模板" + id);
        row.setCategoryCode("GENERAL");
        row.setAvailabilityCode(availability);
        row.setCurrentPublishedRevisionId(publishedRevisionId);
        row.setVersion(version);
        return row;
    }

    private DynamicFormTemplateRevisionDO draftRevision(Long id, Long templateId, int no, int version) {
        DynamicFormTemplateRevisionDO row = revision(id, templateId, no, version);
        row.setStatusCode("DRAFT");
        row.setDraftMarker(1);
        return row;
    }

    private DynamicFormTemplateRevisionDO publishedRevision(Long id, Long templateId, int no, int version) {
        DynamicFormTemplateRevisionDO row = revision(id, templateId, no, version);
        row.setStatusCode("PUBLISHED");
        row.setDraftMarker(null);
        return row;
    }

    private DynamicFormTemplateRevisionDO revision(Long id, Long templateId, int no, int version) {
        DynamicFormTemplateRevisionDO row = new DynamicFormTemplateRevisionDO();
        row.setId(id);
        row.setTenantId(ACTOR.tenantId());
        row.setTemplateId(templateId);
        row.setRevisionNo(no);
        row.setFormConfJson(CONF);
        row.setFormRulesJson(RULES);
        row.setEngineCode(DynamicFormSchemaService.ENGINE_CODE);
        row.setDesignerVersion(DynamicFormSchemaService.DESIGNER_VERSION);
        row.setRendererVersion(DynamicFormSchemaService.RENDERER_VERSION);
        row.setVersion(version);
        return row;
    }

    private PlatformDynamicFormInstanceDO instance(Long id, Long revisionId, int version) {
        PlatformDynamicFormInstanceDO row = new PlatformDynamicFormInstanceDO();
        row.setId(id);
        row.setTenantId(ACTOR.tenantId());
        row.setTemplateId(11L);
        row.setTemplateRevisionId(revisionId);
        row.setTemplateRevisionNo(1);
        row.setCreatedBy(ACTOR.userId());
        row.setVersion(version);
        row.setValueJson("{}");
        return row;
    }

    private DynamicFormViews.Template templateView(DynamicFormTemplateDO row) {
        return templateView(row, null);
    }

    private DynamicFormViews.Template templateView(DynamicFormTemplateDO row,
                                                    DynamicFormTemplateRevisionDO draft) {
        DynamicFormViews.RevisionSummary draftSummary = draft == null ? null
                : new DynamicFormViews.RevisionSummary(draft.getId(), draft.getRevisionNo(), draft.getStatusCode(),
                draft.getVersion(), draft.getSourceRevisionId(), draft.getEngineCode(), draft.getDesignerVersion(),
                draft.getRendererVersion(), draft.getPublishedBy(), draft.getPublishedAt());
        return new DynamicFormViews.Template(row.getId(), row.getTemplateCode(), row.getTemplateName(),
                row.getCategoryCode(), row.getDescription(), row.getAvailabilityCode(), row.getVersion(),
                row.getCurrentPublishedRevisionId(), draftSummary, null, Set.of(), null, null);
    }

    private DynamicFormViews.Revision revisionView(DynamicFormTemplateRevisionDO row) {
        return new DynamicFormViews.Revision(row.getId(), row.getTemplateId(), row.getRevisionNo(),
                row.getStatusCode(), row.getSourceRevisionId(), JsonUtils.parseTree(row.getFormConfJson()),
                JsonUtils.parseTree(row.getFormRulesJson()), row.getEngineCode(), row.getDesignerVersion(),
                row.getRendererVersion(), row.getVersion(), row.getPublishedBy(), row.getPublishedAt(), Set.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> captureSuccessAudit() {
        ArgumentCaptor<Map<String, ?>> detail = ArgumentCaptor.forClass((Class) Map.class);
        verify(operationAuditApi).record(eq(ACTOR.tenantId()), eq(ACTOR.userId()), any(), any(), any(), any(),
                eq("SUCCESS"), detail.capture());
        return detail.getValue();
    }

    private void assertSafe(String value, String... forbidden) {
        for (String token : forbidden) {
            assertFalse(value.contains(token), () -> "审计不应包含: " + token);
        }
        assertNull(JsonUtils.parseTree(value).get("formConfJson"));
        assertNull(JsonUtils.parseTree(value).get("formRulesJson"));
    }
}
