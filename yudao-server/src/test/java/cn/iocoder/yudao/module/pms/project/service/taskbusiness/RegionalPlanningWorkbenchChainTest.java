package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.*;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.*;
import cn.iocoder.yudao.module.pms.project.api.participant.*;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.ProjectTaskBusinessLinkMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.*;
import cn.iocoder.yudao.module.pms.project.service.projectscope.*;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.*;
import cn.iocoder.yudao.module.pms.platform.api.businessview.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.StagePlanApprovalCompletionProvider;
import cn.iocoder.yudao.module.system.api.permission.*;
import cn.iocoder.yudao.module.system.api.permission.dto.*;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 装配层真Service链；冻结绑定按84575只读证据，未连接真实数据库。 */
class RegionalPlanningWorkbenchChainTest {
 ProjectTaskQueryService workbench;ProjectTaskBusinessService business;
 ProjectMasterMapper projects;ProjectMemberAssignmentMapper members;
 ProjectTaskRuntimeMapper tasks;ProjectTaskAssignmentMapper assignments;
 ProjectTaskExecutionContractMapper contracts;ProjectTreeScopeService treeScopes;
 ProjectOrganizationScopeService organizationScopes;OrganizationScopeApi organizations;
 PermissionApi permissions;ProjectScopeApi scope;ProjectMasterDO project;
 ProjectTaskInstanceDO task;ProjectTaskExecutionContractDO contract;
 TaskBusinessAccess access;
 ProjectNodeExecutionApi execution;
 ProjectManagementQualificationApi management;
 PermissionCommonApi common;
 @BeforeEach void setup(){
  TenantContextHolder.setTenantId(1L);SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(9L).setTenantId(1L),new MockHttpServletRequest());
  projects=mock(ProjectMasterMapper.class);members=mock(ProjectMemberAssignmentMapper.class);tasks=mock(ProjectTaskRuntimeMapper.class);assignments=mock(ProjectTaskAssignmentMapper.class);contracts=mock(ProjectTaskExecutionContractMapper.class);
  permissions=mock(PermissionApi.class);scope=mock(ProjectScopeApi.class);treeScopes=mock(ProjectTreeScopeService.class);organizations=mock(OrganizationScopeApi.class);var users=mock(AdminUserApi.class);
  when(permissions.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(true);when(permissions.hasAnyRoles(anyLong(),any(String[].class))).thenReturn(true);
  // This fixture grants planning functions; task metadata functions are independently withheld.
  for(var permission:List.of("pms:project-task:create","pms:project-task:update","pms:project-task:move","pms:project-task:assign"))when(permissions.hasAnyPermissions(9L,permission)).thenReturn(false);
  when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L,7L,Set.of(100L),Set.of()));
  when(treeScopes.resolve(any())).thenReturn(new ProjectTreeScopeService.ProjectTreeScope(100L,7L,Set.of(100L),Set.of(),Set.of()));
  when(users.getUser(9L)).thenReturn(new AdminUserRespDTO().setId(9L).setStatus(0));
  var row=new UserCompanyDepartmentScopeRespDTO();row.setUserId(9L);row.setCompanyId(11L);row.setDepartmentId(21L);row.setScopeRole("SERVICE_MANAGER");when(organizations.getActiveScopes(9L)).thenReturn(List.of(row));
  project=new ProjectMasterDO();project.setId(100L);project.setTenantId(1L);project.setVersion(3L);project.setLifecycleStatus("ACTIVE");project.setCompanyId(11L);project.setDepartmentId(21L);project.setTaskTreeVersion(1L);
  when(projects.selectById(100L)).thenReturn(project);
  when(projects.selectOrganizationProjectIds(any())).thenAnswer(i->{cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.OrganizationProjectScopeQuery q=i.getArgument(0);return (!q.activeOnly()||"ACTIVE".equals(project.getLifecycleStatus()))&&q.organizations().stream().anyMatch(r->Objects.equals(r.companyId(),project.getCompanyId())&&(r.departmentId()==null||Objects.equals(r.departmentId(),project.getDepartmentId())))?List.of(100L):List.of();});
  organizationScopes=new ProjectOrganizationScopeService(organizations,permissions,users,projects);
  when(organizations.inspectRoleScope(any())).thenAnswer(call->{OrganizationRoleScopeQuery query=call.getArgument(0);return organizations.getActiveScopes(query.userId()).stream().filter(grant->Objects.equals(grant.getCompanyId(),query.companyId())&&(grant.getDepartmentId()==null||Objects.equals(grant.getDepartmentId(),query.departmentId()))&&query.requiredRoleCodes().contains(grant.getScopeRole())).findFirst().orElse(null);});
  management=new ProjectManagementQualificationApiImpl(new ProjectParticipantFactApiImpl(projects,members),new ProjectRegionalManagerFactApiImpl(projects,organizations),projects,mock(cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectCurrentStageService.class));
  task=new ProjectTaskInstanceDO();task.setId(10L);task.setTenantId(1L);task.setProjectId(100L);task.setCode("PLAN_CONSTRUCTION");task.setName("施工计划");task.setStageCode("S2");task.setStatus("IN_PROGRESS");task.setVersion(1);when(tasks.selectTask(any())).thenReturn(task);
  contract=new ProjectTaskExecutionContractDO();contract.setId(30L);contract.setTenantId(1L);contract.setProjectTaskId(10L);contract.setContractVersion(1);contract.setWorkBindingTypeCode("BUSINESS_OBJECT");contract.setTargetContextCode("PLN");contract.setTargetObjectType("STAGE_PLAN");contract.setTargetObjectKey("STAGE_PLAN");contract.setComponentKey("PLN_STAGE_PLAN_APPROVAL");contract.setBindingParameterSnapshot("{\"businessViewRevisionId\":40,\"instanceResolutionStrategy\":\"REFERENCE_EXISTING\",\"contextMapping\":{\"projectId\":\"projectId\"}}");when(contracts.selectCurrentByTaskId(10L)).thenReturn(contract);
  var links=mock(ProjectTaskBusinessLinkMapper.class);when(links.selectCurrentContract(any())).thenReturn(contract);
  execution=mock(ProjectNodeExecutionApi.class);when(execution.inspect(any())).thenReturn(new ProjectTaskExecutionContext(100L,3L,10L,1,30L,1,50L,60L,1,1,70L,1,true,LocalDateTime.now()));
  var views=mock(BusinessViewQueryApi.class);when(views.getRevision(any())).thenReturn(new BusinessViewRevision(40L,"STAGE_PLAN","stage-plan",1L,"PLN",BusinessViewComponentProvider.ViewSource.PAGE,"PLN_STAGE_PLAN_APPROVAL","1",null,JsonUtils.parseTree("{}"),JsonUtils.parseTree("[\"QUERY\"]"),"q","c","p",null,null,1L,"PUBLISHED",Set.of()));
  access=new TaskBusinessAccess(scope,projects,members,assignments,tasks,permissions,treeScopes,organizationScopes,management);
  var provider=new StagePlanApprovalCompletionProvider(mock(cn.iocoder.yudao.module.pms.engineering.dal.mysql.completion.StagePlanCompletionMapper.class),scope,permissions,execution,mock(cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper.class));
  business=new ProjectTaskBusinessService(access,new TaskBusinessProviderRegistry(List.of(provider)),links,tasks,contracts,mock(PlatformCommandExecutionApi.class),mock(OperationAuditApi.class),views,execution);
  var binding=new TaskBusinessBindingHostProvider(business,tasks,assignments,members,mock(TaskStateMachineMapper.class),scope,permissions,management);
  var versions=mock(ProjectTreeVersionMapper.class);var version=new ProjectTreeVersionDO();version.setTreeVersion(7L);when(versions.selectLatestActive(100L)).thenReturn(version);
  common=mock(PermissionCommonApi.class);when(common.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(true);
  for(var permission:List.of("pms:project-task:create","pms:project-task:update","pms:project-task:move","pms:project-task:assign","pms:project-plan:manage"))when(common.hasAnyPermissions(9L,permission)).thenReturn(false);
  workbench=new ProjectTaskQueryService(projects,versions,treeScopes,members,mock(ProjectStageInstanceMapper.class),tasks,assignments,contracts,new TaskBindingHostRegistry(List.of(binding)),mock(cn.iocoder.yudao.module.pms.project.service.operation.ProjectResultSubscriptionObservationQuery.class),mock(cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService.class),common,organizationScopes,management);
 }
 @AfterEach void cleanup(){TenantContextHolder.clear();SecurityContextHolder.clearContext();}
 @Test void fullyPermittedRegionalManagerHasTheSameTaskManagementActionsWithoutInventingMembership(){
  for(var permission:List.of("pms:project-task:create","pms:project-task:update","pms:project-task:move","pms:project-task:assign"))when(permissions.hasAnyPermissions(9L,permission)).thenReturn(true);
  for(var permission:List.of("pms:project-task:create","pms:project-task:update","pms:project-task:move","pms:project-task:assign"))when(common.hasAnyPermissions(9L,permission)).thenReturn(true);
  assertEquals(ProjectManagementQualificationApi.Source.REGIONAL_MANAGER,management.inspect(new ProjectManagementQualificationApi.Query(1L,100L,9L)).source());
  var actor=new TaskWorkbenchActor(1L,9L,"full-region");
  assertTrue(workbench.getMaintenanceAccess(10L,actor).canUpdate());
  assertTrue(workbench.getWorkbench(10L,actor).getAllowedActions().containsAll(Set.of("UPDATE","MOVE","ASSIGN")));
  assertEquals(Set.of("CREATE"),workbench.getWorkspace(100L,actor).getAllowedActions());
  assertTrue(access.writable(task,project,new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context(1L,9L,100L,10L,"full-region")));
  verify(members,never()).insert(any(ProjectMemberAssignmentDO.class));
 }
 @Test void anotherAssigneeStillBlocksGenericExecutionWhileIndependentPlanningOwnerRemainsAvailable(){
  when(permissions.hasAnyPermissions(9L,"pms:project-task:update")).thenReturn(true);
  when(common.hasAnyPermissions(9L,"pms:project-task:update")).thenReturn(true);
  designateAnotherUser();
  var subject=new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context(1L,9L,100L,10L,"designated-other");
  assertFalse(access.writable(task,project,subject));assertFalse(access.executable(task,project,subject));
  assertTrue(workbench.getMaintenanceAccess(10L,new TaskWorkbenchActor(1L,9L,"designated-other")).canUpdate());
  assertTrue(business.getContext(10L,1L,9L,"independent-owner").executionAllowed());
 }
 private void designateAnotherUser(){
  var assigned=new cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO();assigned.setProjectTaskId(10L);assigned.setAssigneeUserId(88L);
  when(assignments.selectCurrent(any())).thenReturn(List.of(assigned));
 }
 @Test void regionalNonMemberCanOpenTheRealStageTaskWorkbenchWithoutGettingTaskWriteActions(){
  var result=workbench.getWorkbench(10L,new TaskWorkbenchActor(1L,9L,"regional-plan"));
  assertEquals("BUSINESS_OBJECT",result.getBindingType());assertEquals("PLN:STAGE_PLAN:STAGE_PLAN",result.getTrustedTargetRef());assertNull(result.getRecoverableError());assertTrue(result.getAllowedActions().isEmpty());
  assertFalse(workbench.getMaintenanceAccess(10L,new TaskWorkbenchActor(1L,9L,"regional-plan")).canUpdate());
  assertTrue(workbench.getWorkspace(100L,new TaskWorkbenchActor(1L,9L,"regional-plan")).getAllowedActions().isEmpty());
 }
 @Test void regionalNonMemberGetsWritablePlanningOwnerContextWithoutTaskAssignmentOrRelationWrite(){
  var result=business.getContext(10L,1L,9L,"regional-plan");
  assertNull(result.recoverableError());assertTrue(result.executionAllowed());assertEquals(Set.of("QUERY"),result.ownerActions());
  assertFalse(access.writable(task,project,new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context(1L,9L,100L,10L,"regional-plan")));
 }
 @Test void regionalReadMakesTheStageTaskTreeReachableButNotAnAssignmentOrLifecycleGrant(){
  when(tasks.selectTree(any())).thenAnswer(i->{cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskTreeQuery query=i.getArgument(0);return query.visibilityQuery().fullProjectAccess()?List.of(task):List.of();});
  var request=new cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskTreeQueryReqVO();request.setMode("DIRECT_CHILDREN");request.setStageCode("S2");
  var visible=workbench.getTasks(100L,request,new TaskWorkbenchActor(1L,9L,"stage-tree"));assertEquals(1,visible.getRows().size());assertFalse(visible.getRows().getFirst().isPlaceholder());
  when(organizations.getActiveScopes(9L)).thenReturn(List.of());
  assertTrue(workbench.getTasks(100L,request,new TaskWorkbenchActor(1L,9L,"no-scope-tree")).getRows().isEmpty());
  verify(assignments,never()).insert(any(cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO.class));
 }
 @Test void regionalPlanningOperationStillRequiresEditScopeAndAnActiveProject(){
  when(scope.resolveCurrent(any())).thenAnswer(i->{ProjectCurrentScopeQuery query=i.getArgument(0);return new ProjectScopeResult(100L,7L,ProjectScopeApi.ACTION_EDIT.equals(query.actionCode())?Set.of():Set.of(100L),Set.of());});
  assertFalse(business.getContext(10L,1L,9L,"no-edit").executionAllowed());
  project.setLifecycleStatus("NORMAL_CLOSED");
  assertThrows(RuntimeException.class,()->business.getContext(10L,1L,9L,"closed-project"));
 }
 @Test void planningOperationKeepsTheOriginalNonterminalRuleAndStillNeedsAWritableExecution(){
  task.setStatus("PENDING_START");
  assertTrue(business.getContext(10L,1L,9L,"pending-with-current-writable-round").executionAllowed());
  assertTrue(workbench.getWorkbench(10L,new TaskWorkbenchActor(1L,9L,"pending-plan")).getAllowedActions().isEmpty());
  task.setStatus("DONE");assertFalse(business.getContext(10L,1L,9L,"ended-task").executionAllowed());
 }
 @Test void crossRegionAndMissingOrExpiredOrganizationGrantCannotOpenWorkbench(){
  project.setDepartmentId(22L);
  assertThrows(RuntimeException.class,()->workbench.getWorkbench(10L,new TaskWorkbenchActor(1L,9L,"cross-region")));
  project.setDepartmentId(21L);when(organizations.getActiveScopes(9L)).thenReturn(List.of());
  assertThrows(RuntimeException.class,()->business.getContext(10L,1L,9L,"expired-or-no-scope"));
 }
 @Test void unconfirmedMembershipCannotReplaceARegionalOrTaskReadGrant(){
  when(organizations.getActiveScopes(9L)).thenReturn(List.of());
  var unconfirmed=new ProjectMemberAssignmentDO();unconfirmed.setProjectId(100L);unconfirmed.setMemberRole("PROJECT_MANAGER");unconfirmed.setAuthorizationConfirmed(false);
  when(members.selectActiveByUser(any())).thenReturn(List.of(unconfirmed));
  assertThrows(RuntimeException.class,()->workbench.getWorkbench(10L,new TaskWorkbenchActor(1L,9L,"unconfirmed")));
  verify(members).selectAuthorizedByUser(any());verify(members,never()).selectActiveByUser(any());
 }
 @Test void regionalReadDoesNotWaiveTaskExecutionFunctionOrOwnerBusinessPermission(){
  when(permissions.hasAnyPermissions(9L,"pms:project-task:execute")).thenReturn(false);
  assertFalse(business.getContext(10L,1L,9L,"no-task-execute").executionAllowed());
  var subject=new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context(1L,9L,100L,10L,"planning-functions");
  assertFalse(access.planningOperationAllowed(TaskBusinessAccess.PlanningOperationQuery.fromContract(task,project,subject,contract)));
  when(permissions.hasAnyPermissions(9L,"pms:project-task:execute")).thenReturn(true);
  when(permissions.hasAnyPermissions(9L,"pms:imp-stage-plan:create","pms:imp-stage-plan:update","pms:imp-stage-plan:submit","pms:construction-plan:duration-manage")).thenReturn(false);
  assertFalse(business.getContext(10L,1L,9L,"no-owner-operation").executionAllowed());
  assertFalse(access.planningOperationAllowed(TaskBusinessAccess.PlanningOperationQuery.fromContract(task,project,subject,contract)));
 }
 @Test void unrelatedBusinessReadonlySnapshotAndEndedRoundNeverGainPlanningOperation(){
  var subject=new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context(1L,9L,100L,10L,"negative");
  contract.setTargetObjectType("OTHER");
  assertFalse(access.planningOperationAllowed(TaskBusinessAccess.PlanningOperationQuery.fromContract(task,project,subject,contract)));
  contract.setTargetObjectType("STAGE_PLAN");contract.setBindingParameterSnapshot("{\"businessViewRevisionId\":40,\"instanceResolutionStrategy\":\"READ_ONLY_AGGREGATE\"}");
  assertFalse(access.planningOperationAllowed(TaskBusinessAccess.PlanningOperationQuery.fromContract(task,project,subject,contract)));
  contract.setBindingParameterSnapshot("{\"businessViewRevisionId\":40,\"instanceResolutionStrategy\":\"REFERENCE_EXISTING\"}");
  when(execution.inspect(any())).thenReturn(new ProjectTaskExecutionContext(100L,3L,10L,1,30L,1,50L,60L,1,1,70L,1,false,null));
  assertFalse(business.getContext(10L,1L,9L,"ended-round").executionAllowed());
 }
 @Test void actualOperationResolverRecognizesOnlyTheCurrentFrozenPlanningBinding(){
  designateAnotherUser(); // Isolate the independent planning admission from ordinary unassigned-task execution.
  project.setActivePlanVersionId(50L);
  var plans=mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper.class);
  var nodes=mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper.class);
  var frozen=new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot();
  var node=new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.TaskContract();node.setNodeKey("task:plan");node.setCode("PLAN_CONSTRUCTION");node.setBinding(TaskBusinessAccess.PlanningOperationQuery.fromContract(task,project,new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context(1L,9L,100L,10L,"resolver"),contract).binding());frozen.setTasks(List.of(node));
  var plan=new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO();plan.setId(50L);plan.setTenantId(1L);plan.setProjectId(100L);plan.setStatus("EFFECTIVE");plan.setExecutionSnapshot(JsonUtils.toJsonString(frozen));when(plans.selectEffective(any())).thenReturn(plan);
  var round=new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO();round.setId(60L);round.setTenantId(1L);round.setProjectId(100L);round.setPlanVersionId(50L);round.setNodeKind("TASK");round.setNodeInstanceId(10L);round.setNodeKey("task:plan");round.setContractId(30L);round.setStatus("ACTIVE");when(nodes.selectCurrent(any())).thenReturn(List.of(round));
  var resolver=new ProjectOperationContextResolver(access,mock(cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.class),projects,plans,nodes,mock(cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper.class),execution,scope,permissions);
  assertTrue(resolver.resolve(100L,"TASK",10L,null).permitted());
  node.getBinding().setTargetObjectType("OTHER");plan.setExecutionSnapshot(JsonUtils.toJsonString(frozen));
  assertFalse(resolver.resolve(100L,"TASK",10L,null).permitted());
 }
 @Test void admittedPlanningContextCanSaveThroughTheOriginalStagePlanOwnerWithVersionAndScopeProtection(){
  assertTrue(business.getContext(10L,1L,9L,"save-plan").executionAllowed());
  var owner=new cn.iocoder.yudao.module.pms.engineering.service.stageplan.StagePlanBatchServiceImpl();
  var batches=mock(cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.StagePlanBatchMapper.class);
  var items=mock(cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.StagePlanItemMapper.class);
  var batch=new cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanBatchDO();batch.setId(80L);batch.setProjectId(100L);batch.setStatus(0);batch.setVersion(1L);
  var item=new cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanItemDO();item.setId(81L);item.setBatchId(80L);item.setPhaseCode("S2");
  when(batches.selectById(80L)).thenReturn(batch);when(batches.updateById(any(cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanBatchDO.class))).thenReturn(1);when(items.selectListByBatchId(80L)).thenReturn(List.of(item));
  when(scope.resolveAllCurrent(any())).thenReturn(Set.of(100L));
  var guard=mock(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.class);
  var expectedAdmission=new cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.WriteRequest(100L,"PLN","STAGE_PLAN",null);
  org.springframework.test.util.ReflectionTestUtils.setField(owner,"batchMapper",batches);org.springframework.test.util.ReflectionTestUtils.setField(owner,"itemMapper",items);org.springframework.test.util.ReflectionTestUtils.setField(owner,"projectScopeApi",scope);
  org.springframework.test.util.ReflectionTestUtils.setField(owner,"executionGuard",guard);
  org.springframework.test.util.ReflectionTestUtils.setField(owner,"stagePlanApi",mock(cn.iocoder.yudao.module.pms.project.api.stageplan.ProjectStagePlanApi.class));
  org.springframework.test.util.ReflectionTestUtils.setField(owner,"constructionPlanMapper",mock(cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper.class));
  var request=new cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanItemUpdateReqVO();request.setId(80L);request.setVersion(1);request.setRemark("region-authorized-plan");
  var edit=new cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanItemUpdateReqVO.Item();edit.setId(81L);edit.setPlanStart(java.time.LocalDate.of(2026,10,1));edit.setPlanEnd(java.time.LocalDate.of(2026,10,5));request.setItems(List.of(edit));
  assertEquals(80L,owner.updateItems(request).getId());verify(items).updateById(any(cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanItemDO.class));
  verify(guard).lockForWrite(expectedAdmission);
  clearInvocations(batches,items);request.setVersion(0);assertThrows(RuntimeException.class,()->owner.updateItems(request));verify(items,never()).updateById(any(cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanItemDO.class));
  request.setVersion(1);when(scope.resolveAllCurrent(any())).thenReturn(Set.of());assertThrows(RuntimeException.class,()->owner.updateItems(request));
  when(scope.resolveAllCurrent(any())).thenReturn(Set.of(100L));clearInvocations(batches,items,guard);
  var rejected=new IllegalStateException("EXECUTION_ADMISSION_REJECTED");doThrow(rejected).when(guard).lockForWrite(expectedAdmission);
  assertSame(rejected,assertThrows(IllegalStateException.class,()->owner.updateItems(request)));
  verify(guard).lockForWrite(expectedAdmission);
  verify(items,never()).updateById(any(cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanItemDO.class));
  verify(batches,never()).updateById(any(cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanBatchDO.class));
 }
}
