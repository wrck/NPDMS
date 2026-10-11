package cn.iocoder.yudao.module.pms.project.service.deliverable;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StagePlanDeliverableTargetsTest {
 final ProjectMasterMapper projects=mock(ProjectMasterMapper.class);final ProjectPlanVersionMapper plans=mock(ProjectPlanVersionMapper.class);
 final ProjectDeliverableRuleService service=new ProjectDeliverableRuleService(projects,plans,mock(ProjectRuntimeGraphMapper.class),mock(ProjectGateReferenceInstanceMapper.class),mock(ProjectRuleCompiler.class),mock(ObjectProvider.class));
 ProjectMasterDO project;ProjectPlanVersionDO plan;
 static final String SNAPSHOT="""
 {"executionSchemaVersion":2,"tasks":[{"code":"T","stageCode":"S2","binding":{"targetContextCode":"PLN","targetObjectType":"STAGE_PLAN"},"completionRule":{"operator":"ALL","rules":[{"predicate":"BUSINESS_FACT","parameters":{"factCode":"CONSTRUCTION_PLAN_APPROVED"}},{"predicate":"DELIVERABLE","parameters":{"refCode":"D"}}]}}],"deliverables":[{"code":"D","taskCode":"T","stageCode":"S2","configuration":{"allowedSources":["BUSINESS_RESULT"]}},{"code":"OTHER","taskCode":"T","stageCode":"S2","configuration":{"allowedSources":["BUSINESS_RESULT"]}}]}
 """;
 @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);project=new ProjectMasterDO();project.setId(9L);project.setTenantId(1L);project.setLifecycleStatus("ACTIVE");project.setActivePlanVersionId(15L);
  plan=new ProjectPlanVersionDO();plan.setId(15L);plan.setTenantId(1L);plan.setProjectId(9L);plan.setStatus("EFFECTIVE");plan.setExecutionSnapshot(SNAPSHOT);
  when(projects.selectById(9L)).thenReturn(project);when(plans.selectById(15L)).thenReturn(plan);}
 @AfterEach void clear(){TenantContextHolder.clear();}
 @Test void installationTargetsOnlyTheBoundPageCompletionReference(){
  String install=SNAPSHOT.replace("\"targetContextCode\":\"PLN\",\"targetObjectType\":\"STAGE_PLAN\"", "\"type\":\"PAGE\",\"parameters\":{\"routePath\":\"/pms/engineering/execution/imp-installation\"}");
  plan.setExecutionSnapshot(install);assertEquals(Set.of("D"),service.documentTargets(9L,"IMP.INSTALLATION.INSTALLATION_COMPLETED"));
  assertEquals(install,plan.getExecutionSnapshot());
  plan.setExecutionSnapshot(install.replace("imp-installation","imp-arrival"));assertTrue(service.documentTargets(9L,"IMP.INSTALLATION.INSTALLATION_COMPLETED").isEmpty());
  plan.setExecutionSnapshot(install.replace("BUSINESS_RESULT","UPLOAD"));assertTrue(service.documentTargets(9L,"IMP.INSTALLATION.INSTALLATION_COMPLETED").isEmpty());
 }
 @Test void configurationLogsOnlyMatchTheBoundPageCompletionReference(){
  String install=SNAPSHOT.replace("BUSINESS_RESULT","UPLOAD").replace("\"targetContextCode\":\"PLN\",\"targetObjectType\":\"STAGE_PLAN\"", "\"type\":\"PAGE\",\"parameters\":{\"routePath\":\"/pms/engineering/execution/imp-configuration\"}");
  plan.setExecutionSnapshot(install);assertEquals(Set.of("D"),service.documentTargets(9L,"AST.DEVICE_CONFIGURATION_LOG"));
  assertEquals(install,plan.getExecutionSnapshot());
  plan.setExecutionSnapshot(install.replace("imp-configuration","imp-arrival"));assertTrue(service.documentTargets(9L,"AST.DEVICE_CONFIGURATION_LOG").isEmpty());
  plan.setExecutionSnapshot(install.replace("UPLOAD","BUSINESS_RESULT"));assertTrue(service.documentTargets(9L,"AST.DEVICE_CONFIGURATION_LOG").isEmpty());
 }
 @Test void onlyTheExplicitCompletionReferenceOfThePlanOwnerTaskMatches(){assertEquals(Set.of("D"),service.documentTargets(9L,"PLN.STAGE_PLAN.CONSTRUCTION_PLAN_APPROVED"));assertEquals(SNAPSHOT,plan.getExecutionSnapshot());}
 @Test void otherOwnerDisallowedBusinessResultsAndClosedProjectsDoNotMatch(){
  plan.setExecutionSnapshot(SNAPSHOT.replace("\"PLN\"","\"SOL\""));assertTrue(service.documentTargets(9L,"PLN.STAGE_PLAN.CONSTRUCTION_PLAN_APPROVED").isEmpty());
  plan.setExecutionSnapshot(SNAPSHOT.replace("\"BUSINESS_RESULT\"","\"UPLOAD\""));assertTrue(service.documentTargets(9L,"PLN.STAGE_PLAN.CONSTRUCTION_PLAN_APPROVED").isEmpty());
  project.setLifecycleStatus("NORMAL_CLOSED");assertTrue(service.documentTargets(9L,"PLN.STAGE_PLAN.CONSTRUCTION_PLAN_APPROVED").isEmpty());
 }
}
