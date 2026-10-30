package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

/** 迁移预演目标视图：既有外部键映射的只读投影。 */
public record MigrationPreviewTarget(
        String resultType,
        String targetContext,
        String targetObjectType,
        String targetTable,
        Long targetId,
        String targetRole,
        Integer targetSequence,
        String resultKey) {
}
