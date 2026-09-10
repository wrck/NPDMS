package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query;

/** Delete only the editable record version observed by the application service. */
public record ArrivalEditableDeleteQuery(Long id, Integer expectedVersion) {
}
