package cn.iocoder.yudao.module.pms.engineering.service.solution;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class SolutionCustomerDocumentServiceTest {
 final SolutionMapper roots=mock(SolutionMapper.class);final PermissionApi permissions=mock(PermissionApi.class);final ProjectScopeApi scopes=mock(ProjectScopeApi.class);final ProjectAcceptanceContextApi projects=mock(ProjectAcceptanceContextApi.class);
 final FileEvidenceApi evidence=mock(FileEvidenceApi.class);final FileArtifactApi files=mock(FileArtifactApi.class);final PlatformDeliveryMaterialApi materials=mock(PlatformDeliveryMaterialApi.class);final NativeGeneratedFileApi downloads=mock(NativeGeneratedFileApi.class);
 final SolutionCustomerDocumentService service=new SolutionCustomerDocumentService(roots,permissions,scopes,projects,evidence,files,materials,downloads);
 SolutionDO row; final SolutionCustomerDocumentService.Attach command=new SolutionCustomerDocumentService.Attach(2,List.of(31L));
 final FileArtifactVersionFact fact=new FileArtifactVersionFact(41L,3,"reference","BUSINESS_DOCUMENT","customer.pdf",12L,"application/pdf","a".repeat(64),"AVAILABLE","ACTIVE",new FileFactVersion(1,2,3),5L);
 @BeforeEach void setup(){TenantContextHolder.setTenantId(7L);SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(10L).setTenantId(7L).setUserType(2),new MockHttpServletRequest());row=new SolutionDO();row.setId(9L);row.setTenantId(7L);row.setProjectId(20L);row.setVersion(2L);row.setStatus(0);row.setRemark("{\"hasCustomerPlan\":\"yes\",\"customerPlanUrl\":\"https://legacy/old.pdf\",\"other\":\"preserved\"}");when(roots.selectById(9L)).thenReturn(row);when(roots.selectResultForUpdate(any())).thenReturn(row);when(roots.updateById(row)).thenReturn(1);}
 @AfterEach void clear(){TenantContextHolder.clear();SecurityContextHolder.clearContext();}
 void authorized(){when(permissions.hasAnyPermissions(eq(10L),any(String[].class))).thenReturn(true);var scope=new ProjectScopeResult(20L,5L,Set.of(20L),Set.of());when(scopes.resolveCurrent(any())).thenReturn(scope);when(scopes.lockAndRevalidate(any())).thenReturn(scope);var project=new ProjectAcceptanceContextApi.Context(20L,20L,1L,5L,"ACTIVE");when(projects.inspect(any())).thenReturn(project);when(projects.lock(any(),eq(1L),eq(5L))).thenReturn(project);}
 void file(){when(evidence.inspectDocument(7L,31L)).thenReturn(new FileEvidenceApi.Document(31L,"PLT","DELIVERY_MATERIAL","SOL:solution:9","IMPLEMENTATION_PLAN","reference",41L,3,"a".repeat(64),"customer.pdf",true));when(files.inspect(any())).thenReturn(fact);when(files.lockAndRevalidate(any())).thenReturn(fact);when(materials.registerNativeUploadedFile(fact)).thenReturn(51L);}
 @Test void creationAndReplacementBindActualSavedRootFileVersionAndPreserveMetadata(){authorized();file();var result=service.attach(9L,command);assertEquals(3L,result.version());assertEquals(List.of(51L),result.materialIds());assertTrue(result.customerPlanUrl().contains("/solutions/9/customer-files/51/customer.pdf"));assertEquals("preserved",JsonUtils.parseObject(row.getRemark(),Map.class).get("other"));verify(files).lockAndRevalidate(argThat(q->q.versionNo()==3&&q.objectId().equals("SOL:solution:9")));assertEquals(0,row.getStatus());}
 @Test void optimisticPluginMutatesEntityVersionOnlyOnce(){authorized();file();when(roots.updateById(row)).thenAnswer(call->{row.setVersion(row.getVersion()+1);return 1;});assertEquals(3L,service.attach(9L,command).version());assertEquals(3L,row.getVersion());}
 @Test void lostResponseRetryOfIdenticalBindingReusesMaterialWithoutOverwritingNewVersion(){authorized();file();row.setVersion(4L);row.setRemark("{\"hasCustomerPlan\":\"yes\",\"customerPlanUrl\":\"/api/v1/pms/solutions/9/customer-files/51/customer.pdf\"}");assertEquals(4L,service.attach(9L,command).version());verify(roots,never()).updateById(any(SolutionDO.class));}
 @Test void staleVersionCannotReplaceDifferentCustomerBody(){authorized();file();row.setVersion(4L);assertThrows(Exception.class,()->service.attach(9L,command));assertTrue(row.getRemark().contains("https://legacy/old.pdf"));verify(roots,never()).updateById(any(SolutionDO.class));}
 @Test void queryOnlyActorCannotAttachCustomerFile(){when(permissions.hasAnyPermissions(10L,"pms:sol-solution:update")).thenReturn(false);assertThrows(Exception.class,()->service.attach(9L,command));verifyNoInteractions(scopes,evidence,files,materials);}
 @Test void crossProjectDeniedBeforeFileMaterial(){when(permissions.hasAnyPermissions(eq(10L),any(String[].class))).thenReturn(true);when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(21L,5L,Set.of(21L),Set.of()));assertThrows(Exception.class,()->service.attach(9L,command));verifyNoInteractions(evidence,files,materials);}
 @Test void crossTenantDeniedBeforeProjectOrFile(){when(permissions.hasAnyPermissions(eq(10L),any(String[].class))).thenReturn(true);row.setTenantId(8L);assertThrows(Exception.class,()->service.attach(9L,command));verifyNoInteractions(scopes,evidence,files,materials);}
 @Test void approvedBodyCannotBeReplacedThroughFileBinding(){authorized();row.setStatus(2);assertThrows(Exception.class,()->service.attach(9L,command));verifyNoInteractions(evidence,files,materials);}
 @Test void foreignRootReferenceCannotBecomeCustomerDocument(){authorized();when(evidence.inspectDocument(7L,31L)).thenReturn(new FileEvidenceApi.Document(31L,"PLT","DELIVERY_MATERIAL","SOL:solution:99","IMPLEMENTATION_PLAN","reference",41L,3,"sha","other.pdf",true));assertThrows(Exception.class,()->service.attach(9L,command));verifyNoInteractions(files,materials);}
 @Test void materialFailureLeavesOldPointerAndPropagates(){authorized();file();when(materials.registerNativeUploadedFile(any())).thenThrow(new IllegalStateException("material failed"));assertThrows(Exception.class,()->service.attach(9L,command));assertTrue(row.getRemark().contains("https://legacy/old.pdf"));verify(roots,never()).updateById(any(SolutionDO.class));}
 @Test void nativeOwnerCasFailureCannotReportBoundSuccess(){authorized();file();when(roots.updateById(row)).thenReturn(0);assertThrows(Exception.class,()->service.attach(9L,command));}
 @Test void treeChangedDuringProjectLockCannotBind(){authorized();when(projects.lock(any(),eq(1L),eq(5L))).thenReturn(new ProjectAcceptanceContextApi.Context(20L,20L,1L,6L,"ACTIVE"));assertThrows(Exception.class,()->service.attach(9L,command));verifyNoInteractions(evidence,files,materials);}
 @Test void legacyHistoryPointerRemainsReadableWithoutInventingFileReference(){service.validatePointer(9L,"{\"customerPlanUrl\":\"https://legacy/old.pdf\"}");verifyNoInteractions(materials,files,downloads);}
 @Test void nativeFileNamesEscapeListDelimiterAndDecodeExactlyOnce(){authorized();file();
  for(String name:List.of("customer,plan.txt","customer plan.txt","客户方案.txt","customer%2Cplan.txt")){
   when(evidence.inspectDocument(7L,31L)).thenReturn(new FileEvidenceApi.Document(31L,"PLT","DELIVERY_MATERIAL","SOL:solution:9","IMPLEMENTATION_PLAN","reference",41L,3,"a".repeat(64),name,true));
   var result=service.attach(9L,new SolutionCustomerDocumentService.Attach(row.getVersion().intValue(),List.of(31L)));
   assertEquals(1,result.customerPlanUrl().split(",").length);
   String encoded=result.customerPlanUrl().substring(result.customerPlanUrl().lastIndexOf('/')+1);
   assertEquals(name,org.springframework.web.util.UriUtils.decode(encoded,java.nio.charset.StandardCharsets.UTF_8));
   assertFalse(encoded.contains(" "));assertFalse(encoded.contains(","));
  }
 }
 @Test void legacyMultipleLinksKeepOriginalMeaning(){service.validatePointer(9L,"{\"customerPlanUrl\":\"https://legacy/one.pdf,https://legacy/two.pdf\"}");verifyNoInteractions(materials,files,downloads);}
}
