package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

/** Result事件生产者用于冻结PROJ当前满意度任务版本的精确身份。 */
public record ProjectSatisfactionTaskIdentityQuery(Long projectId, Long projectTaskId) {
}
