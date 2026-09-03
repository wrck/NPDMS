package cn.iocoder.yudao.module.pms.platform.service.export;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.export.ExportBusinessDataQuery;
import cn.iocoder.yudao.module.pms.platform.api.export.ExportBusinessDataSnapshot;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportAuditDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.PlatformExportAuditMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.PlatformExportTaskMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query.ExportTaskDueQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query.ExportTaskStatusUpdate;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportTaskExecutionService {

    private static final int BATCH_SIZE = 20;
    private final PlatformExportTaskMapper taskMapper;
    private final PlatformExportAuditMapper auditMapper;
    private final ExportBusinessDataProviderRegistry providerRegistry;
    private final ExportFileWriter fileWriter;

    @Transactional(rollbackFor = Exception.class)
    public int executeRequested(Long tenantId) {
        List<PlatformExportTaskDO> tasks = taskMapper.selectRequestedForUpdate(new ExportTaskDueQuery(tenantId, BATCH_SIZE));
        for (PlatformExportTaskDO task : tasks) execute(task);
        return tasks.size();
    }

    private void execute(PlatformExportTaskDO task) {
        transition(task, "REQUESTED", "GENERATING", null, null, null);
        appendAudit(task, "GENERATION_STARTED", "{}");
        try {
            List<String> fields = JsonUtils.parseArray(task.getRequestedFieldsSnapshot(), String.class);
            ExportBusinessDataSnapshot data = providerRegistry.require(task.getOwnerContext(), task.getExportType())
                    .generate(new ExportBusinessDataQuery(task.getTenantId(), task.getActorUserId(),
                            task.getFilterSnapshot(), fields, Boolean.TRUE.equals(task.getIncludeFiles()),
                            task.getScopeVersion()));
            if (data != null && "REJECTED".equals(data.outcome())) {
                throw new ExportExecutionFailure("BUSINESS_SCOPE_REJECTED", false, true);
            }
            if (data != null && "TEMPORARILY_UNAVAILABLE".equals(data.outcome())) {
                throw new ExportExecutionFailure("PROVIDER_TEMPORARILY_UNAVAILABLE", true, false);
            }
            if (data == null || !"AVAILABLE".equals(data.outcome()) || data.scopeVersion() == null
                    || !data.scopeVersion().equals(task.getScopeVersion()) || data.rows() == null) {
                throw new ExportExecutionFailure("PROVIDER_CONTRACT_INVALID", false, false);
            }
            byte[] content = csv(data.allowedFields(), data.rows());
            ExportFileWriter.WrittenExportFile file = fileWriter.write(new ExportFileWriter.Command(
                    task.getTenantId(), task.getActorUserId(), task.getId(), task.getOperationId() + ":file",
                    task.getScopeVersion(), content));
            ExportTaskStatusUpdate update = new ExportTaskStatusUpdate(task.getTenantId(), task.getId(),
                    task.getVersion(), "GENERATING", "SUCCEEDED", (long) data.rows().size(), file.artifactId(),
                    file.versionNo(), file.referenceKey(), file.artifactVersion(), file.referenceVersion(),
                    file.availabilityVersion(), file.sha256(), LocalDateTime.now().plusHours(24), null, null,
                    String.valueOf(task.getActorUserId()));
            if (taskMapper.transition(update) != 1) throw new IllegalStateException("统一导出成功状态版本冲突");
            task.setVersion(task.getVersion() + 1);
            appendAudit(task, "SUCCEEDED", "{\"resultCount\":" + data.rows().size() + "}");
        } catch (ExportExecutionFailure failure) {
            fail(task, failure.code, failure.retryable, failure.rejected);
        } catch (ExportBusinessDataProviderRegistry.ProviderContractException failure) {
            fail(task, "PROVIDER_CONTRACT_INVALID", false, false);
        } catch (RuntimeException failure) {
            log.warn("[execute][统一导出任务({})执行失败]", task.getId(), failure);
            fail(task, "PROVIDER_TEMPORARILY_UNAVAILABLE", true, false);
        }
    }

    private void fail(PlatformExportTaskDO task, String code, boolean retryable, boolean rejected) {
        String target = rejected ? "REJECTED" : "FAILED";
        transition(task, "GENERATING", target, code, rejected ? null : retryable, null);
        appendAudit(task, target, "{\"failureCode\":\"" + code + "\"}");
    }

    private void transition(PlatformExportTaskDO task, String expected, String target,
                            String failureCode, Boolean failureRetryable, LocalDateTime expiresAt) {
        ExportTaskStatusUpdate update = new ExportTaskStatusUpdate(task.getTenantId(), task.getId(),
                task.getVersion(), expected, target, null, null, null, null, null, null, null, null, expiresAt,
                failureCode, failureRetryable, String.valueOf(task.getActorUserId()));
        if (taskMapper.transition(update) != 1) throw new IllegalStateException("统一导出状态版本冲突");
        task.setTaskStatus(target);
        task.setVersion(task.getVersion() + 1);
    }

    private void appendAudit(PlatformExportTaskDO task, String action, String detail) {
        PlatformExportAuditDO audit = new PlatformExportAuditDO();
        audit.setId(IdWorker.getId());
        audit.setTenantId(task.getTenantId());
        audit.setExportTaskId(task.getId());
        Integer next = auditMapper.selectNextSequenceForUpdate(task.getTenantId(), task.getId());
        audit.setAuditSequence(next == null ? 1 : next);
        audit.setActionCode(action);
        audit.setActorUserId(task.getActorUserId());
        audit.setDetailSnapshot(detail);
        audit.setOccurredAt(LocalDateTime.now());
        audit.setCreator(String.valueOf(task.getActorUserId()));
        audit.setCreateTime(LocalDateTime.now());
        if (auditMapper.insert(audit) != 1) throw new IllegalStateException("统一导出审计写入失败");
    }

    private byte[] csv(List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty()) throw new ExportExecutionFailure("PROVIDER_CONTRACT_INVALID", false, false);
        StringBuilder result = new StringBuilder();
        appendRow(result, headers);
        for (List<String> row : rows) {
            if (row == null || row.size() != headers.size()) {
                throw new ExportExecutionFailure("PROVIDER_CONTRACT_INVALID", false, false);
            }
            appendRow(result, row);
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendRow(StringBuilder target, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) target.append(',');
            String value = values.get(index) == null ? "" : values.get(index);
            target.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        target.append("\r\n");
    }

    public static final class ExportExecutionFailure extends RuntimeException {
        private final String code;
        private final boolean retryable;
        private final boolean rejected;

        public ExportExecutionFailure(String code, boolean retryable, boolean rejected) {
            super(code);
            this.code = code;
            this.retryable = retryable;
            this.rejected = rejected;
        }
    }
}
