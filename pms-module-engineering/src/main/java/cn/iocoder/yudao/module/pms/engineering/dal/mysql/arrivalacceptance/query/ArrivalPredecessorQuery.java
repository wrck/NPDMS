package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

public record ArrivalPredecessorQuery(Long tenantId, Long predecessorAcceptanceId) {

    public ArrivalPredecessorQuery {
        if (tenantId == null || tenantId < 0 || predecessorAcceptanceId == null
                || predecessorAcceptanceId <= 0) {
            throw new IllegalArgumentException("invalid arrival predecessor query");
        }
    }
}
