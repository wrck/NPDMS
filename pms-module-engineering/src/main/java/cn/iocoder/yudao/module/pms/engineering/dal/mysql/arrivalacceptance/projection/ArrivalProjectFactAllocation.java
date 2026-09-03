package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.projection;

public record ArrivalProjectFactAllocation(
        Long projectFactVersion,
        String sourceType,
        Long sourceId,
        Long acceptanceId,
        Long predecessorAcceptanceId) {
}
