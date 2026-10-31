package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query;

/** Candidate projects only; callers must intersect them with the current authorization scope. */
public record InspectionWorkbenchProjectsQuery(Long tenantId) { }
