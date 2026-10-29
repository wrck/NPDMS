package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.core.KeyValue;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProjectSummaryProvider;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BpmProjectSummaryServiceTest {
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void addsFrozenProjectOnlyToAuthorizedRowsWithoutReplacingOriginalSummaryOrTotal() {
        TenantContextHolder.setTenantId(1L);
        BpmProjectSummaryProvider provider = query -> {
            assertEquals(1L, query.tenantId());
            assertEquals(Set.of("cut", "other"), query.authorizedProcessInstanceIds());
            return List.of(new BpmProjectSummaryProvider.ProjectSummary("cut", "提交时项目名称"),
                    new BpmProjectSummaryProvider.ProjectSummary("outside", "不可见项目"));
        };
        var cut = row("cut"); var other = row("other");
        cut.getProcessInstance().setSummary(List.of(new KeyValue<>("原摘要", "原值")));
        var page = new PageResult<>(List.of(cut, other), 47L);
        var service = new BpmProjectSummaryService(List.of(provider));
        assertSame(page, service.appendToAuthorizedPage(page));
        assertEquals(47L, page.getTotal());
        assertEquals(2, cut.getProcessInstance().getSummary().size());
        assertEquals("原值", cut.getProcessInstance().getSummary().getFirst().getValue());
        assertEquals("提交时项目名称", cut.getProcessInstance().getSummary().get(1).getValue());
        assertNull(other.getProcessInstance().getSummary());
        service.appendToAuthorizedPage(page);
        assertEquals(2, cut.getProcessInstance().getSummary().size());
    }

    @Test void emptyPageDoesNotQueryBusinessOwners() {
        var service = new BpmProjectSummaryService(List.of(query -> { fail("empty scope queried"); return List.of(); }));
        assertTrue(service.appendToAuthorizedPage(PageResult.empty()).getList().isEmpty());
    }

    @Test void conflictingOwnerNamesAreNotSilentlyChosen() {
        TenantContextHolder.setTenantId(1L);
        var service = new BpmProjectSummaryService(List.of(
                query -> List.of(new BpmProjectSummaryProvider.ProjectSummary("cut", "A")),
                query -> List.of(new BpmProjectSummaryProvider.ProjectSummary("cut", "B"))));
        assertThrows(IllegalStateException.class, () -> service.appendToAuthorizedPage(new PageResult<>(List.of(row("cut")), 1L)));
    }

    private BpmTaskRespVO row(String id) {
        return new BpmTaskRespVO().setProcessInstance(new BpmTaskRespVO.ProcessInstance().setId(id));
    }
}
