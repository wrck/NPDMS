package cn.iocoder.yudao.module.pms.project.api.customer;

public interface ProjectCustomerSummaryApi {

    CustomerProjectSummarySlice query(CustomerProjectSummaryQuery query);
}
