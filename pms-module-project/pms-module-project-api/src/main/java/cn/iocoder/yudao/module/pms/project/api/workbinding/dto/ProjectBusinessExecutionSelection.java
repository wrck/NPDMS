package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

/** Optional in standalone menus; when supplied exactly one versioned node must be present. */
public record ProjectBusinessExecutionSelection(ProjectTaskExecutionContext task, ProjectStageExecutionContext stage) { }
