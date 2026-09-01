package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureAttachmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverCollectionEvidenceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureAttachmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverCollectionEvidenceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.HandleClosureCollectionCallbackCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.LinkClosureManualResultCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.RequestClosureCollectionCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.AttachmentInput;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.ClosureContent;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.AttachmentPurpose;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.CollectionStage;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.DispatchOutcome;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.SavedCredential;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort.FileFactVersion;
import cn.iocoder.yudao.module.pms.cutover.service.closure.result.CutoverClosureCommandResult;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CutoverClosureApplicationServiceTest {

    @Test
    void completesCollectionCallbackAndManualFallbackWithControlledOwnerPorts() {
        CutoverTaskMapper taskMapper = mock(CutoverTaskMapper.class);
        CutoverApprovalInstanceMapper approvalMapper = mock(CutoverApprovalInstanceMapper.class);
        CutoverPlanRevisionMapper planMapper = mock(CutoverPlanRevisionMapper.class);
        CutoverClosureMapper closureMapper = mock(CutoverClosureMapper.class);
        CutoverClosureAttachmentMapper attachmentMapper = mock(CutoverClosureAttachmentMapper.class);
        CutoverCollectionEvidenceMapper evidenceMapper = mock(CutoverCollectionEvidenceMapper.class);
        CutoverTaskDeviceScopeMapper deviceScopeMapper = mock(CutoverTaskDeviceScopeMapper.class);
        CutoverTaskStageHistoryMapper stageHistoryMapper = mock(CutoverTaskStageHistoryMapper.class);
        CutoverTaskDO task = task();
        CutoverClosureDO closure = new CutoverClosureDO();
        closure.setId(400L); closure.setTenantId(1L); closure.setTaskId(100L); closure.setProjectId(10L);
        closure.setStatusCode("DRAFT"); closure.setVersion(0);
        CutoverTaskDeviceScopeDO device = new CutoverTaskDeviceScopeDO();
        device.setTenantId(1L); device.setCutoverTaskId(100L); device.setProjectId(10L); device.setDeviceId(11L);
        List<CutoverCollectionEvidenceDO> evidence = new ArrayList<>();
        List<CutoverClosureAttachmentDO> attachments = new ArrayList<>();

        when(taskMapper.selectForUpdate(any())).thenReturn(task);
        when(closureMapper.selectByTaskForUpdate(any())).thenReturn(closure);
        when(deviceScopeMapper.selectActiveByTaskForUpdate(any())).thenReturn(List.of(device));
        when(evidenceMapper.selectListByClosureForUpdate(any())).thenAnswer(ignored -> List.copyOf(evidence));
        when(evidenceMapper.insert(any(CutoverCollectionEvidenceDO.class))).thenAnswer(invocation -> {
            evidence.add(invocation.getArgument(0)); return 1;
        });
        when(attachmentMapper.insert(any(CutoverClosureAttachmentDO.class))).thenAnswer(invocation -> {
            attachments.add(invocation.getArgument(0)); return 1;
        });
        when(closureMapper.advanceDraftVersionIfMatch(any())).thenAnswer(invocation -> {
            var update = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureVersionUpdate)
                    invocation.getArgument(0);
            if (!closure.getVersion().equals(update.expectedVersion())) return 0;
            closure.setVersion(closure.getVersion() + 1); return 1;
        });

        Clock clock = Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC);
        CutoverClosureControlledPorts.Collections collections = new CutoverClosureControlledPorts.Collections(clock);
        CutoverClosureApplicationService service = new CutoverClosureApplicationService(taskMapper, approvalMapper,
                planMapper, closureMapper, attachmentMapper, evidenceMapper, deviceScopeMapper, stageHistoryMapper,
                new CutoverClosureControlledPorts.ProjectScopes(10L, 5L),
                new CutoverClosureControlledPorts.Files(), collections, new DirectPlatform(), clock);

        service.requestCollection(new RequestClosureCollectionCommand(1L, 9L, 100L, 7, 400L, 0,
                11L, CollectionStage.POST_COLLECTION, new SavedCredential(71L, 3L),
                "post-check", 2L, "collect-ok", "corr-collect-ok"));
        String acceptedTaskId = evidence.getFirst().getCollectionTaskId();
        service.handleCollectionCallback(new HandleClosureCollectionCallbackCommand(1L, 100L, 400L, 11L,
                CollectionStage.POST_COLLECTION, "callback-ok", acceptedTaskId, true,
                "result-ref", "result-v1", LocalDateTime.of(2026, 9, 2, 8, 1), "corr-callback-ok"));
        service.handleCollectionCallback(new HandleClosureCollectionCallbackCommand(1L, 100L, 400L, 11L,
                CollectionStage.POST_COLLECTION, "callback-ok", acceptedTaskId, true,
                "result-ref", "result-v1", LocalDateTime.of(2026, 9, 2, 8, 1), "corr-callback-replay"));

        collections.nextDispatch(DispatchOutcome.FAILED, "OWNER_REJECTED");
        service.requestCollection(new RequestClosureCollectionCommand(1L, 9L, 100L, 7, 400L, 2,
                11L, CollectionStage.TEST, new SavedCredential(71L, 3L),
                "test-check", 2L, "collect-failed", "corr-collect-failed"));
        String failedTaskId = evidence.get(2).getCollectionTaskId();
        service.linkManualResult(new LinkClosureManualResultCommand(1L, 9L, 100L, 7, 400L, 3,
                failedTaskId, 11L, CollectionStage.TEST,
                file(AttachmentPurpose.MANUAL_COLLECTION_RESULT, 503L, "ref-manual"),
                "manual-result", "corr-manual-result"));
        service.linkManualResult(new LinkClosureManualResultCommand(1L, 9L, 100L, 7, 400L, 3,
                failedTaskId, 11L, CollectionStage.TEST,
                file(AttachmentPurpose.MANUAL_COLLECTION_RESULT, 503L, "ref-manual"),
                "manual-result", "corr-manual-replay"));

        assertThat(closure.getVersion()).isEqualTo(4);
        assertThat(evidence).extracting(CutoverCollectionEvidenceDO::getEvidenceTypeCode)
                .containsExactly("DISPATCH_ACCEPTED", "CALLBACK_SUCCEEDED", "DISPATCH_FAILED", "MANUAL_UPLOAD");
        assertThat(evidence.getLast().getOriginalFailedCollectionTaskId()).isEqualTo(failedTaskId);
        assertThat(attachments).extracting(CutoverClosureAttachmentDO::getPurposeCode)
                .containsExactly("MANUAL_COLLECTION_RESULT");
    }

    @Test
    void createsThenSavesDraftWithFrozenSourcesAndControlledOwnerFacts() {
        CutoverTaskMapper taskMapper = mock(CutoverTaskMapper.class);
        CutoverApprovalInstanceMapper approvalMapper = mock(CutoverApprovalInstanceMapper.class);
        CutoverPlanRevisionMapper planMapper = mock(CutoverPlanRevisionMapper.class);
        CutoverClosureMapper closureMapper = mock(CutoverClosureMapper.class);
        CutoverClosureAttachmentMapper attachmentMapper = mock(CutoverClosureAttachmentMapper.class);
        CutoverCollectionEvidenceMapper evidenceMapper = mock(CutoverCollectionEvidenceMapper.class);
        CutoverTaskDeviceScopeMapper deviceScopeMapper = mock(CutoverTaskDeviceScopeMapper.class);
        CutoverTaskStageHistoryMapper stageHistoryMapper = mock(CutoverTaskStageHistoryMapper.class);
        CutoverTaskDO task = task();
        CutoverPlanRevisionDO plan = plan();
        CutoverApprovalInstanceDO approval = approval();
        AtomicReference<CutoverClosureDO> closure = new AtomicReference<>();
        List<CutoverClosureAttachmentDO> attachments = new ArrayList<>();

        when(taskMapper.selectById(100L)).thenReturn(task);
        when(taskMapper.selectForUpdate(any())).thenReturn(task);
        when(planMapper.selectCurrent(any())).thenReturn(plan);
        when(planMapper.selectByIdForUpdate(any())).thenReturn(plan);
        when(approvalMapper.selectCurrentByTask(any())).thenReturn(approval);
        when(approvalMapper.selectByIdForUpdate(any())).thenReturn(approval);
        when(closureMapper.selectByTask(any())).thenAnswer(ignored -> closure.get());
        when(closureMapper.selectByTaskForUpdate(any())).thenAnswer(ignored -> closure.get());
        when(closureMapper.insert(any(CutoverClosureDO.class))).thenAnswer(invocation -> {
            closure.set(invocation.getArgument(0));
            return 1;
        });
        when(closureMapper.updateDraftIfMatch(any())).thenAnswer(invocation -> {
            var update = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureDraftUpdate)
                    invocation.getArgument(0);
            if (closure.get().getVersion().equals(update.expectedVersion())) {
                closure.get().setPreCheckNormal(update.preCheckNormal());
                closure.get().setExecutionNormal(update.executionNormal());
                closure.get().setTestNormal(update.testNormal());
                closure.get().setRollbackOccurred(update.rollbackOccurred());
                closure.get().setLegacyItems(update.legacyItems());
                closure.get().setVersion(update.expectedVersion() + 1);
                return 1;
            }
            return 0;
        });
        when(attachmentMapper.selectListByClosureForUpdate(any())).thenAnswer(ignored -> List.copyOf(attachments));
        when(attachmentMapper.deleteDraftRows(any())).thenAnswer(ignored -> {
            int count = attachments.size(); attachments.clear(); return count;
        });
        when(attachmentMapper.insert(any(CutoverClosureAttachmentDO.class))).thenAnswer(invocation -> {
            attachments.add(invocation.getArgument(0)); return 1;
        });

        DirectPlatform platform = new DirectPlatform();
        CutoverClosureApplicationService service = new CutoverClosureApplicationService(taskMapper, approvalMapper,
                planMapper, closureMapper, attachmentMapper, evidenceMapper, deviceScopeMapper, stageHistoryMapper,
                new CutoverClosureControlledPorts.ProjectScopes(10L, 5L),
                new CutoverClosureControlledPorts.Files(), new CutoverClosureControlledPorts.Collections(
                Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC)), platform,
                Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC));

        CutoverClosureCommandResult created = service.save(command(null, "create-1", draft("first", oneAttachment())));
        CutoverClosureCommandResult saved = service.save(command(0, "save-1", draft("second", twoAttachments())));

        assertThat(created.closureVersion()).isZero();
        assertThat(saved.closureVersion()).isEqualTo(1);
        assertThat(closure.get().getApprovalInstanceId()).isEqualTo(200L);
        assertThat(closure.get().getPlanRevisionId()).isEqualTo(300L);
        assertThat(closure.get().getTaskVersionAtP6()).isEqualTo(7);
        assertThat(closure.get().getDeviceScopeWatermark()).isEqualTo("{\"devices\":[11]}");
        assertThat(closure.get().getLegacyItems()).isEqualTo("second");
        assertThat(attachments).extracting(CutoverClosureAttachmentDO::getPurposeCode)
                .containsExactly("IMPLEMENTATION_COMMITMENT", "POST_COLLECTION_CHECKLIST");
        assertThat(platform.facts).hasSize(2).allSatisfy(fact -> assertThat(fact.correlationId()).isNotBlank());
    }

    private static SaveCutoverClosureCommand command(Integer version, String key, ClosureContent content) {
        return new SaveCutoverClosureCommand(1L, 9L, 100L, 7, version, content, key, "corr-" + key);
    }

    private static ClosureContent draft(String legacy, List<AttachmentInput> attachments) {
        return new ClosureContent(true, null, true, null, true, null,
                false, null, null, legacy, null, attachments);
    }

    private static List<AttachmentInput> oneAttachment() {
        return List.of(file(AttachmentPurpose.POST_COLLECTION_CHECKLIST, 501L, "ref-check"));
    }

    private static List<AttachmentInput> twoAttachments() {
        return List.of(file(AttachmentPurpose.POST_COLLECTION_CHECKLIST, 501L, "ref-check"),
                file(AttachmentPurpose.IMPLEMENTATION_COMMITMENT, 502L, "ref-commit"));
    }

    private static AttachmentInput file(AttachmentPurpose purpose, Long artifactId, String reference) {
        return new AttachmentInput(purpose, artifactId, 1, reference,
                new FileFactVersion(1, 2, 3), 4L, "a".repeat(64));
    }

    private static CutoverTaskDO task() {
        CutoverTaskDO row = new CutoverTaskDO();
        row.setId(100L); row.setTenantId(1L); row.setProjectId(10L); row.setTaskOrigin("NEW_PLATFORM");
        row.setCurrentStage("P6"); row.setTaskStatus("CLOSURE_IN_PROGRESS"); row.setOwnerUserId(9L);
        row.setProjectScopeVersion(5L); row.setDeviceScopeWatermark("{\"devices\":[11]}"); row.setVersion(7);
        return row;
    }

    private static CutoverPlanRevisionDO plan() {
        CutoverPlanRevisionDO row = new CutoverPlanRevisionDO();
        row.setId(300L); row.setTenantId(1L); row.setCutoverTaskId(100L); row.setRevisionNo(2);
        row.setStatusCode("SUBMITTED"); row.setCurrentMarker(1); row.setApprovalInstanceId(200L);
        row.setApprovalVersion(4); row.setVersion(6); return row;
    }

    private static CutoverApprovalInstanceDO approval() {
        CutoverApprovalInstanceDO row = new CutoverApprovalInstanceDO();
        row.setId(200L); row.setTenantId(1L); row.setTaskId(100L); row.setProjectId(10L);
        row.setPlanRevisionId(300L); row.setPlanRevisionNo(2); row.setStatusCode("APPROVED"); row.setVersion(4);
        return row;
    }

    private static final class DirectPlatform implements PlatformCommandExecutionApi {
        private final List<SuccessFacts> facts = new ArrayList<>();
        private final Map<String, Cached> completed = new HashMap<>();

        @Override
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String digest, Class<T> type,
                                              Supplier<T> operation, Function<T, SuccessFacts> factsFactory) {
            String key = scope.scopeCode() + ':' + scope.key();
            Cached cached = completed.get(key);
            if (cached != null) return cached.digest.equals(digest)
                    ? new ExecutionResult<>(Decision.REPLAY_COMPLETED, type.cast(cached.value))
                    : new ExecutionResult<>(Decision.CONFLICT, null);
            T value = operation.get(); facts.add(factsFactory.apply(value)); completed.put(key, new Cached(digest, value));
            return new ExecutionResult<>(Decision.NEW, value);
        }

        private record Cached(String digest, Object value) {
        }
    }
}
