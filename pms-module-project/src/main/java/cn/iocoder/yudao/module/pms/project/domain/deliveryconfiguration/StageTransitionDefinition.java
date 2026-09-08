package cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration;

/**
 * PM-03 / F-PROJ-009：模板版本内唯一的阶段关系载体，前后置视图均从这些边派生。
 * conditionRevisionId 仅引用精确 CompletionRule 修订；null 表示未配置条件。
 * 本对象不保存业务谓词，不负责查询、发布、持久化或阶段推进。
 */
public record StageTransitionDefinition(String transitionCode, String fromStageCode, String toStageCode,
                                        Long conditionRevisionId, Integer priority, Boolean defaultBranch) {
}
