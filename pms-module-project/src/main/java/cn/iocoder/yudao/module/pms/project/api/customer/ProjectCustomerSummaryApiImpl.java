package cn.iocoder.yudao.module.pms.project.api.customer;

import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.query.CustomerProjectSummaryPageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProjectCustomerSummaryApiImpl implements ProjectCustomerSummaryApi {

    private final ProjectMapper projectMapper;

    @Override
    public CustomerProjectSummarySlice query(CustomerProjectSummaryQuery query) {
        var page = projectMapper.selectCustomerSummaryPage(new CustomerProjectSummaryPageQuery(
                query.tenantId(), query.customerId(), query.pageNo(), query.pageSize()));
        var items = page.getList().stream()
                .map(project -> new CustomerProjectSummaryItem(project.getId(), project.getCode(),
                        project.getName(), String.valueOf(project.getStatus())))
                .toList();
        return new CustomerProjectSummarySlice("PROJ", true, LocalDateTime.now(), items, page.getTotal());
    }
}
