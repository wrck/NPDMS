package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

public record ProjectWorkBindingStageFactRevalidationQuery(Long projectId, Long projectStageId, Long executionContractId,
        Integer expectedProjectStageVersion, Integer expectedContractVersion, Integer expectedProjectVersion,
        ProjectWorkBindingTarget target) { }
