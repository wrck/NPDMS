package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query;

/** Exact authorized report identity; missing values never widen the content query. */
public record InspectionReportContentQuery(Long tenantId, Long taskId, Long reportId) {
    public InspectionReportContentQuery {
        if (tenantId == null || taskId == null || reportId == null || taskId <= 0 || reportId <= 0)
            throw new IllegalArgumentException("Report identity is required");
    }
}
