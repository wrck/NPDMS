package cn.iocoder.yudao.module.pms.customer.service.summary;

import cn.iocoder.yudao.module.pms.asset.api.customer.AssetCustomerDeviceSummaryApi;
import cn.iocoder.yudao.module.pms.asset.api.customer.CustomerDeviceSummaryQuery;
import cn.iocoder.yudao.module.pms.asset.api.customer.CustomerDeviceSummarySlice;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CustomerDeviceSummaryService {

    @Resource
    private AssetCustomerDeviceSummaryApi deviceSummaryApi;

    public CustomerDeviceSummarySlice query(CustomerDeviceSummaryQuery query) {
        validate(query);
        try {
            CustomerDeviceSummarySlice result = deviceSummaryApi.query(query);
            return result == null ? unavailable() : result;
        } catch (RuntimeException ex) {
            return unavailable();
        }
    }

    private void validate(CustomerDeviceSummaryQuery query) {
        if (query == null || query.tenantId() == null || query.customerId() == null
                || query.pageNo() == null || query.pageNo() < 1
                || query.pageSize() == null || query.pageSize() < 1) {
            throw new IllegalArgumentException("客户设备摘要查询不完整");
        }
    }

    private CustomerDeviceSummarySlice unavailable() {
        return new CustomerDeviceSummarySlice("AST", false, LocalDateTime.now(), List.of(), 0L);
    }
}
