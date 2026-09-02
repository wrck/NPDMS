package cn.iocoder.yudao.module.pms.platform.service.export;

import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportAuditDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.PlatformExportAuditMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.PlatformExportTaskMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query.ExportTaskDueQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query.ExportTaskStatusUpdate;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportTaskExpirationService {

    private static final int BATCH_SIZE = 20;
    private final PlatformExportTaskMapper taskMapper;
    private final PlatformExportAuditMapper auditMapper;
    private final ExportFileWriter fileWriter;

    @Transactional(rollbackFor = Exception.class)
    public int expireDue(Long tenantId) {
        List<PlatformExportTaskDO> tasks = taskMapper.selectExpiredForUpdate(new ExportTaskDueQuery(tenantId, BATCH_SIZE));
        for (PlatformExportTaskDO task : tasks) expire(task);
        return tasks.size();
    }

    private void expire(PlatformExportTaskDO task) {
        ExportFileWriter.WrittenExportFile file = new ExportFileWriter.WrittenExportFile(task.getArtifactId(),
                task.getFileVersionNo(), task.getReferenceKey(), task.getArtifactVersion(), task.getReferenceVersion(),
                task.getAvailabilityVersion(), task.getFileHash());
        fileWriter.expire(new ExportFileWriter.Command(task.getTenantId(), task.getActorUserId(), task.getId(),
                task.getOperationId() + ":expire", task.getScopeVersion(), new byte[0]), file);
        ExportTaskStatusUpdate update = new ExportTaskStatusUpdate(task.getTenantId(), task.getId(), task.getVersion(),
                "SUCCEEDED", "EXPIRED", task.getResultCount(), task.getArtifactId(), task.getFileVersionNo(),
                task.getReferenceKey(), task.getArtifactVersion(), task.getReferenceVersion(),
                task.getAvailabilityVersion(), task.getFileHash(), task.getExpiresAt(), null, null,
                String.valueOf(task.getActorUserId()));
        if (taskMapper.transition(update) != 1) throw new IllegalStateException("统一导出到期状态版本冲突");
        PlatformExportAuditDO audit = new PlatformExportAuditDO();
        audit.setId(IdWorker.getId());
        audit.setTenantId(task.getTenantId());
        audit.setExportTaskId(task.getId());
        Integer next = auditMapper.selectNextSequenceForUpdate(task.getTenantId(), task.getId());
        audit.setAuditSequence(next == null ? 1 : next);
        audit.setActionCode("EXPIRED");
        audit.setActorUserId(task.getActorUserId());
        audit.setDetailSnapshot("{}");
        audit.setOccurredAt(LocalDateTime.now());
        audit.setCreator("exportFileExpirationJob");
        audit.setCreateTime(LocalDateTime.now());
        if (auditMapper.insert(audit) != 1) throw new IllegalStateException("统一导出到期审计写入失败");
    }
}
