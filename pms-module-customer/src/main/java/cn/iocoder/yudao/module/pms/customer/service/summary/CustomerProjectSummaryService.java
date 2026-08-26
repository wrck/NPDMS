package cn.iocoder.yudao.module.pms.customer.service.summary;

import cn.iocoder.yudao.module.pms.project.api.customer.CustomerProjectSummaryQuery;
import cn.iocoder.yudao.module.pms.project.api.customer.CustomerProjectSummarySlice;
import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerSummaryApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CustomerProjectSummaryService {

    @Resource
    private ProjectCustomerSummaryApi projectSummaryApi;

    public CustomerProjectSummarySlice query(CustomerProjectSummaryQuery query) {
        validate(query);
        try {
            CustomerProjectSummarySlice result = projectSummaryApi.query(query);
            return result == null ? unavailable() : result;
        } catch (RuntimeException ex) {
            return unavailable();
        }
    }

    private void validate(CustomerProjectSummaryQuery query) {
        if (query == null || query.tenantId() == null || query.customerId() == null
                || query.pageNo() == null || query.pageNo() < 1
                || query.pageSize() == null || query.pageSize() < 1) {
            throw new IllegalArgumentException("客户项目摘要查询不完整");
        }
    }

    private CustomerProjectSummarySlice unavailable() {
        return new CustomerProjectSummarySlice("PROJ", false, LocalDateTime.now(), List.of(), 0L);
    }
}
