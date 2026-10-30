package cn.iocoder.yudao.module.pms.service.api.governance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.guard.*;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection.InspectionIssueGovernanceFact;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InspectionGovernanceGuardProviderTest {
    private static final long TENANT_ID = 7L;
    private static final LocalDateTime CHECKED_AT = LocalDateTime.of(2026, 8, 25, 15, 0);
    private final InspectionTaskMapper taskMapper = mock(InspectionTaskMapper.class);
    private final ServiceIssueMapper issueMapper = mock(ServiceIssueMapper.class);
    private final InspectionGovernanceGuardProvider provider = new InspectionGovernanceGuardProvider(taskMapper, issueMapper);

    @BeforeEach void setup() { TenantContextHolder.setTenantId(TENANT_ID); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void blocksNonTerminalAndUnknownTasksButAllowsClosedArchivedAndCancelled() {
        when(taskMapper.selectForGovernance(any())).thenReturn(List.of(
                task(1L,201L,"EXECUTING",1L),task(1L,202L,"CLOSED",1L),task(2L,203L,"UNKNOWN",1L),
                task(1L,204L,"ARCHIVED",1L),task(1L,205L,"CANCELLED",1L)));
        var fact = provider.inspect(query(Set.of(2L,1L)));
        assertEquals("INSPECTION",fact.provider());
        assertEquals(List.of("201","203"),fact.blockers().stream().map(ProjectGovernanceBlocker::objectId).toList());
    }

    @Test void cancelledTaskDoesNotHideItsUnclosedRemediation() {
        when(taskMapper.selectForGovernance(any())).thenReturn(List.of(task(1L,201L,"CANCELLED",1L)));
        var issue = issue(1L,301L,"CLOSURE_PENDING",2L);
        when(issueMapper.selectForGovernance(any())).thenReturn(List.of(issue,issue(1L,302L,"CLOSED",3L)));
        var fact = provider.inspect(query(Set.of(1L)));
        assertEquals(1,fact.blockers().size());
        assertEquals("INSPECTION_ISSUE",fact.blockers().getFirst().objectType());
        assertEquals("301",fact.blockers().getFirst().objectId());
        verify(taskMapper,never()).updateById(any(InspectionTaskDO.class));
    }

    @Test void emptyScopeDoesNotQueryAndCrossTenantIsRejected() {
        var fact = provider.inspect(query(Set.of()));
        assertEquals("EMPTY",fact.watermark()); assertTrue(fact.blockers().isEmpty());
        verifyNoInteractions(taskMapper,issueMapper);
        assertThrows(IllegalArgumentException.class,()->provider.inspect(new ProjectGovernanceGuardQuery(8L,Set.of(1L),"REOPEN",CHECKED_AT)));
    }

    @Test void rejectsOutOfScopeTaskAndIssueFacts() {
        when(taskMapper.selectForGovernance(any())).thenReturn(List.of(task(2L,201L,"CLOSED",1L)));
        assertThrows(IllegalStateException.class,()->provider.inspect(query(Set.of(1L))));
        when(taskMapper.selectForGovernance(any())).thenReturn(List.of());
        when(issueMapper.selectForGovernance(any())).thenReturn(List.of(issue(2L,301L,"CLOSED",1L)));
        assertThrows(IllegalStateException.class,()->provider.inspect(query(Set.of(1L))));
    }

    @Test void digestIsOrderIndependentAndTaskVersionChangesWatermark() {
        var first=task(1L,201L,"CLOSED",1L); var second=task(2L,202L,"CANCELLED",1L);
        when(taskMapper.selectForGovernance(any())).thenReturn(List.of(first,second),List.of(second,first),
                List.of(task(1L,201L,"CLOSED",2L),second));
        var before=provider.inspect(query(Set.of(1L,2L)));
        var reorder=provider.inspect(query(Set.of(1L,2L)));
        var after=provider.inspect(query(Set.of(1L,2L)));
        assertEquals(before.factDigest(),reorder.factDigest()); assertEquals(before.watermark(),reorder.watermark());
        assertNotEquals(before.factDigest(),after.factDigest()); assertNotEquals(before.watermark(),after.watermark());
    }

    @Test void issueStateAndVersionContributeToFactDigest() {
        when(taskMapper.selectForGovernance(any())).thenReturn(List.of(task(1L,201L,"CANCELLED",1L)));
        when(issueMapper.selectForGovernance(any())).thenReturn(List.of(issue(1L,301L,"PROCESSING",1L)),List.of(issue(1L,301L,"CLOSED",2L)));
        var before=provider.inspect(query(Set.of(1L))); var after=provider.inspect(query(Set.of(1L)));
        assertEquals(1,before.blockers().size()); assertTrue(after.blockers().isEmpty());
        assertNotEquals(before.factDigest(),after.factDigest());
    }

    private static ProjectGovernanceGuardQuery query(Set<Long> projectIds) {
        return new ProjectGovernanceGuardQuery(TENANT_ID,projectIds,"REOPEN",CHECKED_AT);
    }
    private static InspectionTaskDO task(Long projectId,Long id,String status,Long version) {
        var task=new InspectionTaskDO(); task.setTenantId(TENANT_ID); task.setProjectId(projectId); task.setId(id);
        task.setStatusCode(status); task.setVersion(version); task.setUpdateTime(CHECKED_AT.plusMinutes(id)); return task;
    }
    private static InspectionIssueGovernanceFact issue(Long projectId,Long id,String status,Long version) {
        var issue=new InspectionIssueGovernanceFact(); issue.setTenantId(TENANT_ID); issue.setProjectId(projectId); issue.setId(id);
        issue.setTaskId(201L); issue.setStatusCode(status); issue.setVersion(version); issue.setUpdateTime(CHECKED_AT.plusMinutes(id)); return issue;
    }
}
