package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

public record ArrivalProjectFactVersionQuery(Long tenantId, Long projectId) {

    public ArrivalProjectFactVersionQuery {
        if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("invalid arrival project fact version query");
        }
    }
}
