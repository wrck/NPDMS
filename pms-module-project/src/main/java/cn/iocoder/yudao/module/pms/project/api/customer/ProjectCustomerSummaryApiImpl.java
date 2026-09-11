package cn.iocoder.yudao.module.pms.project.api.customer;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.query.CustomerProjectSummaryPageQuery;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProjectCustomerSummaryApiImpl implements ProjectCustomerSummaryApi {

    private final ProjectMasterMapper projectMapper;
    private final ProjectTreeScopeService projectTreeScopeService;

    @Override
    public CustomerProjectSummarySlice query(CustomerProjectSummaryQuery query) {
        var visibleProjectIds = projectTreeScopeService.resolveAllFullProjectIds(
                query.tenantId(), query.subjectUserId(), ProjectScopeApi.ACTION_VIEW);
        var page = projectMapper.selectCustomerSummaryPage(new CustomerProjectSummaryPageQuery(
                query.tenantId(), query.customerId(), visibleProjectIds, query.pageNo(), query.pageSize()));
        var items = page.getList().stream()
                .map(project -> new CustomerProjectSummaryItem(project.getId(), project.getProjectCode(),
                        project.getProjectName(), project.getStatus()))
                .toList();
        return new CustomerProjectSummarySlice("PROJ", true, LocalDateTime.now(), items, page.getTotal());
    }
}
