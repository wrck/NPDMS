package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

public record ProjectSatisfactionTaskFactQuery(Long projectId, Long projectTaskId,
        Integer expectedProjectTaskVersion) {
}
