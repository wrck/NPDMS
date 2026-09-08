package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

/** PM-01：一次成员操作的主责显示与指派状态CAS。 */
public record ProjectManagerMemberUpdate(Long tenantId, Long projectId, Integer expectedVersion,
                                        Long managerId, String managerName, String managerEmployeeNo,
                                        String assignmentStatus, String updater) { }
