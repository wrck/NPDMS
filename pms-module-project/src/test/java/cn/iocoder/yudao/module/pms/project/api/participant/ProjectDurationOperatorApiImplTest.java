package cn.iocoder.yudao.module.pms.project.api.participant;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectDurationOperatorApiImplTest {
    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final ProjectMemberAssignmentMapper members = mock(ProjectMemberAssignmentMapper.class);
    private final ProjectManagementQualificationApi qualifications = mock(ProjectManagementQualificationApi.class);
    private final ProjectDurationOperatorApiImpl api = new ProjectDurationOperatorApiImpl(projects,members,qualifications);
    private final ProjectDurationOperatorApi.Query subject = new ProjectDurationOperatorApi.Query(1L,100L,9L);
    private final ProjectMasterDO project = new ProjectMasterDO();

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(9L).setTenantId(1L),new MockHttpServletRequest());
        project.setId(100L); project.setTenantId(1L);project.setLifecycleStatus("ACTIVE");project.setVersion(3L);
        when(projects.selectById(100L)).thenReturn(project);when(projects.selectByIdForUpdate(100L)).thenReturn(project);
    }
    @AfterEach void clean() { TenantContextHolder.clear();SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings={"PROJECT_MANAGER","SERVICE_MANAGER","SERVICE_MANAGER_L1","SERVICE_MANAGER_L2","REGION"})
    void delegatesQualificationWithoutInventingPmIdentity(String role) {
        var fact = qualification(role);
        when(qualifications.inspect(any())).thenReturn(fact);
        when(qualifications.lockCurrent(any())).thenReturn(fact);
        String basis = "PROJECT_MANAGER".equals(role) ? "CONFIRMED_PROJECT_PM"
                : "REGION".equals(role) ? "REGIONAL_SERVICE_MANAGER" : "CONFIRMED_PROJECT_MANAGEMENT_MEMBER";
        assertEquals(basis,api.inspect(subject).basis());
        assertEquals(basis,api.lockAndRevalidate(new ProjectDurationOperatorApi.RevalidationQuery(subject,3L)).basis());
        verify(qualifications).lockCurrent(new ProjectManagementQualificationApi.CurrentRevalidation(
                new ProjectManagementQualificationApi.Query(1L,100L,9L),3L,"ACTIVE",null));
        verifyNoInteractions(members);
    }

    @Test void missingQualificationAndRevocationFailClosed() {
        assertThrows(ServiceException.class,()->api.inspect(subject));
        assertThrows(ServiceException.class,()->api.lockAndRevalidate(new ProjectDurationOperatorApi.RevalidationQuery(subject,3L)));
        when(qualifications.inspect(any())).thenReturn(qualification("REGION"));
        assertNotNull(api.inspect(subject));
        when(qualifications.lockCurrent(any())).thenThrow(new IllegalStateException("qualification revoked"));
        assertThrows(IllegalStateException.class,()->api.lockAndRevalidate(new ProjectDurationOperatorApi.RevalidationQuery(subject,3L)));
        verifyNoInteractions(members);
    }

    @Test void projectVersionAndClosedStateRejectBeforeQualificationLookup() {
        assertThrows(ServiceException.class,()->api.lockAndRevalidate(new ProjectDurationOperatorApi.RevalidationQuery(subject,2L)));
        project.setLifecycleStatus("NORMAL_CLOSED");
        assertThrows(ServiceException.class,()->api.inspect(subject));
        assertThrows(ServiceException.class,()->api.lockAndRevalidate(new ProjectDurationOperatorApi.RevalidationQuery(subject,3L)));
        verifyNoInteractions(qualifications,members);
    }

    @Test void rejectsForeignTenantActorAndIgnoredTenant() {
        assertThrows(ServiceException.class,()->api.inspect(new ProjectDurationOperatorApi.Query(2L,100L,9L)));
        assertThrows(ServiceException.class,()->api.inspect(new ProjectDurationOperatorApi.Query(1L,100L,10L)));
        TenantContextHolder.setIgnore(true);
        assertThrows(ServiceException.class,()->api.inspect(subject));
        verifyNoInteractions(qualifications,members);
    }

    @ParameterizedTest @ValueSource(strings={"SERVICE_MANAGER","REGION"})
    void firstDurationStillNeedsARealCurrentProjectManager(String role) {
        when(qualifications.lockCurrent(any())).thenReturn(qualification(role));
        var command = new ProjectDurationOperatorApi.RevalidationQuery(subject,3L);
        assertThrows(ServiceException.class,()->api.lockInitialAndRevalidate(command));
        assertNotNull(api.lockAndRevalidate(command));
        when(members.selectCurrentDurationProjectManagersForUpdate(any())).thenReturn(List.of(new ProjectMemberAssignmentDO()));
        assertNotNull(api.lockInitialAndRevalidate(command));
    }

    @Test void writeConvenienceStillRequiresTheConsumerTransaction() throws Exception {
        for (String method : List.of("lockAndRevalidate","lockInitialAndRevalidate"))
            assertEquals(Propagation.MANDATORY,ProjectDurationOperatorApiImpl.class.getMethod(method,
                    ProjectDurationOperatorApi.RevalidationQuery.class).getAnnotation(Transactional.class).propagation());
    }

    private ProjectManagementQualificationApi.Fact qualification(String role) {
        var query = new ProjectManagementQualificationApi.Query(1L,100L,9L);
        return "REGION".equals(role)
                ? new ProjectManagementQualificationApi.Fact(query,ProjectManagementQualificationApi.Source.REGIONAL_MANAGER,null,
                    new ProjectRegionalManagerFactApi.Fact(100L,9L,3L))
                : new ProjectManagementQualificationApi.Fact(query,ProjectManagementQualificationApi.Source.PROJECT_MEMBER,
                    new ProjectParticipantFact(100L,9L,Set.of(role),"COLLABORATOR","ACTIVE","S3",3L,7L),null);
    }
}
