package cn.iocoder.yudao.module.pms.customer.service.query;

import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerRespVO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerContactAccessService;
import cn.iocoder.yudao.module.pms.project.api.contact.ProjectContactContextApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_NOT_EXISTS;

/** 项目Owner先确认查看范围与客户归属；仅返回该客户摘要，不扩展客户主档全局范围。 */
@Service
@RequiredArgsConstructor
public class ProjectCustomerOverviewService {
    private final ProjectContactContextApi projects;
    private final CustomerMasterMapper customers;
    private final CustomerResponseService responses;
    private final CustomerContactAccessService contacts;

    public record Query(Long tenantId, Long actorUserId, Long projectId) { }

    public CustomerRespVO get(Query query) {
        var project = projects.inspect(new ProjectContactContextApi.Query(
                query.tenantId(), query.actorUserId(), query.projectId()));
        if (project == null || !Objects.equals(query.projectId(), project.projectId()))
            throw exception(CUSTOMER_NOT_EXISTS);
        if (project.customerId() == null) return null;
        var customer = customers.selectById(project.customerId());
        if (customer == null || !Objects.equals(query.tenantId(), customer.getTenantId()))
            throw exception(CUSTOMER_NOT_EXISTS);
        return responses.detail(customer, contacts.resolve(query.actorUserId(), true));
    }
}
