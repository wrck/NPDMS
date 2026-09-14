package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

/** Explicit task and binding identity; never resolves a source asset or an arbitrary task in a stage. */
public record ProjectTaskExecutionQuery(Long projectId, Long taskId, Long executionContractId) { }
