package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverCollectionEvidenceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.framework.runtime.CutoverCollectionSources;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionExecutionQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
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
    private final OperationAuditApi audit;
    public record Command(Long tenant, Long actor, Long taskId, Long closureId, Long taskVersion,
                          Long closureVersion, String entry, Long executionId, String correlationId) { }

    @Transactional(rollbackFor = Exception.class)
    public void link(Command command) {
        String stage = CutoverCollectionSources.CLOSURE_STAGES.get(command.entry());
        if (stage == null) throw new IllegalArgumentException("割接采集环节无效");
        var execution = executions.getObject().get(new CollectionExecutionQueryApi.Query(command.tenant(), command.actor(), command.entry(), command.closureId(), command.executionId()));
        var source = execution.task();
        var task = tasks.selectForUpdate(new CutoverTaskRowQuery(command.tenant(), command.taskId()));
        if (task == null || !Objects.equals(task.getOwnerUserId(), command.actor()) || !"P6".equals(task.getCurrentStage())
                || !"CLOSURE_IN_PROGRESS".equals(task.getTaskStatus()) || !Objects.equals(command.taskVersion(), task.getVersion())
                || !Objects.equals(source.projectId(), String.valueOf(task.getProjectId())))
            throw new IllegalArgumentException("割接任务已变化或无权关联采集");
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
            throw new IllegalArgumentException("闭环草稿已变化");
        var existing = evidence.selectListByClosureForUpdate(new CutoverClosureChildrenQuery(command.tenant(), closure.getId())).stream()
                .filter(row -> Objects.equals(row.getCollectionTaskId(), source.platformTaskId())).toList();
        boolean hasDispatch = existing.stream().anyMatch(row -> row.getEvidenceTypeCode().startsWith("DISPATCH_"));
        boolean hasCallback = existing.stream().anyMatch(row -> row.getEvidenceTypeCode().startsWith("CALLBACK_"));
        boolean noLogFailure = source.fileVersionId() == null && Set.of("FAILED", "CANCELLED", "SECURITY_EXCEPTION").contains(source.status());
        if (hasDispatch && (!noLogFailure || hasCallback)) return;
        if (!Objects.equals(closure.getVersion(), command.closureVersion())) throw new IllegalArgumentException("闭环版本已变化，请刷新重试");
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
            throw new IllegalArgumentException("闭环版本已变化");
        Map<String, Object> detail = new LinkedHashMap<>(); detail.put("executionId", execution.id()); detail.put("platformTaskId", source.platformTaskId());
        detail.put("entry", command.entry()); detail.put("status", source.status()); detail.put("failureCategory", source.failureCategory());
        audit.record(command.tenant(), command.actor(), command.correlationId(), "CUTOVER_PLATFORM_EXECUTION_LINK", "CutoverClosure", closure.getId().toString(), "SUCCESS", detail);
    }
}
