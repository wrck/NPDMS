package cn.iocoder.yudao.module.pms.engineering.service.stageplan;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanBatchDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.completion.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultInventorySource.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StagePlanBusinessResultSourceTest {
 final StagePlanCompletionMapper batches=mock(StagePlanCompletionMapper.class);
 final ConstructionPlanMapper plans=mock(ConstructionPlanMapper.class);
 final StagePlanBusinessResultSource source=new StagePlanBusinessResultSource(batches,plans);
 final Query query=new Query(1L,9L,StagePlanBusinessResultSource.TYPE,"30","30");
 StagePlanBatchDO row;ConstructionPlanDO plan;
 @BeforeEach void setup(){
  TenantContextHolder.setTenantId(1L);row=new StagePlanBatchDO();row.setId(30L);row.setTenantId(1L);row.setProjectId(9L);
  row.setStatus(2);row.setVersion(2L);row.setDurationRevisionId(11L);row.setEffectiveAt(LocalDateTime.now());row.setBpmProcessInstanceId("PI");
  plan=new ConstructionPlanDO();plan.setId(3L);plan.setTenantId(1L);plan.setProjectId(9L);plan.setVersion(2L);plan.setCurrentDurationRevisionId(11L);
  when(plans.selectByProjectId(1L,9L)).thenReturn(plan);when(batches.byId(any())).thenReturn(row);when(batches.current(any())).thenReturn(row);
 }
 @AfterEach void clear(){TenantContextHolder.clear();}
 @Test void approvedBatchIsItsOwnExactIdentityAndKeepsNativeFormationTime(){
  var result=source.inspect(query).result();assertEquals(Validity.CURRENT,result.validity());assertEquals("30",result.resultId());
  assertEquals(row.getEffectiveAt(),result.formedAt());assertNull(result.businessRevision());
 }
 @Test void oldBatchAndChangedDurationCannotBeCurrentEvidence(){
  var newer=new StagePlanBatchDO();newer.setId(31L);newer.setTenantId(1L);newer.setProjectId(9L);when(batches.current(any())).thenReturn(newer);
  assertEquals(Validity.NOT_CURRENT,source.inspect(query).result().validity());
  when(batches.current(any())).thenReturn(row);plan.setCurrentDurationRevisionId(12L);
  assertEquals(Validity.NOT_CURRENT,source.inspect(query).result().validity());
 }
 @Test void pendingRejectedAndInconsistentRowsDoNotFormResults(){
  row.setStatus(1);assertEquals(Status.NOT_FORMED,source.inspect(query).status());row.setStatus(3);
  assertEquals(Status.NOT_FORMED,source.inspect(query).status());row.setStatus(2);row.setEffectiveAt(null);
  assertEquals(Status.UNAVAILABLE,source.inspect(query).status());
 }
 @Test void tenantProjectAndExactIdentityMismatchAreRejected(){
  assertThrows(IllegalArgumentException.class,()->source.inspect(new Query(2L,9L,StagePlanBusinessResultSource.TYPE,"30",null)));
  assertThrows(IllegalArgumentException.class,()->source.inspect(new Query(1L,9L,StagePlanBusinessResultSource.TYPE,"30","31")));
  row.setProjectId(10L);assertThrows(IllegalArgumentException.class,()->source.inspect(query));
 }
 @Test void sharedReadRejectsForeignCurrentPlanAndDuration(){
  var foreign=new StagePlanBatchDO();foreign.setId(31L);foreign.setTenantId(1L);foreign.setProjectId(10L);
  assertThrows(IllegalArgumentException.class,()->source.inspectRead(query,row,foreign,plan));
  plan.setTenantId(2L);
  assertEquals(Validity.NOT_CURRENT,source.inspectRead(query,row,row,plan).result().validity());
 }
 @Test void inventoryHonorsEmptyObjectScopeAndDoesNotEnumerateHistoricalBatches(){
  var empty=source.inventory(new InventoryQuery(1L,9L,StagePlanBusinessResultSource.TYPE,false,List.of(),null,10));assertTrue(empty.observations().isEmpty());
  assertThrows(IllegalArgumentException.class,()->source.inventory(new InventoryQuery(1L,9L,StagePlanBusinessResultSource.TYPE,true,null,null,10)));
  assertEquals(1,source.inventory(new InventoryQuery(1L,9L,StagePlanBusinessResultSource.TYPE,false,List.of("30"),null,10)).observations().size());
 }
 @Test void lockedValidationUsesOwnerLocksAndRejectsStaleDuration(){
  plan.setCurrentDurationRevisionId(12L);when(plans.selectForUpdate(any())).thenReturn(plan);
  assertEquals(Validity.NOT_CURRENT,source.lockAndInspect(query).result().validity());
  verify(batches).byId(new StagePlanCompletionQuery(1L,9L,30L,true));verify(batches).current(new StagePlanCompletionQuery(1L,9L,null,true));
 }
}
