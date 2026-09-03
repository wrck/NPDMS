package cn.iocoder.yudao.module.pms.cutover.service.plan.migration;

/** 解析已由PLT确认的pms_cut_task到cut_task目标映射。 */
public interface LegacyCutoverTaskMappingPort {

    Long resolveTargetTaskId(Long tenantId, Long legacyTaskId);
}
