package cn.iocoder.yudao.module.pms.project.api.commerce.dto;

public record ProjectOfficeFact(
        ProjectFactOutcome outcome,
        Long projectId,
        Long projectVersion,
        String projectCode,
        Long departmentId,
        String departmentCode,
        String departmentName,
        Integer departmentVersion) {
}
