package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

/** Explicit stage instance and frozen binding; never resolves a task or source asset as a substitute. */
public record ProjectStageExecutionQuery(Long projectId, Long stageId, Long executionContractId) { }
