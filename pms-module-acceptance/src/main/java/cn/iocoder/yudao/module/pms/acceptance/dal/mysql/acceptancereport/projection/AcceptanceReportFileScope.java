package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.projection;

public record AcceptanceReportFileScope(
        Long reportVersionId,
        Long acceptanceId,
        Long projectId,
        Long projectTaskId,
        String reportStatus) {
}
