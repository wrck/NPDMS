package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionExecutionProfile;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionExecutionRows;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

/** SRV authorizes the source; PLT independently authorizes the actual credential for each collection. */
@Service
@RequiredArgsConstructor
public class InspectionOnlineAuthorization {
    private final InspectionAccessService access;
    private final InspectionExecutionMapper executions;
    private final InspectionAttemptMapper attempts;
    private final InspectionTaskDeviceMapper devices;
    private final InspectionTaskRuleSnapshotMapper snapshots;
    private final InspectionRuleQueryService rules;
    private final InspectionCollectionReceiptMapper receipts;

    public record Target(Long tenantId, Long taskId, Long attemptId, Long deviceId) { }
    public record Frozen(String commandText, String digest) { }
    public record Bound(InspectionTaskDO task, InspectionExecutionDO execution, InspectionTaskDeviceDO device) { }
    public record Connection(String host, Integer port, String protocol, String username,
                             Long credentialId, Long credentialVersion, Long grantId) {
        public Connection {
            host = host == null ? null : host.trim().toLowerCase(Locale.ROOT);
            username = username == null ? null : username.trim();
        }
    }
    public record Precheck(InspectionExecutionDO execution, InspectionExecutionDO precheck, Connection actual, LocalDateTime now) { }

    public Bound authorize(CollectionSourceAdapter.AuthorizationQuery query) {
        require(query != null && Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()) && query.actorId() != null,
                "巡检采集身份无效");
        var execution = executions.selectById(query.objectId());
        require(execution != null && Objects.equals(query.tenantId(), execution.getTenantId())
                && Set.of("PRECHECK", "COLLECT").contains(execution.getPurposeCode()), "巡检在线执行记录不存在");
        var context = InspectionRequestContext.builder().tenantId(query.tenantId()).actorId(query.actorId()).build();
        String permission = query.access() == CollectionSourceAdapter.Access.EXECUTE || query.access() == CollectionSourceAdapter.Access.CANCEL
                ? "pms:inspection-task:execute" : "pms:inspection-task:query";
        var task = access.task(new InspectionAccessService.TaskAccess(context, execution.getTaskId(), permission, false, false));
        if (query.access() == CollectionSourceAdapter.Access.EXECUTE) {
            task = access.task(new InspectionAccessService.TaskAccess(InspectionRequestContext.builder().tenantId(query.tenantId())
                    .actorId(query.actorId()).expectedVersion(task.getVersion()).build(), task.getId(), permission, true, false));
            execution = executions.selectById(query.objectId());
            require(Objects.equals(execution.getVersion(), query.expectedVersion()), "在线执行记录已更新，请恢复原请求或刷新");
            require(Objects.equals(execution.getSubmittedBy(), query.actorId()) && "PENDING".equals(execution.getStatusCode())
                    && execution.getCollectionExecutionId() == null && !Boolean.TRUE.equals(execution.getCredentialSnapshotRecorded()),
                    "该在线请求已下发或操作者已变化，请查看原执行记录");
            require(Objects.equals(task.getCurrentAttemptId(), execution.getAttemptId())
                    && Set.of("PRECHECK_PENDING", "EXECUTING").contains(task.getStatusCode()), "当前阶段或执行尝试已变化");
            var attempt = attempts.selectById(execution.getAttemptId());
            require(attempt != null && Objects.equals(attempt.getTenantId(), query.tenantId()) && "ONLINE".equals(attempt.getMode())
                    && "ACTIVE".equals(attempt.getStatusCode()), "当前尝试不是有效的在线巡检");
            if ("COLLECT".equals(execution.getPurposeCode())) {
                require("EXECUTING".equals(task.getStatusCode()), "请先完成全部设备预检");
                requirePrecheck(new Precheck(execution, executions.selectById(execution.getPrecheckExecutionId()), null, LocalDateTime.now()));
            }
        }
        require(query.deviceId() == null || Objects.equals(query.deviceId(), execution.getDeviceId()), "采集设备与巡检记录不一致");
        Long deviceId = execution.getDeviceId();
        var device = devices.selectForTask(new InspectionTaskRows(query.tenantId(), task.getId())).stream()
                .filter(d -> Objects.equals(d.getDeviceId(), deviceId)).findFirst().orElseThrow(
                        () -> new InspectionException(InspectionException.Reason.INVALID_REQUEST, "巡检设备不存在"));
        return new Bound(task, execution, device);
    }

    public CollectionExecutionProfile profile(InspectionExecutionDO execution) {
        var frozen = frozen(new Target(execution.getTenantId(), execution.getTaskId(), execution.getAttemptId(), execution.getDeviceId()));
        require(Objects.equals(frozen.digest(), execution.getRuleDigest()), "巡检冻结规则内容不一致，已拒绝执行");
        return new CollectionExecutionProfile("PRECHECK".equals(execution.getPurposeCode()) ? "PRECHECK" : "INSPECTION",
                "PRECHECK".equals(execution.getPurposeCode()) ? "" : frozen.commandText());
    }

    public Frozen frozen(Target target) {
        var selected = snapshots.selectForTask(new InspectionTaskRows(target.tenantId(), target.taskId())).stream()
                .filter(s -> Objects.equals(s.getAttemptId(), target.attemptId()) && Objects.equals(s.getDeviceId(), target.deviceId()))
                .sorted(Comparator.comparing(InspectionTaskRuleSnapshotDO::getId)).toList();
        require(!selected.isEmpty(), "本设备尚未冻结巡检规则");
        var commandLines = new ArrayList<String>();
        for (var snapshot : selected) for (var command : rules.detail(snapshot.getRuleRevisionId()).getCommands().stream()
                .sorted(Comparator.comparingInt(c -> c.executionOrder())).toList()) {
            command.commandContent().replace('\r', '\n').lines().map(String::strip).filter(s -> !s.isEmpty()).forEach(commandLines::add);
        }
        String script = String.join("\n", commandLines);
        require(!script.isBlank() && script.length() <= 65536, "冻结命令清单为空或超过单次采集资源限制");
        return new Frozen(script, DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(
                selected.stream().map(InspectionTaskRuleSnapshotDO::getRuleRevisionId).toList(), script))));
    }

    public void authorizeConnection(CollectionSourceAdapter.PreparedConnection query) {
        // PLT invokes this inside the same transaction, before creating its collection task.
        var bound = authorize(query.authorization());
        var row = bound.execution();
        require(Objects.equals(row.getCredentialId(), query.credentialId()) && Objects.equals(row.getProtocol(), query.protocol()),
                "连接认证方式或协议已变化，请重新创建预检请求");
        var actual = new Connection(query.host(), query.port(), query.protocol(), query.username(), query.credentialId(), query.credentialVersion(), query.grantId());
        require(actual.host() != null && actual.port() != null && actual.username() != null, "实际连接上下文不完整");
        if (row.getCredentialId() == null) require(connection(row).equals(actual), "临时连接上下文与已登记请求不同");
        if ("COLLECT".equals(row.getPurposeCode())) requirePrecheck(new Precheck(row, executions.selectById(row.getPrecheckExecutionId()), actual, LocalDateTime.now()));
        row.setHost(actual.host()); row.setPort(actual.port()); row.setUsername(actual.username());
        row.setCredentialVersion(actual.credentialVersion()); row.setCredentialGrantId(actual.grantId());
        row.setCredentialSnapshotRecorded(true);
        require(executions.updateById(row) == 1, "巡检连接快照并发更新，请刷新");
    }

    public static Connection connection(InspectionExecutionDO row) {
        return new Connection(row.getHost(), row.getPort(), row.getProtocol(), row.getUsername(),
                row.getCredentialId(), row.getCredentialVersion(), row.getCredentialGrantId());
    }

    public void requirePrecheck(Precheck input) {
        verifyPrecheck(input);
        if (!hasSelectedResult(input.precheck())) throw new InspectionException(InspectionException.Reason.PRECHECK_REQUIRED,
                "预检采集结果版本已变化或缺少回传凭据，原版本保留，请重新预检");
    }

    /** Called while holding the task write lock; current reads also see callbacks committed while waiting. */
    public boolean hasSelectedResult(InspectionExecutionDO precheck) {
        if (precheck.getCollectionResultVersion() == null) return false;
        var recorded = receipts.selectForExecutionForUpdate(new InspectionExecutionRows(precheck.getTenantId(), precheck.getId()));
        return !recorded.isEmpty() && recorded.stream().allMatch(r -> Objects.equals(r.getResultVersion(), precheck.getCollectionResultVersion().longValue()));
    }

    public static void verifyPrecheck(Precheck input) {
        var row = input.execution(); var previous = input.precheck();
        boolean valid = previous != null && "PRECHECK".equals(previous.getPurposeCode()) && "PASSED".equals(previous.getStatusCode())
                && Objects.equals(row.getTenantId(), previous.getTenantId()) && Objects.equals(row.getTaskId(), previous.getTaskId())
                && Objects.equals(row.getAttemptId(), previous.getAttemptId()) && Objects.equals(row.getDeviceId(), previous.getDeviceId())
                && Objects.equals(row.getSubmittedBy(), previous.getSubmittedBy()) && Objects.equals(row.getRuleDigest(), previous.getRuleDigest())
                && previous.getPrecheckExpiresAt() != null && previous.getPrecheckExpiresAt().isAfter(input.now())
                && Boolean.TRUE.equals(previous.getCredentialSnapshotRecorded());
        if (valid && input.actual() != null) valid = connection(previous).equals(input.actual());
        if (!valid) throw new InspectionException(InspectionException.Reason.PRECHECK_REQUIRED, "预检已过期或设备、操作者、规则、连接认证上下文已变化，请重新预检");
    }
}
