package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncProjectReferences;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncPublishedProjects;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Execute the real task's protection path without schema or database writes. */
class InstallationHistoryProtectionTest {
 private P33InstallationSyncTask.TargetMapper mapper;private P33InstallationSyncTask task;
 private SyncIdentityStore identities;private SqlSession session;
 private BaseSyncProjectReferences projects;
 private final LocalDateTime at=LocalDateTime.of(2020,1,1,0,0);
 private P33InstallationSyncTask.Source source;
 @BeforeEach void setup(){
  mapper=mock(P33InstallationSyncTask.TargetMapper.class);when(mapper.installLocationCapacity()).thenReturn(1024);
  var publications=mock(BaseSyncPublishedProjects.class);projects=mock(BaseSyncProjectReferences.class);
  var member=new BaseSyncPublishedProjects.Member("1",1000L,"10","P-1","P-1",null);
  when(publications.load(any())).thenReturn(new BaseSyncPublishedProjects.Snapshot(10,Map.of("1",member),Map.of(1000L,member)));
  when(projects.verifyPublished(any())).thenReturn(Map.of("1",new BaseSyncProjectReferences.Identity("1",1000L,1L,0L,"P-1","10",null,null,null,"ACTIVE",false)));
  var device=new P33InstallationSyncTask.Device().setId(9L).setSn("SN-1").setRequestedSn("SN-1");when(mapper.selectDevices(any())).thenReturn(List.of(device));
  identities=mock(SyncIdentityStore.class);when(identities.findByKeys(any())).thenReturn(Map.of());
  session=mock(SqlSession.class);
  task=spy(new P33InstallationSyncTask(mapper,publications,projects,org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDeviceProjects.class)));
  source=new P33InstallationSyncTask.Source().setSourceId(1L).setOriginalProjectId(1L).setBarcode("SN-1").setTransferFlag("-1").setTransferPending(false).setInstallAddress("original address").setLocationSnapshot("{\"fallbackLocation\":\"original address\"}").setCreateTime(at).setSourceCreator("").setSourceUpdater("");
  doAnswer(call->new ArrayList<>(List.of(source))).when(task).syncDataQuery(any());
 }
 private P33InstallationSyncTask.Row existing(){return new P33InstallationSyncTask.Row().setId(99L).setProjectId(1000L).setEquipmentId(9L).setCode("P-1-AZ0001").setCreateTime(at).setDeleted(false).setTenantId(1L);}
 private SyncCounts preview(){return task.execute(new SyncContext<Void>(1,2,3,true,128,10000,at,mock(SyncSource.class),identities,mock(SyncReferences.class),"Task",session,false,false));}
 private void noWrites(){verify(session,never()).insert(anyString(),any());verify(identities,never()).saveAll(argThat(rows->!rows.isEmpty()));}
 @Test void sameProjectDeviceAndOriginalTimeCannotAdoptAnUnownedHistory(){
  when(mapper.selectExisting(any())).thenReturn(List.of(existing()));
  assertEquals("P33_ORIGINAL_INSTALLATION_CANNOT_ADOPT",assertThrows(IllegalArgumentException.class,this::preview).getMessage());noWrites();
 }
 @Test void everyIdentityComponentRemainsNecessaryForAnUnownedCollision(){
  for(var row:List.of(existing().setProjectId(2000L),existing().setEquipmentId(10L),existing().setCreateTime(at.minusSeconds(1)))){
   when(mapper.selectExisting(any())).thenReturn(List.of(row));assertEquals(new SyncCounts(1,1,0,0,0),preview());assertNull(source.getTarget().getId());assertEquals("original address",source.getTarget().getInstallLocation());
  }noWrites();
 }
 @Test void deletedOrDuplicateExistingRowsDoNotWeakenOriginalHistoryProtection(){
  var row=existing().setDeleted(true);when(mapper.selectExisting(any())).thenReturn(List.of(row,row));
  assertEquals("P33_ORIGINAL_INSTALLATION_CANNOT_ADOPT",assertThrows(IllegalArgumentException.class,this::preview).getMessage());noWrites();
 }
 @Test void anOwnedHistoryStillReplaysAndATamperedHistoryStillFails(){
  var row=existing();when(mapper.selectExisting(any())).thenReturn(List.of(row));
  when(identities.findByKeys(any())).thenReturn(Map.of("1",new SyncIdentityStore.Identity("1",99L,P33InstallationSyncTask.fingerprint(row,source))));
  assertEquals(new SyncCounts(1,0,0,1,0),preview());assertSame(row,source.getTarget());
  row.setInstallLocation("changed");assertEquals("P33_EXISTING_INSTALLATION_PROTECTED",assertThrows(IllegalArgumentException.class,this::preview).getMessage());noWrites();
 }
 @Test void referenceServiceFailureRetainsItsStageAndNumericCode(){
  when(projects.verifyPublished(any())).thenThrow(new ServiceException(120001,"private diagnostic text"));
  assertEquals("P33_PROJECT_REFERENCE_REJECTED:code=120001",assertThrows(IllegalStateException.class,this::preview).getMessage());noWrites();
 }
 @Test void rejectedProjectManageScopeStillPreventsAnyHistoryWrite(){
  when(projects.currentManageScope(any())).thenThrow(new org.springframework.security.access.AccessDeniedException("manage rejected"));
  assertEquals("manage rejected",assertThrows(org.springframework.security.access.AccessDeniedException.class,this::preview).getMessage());noWrites();
 }
 @Test void unresolvedAddressSnapshotHasOnlyTheApprovedDisplayProperty(){
  assertEquals(new SyncCounts(1,1,0,0,0),preview());
  var row=source.getTarget();assertEquals("UNRESOLVED",row.getLocationResolutionStatus());
  assertNull(row.getAddressId());assertNull(row.getAddressVersion());assertNull(row.getAddressSnapshot());
  assertEquals(Map.of("fallbackLocation","original address"),JsonUtils.parseObject(row.getLocationSnapshot(),Map.class));
  assertEquals("original address",row.getInstallLocation());noWrites();
 }
 @Test void missingDeviceAndHeldTransferDoNotBlockTheOrdinaryHistoryBatch(){
  var missing=new P33InstallationSyncTask.Source().setSourceId(2L).setOriginalProjectId(1L).setBarcode("UNRESOLVED").setTransferPending(false).setInstallAddress("raw missing").setLocationSnapshot("{\"fallbackLocation\":\"raw missing\"}").setCreateTime(at);
  var held=new P33InstallationSyncTask.Source().setSourceId(3L).setOriginalProjectId(1L).setBarcode("SN-1").setTransferPending(true).setInstallAddress("raw held").setLocationSnapshot("{\"fallbackLocation\":\"raw held\"}").setCreateTime(at);
  doAnswer(call->new ArrayList<>(List.of(source,missing,held))).when(task).syncDataQuery(any());
  when(session.insert(anyString(),any())).thenAnswer(call->call.<SyncBatch<?,?>>getArgument(1).rows().size());
  var context=new SyncContext<Void>(1,2,3,false,128,10000,at,mock(SyncSource.class),identities,mock(SyncReferences.class),"Task",session,false,false);
  assertEquals(new SyncCounts(3,1,0,0,0),task.execute(context));
  assertNull(missing.getEquipmentId());assertNull(missing.getTarget());assertEquals("UNRESOLVED",missing.getBarcode());assertNull(held.getTarget());
  assertTrue(context.runState().warnings().contains("P33_FORMAL_DEVICE_PENDING:1"));assertTrue(context.runState().warnings().contains("P33_TRANSFER_DEFERRED:1"));
  verify(session).insert(endsWith(".insertInstallation"),argThat(batch->((SyncBatch<?,?>)batch).rows().size()==1));
  verify(identities).saveAll(argThat(rows->rows.size()==1&&rows.getFirst().sourceKey().equals("1")));
 }
 @Test void oneFormalManageScopeIsForwardedAcrossAllProjectReferenceBatches(){
  var scopeApi=mock(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.class);
  when(scopeApi.resolveAllCurrent(any())).thenReturn(Set.of(1000L));
  var scope=new BaseSyncProjectReferences(mock(BaseSyncProjectReferences.ReferenceMapper.class),scopeApi).currentManageScope(new BaseSyncProjectReferences.ManageScopeQuery(1,2));
  when(projects.currentManageScope(any())).thenReturn(scope);
  var rows=new ArrayList<P33InstallationSyncTask.Source>();var members=new HashMap<String,BaseSyncPublishedProjects.Member>();
  for(long id=1;id<=2001;id++){
   members.put(Long.toString(id),new BaseSyncPublishedProjects.Member(Long.toString(id),1000+id,"10","P-"+id,"P-"+id,null));
   rows.add(new P33InstallationSyncTask.Source().setSourceId(id).setOriginalProjectId(id).setBarcode("SN-1").setTransferPending(false).setInstallAddress("raw").setLocationSnapshot("{\"fallbackLocation\":\"raw\"}").setCreateTime(at.plusSeconds(id)));
  }
  var publications=mock(BaseSyncPublishedProjects.class);
  when(publications.load(any())).thenReturn(new BaseSyncPublishedProjects.Snapshot(10,members,Map.of()));
  when(projects.verifyPublished(any())).thenAnswer(call->{
   BaseSyncProjectReferences.PublishedQuery q=call.getArgument(0);assertSame(scope,q.manageScope());
   var result=new HashMap<String,BaseSyncProjectReferences.Identity>();
   for(var member:q.members())result.put(member.sourceKey(),new BaseSyncProjectReferences.Identity(member.sourceKey(),member.projectId(),1L,0L,member.projectCode(),"10",null,null,null,"ACTIVE",false));
   return result;
  });
  task=spy(new P33InstallationSyncTask(mapper,publications,projects,org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDeviceProjects.class)));doReturn(rows).when(task).syncDataQuery(any());
  assertEquals(2001,preview().inserted());
  verify(projects).currentManageScope(new BaseSyncProjectReferences.ManageScopeQuery(1,2));verify(projects,times(3)).verifyPublished(any());noWrites();
 }
 @Test void equalLatestOriginalTimesAreBothCarriedWithoutGuessingACurrentInstallation(){
  var other=new P33InstallationSyncTask.Source().setSourceId(2L).setOriginalProjectId(1L).setBarcode("SN-1").setTransferPending(false).setInstallAddress("another raw address").setLocationSnapshot("{\"fallbackLocation\":\"another raw address\"}").setCreateTime(at);
  doReturn(new ArrayList<>(List.of(source,other))).when(task).syncDataQuery(any());
  var context=new SyncContext<Void>(1,2,3,true,128,10000,at,mock(SyncSource.class),identities,mock(SyncReferences.class),"Task",session,false,false);
  assertEquals(new SyncCounts(2,2,0,0,0),task.execute(context));
  for(var raw:List.of(source,other)){
   assertEquals(at,raw.getCreateTime());assertEquals(at,raw.getTarget().getCreateTime());assertEquals(at,raw.getTarget().getInstallTime());
   assertEquals(at,raw.getTarget().getEffectiveFrom());assertEquals(at,raw.getTarget().getEffectiveTo());assertNull(raw.getTarget().getCurrentEquipmentId());
   assertEquals(raw.getInstallAddress(),raw.getTarget().getInstallLocation());
  }
  assertTrue(context.runState().warnings().contains("P33_LATEST_ORIGINAL_TIME_TIED:1"));noWrites();
 }
 @Test void aNewUnboundRowStillCannotTakeOverAnExistingCurrentHistory(){
  when(mapper.selectExisting(any())).thenReturn(List.of(existing().setCurrentEquipmentId(9L).setEffectiveFrom(at).setCreateTime(at.minusDays(1))));
  assertEquals("P33_EQUAL_OR_NEWER_TIME_REQUIRES_ADJUDICATION",assertThrows(IllegalArgumentException.class,this::preview).getMessage());noWrites();
 }
}
