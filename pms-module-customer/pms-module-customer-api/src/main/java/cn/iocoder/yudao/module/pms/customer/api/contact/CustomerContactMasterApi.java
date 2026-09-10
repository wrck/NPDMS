package cn.iocoder.yudao.module.pms.customer.api.contact;

import java.time.LocalDateTime;
import java.util.List;

/** Existing contact entrypoints share one customer-owned master and scoped authorization. */
public interface CustomerContactMasterApi {
    Contact get(Long id);
    ContactPage page(PageQuery query);
    Long create(Save command, String idempotencyKey);
    void update(Save command);
    void delete(Long id, Integer expectedVersion);

    record Details(String name, String department, String title, String mobile, String phone, String email, String remark) {}
    record Save(Long id, Long customerId, Details details, Boolean primaryFlag, Integer status, Integer version) {}
    record PageQuery(Long customerId, String name, Boolean primaryFlag, Integer status, Integer pageNo, Integer pageSize) {}
    record Contact(Long id, Long customerId, String name, String department, String title, String mobile,
                   String phone, String email, Boolean primaryFlag, Integer status, String remark,
                   Integer version, LocalDateTime createTime, String customerName) {}
    record ContactPage(List<Contact> list, Long total) {}
}
