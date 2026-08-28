package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

/** 已完成版本CAS后的指派状态更新。 */
public record ProjectAssignmentStatusUpdate(Long projectId, Integer version, String assignmentStatus) {
}
