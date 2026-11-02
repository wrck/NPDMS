package cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query;
import java.util.Set;
public record ProjectShipmentPageQuery(Long tenantId, Set<String> contractNumbers, String sn, long offset, int limit) {}
