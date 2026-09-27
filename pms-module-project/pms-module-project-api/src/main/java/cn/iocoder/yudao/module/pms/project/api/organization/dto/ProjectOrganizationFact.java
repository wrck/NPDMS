package cn.iocoder.yudao.module.pms.project.api.organization.dto;

/** 项目所属公司和部门事实。 */
public record ProjectOrganizationFact(Long projectId, Long projectVersion,
                                      Long companyId, Long departmentId, String departmentCode) {
}
