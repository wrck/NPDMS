package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.ProjectBusinessObjectDeletionApi.DeletedObject;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskbusiness.ProjectTaskBusinessLinkDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.ProjectTaskBusinessLinkMapper;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectBusinessObjectDeletionServiceTest {
    final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final ProjectTaskBusinessLinkMapper links = mock(ProjectTaskBusinessLinkMapper.class);
    final OperationAuditApi audit = mock(OperationAuditApi.class);
    final ProjectBusinessObjectDeletionService service = new ProjectBusinessObjectDeletionService(projects, scopes, links, audit);
    final DeletedObject deleted = new DeletedObject(7L,"SOL","SITE_SURVEY","12");
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var user = new LoginUser(); user.setId(9L); user.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(user,new MockHttpServletRequest());
        var project = new ProjectMasterDO(); project.setId(7L); project.setTenantId(1L);
        when(projects.selectByIdForUpdate(7L)).thenReturn(project);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(7L,1L,Set.of(7L),Set.of()));
        when(links.unlinkIfMatch(any())).thenReturn(1);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void closesTaskAndStageReferencesWithCasAndKeepsTheHistoryRows() {
        when(links.selectDeletedObjectLiveLinksForUpdate(any())).thenReturn(List.of(link(21L,31L,null),link(22L,null,32L)));
        service.unlinkDeletedObject(deleted);
        verify(links).selectDeletedObjectLiveLinksForUpdate(argThat(q -> q.tenantId()==1L && q.projectId()==7L
                && q.ownerContext().equals("SOL") && q.objectType().equals("SITE_SURVEY") && q.objectId().equals("12")));
        verify(links).unlinkIfMatch(argThat(q -> q.linkId()==21L && q.taskId()==31L && q.stageId()==null && q.expectedVersion()==2 && q.actorId()==9L));
        verify(links).unlinkIfMatch(argThat(q -> q.linkId()==22L && q.taskId()==null && q.stageId()==32L && q.expectedVersion()==2));
        verifyNoMoreInteractions(links);
        verify(audit).record(eq(1L),eq(9L),anyString(),eq("PROJECT_DELETED_BUSINESS_OBJECT_UNLINK"),eq("SITE_SURVEY"),eq("12"),eq("SUCCESS"),anyMap());
    }
    @Test void emptyProjectScopeCannotReadOrCloseReferences() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(7L,1L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class,()->service.unlinkDeletedObject(deleted));
        verifyNoInteractions(links,audit);
    }
    @Test void wrongTenantProjectCannotReadOrCloseReferences() {
        var project = new ProjectMasterDO(); project.setId(7L); project.setTenantId(2L);
        when(projects.selectByIdForUpdate(7L)).thenReturn(project);
        assertThrows(RuntimeException.class,()->service.unlinkDeletedObject(deleted));
        verifyNoInteractions(scopes,links,audit);
    }
    @Test void unauthenticatedAndMismatchedTenantActorsCannotAccessReferences() {
        SecurityContextHolder.clearContext();
        assertThrows(RuntimeException.class,()->service.unlinkDeletedObject(deleted));
        var user=new LoginUser(); user.setId(9L); user.setTenantId(2L);
        SecurityFrameworkUtils.setLoginUser(user,new MockHttpServletRequest());
        assertThrows(RuntimeException.class,()->service.unlinkDeletedObject(deleted));
        verifyNoInteractions(projects,scopes,links,audit);
    }
    @Test void staleLinkVersionFailsTheOwnerTransactionInsteadOfReportingSuccessfulCleanup() {
        when(links.selectDeletedObjectLiveLinksForUpdate(any())).thenReturn(List.of(link(21L,31L,null)));
        when(links.unlinkIfMatch(any())).thenReturn(0);
        assertEquals("DELETED_OBJECT_REFERENCE_CONFLICT",assertThrows(IllegalStateException.class,()->service.unlinkDeletedObject(deleted)).getMessage());
        verifyNoInteractions(audit);
    }
    @Test void foreignObjectReturnedByAQueryIsNeverUnlinked() {
        var foreign=link(21L,31L,null); foreign.setObjectId("13");
        when(links.selectDeletedObjectLiveLinksForUpdate(any())).thenReturn(List.of(foreign));
        assertThrows(RuntimeException.class,()->service.unlinkDeletedObject(deleted));
        verify(links,never()).unlinkIfMatch(any()); verifyNoInteractions(audit);
    }
    private ProjectTaskBusinessLinkDO link(Long id,Long task,Long stage) {
        var link=new ProjectTaskBusinessLinkDO(); link.setId(id); link.setTenantId(1L); link.setProjectId(7L);
        link.setTaskId(task); link.setStageId(stage); link.setVersion(2); link.setOwnerContext("SOL");
        link.setObjectType("SITE_SURVEY"); link.setObjectId("12"); return link;
    }
}
