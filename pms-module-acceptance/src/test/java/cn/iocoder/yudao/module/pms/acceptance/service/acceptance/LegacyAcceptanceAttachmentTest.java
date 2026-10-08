package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AcceptanceDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AcceptanceMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LegacyAcceptanceAttachmentTest {
    AcceptanceMapper rows=mock(AcceptanceMapper.class);
    PermissionApi permissions=mock(PermissionApi.class);
    ProjectScopeApi scopes=mock(ProjectScopeApi.class);
    ProjectAcceptanceContextApi projects=mock(ProjectAcceptanceContextApi.class);
    LegacyAcceptanceAttachmentFilePolicy policy=new LegacyAcceptanceAttachmentFilePolicy(rows,permissions,scopes,projects);
    AcceptanceDO row;
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(7L); SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(17L).setTenantId(7L),new MockHttpServletRequest());
        row=new AcceptanceDO();row.setId(19L);row.setTenantId(7L);row.setProjectId(20L);row.setStatus(0);row.setVersion(0L);
        when(rows.selectById(19L)).thenReturn(row);when(rows.selectOwnerForUpdate(any())).thenReturn(row);
        when(permissions.hasAnyPermissions(eq(17L),any(String[].class))).thenReturn(true);
        var scope=new ProjectScopeResult(20L,3L,Set.of(20L),Set.of());when(scopes.resolveCurrent(any())).thenReturn(scope);when(scopes.lockAndRevalidate(any())).thenReturn(scope);
        var project=new ProjectAcceptanceContextApi.Context(20L,20L,0L,3L,"ACTIVE");when(projects.inspect(any())).thenReturn(project);when(projects.lock(any(),any(),any())).thenReturn(project);
    }
    @AfterEach void clear(){TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    FileBusinessObjectPolicyFact inspect(String action){return policy.inspect(new FileBusinessObjectPolicyQuery(7L,17L,"ACC","acceptance","19","LEGACY_ACCEPTANCE_ATTACHMENT","actual-slot",action));}
    FileBusinessObjectPolicyFact locked(){return policy.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(7L,17L,"ACC","acceptance","19","LEGACY_ACCEPTANCE_ATTACHMENT","actual-slot","UPLOAD",3L));}
    @Test void draftUploadKeepsLegacyMediaSizeAndRequiresManagedProject(){
        assertEquals(5_242_880L,locked().maxSizeBytes());assertEquals("MULTIPLE",inspect("UPLOAD").cardinality());
        verify(scopes,times(2)).resolveCurrent(argThat(query->ProjectScopeApi.ACTION_MANAGE.equals(query.actionCode())));
        assertTrue(inspect("READ").allowed());
    }
    @Test void allNonDraftStatesAreReadOnly(){for(int status:List.of(1,2,3,4,5)){row.setStatus(status);assertThrows(BusinessContractException.class,()->inspect("UPLOAD"));assertTrue(inspect("READ").allowed());}}
    @Test void queryPermissionDoesNotGrantUpload(){when(permissions.hasAnyPermissions(17L,"pms:acc-acceptance:update")).thenReturn(false);assertThrows(BusinessContractException.class,()->locked());assertTrue(inspect("READ").allowed());}
    @Test void tenantPrincipalAndPlaceholderAreDenied(){row.setTenantId(8L);assertThrows(BusinessContractException.class,()->inspect("READ"));row.setTenantId(7L);when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(20L),Set.of(20L)));assertThrows(BusinessContractException.class,()->locked());}
    @Test void scopeAndProjectChangeUnderLockAreDenied(){when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L,4L,Set.of(20L),Set.of()));assertThrows(BusinessContractException.class,()->locked());when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(20L),Set.of()));when(projects.lock(any(),any(),any())).thenReturn(new ProjectAcceptanceContextApi.Context(20L,20L,0L,3L,"ARCHIVED"));assertThrows(BusinessContractException.class,()->locked());}
    @Test void originUsesActualRootNeverThePassResultType(){var sources=new LegacyAcceptanceAttachmentSources(rows);var scope=sources.resolve(7L,"ACC","acceptance","19","LEGACY_ACCEPTANCE_ATTACHMENT");assertEquals("ACC.LEGACY_ACCEPTANCE_ATTACHMENT",scope.sourceCode());assertEquals(19L,scope.entityId());assertNull(scope.revisionId());assertNull(sources.resolve(7L,"ACC","acceptance","19","acceptance"));}
    @Test void nativeCollectionUsesLockedActualFactsAndDoesNotInventFiles(){
        var files=mock(FileArtifactApi.class);var materials=mock(PlatformDeliveryMaterialApi.class);var registration=new LegacyAcceptanceAttachmentRegistration(policy,files,materials);
        var key=new FileReferenceSetKey("ACC","acceptance","19","LEGACY_ACCEPTANCE_ATTACHMENT");
        var actual=new FileArtifactVersionFact(31L,1,"actual-slot","LEGACY_ACCEPTANCE_ATTACHMENT","a.txt",3L,"text/plain","sha","AVAILABLE","ACTIVE",null,3L);
        var set=new FileReferenceSetFact(key,3L,List.of(actual));when(files.inspectReferenceSets(any())).thenReturn(List.of(set));when(files.lockAndRevalidateReferenceSets(any())).thenReturn(List.of(set));
        registration.register(19L,false);verify(materials).registerNativeSourceFile(actual);
        when(files.lockAndRevalidateReferenceSets(any())).thenThrow(new IllegalStateException("source changed"));assertThrows(IllegalStateException.class,()->registration.register(19L,false));verify(materials,times(1)).registerNativeSourceFile(any());
    }
    @Test void missingActualOwnerVersionCannotGrantWrites(){row.setVersion(null);assertThrows(BusinessContractException.class,()->locked());}
}
