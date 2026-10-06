package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.delivery.DeliveryController;
import cn.iocoder.yudao.module.pms.platform.service.delivery.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AcceptanceResultDeliveryAccessTest {
 final DeliverableChecklistMapper checklists=mock(DeliverableChecklistMapper.class);
 final AcceptanceActivityMapper reports=mock(AcceptanceActivityMapper.class);
 final SatisfactionCollectionTaskMapper tasks=mock(SatisfactionCollectionTaskMapper.class);
 final PermissionApi permissions=mock(PermissionApi.class);
 final ProjectScopeApi scopes=mock(ProjectScopeApi.class);
 final AcceptanceResultDeliveryAccess adapter=new AcceptanceResultDeliveryAccess(checklists,reports,tasks,permissions,scopes);
 final DeliveryMaterialService materials=mock(DeliveryMaterialService.class);
 final DeliveryRequirementService requirements=mock(DeliveryRequirementService.class);
 final ProjectDeliverableUploadPolicyValidator template=new ProjectDeliverableUploadPolicyValidator(null,null,null);
 final DeliveryOwnerAccess access=new DeliveryOwnerAccess(List.of(template,adapter),null,null,null,null,null);
 final DeliveryController controller=new DeliveryController(null,materials,requirements,access,null,null);
 @BeforeEach void before(){TenantContextHolder.setTenantId(7L);SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(10L).setTenantId(7L).setUserType(2),new MockHttpServletRequest());}
 @AfterEach void after(){TenantContextHolder.clear();SecurityContextHolder.clearContext();}
 void root(String type,long tenant,long actor){
  if(type.equals("deliverableChecklist")){var r=new DeliverableChecklistDO();r.setId(9L);r.setTenantId(tenant);r.setProjectId(20L);r.setVersion(2L);r.setStatus(2);when(checklists.selectById(9L)).thenReturn(r);}
  else if(type.equals("acceptanceActivity")){var r=new AcceptanceActivityDO();r.setId(9L);r.setTenantId(tenant);r.setProjectId(20L);r.setVersion(2L);when(reports.selectById(9L)).thenReturn(r);}
  else {var r=new SatisfactionCollectionTaskDO();r.setId(9L);r.setTenantId(tenant);r.setProjectId(20L);r.setVersion(2L);r.setAssignedToUserId(actor);when(tasks.selectById(9L)).thenReturn(r);}
 }
 void granted(){when(permissions.hasAnyPermissions(eq(10L),any(String[].class))).thenReturn(true);when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(20L),Set.of()));}
 @Test void exactAdapterRoutesAllThreeActualControllerReads(){for(String type:List.of("deliverableChecklist","acceptanceActivity","satisfactionCollectionTask")){root(type,7,10);granted();assertFalse(template.supportsEntityType(type));assertTrue(adapter.supportsEntityType(type));assertDoesNotThrow(()->controller.listMaterials("ACC",type,9L,null));assertDoesNotThrow(()->controller.listRequirements("ACC",type,9L));}verify(scopes,times(6)).resolveCurrent(new ProjectCurrentScopeQuery(7L,10L,20L,ProjectScopeApi.ACTION_VIEW));}
 @Test void noFunctionPermissionDeniesActualControllerBeforeMaterialQuery(){root("acceptanceActivity",7,10);assertThrows(Exception.class,()->controller.listMaterials("ACC","acceptanceActivity",9L,null));verifyNoInteractions(materials,scopes);}
 @Test void otherProjectScopeDeniesActualRequirementsController(){root("deliverableChecklist",7,10);when(permissions.hasAnyPermissions(10L,"pms:acc-deliverable-checklist:query")).thenReturn(true);when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(21L,3L,Set.of(21L),Set.of()));assertThrows(Exception.class,()->controller.listRequirements("ACC","deliverableChecklist",9L));verifyNoInteractions(requirements);}
 @Test void foreignTenantDeniesBeforeScope(){root("acceptanceActivity",8,10);assertThrows(Exception.class,()->controller.listMaterials("ACC","acceptanceActivity",9L,null));verifyNoInteractions(permissions,scopes,materials);}
 @Test void satisfactionOtherAssigneeDoesNotInheritGeneralModulePermission(){root("satisfactionCollectionTask",7,11);assertThrows(Exception.class,()->controller.listMaterials("ACC","satisfactionCollectionTask",9L,null));verifyNoInteractions(permissions,scopes,materials);}
 @Test void staleScopeVersionCannotObtainLockedRead(){root("deliverableChecklist",7,10);granted();assertThrows(Exception.class,()->adapter.requireDeliveryAccess(7L,10L,"deliverableChecklist","9",null,false,true,2L));verify(scopes,never()).lockAndRevalidate(any());}
 @Test void scopeRevokedDuringLockDenies(){root("acceptanceActivity",7,10);granted();when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(),Set.of(20L)));assertThrows(Exception.class,()->adapter.requireDeliveryAccess(7L,10L,"acceptanceActivity","9",null,false,true,3L));}
 @Test void changedTreeDuringLockDenies(){root("satisfactionCollectionTask",7,10);granted();when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L,4L,Set.of(20L),Set.of()));assertThrows(Exception.class,()->adapter.requireDeliveryAccess(7L,10L,"satisfactionCollectionTask","9",null,false,true,3L));}
 @Test void panelsNeverCreateNativeBusinessResultsOrGenericUploads(){for(String type:List.of("deliverableChecklist","acceptanceActivity","satisfactionCollectionTask")){assertThrows(Exception.class,()->adapter.requireDeliveryAccess(7L,10L,type,"9",null,true,true,null));assertThrows(Exception.class,()->adapter.validateUpload(7L,10L,type,"9","A","UPLOAD",true,null));}verifyNoInteractions(checklists,reports,tasks,permissions,scopes);}
 @Test void unknownSiblingOwnerIsNeverClaimed(){assertFalse(adapter.supportsEntityType("archiveDocument"));assertFalse(adapter.supportsEntityType("acceptance"));assertFalse(template.supportsEntityType("satisfactionCollectionTask"));}
 @Test void readonlyOwnersProjectNoGenericActionsEvenForAnOperator(){
  var security=mock(cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService.class);
  org.springframework.test.util.ReflectionTestUtils.setField(controller,"securityFrameworkService",security);
  for(boolean operator:List.of(false,true))for(String type:List.of("acceptanceActivity","satisfactionCollectionTask")){
   root(type,7,10);granted();when(security.hasPermission("pms:delivery:operate")).thenReturn(operator);
   assertTrue(controller.allowedActions("ACC",type,9L).getData().isEmpty());
   assertFalse(adapter.allowsGenericDeliveryActions(type));
  }
  verifyNoInteractions(materials);
 }
}
