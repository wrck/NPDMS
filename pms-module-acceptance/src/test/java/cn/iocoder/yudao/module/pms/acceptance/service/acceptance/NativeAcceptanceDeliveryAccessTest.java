package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.archivedocument.ArchiveDocumentDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionCertificateDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.ArchiveDocumentMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
class NativeAcceptanceDeliveryAccessTest {
 @Mock ArchiveDocumentMapper archives;@Mock CompletionCertificateMapper certificates;@Mock PermissionApi permissions;
 @Mock ProjectScopeApi scopes;@Mock ProjectAcceptanceContextApi projects;
 @InjectMocks NativeAcceptanceDeliveryAccess access;
 @BeforeEach void setup(){TenantContextHolder.setTenantId(7L);}
 @AfterEach void clear(){TenantContextHolder.clear();}
 ArchiveDocumentDO archive(int state,long tenant){var row=new ArchiveDocumentDO();row.setId(9L);row.setTenantId(tenant);row.setProjectId(20L);row.setStatus(state);return row;}
 void scope(){when(permissions.hasAnyPermissions(eq(10L),any(String[].class))).thenReturn(true);when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(20L),Set.of()));}
 @Test void archivedReadWorksWithoutActiveProjectOrWritePermission(){when(archives.selectById(9L)).thenReturn(archive(2,7L));scope();assertEquals(3L,access.require(7L,10L,"archiveDocument","9",null,false,false,null));verifyNoInteractions(projects);}
 @Test void archivedAppendOrWithdrawalIsDeniedBeforeScope(){when(archives.selectDeliveryOwnerForUpdate(any())).thenReturn(archive(2,7L));assertThrows(Exception.class,()->access.require(7L,10L,"archiveDocument","9","ARCHIVE_DOCUMENT",true,true,null));verifyNoInteractions(scopes);}
 @Test void foreignTenantNativeRootIsRejected(){when(archives.selectById(9L)).thenReturn(archive(0,8L));assertThrows(Exception.class,()->access.require(7L,10L,"archiveDocument","9",null,false,false,null));verifyNoInteractions(scopes);}
 @Test void noVisibleProjectsDoesNotBecomeUnrestrictedRead(){when(archives.selectById(9L)).thenReturn(archive(0,7L));when(permissions.hasAnyPermissions(eq(10L),any(String[].class))).thenReturn(true);when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(),Set.of(20L)));assertThrows(Exception.class,()->access.require(7L,10L,"archiveDocument","9",null,false,false,null));}
 @Test void changedTreeDuringUploadCompletionIsRejected(){when(archives.selectDeliveryOwnerForUpdate(any())).thenReturn(archive(0,7L));scope();when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L,4L,Set.of(20L),Set.of()));assertThrows(Exception.class,()->access.require(7L,10L,"archiveDocument","9","ARCHIVE_DOCUMENT",true,true,3L));verifyNoInteractions(projects);}
 @Test void closedProjectCannotAcceptNewDocument(){when(archives.selectDeliveryOwnerForUpdate(any())).thenReturn(archive(0,7L));scope();when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(20L),Set.of()));var closed=new ProjectAcceptanceContextApi.Context(20L,20L,1L,3L,"CLOSED");when(projects.inspect(any())).thenReturn(closed);when(projects.lock(any(),eq(1L),eq(3L))).thenReturn(closed);assertThrows(Exception.class,()->access.require(7L,10L,"archiveDocument","9","ARCHIVE_DOCUMENT",true,true,3L));}
 @Test void activeDraftAcceptsOriginalArchiveFormats(){when(archives.selectDeliveryOwnerForUpdate(any())).thenReturn(archive(0,7L));scope();when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(20L),Set.of()));var active=new ProjectAcceptanceContextApi.Context(20L,20L,1L,3L,"ACTIVE");when(projects.inspect(any())).thenReturn(active);when(projects.lock(any(),eq(1L),eq(3L))).thenReturn(active);assertEquals(3L,access.require(7L,10L,"archiveDocument","9","ARCHIVE_DOCUMENT",true,true,3L));}
 @Test void certificatePanelCannotReplaceNativeConfirmation(){var row=new CompletionCertificateDO();row.setId(9L);row.setTenantId(7L);row.setProjectId(20L);row.setStatus(1);when(certificates.selectDeliveryOwnerForUpdate(any())).thenReturn(row);assertThrows(Exception.class,()->access.require(7L,10L,"completionCertificate","9","COMPLETION_CERTIFICATE",true,true,null));verifyNoInteractions(scopes);}
 @Test void nativeAuditDeniesFunctionGrantedActorOutsideProjectScope(){when(permissions.hasAnyPermissions(10L,"pms:acc-completion-certificate:audit")).thenReturn(true);when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(20L),Set.of()));assertThrows(Exception.class,()->access.requireCommand(7L,10L,"completionCertificate",21L,"audit"));verify(scopes,never()).lockAndRevalidate(any());verifyNoInteractions(projects);}
 @Test void nativeAuditRevalidatesManageScopeWithoutChangingLifecycleRules(){when(permissions.hasAnyPermissions(10L,"pms:acc-archive-document:audit")).thenReturn(true);var scoped=new ProjectScopeResult(20L,3L,Set.of(20L),Set.of());when(scopes.resolveCurrent(any())).thenReturn(scoped);when(scopes.lockAndRevalidate(any())).thenReturn(scoped);assertEquals(3L,access.requireCommand(7L,10L,"archiveDocument",20L,"audit"));verify(scopes).resolveCurrent(new ProjectCurrentScopeQuery(7L,10L,20L,ProjectScopeApi.ACTION_MANAGE));verifyNoInteractions(projects);}
 @Test void nativeSubmitRechecksEmptyScopeAfterLock(){when(permissions.hasAnyPermissions(10L,"pms:acc-archive-document:submit")).thenReturn(true);when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(20L),Set.of()));when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(),Set.of(20L)));assertThrows(Exception.class,()->access.requireCommand(7L,10L,"archiveDocument",20L,"submit"));}
 @Test void anonymousNativeConfirmationNeverUsesOtherEmployeeIdentity(){assertThrows(Exception.class,()->access.requireCommand(7L,null,"completionCertificate",20L,"audit"));verifyNoInteractions(permissions,scopes);}

}
