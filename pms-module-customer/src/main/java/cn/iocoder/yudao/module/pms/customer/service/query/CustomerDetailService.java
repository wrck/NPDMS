package cn.iocoder.yudao.module.pms.customer.service.query;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.asset.api.customer.CustomerDeviceSummaryQuery;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerDetailRespVO;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerRespVO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.service.history.CustomerHistoryService;
import cn.iocoder.yudao.module.pms.customer.service.location.CustomerLocationReferenceService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService;
import cn.iocoder.yudao.module.pms.customer.service.summary.CustomerDeviceSummaryService;
import cn.iocoder.yudao.module.pms.customer.service.summary.CustomerProjectSummaryService;
import cn.iocoder.yudao.module.pms.project.api.customer.CustomerProjectSummaryQuery;
import org.springframework.stereotype.Service;

@Service
public class CustomerDetailService {

    private static final int SUMMARY_PAGE_NO = 1;
    private static final int SUMMARY_PAGE_SIZE = 20;

    private final CustomerResponseService responseService;
    private final CustomerLocationReferenceService locationService;
    private final CustomerProjectSummaryService projectSummaryService;
    private final CustomerDeviceSummaryService deviceSummaryService;
    private final CustomerHistoryService historyService;

    public CustomerDetailService(
            CustomerResponseService responseService,
            CustomerLocationReferenceService locationService,
            CustomerProjectSummaryService projectSummaryService,
            CustomerDeviceSummaryService deviceSummaryService,
            CustomerHistoryService historyService) {
        this.responseService = responseService;
        this.locationService = locationService;
        this.projectSummaryService = projectSummaryService;
        this.deviceSummaryService = deviceSummaryService;
        this.historyService = historyService;
    }

    public CustomerDetailRespVO get(
            CustomerMasterDO customer,
            CustomerFieldMaskingService.ContactAccess access) {
        if (customer == null || customer.getTenantId() == null || customer.getId() == null) {
            throw new IllegalArgumentException("客户详情查询不完整");
        }
        CustomerRespVO base = responseService.detail(customer, access);
        CustomerDetailRespVO response = BeanUtils.toBean(base, CustomerDetailRespVO.class);
        Long tenantId = customer.getTenantId();
        Long customerId = customer.getId();
        response.setLocations(BeanUtils.toBean(
                locationService.listCurrent(tenantId, customerId),
                CustomerDetailRespVO.Location.class));
        response.setProjects(projectSummaryService.query(
                new CustomerProjectSummaryQuery(
                        tenantId, customerId, SUMMARY_PAGE_NO, SUMMARY_PAGE_SIZE)));
        response.setDevices(deviceSummaryService.query(
                new CustomerDeviceSummaryQuery(
                        tenantId, customerId, SUMMARY_PAGE_NO, SUMMARY_PAGE_SIZE)));
        response.setHistory(BeanUtils.toBean(
                historyService.list(tenantId, customerId),
                CustomerDetailRespVO.History.class));
        return response;
    }
}
