package cn.iocoder.yudao.module.pms.customer.controller.admin.customer;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.customer.service.inspection.CustomerInspectionHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pms/customers/{customerId}/inspection-history")
public class CustomerInspectionHistoryController {
    private final CustomerInspectionHistoryService histories;
    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:customer:query')")
    public CommonResult<PageResult<CustomerInspectionHistoryService.HistoryView>> page(@PathVariable Long customerId, @Valid PageParam page) {
        return success(histories.page(new CustomerInspectionHistoryService.HistoryQuery(customerId, page)));
    }
}
