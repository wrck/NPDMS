package cn.iocoder.yudao.module.pms.platform.service.export;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.export.ExportBusinessDataQuery;
import cn.iocoder.yudao.module.pms.platform.api.export.ExportBusinessDataSnapshot;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileAccessTicketRespVO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportAuditDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.PlatformExportAuditMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.PlatformExportTaskMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query.ExportTaskActorQuery;
import cn.iocoder.yudao.module.pms.platform.service.file.FileAccessTicketService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportTaskAccessService {

    private final PlatformExportTaskMapper taskMapper;
    private final PlatformExportAuditMapper auditMapper;
    private final ExportBusinessDataProviderRegistry providerRegistry;
    private final FileAccessTicketService accessTicketService;

    @Transactional(rollbackFor = Exception.class)
    public FileAccessTicketRespVO createTicket(Long tenantId, Long actorUserId, Long taskId) {
        PlatformExportTaskDO task = taskMapper.selectByActorForUpdate(
                new ExportTaskActorQuery(tenantId, actorUserId, taskId));
        if (task == null || !"SUCCEEDED".equals(task.getTaskStatus()) || task.getExpiresAt() == null
                || !task.getExpiresAt().isAfter(LocalDateTime.now()) || task.getArtifactId() == null) {
            throw new IllegalStateException("统一导出任务不存在或不可下载");
        }
        List<String> fields = JsonUtils.parseArray(task.getRequestedFieldsSnapshot(), String.class);
        ExportBusinessDataSnapshot snapshot = providerRegistry.require(task.getOwnerContext(), task.getExportType())
                .inspect(new ExportBusinessDataQuery(tenantId, actorUserId, task.getFilterSnapshot(), fields,
                        Boolean.TRUE.equals(task.getIncludeFiles()), task.getScopeVersion()));
        if (snapshot == null || !"AVAILABLE".equals(snapshot.outcome())
                || !task.getScopeVersion().equals(snapshot.scopeVersion())) {
            throw new IllegalStateException("统一导出当前业务范围不可用");
        }
        FileAccessTicketRespVO ticket = accessTicketService.create(new FileAccessTicketService.AccessCommand(
                tenantId, actorUserId, task.getArtifactId(), task.getFileVersionNo(), FileActionCodes.DOWNLOAD,
                "PLATFORM", "EXPORT_TASK", String.valueOf(taskId), "EXPORT_FILE", task.getReferenceKey()));
        appendDownloaded(task);
        return ticket;
    }

    private void appendDownloaded(PlatformExportTaskDO task) {
        PlatformExportAuditDO audit = new PlatformExportAuditDO();
        audit.setId(IdWorker.getId());
        audit.setTenantId(task.getTenantId());
        audit.setExportTaskId(task.getId());
        Integer next = auditMapper.selectNextSequenceForUpdate(task.getTenantId(), task.getId());
        audit.setAuditSequence(next == null ? 1 : next);
        audit.setActionCode("DOWNLOADED");
        audit.setActorUserId(task.getActorUserId());
        audit.setDetailSnapshot("{}");
        audit.setOccurredAt(LocalDateTime.now());
        audit.setCreator(String.valueOf(task.getActorUserId()));
        audit.setCreateTime(LocalDateTime.now());
        if (auditMapper.insert(audit) != 1) throw new IllegalStateException("统一导出下载审计写入失败");
    }
}
