package cn.iocoder.yudao.module.pms.customer.api;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.contact.CustomerContactMasterApi;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.CustomerContactMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.CustomerContactMasterMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query.VisibleContactPageQuery;
import cn.iocoder.yudao.module.pms.customer.service.contact.*;
import cn.iocoder.yudao.module.pms.customer.service.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class CustomerContactMasterApiImpl implements CustomerContactMasterApi {
    private final CustomerContactMasterService service;
    private final CustomerContactMasterMapper mapper;
    private final CustomerScopeContextService scopes;
    private final CustomerContactAccessService access;
    private final CustomerFieldMaskingService masking;

    @Override public Contact get(Long id) {
        var row = mapper.selectById(id);
        if (row == null) return null;
        return response(service.get(actor(), row.getCustomerId(), id));
    }
    @Override public ContactPage page(PageQuery query) {
        var actor = actor();
        if (query.pageNo() == null || query.pageNo()<1 || query.pageSize() == null || query.pageSize()<1 || query.pageSize()>100) throw exception(CONTACT_VALUES_INVALID, "分页参数无效");
        var scope = scopes.resolve(actor.tenantId(), actor.userId());
        if (!scope.all() && scope.slices().isEmpty()) return new ContactPage(java.util.List.of(), 0L);
        var page = new PageParam(); page.setPageNo(query.pageNo()); page.setPageSize(query.pageSize());
        var criteria = new VisibleContactPageQuery(actor.tenantId(), query.customerId(), query.name(), query.primaryFlag(), query.status(), scope, page);
        long count = mapper.selectVisibleCount(criteria);
        return new ContactPage(count == 0 ? java.util.List.of() : mapper.selectVisiblePage(criteria).stream().map(this::response).toList(), count);
    }
    @Override public Long create(Save command, String key) { return service.create(actor(), write(command), key).getId(); }
    @Override public void update(Save command) { service.update(actor(), write(command)); }
    @Override public void delete(Long id, Integer version) {
        var row = mapper.selectById(id);
        if (row == null) throw exception(CONTACT_NOT_EXISTS);
        service.delete(actor(), row.getCustomerId(), id, version);
    }

    private ContactMasterWrite write(Save command) {
        var values = command.details();
        if (values == null || command.status() == null) throw exception(CONTACT_VALUES_INVALID, "联系人内容或状态缺失");
        return new ContactMasterWrite(command.customerId(), command.id(), command.version(),
                new ContactValues(values.name(), values.department(), values.title(), values.mobile(), values.phone(), values.email(), null, values.remark()),
                Boolean.TRUE.equals(command.primaryFlag()), command.status(), false);
    }
    private Contact response(CustomerContactMasterDO row) {
        boolean raw = access.resolve(SecurityFrameworkUtils.getLoginUserId(), true) == CustomerFieldMaskingService.ContactAccess.RAW;
        return new Contact(row.getId(), row.getCustomerId(), row.getName(), row.getDepartment(), row.getTitle(),
                raw ? row.getMobile() : masking.maskPhone(row.getMobile()), raw ? row.getPhone() : masking.maskPhone(row.getPhone()),
                raw ? row.getEmail() : masking.maskEmail(row.getEmail()), row.getPrimaryFlag(), row.getStatus(), row.getRemark(), row.getVersion(), row.getCreateTime(), row.getCustomerName());
    }
    private CustomerContactMasterService.Actor actor() {
        return new CustomerContactMasterService.Actor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId());
    }
}
