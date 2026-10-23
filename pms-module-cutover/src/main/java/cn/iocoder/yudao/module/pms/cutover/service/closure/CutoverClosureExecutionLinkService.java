package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverCollectionEvidenceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.framework.adapter.CutoverCollectionSources;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionExecutionQueryApi;
import cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandExecutor;
import cn.iocoder.yudao.module.pms.cutover.service.support.CutoverWriteIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 将真实平台执行关联到 P6，包括没有产生日志的终止失败；只追加证据。 */
@Service
@RequiredArgsConstructor
public class CutoverClosureExecutionLinkService {
    private final CutoverTaskMapper tasks;
    private final CutoverClosureMapper closures;
    private final CutoverCollectionEvidenceMapper evidence;
    private final CutoverProjectScopePort scopes;
    private final ObjectProvider<CollectionExecutionQueryApi> executions;
    private final CutoverCommandExecutor commands;
    public record Command(Long tenant, Long actor, Long taskId, Long closureId, Long taskVersion,
                          Long closureVersion, String entry, Long executionId, CutoverWriteIntent intent) { }
    private record LinkInput(Long taskId, Long closureId, Long taskVersion, Long closureVersion,
                             String entry, Long executionId) { }
    public record LinkResult(Long closureId, Long executionId, String platformTaskId, String entry,
                             String status, String failureCategory, Long closureVersion) { }

    @Transactional(rollbackFor = Exception.class)
    public LinkResult link(Command command) {
        var task = tasks.selectById(command.taskId());
        if (task == null || !Objects.equals(task.getTenantId(), command.tenant())
                || !Objects.equals(task.getOwnerUserId(), command.actor()))
            throw new org.springframework.security.access.AccessDeniedException("无权关联当前割接任务的采集");
        var scope = scopes.inspect(command.actor(), task.getProjectId(), "ACTION_EDIT");
        if (scope == null || !scope.allowed() || !Objects.equals(scope.projectId(), task.getProjectId()))
            throw new org.springframework.security.access.AccessDeniedException("无项目编辑权限");
        return commands.execute(CutoverCommandExecutor.Write.<LinkResult>builder()
                .tenantId(command.tenant()).actorId(command.actor()).operationCode("CUTOVER_PLATFORM_EXECUTION_LINK")
                .scopeKey(String.valueOf(command.taskId())).aggregateType("CutoverClosure").intent(command.intent())
                .businessInput(new LinkInput(command.taskId(), command.closureId(), command.taskVersion(),
                        command.closureVersion(), command.entry(), command.executionId()))
                .responseType(LinkResult.class).operation(() -> linkNew(command))
                .resourceKey(value -> String.valueOf(value.closureId())).build());
    }

    private LinkResult linkNew(Command command) {
        String stage = CutoverCollectionSources.CLOSURE_STAGES.get(command.entry());
        if (stage == null) throw new IllegalArgumentException("割接采集环节无效");
        var execution = executions.getObject().get(new CollectionExecutionQueryApi.Query(command.tenant(), command.actor(), command.entry(), command.closureId(), command.executionId()));
        var source = execution.task();
        var task = tasks.selectForUpdate(new CutoverTaskRowQuery(command.tenant(), command.taskId()));
        if (task == null || !Objects.equals(task.getTenantId(), command.tenant())
                || !Objects.equals(task.getOwnerUserId(), command.actor())
                || !Objects.equals(source.projectId(), String.valueOf(task.getProjectId())))
            throw new IllegalArgumentException("割接任务已变化或无权关联采集");
        if (!"P6".equals(task.getCurrentStage()) || !"CLOSURE_IN_PROGRESS".equals(task.getTaskStatus()))
            throw new CutoverClosureApplicationException(CutoverClosureApplicationException.Code.STATE_CONFLICT,
                    CutoverClosureApplicationException.Reason.TASK_NOT_IN_P6, null, task.getVersion(), null,
                    "任务已离开割接闭环阶段，请刷新任务");
        if (!Objects.equals(command.taskVersion(), task.getVersion()))
            throw new CutoverClosureApplicationException(CutoverClosureApplicationException.Code.TASK_VERSION_STALE,
                    CutoverClosureApplicationException.Reason.TASK_VERSION_STALE, null, task.getVersion(), null,
                    "任务版本已变化，请刷新任务");
        var scope = scopes.inspect(command.actor(), task.getProjectId(), "ACTION_EDIT");
        if (scope == null || !scope.allowed() || !scopes.lockAndRevalidate(CutoverProjectScopePort.RevalidationQuery.builder()
                .actorId(command.actor())
                .projectId(task.getProjectId())
                .action("ACTION_EDIT")
                .expectedProjectScopeVersion(scope.projectScopeVersion())
                .build()).allowed())
            throw new org.springframework.security.access.AccessDeniedException("无项目编辑权限");
        var closure = closures.selectByTaskForUpdate(new CutoverClosureRowQuery(command.tenant(), command.taskId()));
        if (closure == null || !Objects.equals(closure.getId(), command.closureId()) || !"DRAFT".equals(closure.getStatusCode()))
            throw new CutoverClosureApplicationException(CutoverClosureApplicationException.Code.STATE_CONFLICT,
                    CutoverClosureApplicationException.Reason.CLOSURE_ALREADY_SUBMITTED, null, task.getVersion(),
                    closure == null ? null : closure.getVersion(), "闭环草稿已变化，请刷新任务");
        var existing = evidence.selectListByClosureForUpdate(new CutoverClosureChildrenQuery(command.tenant(), closure.getId())).stream()
                .filter(row -> Objects.equals(row.getCollectionTaskId(), source.platformTaskId())).toList();
        boolean hasDispatch = existing.stream().anyMatch(row -> row.getEvidenceTypeCode().startsWith("DISPATCH_"));
        boolean hasCallback = existing.stream().anyMatch(row -> row.getEvidenceTypeCode().startsWith("CALLBACK_"));
        if (existing.stream().anyMatch(row -> !Objects.equals(row.getTenantId(), command.tenant())
                || !Objects.equals(row.getTaskId(), task.getId()) || !Objects.equals(row.getProjectId(), task.getProjectId())
                || !Objects.equals(String.valueOf(row.getDeviceId()), source.deviceId())
                || !stage.equals(row.getCollectionStageCode()) || "DISPATCH_FAILED".equals(row.getEvidenceTypeCode())))
            throw new CutoverClosureApplicationException(CutoverClosureApplicationException.Code.OWNER_DATA_CORRUPTED,
                    CutoverClosureApplicationException.Reason.COLLECTION_EVIDENCE_MISMATCH, "CUT", task.getVersion(),
                    closure.getVersion(), "平台采集记录与已有割接证据不一致");
        boolean noLogFailure = source.fileVersionId() == null && Set.of("FAILED", "CANCELLED", "SECURITY_EXCEPTION").contains(source.status());
        if (hasDispatch && (!noLogFailure || hasCallback)) return new LinkResult(closure.getId(), execution.id(),
                source.platformTaskId(), command.entry(), source.status(), source.failureCategory(), closure.getVersion());
        if (!Objects.equals(closure.getVersion(), command.closureVersion()))
            throw new CutoverClosureApplicationException(CutoverClosureApplicationException.Code.CLOSURE_VERSION_STALE,
                    CutoverClosureApplicationException.Reason.CLOSURE_VERSION_STALE, null, task.getVersion(),
                    closure.getVersion(), "闭环版本已变化，请刷新重试");
        var now = LocalDateTime.now();
        for (String type : !hasDispatch ? noLogFailure ? List.of("DISPATCH_ACCEPTED", "CALLBACK_FAILED") : List.of("DISPATCH_ACCEPTED") : List.of("CALLBACK_FAILED")) {
            var row = new CutoverCollectionEvidenceDO(); row.setId(IdUtil.getSnowflakeNextId()); row.setTenantId(command.tenant());
            row.setClosureId(closure.getId()); row.setTaskId(task.getId()); row.setProjectId(task.getProjectId());
            row.setDeviceId(Long.valueOf(source.deviceId())); row.setCollectionStageCode(stage); row.setEvidenceTypeCode(type);
            row.setCollectionTaskId(source.platformTaskId()); row.setRecordedBy(command.actor()); row.setCreator(command.actor().toString());
            row.setOccurredAt(now); row.setCreateTime(now); row.setDeleted(false);
            if ("CALLBACK_FAILED".equals(type)) {
                row.setCallbackEventId("PLT-EXECUTION-FAILURE-" + execution.id());
                row.setResultRef("PLATFORM_EXECUTION:" + execution.id());
                row.setResultVersion("EXECUTION_STATE_" + source.status());
            }
            if (evidence.insert(row) != 1) throw new IllegalStateException("采集证据关联失败");
        }
        if (closures.advanceDraftVersionIfMatch(new CutoverClosureVersionUpdate(command.tenant(), closure.getId(), closure.getVersion(), command.actor().toString(), now)) != 1)
            throw new CutoverClosureApplicationException(CutoverClosureApplicationException.Code.CLOSURE_VERSION_STALE,
                    CutoverClosureApplicationException.Reason.CLOSURE_VERSION_STALE, null, task.getVersion(),
                    closure.getVersion(), "闭环版本已变化");
        return new LinkResult(closure.getId(), execution.id(), source.platformTaskId(), command.entry(),
                source.status(), source.failureCategory(), closure.getVersion() + 1);
    }
}
