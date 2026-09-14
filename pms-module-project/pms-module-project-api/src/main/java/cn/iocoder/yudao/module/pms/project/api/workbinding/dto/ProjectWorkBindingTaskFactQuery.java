package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

/** Resolve one task's current binding, never an arbitrary project-wide match. Tenant comes from the trusted context. */
public record ProjectWorkBindingTaskFactQuery(Long projectId, Long projectTaskId, ProjectWorkBindingTarget target) { }
