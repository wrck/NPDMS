package cn.iocoder.yudao.module.pms.project.api.customer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerReferenceGuardStatus;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.query.CustomerProjectReferenceQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.query.CustomerProjectSummaryPageQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCustomerApiImplTest {

    @Mock
    private ProjectMapper projectMapper;
    @InjectMocks
    private ProjectCustomerReferenceGuardApiImpl guardApi;
    @InjectMocks
    private ProjectCustomerSummaryApiImpl summaryApi;

    @Test
    void reportsReferencedProjects() {
        var query = new CustomerReferenceGuardQuery(1L, 100L);
        when(projectMapper.selectCountByCustomer(
                new CustomerProjectReferenceQuery(1L, 100L)))
                .thenReturn(2L);

        var result = guardApi.check(query);

        assertEquals(CustomerReferenceGuardStatus.REFERENCED.name(), result.status());
        assertEquals("PROJ", result.provider());
        assertEquals(2L, result.referenceCount());
    }

    @Test
    void returnsProjectSummaryPage() {
        ProjectDO project = new ProjectDO();
        project.setId(10L);
        project.setCode("P-10");
        project.setName("项目十");
        project.setStatus(1);
        when(projectMapper.selectCustomerSummaryPage(
                new CustomerProjectSummaryPageQuery(1L, 100L, 1, 20)))
                .thenReturn(new PageResult<>(List.of(project), 1L));

        var result = summaryApi.query(new CustomerProjectSummaryQuery(1L, 100L, 1, 20));

        assertTrue(result.available());
        assertEquals("PROJ", result.provider());
        assertEquals(1L, result.total());
        assertEquals(new CustomerProjectSummaryItem(10L, "P-10", "项目十", "1"), result.items().getFirst());
    }
}
