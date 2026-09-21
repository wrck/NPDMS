package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.projection;

public record AcceptanceReportFileScope(
        Long reportVersionId,
        Long acceptanceId,
        Long projectId,
        Long projectTaskId,
        String reportStatus,
        String originKind,
        String ruleSnapshot) {
    @org.apache.ibatis.annotations.AutomapConstructor
    public AcceptanceReportFileScope { }
    public AcceptanceReportFileScope(Long reportVersionId, Long acceptanceId, Long projectId, Long projectTaskId, String reportStatus) {
        this(reportVersionId, acceptanceId, projectId, projectTaskId, reportStatus, "LEGACY_TASK", null);
    }
}
