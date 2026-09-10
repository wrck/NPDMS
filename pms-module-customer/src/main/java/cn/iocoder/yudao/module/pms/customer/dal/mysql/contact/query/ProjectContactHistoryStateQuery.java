package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query;
import java.util.Set;
public record ProjectContactHistoryStateQuery(Long tenantId, Long projectId, Long customerId, Set<Long> contactIds) {}
