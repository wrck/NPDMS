package cn.iocoder.yudao.module.pms.project.api.commerce.dto;

public record ProjectOfficeFact(
        ProjectFactOutcome outcome,
        Long projectId,
        Integer projectVersion,
        String projectCode,
        Long officeDepartmentId,
        String officeDepartmentCode,
        String officeDepartmentName,
        Integer officeDepartmentVersion) {
}
