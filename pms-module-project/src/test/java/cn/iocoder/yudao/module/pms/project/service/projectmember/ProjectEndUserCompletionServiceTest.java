package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.completion.ProjectAssignmentCompletionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectEndUserCompletionServiceTest {
    final ProjectAssignmentCompletionMapper mapper = mock(ProjectAssignmentCompletionMapper.class);
    final ProjectEndUserCompletionService service = new ProjectEndUserCompletionService(mapper);
    final ProjectEndUserCompletionService.Query query = new ProjectEndUserCompletionService.Query(7L, 9L);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L);
        when(mapper.project(any())).thenReturn(project);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    private ProjectMemberAssignmentDO member() {
        var member = new ProjectMemberAssignmentDO(); member.setId(30L); member.setTenantId(7L);
        member.setProjectId(9L); member.setMemberRole("END_USER"); member.setMemberName("外部最终用户");
        member.setVersion(2); member.setStatus("ACTIVE"); member.setDeleted(false);
        member.setEffectiveFrom(LocalDateTime.now().minusDays(1)); return member;
    }
    @Test void validExternalIntervalProducesVersionedFactWithoutSystemIdentity() {
        var member = member(); when(mapper.members(any())).thenReturn(List.of(member));
        var fact = service.lockAndInspect(query);
        assertTrue(fact.completed()); assertNull(member.getUserId());
        assertEquals(List.of(new ProjectEndUserCompletionService.MemberFact(30L, 2)), fact.members());
        verify(mapper).project(argThat(value -> value.lock() && value.tenantId().equals(7L) && value.projectId().equals(9L)));
    }
    @Test void emptyInactiveExpiredFutureDeletedAndOtherIdentitiesCannotSatisfy() {
        when(mapper.members(any())).thenReturn(List.of()); assertFalse(service.lockAndInspect(query).completed());
        var ended = member(); ended.setEffectiveTo(LocalDateTime.now().minusSeconds(1));
        var future = member(); future.setEffectiveFrom(LocalDateTime.now().plusDays(1));
        var inactive = member(); inactive.setStatus("INACTIVE");
        var deleted = member(); deleted.setDeleted(true);
        var account = member(); account.setUserId(100L);
        var internal = member(); internal.setMemberRole("TEAM_MEMBER");
        var foreign = member(); foreign.setProjectId(10L);
        var tenant = member(); tenant.setTenantId(8L);
        var unnamed = member(); unnamed.setMemberName(" ");
        when(mapper.members(any())).thenReturn(List.of(ended, future, inactive, deleted, account, internal, foreign, tenant, unnamed));
        assertFalse(service.lockAndInspect(query).completed());
    }
    @Test void untrustedTenantAndMissingProjectFailBeforeReadingMemberEvidence() {
        assertThrows(IllegalArgumentException.class, () -> service.lockAndInspect(new ProjectEndUserCompletionService.Query(8L, 9L)));
        verifyNoInteractions(mapper);
        when(mapper.project(any())).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.lockAndInspect(query));
        verify(mapper, never()).members(any());
    }
}
