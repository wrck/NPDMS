package cn.iocoder.yudao.module.pms.engineering.service.configuration;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceConfigurationLogImportApi;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.query.ConfigurationImportedLogQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.query.ConfigurationCodeQuery;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ConfigurationLogImportService {
    private final ConfigurationMapper configurations;
    private final EngineeringRecordCodeGenerator codes;
    private final DeviceConfigurationLogImportApi logs;
    private final ProjectDeviceSelectionApi devices;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;

    @Transactional(rollbackFor = Exception.class)
    public Imported importLog(Long projectId, String operationId, String fileName, byte[] content) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        if (actor == null || projectId == null || projectId <= 0
                || !permissions.hasAnyPermissions(actor, "pms:imp-configuration:create"))
            throw rejected("没有创建配置记录的权限");
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(tenant)
                .subjectUserId(actor)
                .anchorProjectId(projectId)
                .actionCode(ProjectScopeApi.ACTION_EDIT).build());
        if (scope == null || scope.treeVersion() == null || scope.fullProjectIds() == null
                || !scope.fullProjectIds().contains(projectId)) throw rejected("项目不在当前可编辑范围");
        var locked = scopes.lockAndRevalidate(ProjectScopeRevalidationQuery.builder()
                .tenantId(tenant)
                .subjectUserId(actor)
                .anchorProjectId(projectId)
                .actionCode(ProjectScopeApi.ACTION_EDIT)
                .expectedScopeVersion(scope.treeVersion()).build());
        if (locked == null || locked.fullProjectIds() == null || !locked.fullProjectIds().contains(projectId)
                || !Objects.equals(scope.treeVersion(), locked.treeVersion())) throw rejected("项目范围已变化，请刷新后重试");
        var result = logs.importLog(new DeviceConfigurationLogImportApi.Command(tenant, actor, projectId, operationId, fileName, content));
        if (!"ARCHIVED".equals(result.outcome())) throw rejected(result.rejectionReason());
        if (result.files().isEmpty() || result.files().stream().anyMatch(file -> !file.available()))
            throw rejected("日志未形成完整可用的设备文件");
        devices.validateSelection(projectId, result.files().stream().map(DeviceConfigurationLogImportApi.File::deviceId).toList());
        List<Long> ids = new ArrayList<>();
        for (var file : result.files()) {
            var query = new ConfigurationImportedLogQuery(tenant, file.fileId());
            var existing = configurations.selectByImportedLogForUpdate(query);
            if (existing != null) {
                if (!Objects.equals(existing.getProjectId(), projectId)
                        || !Objects.equals(existing.getEquipmentId(), file.deviceId())) throw rejected("原配置日志归属不一致");
                ids.add(existing.getId());
                continue;
            }
            ConfigurationDO row = new ConfigurationDO();
            row.setTenantId(tenant); row.setProjectId(projectId); row.setEquipmentId(file.deviceId());
            row.setImportedLogFileId(file.fileId()); row.setImportedLogBatchId(result.importId());
            row.setStatus(0); row.setVersion(0L); row.setCreator(actor.toString()); row.setUpdater(actor.toString());
            List<String> excluded = new ArrayList<>();
            for (;;) {
                row.setCode(codes.next(projectId, EngineeringRecordCodeGenerator.CONFIGURATION, configurations,
                        excluded.toArray(String[]::new)));
                try {
                    if (configurations.insert(row) != 1) throw rejected("配置记录保存失败");
                    ids.add(row.getId());
                    break;
                } catch (DuplicateKeyException collision) {
                    existing = configurations.selectByImportedLogForUpdate(query);
                    if (existing != null && Objects.equals(existing.getProjectId(), projectId)
                            && Objects.equals(existing.getEquipmentId(), file.deviceId())) {
                        ids.add(existing.getId());
                        break;
                    }
                    // Only an occupied project code may advance the generated sequence.
                    if (configurations.selectCodeIncludingDeletedForUpdate(
                            new ConfigurationCodeQuery(tenant, projectId, row.getCode())) == null) throw collision;
                    excluded.add(row.getCode());
                }
            }
        }
        return new Imported(result.importId(), List.copyOf(ids));
    }

    private BusinessContractException rejected(String message) {
        return new BusinessContractException("CONFIGURATION_LOG_IMPORT_REJECTED", message);
    }

    public record Imported(Long importId, List<Long> configurationIds) {}
}
