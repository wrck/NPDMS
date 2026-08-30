package cn.iocoder.yudao.module.pms.cutover.service.taskv2.migration;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;

import java.util.Objects;

/** 已通过来源、项目和目标身份资格校验的旧任务行转换器。 */
public final class LegacyCutoverRowConverter {

    public static final String MAPPING_VERSION = "F-CUT-002-PMS-CUT-TASK-V1";

    public CutoverTaskDO convert(Long targetId, Long trustedTenantId, CutTaskDO source) {
        require(positive(targetId), "targetId非法");
        require(source != null && positive(source.getId()) && positive(trustedTenantId)
                && Objects.equals(trustedTenantId, source.getTenantId()) && positive(source.getProjectId()),
                "旧任务身份非法");
        require(!Boolean.TRUE.equals(source.getDeleted()), "软删除旧任务不可转换");
        String taskNo = text(source.getCode(), 64, "code");
        String taskName = text(source.getName(), 128, "name");
        String legacyCutoverType = text(source.getCutoverType(), 32, "cutoverType");
        String legacyNetworkMode = optionalText(source.getNetworkMode(), 32, "networkMode");
        require(source.getStatus() != null && source.getStatus() >= 0 && source.getStatus() <= 8,
                "旧任务状态非法");
        require(source.getVersion() != null && source.getVersion() >= 0,
                "旧任务版本非法");
        require(auditText(source.getCreator()) && auditText(source.getUpdater())
                        && source.getCreateTime() != null && source.getUpdateTime() != null,
                "旧任务审计事实非法");

        CutoverTaskDO target = new CutoverTaskDO();
        target.setId(targetId);
        target.setTenantId(trustedTenantId);
        target.setProjectId(source.getProjectId());
        target.setTaskNo(taskNo);
        target.setTaskName(taskName);
        target.setScheduledTime(source.getScheduledTime());
        target.setTaskOrigin("LEGACY_FORWARD");
        target.setIntakeSourceType("LEGACY_FORWARD");
        target.setTaskStatus("LEGACY_UNKNOWN");
        target.setLegacyTaskId(source.getId());
        target.setLegacyCutoverTypeRaw(legacyCutoverType);
        target.setLegacyNetworkModeRaw(legacyNetworkMode);
        target.setLegacyStatusValue(source.getStatus());
        target.setLegacySourceVersion(source.getVersion());
        target.setLegacyMappingVersion(MAPPING_VERSION);
        target.setVersion(0);
        target.setCreator(source.getCreator());
        target.setCreateTime(source.getCreateTime());
        target.setUpdater(source.getUpdater());
        target.setUpdateTime(source.getUpdateTime());
        target.setDeleted(false);
        return target;
    }

    private static String text(String value, int maxLength, String field) {
        require(value != null && !value.trim().isEmpty(), field + "为空");
        String normalized = value.trim();
        require(normalized.length() <= maxLength, field + "超长");
        return normalized;
    }

    private static String optionalText(String value, int maxLength, String field) {
        return value == null ? null : text(value, maxLength, field);
    }

    private static boolean auditText(String value) {
        return value != null && value.length() <= 64;
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new LegacyCutoverMigrationException(message);
        }
    }
}
