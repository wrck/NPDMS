package cn.iocoder.yudao.module.pms.platform.service.export;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.export.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportAuditDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.PlatformExportAuditMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.PlatformExportTaskMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query.ExportTaskActorQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query.ExportTaskIdentityQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query.ExportTaskRetryUpdate;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class ExportTaskApiImpl implements ExportTaskApi {

    private static final String AVAILABLE = "AVAILABLE";
    private static final String REQUESTED = "REQUESTED";
    private static final String FAILED = "FAILED";

    @Resource
    private PlatformExportTaskMapper taskMapper;
    @Resource
    private PlatformExportAuditMapper auditMapper;
    @Resource
    private ExportBusinessDataProviderRegistry providerRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExportTaskFact request(ExportTaskRequestCommand command) {
        validateRequest(command);
        ExportBusinessDataSnapshot snapshot = providerRegistry.require(command.ownerContext(), command.exportType())
                .inspect(new ExportBusinessDataQuery(command.tenantId(), command.actorUserId(),
                        command.normalizedFilter(), List.copyOf(command.requestedFields()),
                        command.includeFiles(), null));
        requireAvailable(snapshot);
        String normalizedFields = JsonUtils.toJsonString(snapshot.allowedFields());
        String digest = digest(JsonUtils.toJsonString(List.of(command.ownerContext(), command.exportType(),
                snapshot.normalizedFilter(), normalizedFields, snapshot.includeFiles(), snapshot.scopeVersion())));
        ExportTaskIdentityQuery identity = new ExportTaskIdentityQuery(command.tenantId(), command.ownerContext(),
                command.exportType(), command.actorUserId(), command.operationId());
        PlatformExportTaskDO existing = taskMapper.selectByIdentity(identity);
        if (existing != null) return replay(existing, digest);

        LocalDateTime now = LocalDateTime.now();
        PlatformExportTaskDO row = new PlatformExportTaskDO();
        row.setId(IdWorker.getId());
        row.setTenantId(command.tenantId());
        row.setOwnerContext(command.ownerContext());
        row.setExportType(command.exportType());
        row.setOperationId(command.operationId());
        row.setRequestDigest(digest);
        row.setActorUserId(command.actorUserId());
        row.setFilterSnapshot(snapshot.normalizedFilter());
        row.setScopeSnapshot(snapshot.scopeSnapshot());
        row.setRequestedFieldsSnapshot(normalizedFields);
        row.setIncludeFiles(snapshot.includeFiles());
        row.setScopeVersion(snapshot.scopeVersion());
        row.setTaskStatus(REQUESTED);
        row.setRetryCount(0);
        row.setVersion(0);
        row.setCreator(String.valueOf(command.actorUserId()));
        row.setUpdater(String.valueOf(command.actorUserId()));
        row.setCreateTime(now);
        row.setUpdateTime(now);
        if (taskMapper.insertIfAbsent(row) == 0) return replay(taskMapper.selectByIdentity(identity), digest);
        appendAudit(row, "REQUESTED", command.actorUserId(), "{}");
        return fact(row);
    }

    @Override
    public ExportTaskFact getFact(ExportTaskFactQuery query) {
        if (query == null || query.tenantId() == null || query.actorUserId() == null || query.taskId() == null) {
            throw new IllegalArgumentException("统一导出查询参数不完整");
        }
        PlatformExportTaskDO row = taskMapper.selectByActor(
                new ExportTaskActorQuery(query.tenantId(), query.actorUserId(), query.taskId()));
        if (row == null) throw new IllegalStateException("统一导出任务不存在或不可见");
        revalidateCurrentScope(row);
        return fact(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExportTaskFact retry(ExportTaskRetryCommand command) {
        if (command == null || command.tenantId() == null || command.actorUserId() == null
                || command.taskId() == null || command.expectedVersion() == null || command.expectedVersion() < 0) {
            throw new IllegalArgumentException("统一导出重试参数不完整");
        }
        PlatformExportTaskDO row = taskMapper.selectByActorForUpdate(
                new ExportTaskActorQuery(command.tenantId(), command.actorUserId(), command.taskId()));
        if (row == null) throw new IllegalStateException("统一导出任务不存在或不可见");
        if (!FAILED.equals(row.getTaskStatus()) || !Boolean.TRUE.equals(row.getFailureRetryable())
                || !command.expectedVersion().equals(row.getVersion())) {
            throw new IllegalStateException("统一导出任务不可重试或版本冲突");
        }
        List<String> fields = JsonUtils.parseArray(row.getRequestedFieldsSnapshot(), String.class);
        ExportBusinessDataSnapshot snapshot = providerRegistry.require(row.getOwnerContext(), row.getExportType())
                .inspect(new ExportBusinessDataQuery(row.getTenantId(), row.getActorUserId(), row.getFilterSnapshot(),
                        fields, Boolean.TRUE.equals(row.getIncludeFiles()), row.getScopeVersion()));
        requireAvailable(snapshot);
        if (!row.getScopeVersion().equals(snapshot.scopeVersion())) {
            throw new IllegalStateException("统一导出范围版本已变化");
        }
        if (taskMapper.retryFailed(new ExportTaskRetryUpdate(row.getTenantId(), row.getId(), row.getVersion(),
                String.valueOf(command.actorUserId()))) != 1) {
            throw new IllegalStateException("统一导出任务重试版本冲突");
        }
        row.setTaskStatus(REQUESTED);
        row.setFailureCode(null);
        row.setFailureRetryable(null);
        row.setRetryCount(row.getRetryCount() + 1);
        row.setVersion(row.getVersion() + 1);
        appendAudit(row, "RETRY_REQUESTED", command.actorUserId(), "{}");
        return fact(row);
    }

    private void appendAudit(PlatformExportTaskDO task, String action, Long actor, String detail) {
        PlatformExportAuditDO audit = new PlatformExportAuditDO();
        audit.setId(IdWorker.getId());
        audit.setTenantId(task.getTenantId());
        audit.setExportTaskId(task.getId());
        Integer next = auditMapper.selectNextSequenceForUpdate(task.getTenantId(), task.getId());
        audit.setAuditSequence(next == null ? 1 : next);
        audit.setActionCode(action);
        audit.setActorUserId(actor);
        audit.setDetailSnapshot(detail);
        audit.setOccurredAt(LocalDateTime.now());
        audit.setCreator(String.valueOf(actor));
        audit.setCreateTime(LocalDateTime.now());
        if (auditMapper.insert(audit) != 1) throw new IllegalStateException("统一导出审计写入失败");
    }

    private ExportTaskFact replay(PlatformExportTaskDO row, String digest) {
        if (row == null) throw new IllegalStateException("统一导出幂等记录读取失败");
        if (!digest.equals(row.getRequestDigest())) throw new IllegalStateException("统一导出幂等键载荷冲突");
        return fact(row);
    }

    private ExportTaskFact fact(PlatformExportTaskDO row) {
        return new ExportTaskFact(row.getId(), row.getOwnerContext(), row.getExportType(), row.getTaskStatus(),
                Boolean.TRUE.equals(row.getFailureRetryable()), row.getRetryCount(), row.getVersion(),
                row.getResultCount(), row.getArtifactId(), row.getFileVersionNo(), row.getExpiresAt());
    }

    private void revalidateCurrentScope(PlatformExportTaskDO row) {
        List<String> fields = JsonUtils.parseArray(row.getRequestedFieldsSnapshot(), String.class);
        ExportBusinessDataSnapshot snapshot = providerRegistry.require(row.getOwnerContext(), row.getExportType())
                .inspect(new ExportBusinessDataQuery(row.getTenantId(), row.getActorUserId(), row.getFilterSnapshot(),
                        fields, Boolean.TRUE.equals(row.getIncludeFiles()), row.getScopeVersion()));
        requireAvailable(snapshot);
        if (!row.getScopeVersion().equals(snapshot.scopeVersion())) {
            throw new IllegalStateException("统一导出范围版本已变化");
        }
    }

    private void validateRequest(ExportTaskRequestCommand command) {
        if (command == null || command.tenantId() == null || command.actorUserId() == null
                || blank(command.operationId()) || blank(command.ownerContext()) || blank(command.exportType())
                || blank(command.normalizedFilter()) || command.requestedFields() == null
                || command.requestedFields().isEmpty() || command.requestedFields().stream().anyMatch(this::blank)) {
            throw new IllegalArgumentException("统一导出申请参数不完整");
        }
    }

    private void requireAvailable(ExportBusinessDataSnapshot snapshot) {
        if (snapshot == null || !AVAILABLE.equals(snapshot.outcome()) || blank(snapshot.normalizedFilter())
                || blank(snapshot.scopeSnapshot()) || snapshot.allowedFields() == null
                || snapshot.allowedFields().isEmpty() || snapshot.scopeVersion() == null) {
            throw new IllegalStateException("统一导出业务范围不可用");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }
}
