package cn.iocoder.yudao.module.pms.acceptance.api.deliverable;

import java.util.List;

/**
 * 项目交付件归档事实只读契约（ACC Owner）。
 * <p>
 * 供工程实施等消费方按项目汇总查看验收/满意度交付件归档状态；
 * 只读投影，不提供写入口，归档状态以 ACC 权威表为准。
 */
public interface ProjectDeliverableArchiveFactApi {

    /**
     * 查询项目全部交付件定义及其当前归档来源。
     * 尚无归档来源的交付件定义同样返回（来源字段为 null），供消费方展示未归档状态。
     *
     * @param tenantId  租户编号（须与受信运行时租户一致）
     * @param projectId 项目编号
     * @return 交付件归档事实列表
     */
    List<ProjectDeliverableArchiveFact> listByProject(Long tenantId, Long projectId);

    /**
     * @param deliverableId   交付件定义编号（acc_project_deliverable.id）
     * @param deliverableCode 交付件编码
     * @param name            交付件名称
     * @param stageCode       所属阶段编码
     * @param required        是否必交
     * @param sourceObjectType 当前来源对象类型（AcceptanceReportVersion / SatisfactionResult）；未归档为 null
     * @param sourceCategory   归档类别：PRELIMINARY 初验报告 / FINAL 终验报告 / SATISFACTION 满意度调查报告；未归档为 null
     * @param sourceObjectId   当前来源对象编号；未归档为 null
     * @param sourceVersion    当前来源版本；未归档为 null
     * @param relationStatus   来源关系状态（CURRENT / SUPERSEDED / REVOKED）；未归档为 null
     * @param archiveStatus    归档状态（ARCHIVED / PENDING_COMPENSATION / INVALID）；未归档为 null
     */
    record ProjectDeliverableArchiveFact(
            Long deliverableId,
            String deliverableCode,
            String name,
            String stageCode,
            Boolean required,
            String sourceObjectType,
            String sourceCategory,
            Long sourceObjectId,
            Integer sourceVersion,
            String relationStatus,
            String archiveStatus) {
    }
}
