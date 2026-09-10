package cn.iocoder.yudao.module.pms.project.service.customercontact;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.customer.api.contact.CustomerContactMasterApi;
import cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo.CustomerContactPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo.CustomerContactSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customercontact.CustomerContactDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Legacy entrypoint adapter: original URLs and page fields now use the single CUS-owned master. */
@Service
@Validated
public class CustomerContactServiceImpl implements CustomerContactService {
    @Resource private CustomerContactMasterApi contacts;

    @Override public Long createCustomerContact(CustomerContactSaveReqVO request) {
        // The legacy POST never promised safe automatic retries; the new endpoint accepts a stable key.
        return contacts.create(command(request), UUID.randomUUID().toString());
    }
    @Override public void updateCustomerContact(CustomerContactSaveReqVO request) {
        contacts.update(command(request));
    }
    @Override public void deleteCustomerContact(Long id) {
        var current = contacts.get(id);
        if (current == null) throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.CUSTOMER_CONTACT_NOT_EXISTS);
        contacts.delete(id, current.version());
    }
    @Override public CustomerContactDO getCustomerContact(Long id) { return convert(contacts.get(id)); }

    @Override public PageResult<CustomerContactDO> getCustomerContactPage(CustomerContactPageReqVO query) {
        var page = contacts.page(new CustomerContactMasterApi.PageQuery(query.getCustomerId(), query.getName(),
                query.getPrimaryFlag(), query.getStatus(), query.getPageNo(), query.getPageSize()));
        return new PageResult<>(page.list().stream().map(this::convert).toList(), page.total());
    }

    @Override public List<CustomerContactDO> getContactListByCustomerId(Long customerId) {
        if (customerId == null) return List.of();
        List<CustomerContactDO> result = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            var page = contacts.page(new CustomerContactMasterApi.PageQuery(customerId, null, null, null, pageNo, 100));
            page.list().stream().map(this::convert).forEach(result::add);
            if ((long) pageNo * 100 >= page.total() || page.list().isEmpty()) break;
            pageNo++;
        }
        return result;
    }

    private CustomerContactMasterApi.Save command(CustomerContactSaveReqVO request) {
        return new CustomerContactMasterApi.Save(request.getId(), request.getCustomerId(),
                new CustomerContactMasterApi.Details(request.getName(), request.getDepartment(), request.getTitle(),
                        request.getMobile(), request.getPhone(), request.getEmail(), request.getRemark()),
                request.getPrimaryFlag(), request.getStatus(), request.getVersion());
    }

    private CustomerContactDO convert(CustomerContactMasterApi.Contact value) {
        if (value == null) return null;
        CustomerContactDO row = new CustomerContactDO();
        row.setId(value.id()); row.setCustomerId(value.customerId()); row.setName(value.name());
        row.setDepartment(value.department()); row.setTitle(value.title()); row.setMobile(value.mobile());
        row.setPhone(value.phone()); row.setEmail(value.email()); row.setPrimaryFlag(value.primaryFlag());
        row.setStatus(value.status()); row.setRemark(value.remark()); row.setVersion(value.version()); row.setCreateTime(value.createTime());
        return row;
    }
}
