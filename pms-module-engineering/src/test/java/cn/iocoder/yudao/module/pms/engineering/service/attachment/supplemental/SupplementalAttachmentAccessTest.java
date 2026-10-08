package cn.iocoder.yudao.module.pms.engineering.service.attachment.supplemental;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.BriefingMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.BriefingDO;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;import org.springframework.mock.web.MockHttpServletRequest;import java.util.*;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.junit.jupiter.api.Assertions.*;
class SupplementalAttachmentAccessTest {
 BriefingMapper briefings=mock(BriefingMapper.class);
 PermissionApi permissions=mock(PermissionApi.class);ProjectScopeApi scopes=mock(ProjectScopeApi.class);ProjectAcceptanceContextApi projects=mock(ProjectAcceptanceContextApi.class);
 SupplementalAttachmentAccess access=new SupplementalAttachmentAccess(briefings,permissions,scopes,projects);
 BriefingDO briefing;
 @BeforeEach void setup(){TenantContextHolder.setTenantId(7L);SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(17L).setTenantId(7L),new MockHttpServletRequest());
  briefing=new BriefingDO();briefing.setId(40L);briefing.setTenantId(7L);briefing.setProjectId(20L);briefing.setVersion(4L);briefing.setStatus(0);
  when(briefings.selectById(40L)).thenReturn(briefing);when(briefings.selectFileOwnerForUpdate(any())).thenReturn(briefing);
  when(permissions.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(true);
  when(scopes.resolveCurrent(any())).thenReturn(scope());when(scopes.lockAndRevalidate(any())).thenReturn(scope());
  var active=new ProjectAcceptanceContextApi.Context(20L,20L,0L,3L,"ACTIVE");when(projects.inspect(any())).thenReturn(active);when(projects.lock(any(),any(),any())).thenReturn(active);
 }
 ProjectScopeResult scope(){return new ProjectScopeResult(20L,3L,Set.of(20L),Set.of());}
 @AfterEach void clear(){TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 Object require(SupplementalAttachmentKind kind,String action,Long expected,boolean freeze){return access.require(kind,7L,17L,"40",kind.getPurpose(),action,true,expected,freeze);}
 @Test void generatedPurposeCannotBeWrittenThroughManualPolicy(){assertThrows(BusinessContractException.class,()->access.require(SupplementalAttachmentKind.BRIEFING,7L,17L,"40","BRIEFING_DOCUMENT_HTML/4","UPLOAD",true,3L,false));}
 @Test void generatedBriefingKeepsManualAttachmentsReadOnly(){briefing.setStatus(1);assertThrows(BusinessContractException.class,()->require(SupplementalAttachmentKind.BRIEFING,"UPLOAD",3L,false));require(SupplementalAttachmentKind.BRIEFING,"READ",3L,false);}
 @Test void briefingGenerateUsesNativeGeneratePermission(){when(permissions.hasAnyPermissions(17L,"pms:sol-briefing:update")).thenReturn(false);require(SupplementalAttachmentKind.BRIEFING,"UPLOAD",3L,true);assertThrows(BusinessContractException.class,()->require(SupplementalAttachmentKind.BRIEFING,"UPLOAD",3L,false));}
 @Test void placeholderOrRevokedScopeBlocksManualWrites(){when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(20L),Set.of(20L)));assertThrows(BusinessContractException.class,()->require(SupplementalAttachmentKind.BRIEFING,"UPLOAD",3L,false));}
 @Test void scopeVersionAndArchivedProjectFailClosed(){when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L,4L,Set.of(20L),Set.of()));assertThrows(BusinessContractException.class,()->require(SupplementalAttachmentKind.BRIEFING,"UPLOAD",3L,false));when(scopes.lockAndRevalidate(any())).thenReturn(scope());when(projects.lock(any(),any(),any())).thenReturn(new ProjectAcceptanceContextApi.Context(20L,20L,0L,3L,"ARCHIVED"));assertThrows(BusinessContractException.class,()->require(SupplementalAttachmentKind.BRIEFING,"UPLOAD",3L,false));}
 @Test void genericOrTemplateWriteCannotUseManualMaterialAdapter(){var adapter=new BriefingAttachmentDeliveryAccess(access);assertThrows(BusinessContractException.class,()->adapter.requireDeliveryAccess(7L,17L,"briefing","40","BRIEFING_DOCUMENT",true,true,3L));assertFalse(adapter.allowsGenericDeliveryActions("briefing"));}
 @Test void manualSourceUsesActualBriefingRootAndNoSyntheticRevision(){var source=new SupplementalAttachmentSources(access).resolve(7L,"SOL","briefing","40","BRIEFING_ATTACHMENT");assertEquals(20L,source.projectId());assertEquals("SOL.BRIEFING_ATTACHMENT",source.sourceCode());assertEquals(40L,source.entityId());assertNull(source.revisionId());assertNull(new SupplementalAttachmentSources(access).resolve(7L,"SOL","BRIEFING_DOCUMENT","40","BRIEFING_DOCUMENT_HTML/4"));}
 @Test void nullOwnerVersionCannotAuthorizeManualUpload(){briefing.setVersion(null);assertThrows(BusinessContractException.class,()->require(SupplementalAttachmentKind.BRIEFING,"UPLOAD",3L,false));}
 @Test void wrongTenantOrPrincipalCannotUseTheManualOwner(){briefing.setTenantId(8L);assertThrows(BusinessContractException.class,()->require(SupplementalAttachmentKind.BRIEFING,"READ",3L,false));briefing.setTenantId(7L);assertThrows(BusinessContractException.class,()->access.require(SupplementalAttachmentKind.BRIEFING,7L,18L,"40","BRIEFING_ATTACHMENT","READ",false,null,false));}
}
