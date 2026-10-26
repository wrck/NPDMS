package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.*;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Same native temporary-table harness; generic location/extension capabilities stay mocked. */
class NewHistoryTaskMySqlTest extends RemainingTaskMySqlSupport {
 BaseSyncPublishedProjects publications;
 @BeforeEach void publishedScope(){
  publications=mock(BaseSyncPublishedProjects.class);
  var member=new BaseSyncPublishedProjects.Member("1",1000L,"10","P-1","P-1",null);
  when(publications.load(any())).thenReturn(new BaseSyncPublishedProjects.Snapshot(10,Map.of("1",member),Map.of(1000L,member)));
  when(projects.verifyPublished(any())).thenAnswer(call->{var query=call.<BaseSyncProjectReferences.PublishedQuery>getArgument(0);var result=new HashMap<String,BaseSyncProjectReferences.Identity>();for(var m:query.members())result.put(m.sourceKey(),new BaseSyncProjectReferences.Identity(m.sourceKey(),m.projectId(),1L,0L,m.projectCode(),m.projectType(),null,null,null,"ACTIVE",false));return result;});
 }
 static <T> void emit(List<T> rows,ResultHandler<T> handler){for(var row:rows){var result=new DefaultResultContext<T>();result.nextResultObject(row);handler.handleResult(result);}}
 @Test void unconfirmedMemberPreviewAndNativeInsertNeverResolveOrGrantUserIdentity(){
  var task=spy(new P31MemberContactSyncTask(session.getMapper(P31MemberContactSyncTask.TargetMapper.class),publications,projects));
  var source=new P31MemberContactSyncTask.Source().setSourceId(1L).setOriginalProjectId(1L).setProjectType("10").setMemberRole("30").setMemberCode("w123").setMemberName("historical").setCreateTime(at);
  doAnswer(call->{emit(List.of(source),call.getArgument(1));return null;}).when(task).syncDataQueryByRowHandler(any(),any());
  assertEquals(1,run(task,true).inserted());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_member_assignment",Integer.class));
  assertEquals(1,run(task,false).inserted());assertNull(jdbc.queryForObject("SELECT user_id FROM proj_project_member_assignment",Long.class));assertFalse(jdbc.queryForObject("SELECT authorization_confirmed FROM proj_project_member_assignment",Boolean.class));assertEquals("PROJECT_MANAGER",jdbc.queryForObject("SELECT member_role FROM proj_project_member_assignment",String.class));
  assertEquals(1,run(task,true).unchanged());
  jdbc.update("UPDATE proj_project_member_assignment SET member_name='changed'");assertThrows(IllegalArgumentException.class,()->run(task,true));
 }
 @Test void historicalFinalCustomerContactIsAnUnconfirmedMemberWithoutCustomerParty(){
  var task=spy(new P31MemberContactSyncTask(session.getMapper(P31MemberContactSyncTask.TargetMapper.class),publications,projects));
  var member=new P31MemberContactSyncTask.Source().setSourceId(1L).setOriginalProjectId(1L).setProjectType("10").setMemberRole("10").setMemberCode("w123");
  var contact=new P31MemberContactSyncTask.Source().setSourceId(2L).setOriginalProjectId(1L).setProjectType("10").setMemberRole("60").setMemberName("contact").setMemberCode("original-contact").setPhoneNum("12345").setEmail("contact@example.invalid").setFromFlag("1").setCreateTime(at);
  doAnswer(call->{emit(List.of(member,contact),call.getArgument(1));return null;}).when(task).syncDataQueryByRowHandler(any(),any());
  assertEquals(2,run(task,true).inserted());assertTrue(saved.isEmpty());
  assertEquals(2,run(task,false).inserted());
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_member_assignment",Integer.class));
  assertEquals("contact",jdbc.queryForObject("SELECT member_name FROM proj_project_member_assignment WHERE member_role='60'",String.class));
  assertEquals("12345",jdbc.queryForObject("SELECT phone_number FROM proj_project_member_assignment WHERE member_role='60'",String.class));
  assertEquals("contact@example.invalid",jdbc.queryForObject("SELECT email FROM proj_project_member_assignment WHERE member_role='60'",String.class));
  assertEquals("1",jdbc.queryForObject("SELECT registration_source FROM proj_project_member_assignment WHERE member_role='60'",String.class));
  assertNull(jdbc.queryForObject("SELECT user_id FROM proj_project_member_assignment WHERE member_role='60'",Long.class));
  assertFalse(jdbc.queryForObject("SELECT authorization_confirmed FROM proj_project_member_assignment WHERE member_role='60'",Boolean.class));
  assertTrue(saved.containsKey("MEMBER_HISTORY:2"));assertFalse(saved.containsKey("CONTACT_HISTORY:2"));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cus_project_customer_contact_relation",Integer.class));
  assertEquals(2,run(task,true).unchanged());
  jdbc.update("UPDATE proj_project_member_assignment SET phone_number='changed' WHERE member_role='60'");
  assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,true)).getMessage().contains("EXISTING_MEMBER_PROTECTED"));
 }
 @Test void priorContactBindingCannotBeRewrittenAsMember(){
  var task=spy(new P31MemberContactSyncTask(session.getMapper(P31MemberContactSyncTask.TargetMapper.class),publications,projects));
  var contact=new P31MemberContactSyncTask.Source().setSourceId(2L).setOriginalProjectId(1L).setProjectType("10").setMemberRole("60").setMemberName("contact").setCreateTime(at);
  var original=new cn.iocoder.yudao.module.pms.integration.api.basesync.SyncIdentityStore.Identity("2",99L,"existing-contact-baseline");
  saved.put("CONTACT_HISTORY:2",original);
  doAnswer(call->{emit(List.of(contact),call.getArgument(1));return null;}).when(task).syncDataQueryByRowHandler(any(),any());
  assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,false)).getMessage().contains("EXISTING_CONTACT_HISTORY_PROTECTED"));
  assertEquals(original,saved.get("CONTACT_HISTORY:2"));assertFalse(saved.containsKey("MEMBER_HISTORY:2"));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_member_assignment",Integer.class));
 }
 @Test void boundMemberLookupIntersectsExactIdsTenantAndProjectAndEmptyIdsStayEmpty(){
  jdbc.update("INSERT INTO proj_project_member_assignment(id,project_id,member_role,tenant_id,authorization_confirmed) VALUES(11,1000,'60',1,0),(12,1000,'60',1,0),(13,2000,'60',1,0),(14,1000,'60',2,0)");
  var mapper=session.getMapper(P31MemberContactSyncTask.TargetMapper.class);
  assertEquals(List.of(11L),mapper.selectMembers(new P31MemberContactSyncTask.TargetQuery(1,3,List.of(1000L),100,false,List.of(11L,13L,14L))).stream().map(P31MemberContactSyncTask.MemberRow::getId).toList());
  assertTrue(mapper.selectMembers(new P31MemberContactSyncTask.TargetQuery(1,3,List.of(1000L),100,true,List.of())).isEmpty());
  assertEquals(2,mapper.selectMembers(new P31MemberContactSyncTask.TargetQuery(1,3,List.of(1000L),100,true)).size());
 }
 @Test void mixedBoundAndFreshBatchStillProtectsUnownedOriginalMember(){
  var task=spy(new P31MemberContactSyncTask(session.getMapper(P31MemberContactSyncTask.TargetMapper.class),publications,projects));
  var existing=new P31MemberContactSyncTask.Source().setSourceId(1L).setOriginalProjectId(1L).setProjectType("10").setMemberRole("30").setMemberCode("owned");
  doAnswer(call->{emit(List.of(existing),call.getArgument(1));return null;}).when(task).syncDataQueryByRowHandler(any(),any());
  assertEquals(1,run(task,false).inserted());
  jdbc.update("INSERT INTO proj_project_member_assignment(id,project_id,member_code,member_role,tenant_id,authorization_confirmed) VALUES(99,1000,'original','PROJECT_MANAGER',1,0)");
  var fresh=new P31MemberContactSyncTask.Source().setSourceId(2L).setOriginalProjectId(1L).setProjectType("10").setMemberRole("30").setMemberCode("original");
  doAnswer(call->{emit(List.of(existing,fresh),call.getArgument(1));return null;}).when(task).syncDataQueryByRowHandler(any(),any());
  var batch=new SyncContext<>(1,2,3,true,2,10000,at,source,identities,mock(SyncReferences.class),"Task",session,false,true);
  assertTrue(assertThrows(IllegalArgumentException.class,()->transaction.execute(status->task.execute(batch))).getMessage().contains("ORIGINAL_MEMBER_CANNOT_ADOPT"));
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_member_assignment",Integer.class));assertFalse(saved.containsKey("MEMBER_HISTORY:2"));
 }
 @Test void existingFormalDateCheckStillRejectsRawIntervalAndRollsBackEarlierMember(){
  var task=spy(new P31MemberContactSyncTask(session.getMapper(P31MemberContactSyncTask.TargetMapper.class),publications,projects));
  var member=new P31MemberContactSyncTask.Source().setSourceId(1L).setOriginalProjectId(1L).setProjectType("10").setMemberRole("10").setMemberCode("w123");
  var contact=new P31MemberContactSyncTask.Source().setSourceId(2L).setOriginalProjectId(1L).setProjectType("10").setMemberRole("60").setMemberName("contact").setCreateTime(at).setEffectiveFrom(at).setEffectiveTo(at.minusDays(1));
  doAnswer(call->{emit(List.of(member,contact),call.getArgument(1));return null;}).when(task).syncDataQueryByRowHandler(any(),any());
  var failure=assertThrows(RuntimeException.class,()->run(task,false));
  boolean nativeDateCheck=false;
  for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof java.sql.SQLException sql&&sql.getErrorCode()==3819&&sql.getMessage().contains("chk_proj_member_dates"))nativeDateCheck=true;
  assertTrue(nativeDateCheck,"The existing database CHECK, rather than source-data validation, rejects the original interval");
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_member_assignment",Integer.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cus_project_customer_contact_relation",Integer.class));
 }
 @Test void executionRetainsOriginalIntervalsAndReplaysWhenTargetAcceptsRawDates(){
  // This change affects only the connection-local TEMPORARY fixture, never a formal table.
  // The separate test above retains the actual formal CHECK and proves its remaining effect.
  jdbc.execute("ALTER TABLE proj_project_member_assignment DROP CHECK chk_proj_member_dates");
  var task=spy(new P31MemberContactSyncTask(session.getMapper(P31MemberContactSyncTask.TargetMapper.class),publications,projects));
  var valid=new P31MemberContactSyncTask.Source().setSourceId(1L).setOriginalProjectId(1L).setProjectType("10").setMemberRole("10").setCreateTime(at).setEffectiveFrom(at);
  var sameDay=new P31MemberContactSyncTask.Source().setSourceId(2L).setOriginalProjectId(1L).setProjectType("10").setMemberRole("30").setCreateTime(at).setEffectiveFrom(at).setEffectiveTo(at.minusMinutes(1));
  var earlierDay=new P31MemberContactSyncTask.Source().setSourceId(3L).setOriginalProjectId(1L).setProjectType("10").setMemberRole("60").setCreateTime(at).setEffectiveFrom(at).setEffectiveTo(at.minusDays(1));
  var rows=List.of(valid,sameDay,earlierDay);
  doAnswer(call->{emit(rows,call.getArgument(1));return null;}).when(task).syncDataQueryByRowHandler(any(),any());
  var counts=run(task,false);assertEquals(3,counts.read());assertEquals(3,counts.inserted());
  for(var row:rows){
   assertEquals(row.getEffectiveFrom(),jdbc.queryForObject("SELECT effective_from FROM proj_project_member_assignment WHERE id=?",LocalDateTime.class,row.getMember().getId()));
   assertEquals(row.getEffectiveTo(),jdbc.queryForObject("SELECT effective_to FROM proj_project_member_assignment WHERE id=?",LocalDateTime.class,row.getMember().getId()));
   assertFalse(row.getMember().getAuthorizationConfirmed());assertNull(row.getMember().getUserId());
   assertTrue(saved.containsKey("MEMBER_HISTORY:"+row.getSourceId()));
  }
  var replay=run(task,false);assertEquals(3,replay.unchanged());assertEquals(0,replay.inserted());assertEquals(0,replay.updated());
  jdbc.update("UPDATE proj_project_member_assignment SET member_name='changed' WHERE id=?",earlierDay.getMember().getId());
  assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,false)).getMessage().contains("EXISTING_MEMBER_PROTECTED"));
 }
 @Test void previewRetainsEveryOriginalIntervalWithoutSourceDataQualityControl(){
  var task=spy(new P31MemberContactSyncTask(session.getMapper(P31MemberContactSyncTask.TargetMapper.class),publications,projects));
  var rows=new ArrayList<P31MemberContactSyncTask.Source>();
  for(long id=1;id<=160;id++)rows.add(new P31MemberContactSyncTask.Source().setSourceId(id).setOriginalProjectId(1L).setProjectType("10").setMemberRole(id%3==0?"10":id%3==1?"30":"60").setEffectiveFrom(at).setEffectiveTo(at.minusDays(1)));
  rows.add(new P31MemberContactSyncTask.Source().setSourceId(161L).setOriginalProjectId(1L).setProjectType("10").setMemberRole("60").setEffectiveFrom(at));
  doAnswer(call->{emit(rows,call.getArgument(1));return null;}).when(task).syncDataQueryByRowHandler(any(),any());
  var context=context(true);assertEquals(161,transaction.execute(status->task.execute(context)).inserted());
  assertTrue(context.runState().warnings().stream().noneMatch(message->message.startsWith("P31_SOURCE_INTERVAL")));
  for(var row:rows.subList(0,160)){
   assertEquals(at,row.getEffectiveFrom());assertEquals(at.minusDays(1),row.getEffectiveTo());
   assertEquals(at,row.getMember().getEffectiveFrom());assertEquals(at.minusDays(1),row.getMember().getEffectiveTo());
   assertFalse(row.getMember().getAuthorizationConfirmed());
  }
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_member_assignment",Integer.class));assertTrue(saved.isEmpty());
 }
 @Test void previewIntervalExceptionDoesNotAllowLossOfAuditPrecision(){
  var task=spy(new P31MemberContactSyncTask(session.getMapper(P31MemberContactSyncTask.TargetMapper.class),publications,projects));
  var source=new P31MemberContactSyncTask.Source().setSourceId(1L).setOriginalProjectId(1L).setProjectType("10").setMemberRole("60").setCreateTime(at.plusNanos(1));
  doAnswer(call->{emit(List.of(source),call.getArgument(1));return null;}).when(task).syncDataQueryByRowHandler(any(),any());
  assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,true)).getMessage().contains("SOURCE_INTERVAL_OR_AUDIT_PRECISION_INVALID"));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_member_assignment",Integer.class));assertTrue(saved.isEmpty());
 }
 P33InstallationSyncTask installation(List<P33InstallationSyncTask.Source> rows,AssetLocationApi locations){
  jdbc.update("INSERT INTO ast_device(id,tenant_id,sn,status,version,source_system,source_key) VALUES(9,1,'SN-1','IN_STOCK',0,'DPPMS','SN-1')");
  var mapper=mock(P33InstallationSyncTask.TargetMapper.class,org.mockito.AdditionalAnswers.delegatesTo(session.getMapper(P33InstallationSyncTask.TargetMapper.class)));
  doReturn(255).when(mapper).installLocationCapacity(); // Temporary fixture capacity; information_schema describes the permanent table.
  var task=spy(new P33InstallationSyncTask(mapper,publications,projects));doReturn(new ArrayList<>(rows)).when(task).syncDataQuery(any());return task;
 }
 P33InstallationSyncTask.Source installationSource(long id,LocalDateTime created){return new P33InstallationSyncTask.Source().setSourceId(id).setOriginalProjectId(1L).setBarcode("SN-1").setTransferFlag("-1").setTransferPending(false).setInstallAddress("original address").setLocationSnapshot(JsonUtils.toJsonString(Map.of("fallbackLocation","original address"))).setCreateTime(created);}
 @Test void installationChoosesOriginalLatestTimeAndPreviewNeverCreatesAnAddress(){
  var location=mock(AssetLocationApi.class);var task=installation(List.of(installationSource(20,at.minusDays(2)),installationSource(10,at)),location);
  assertEquals(2,run(task,true).inserted());verifyNoInteractions(location);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));
  assertEquals(2,run(task,false).inserted());assertEquals(at,jdbc.queryForObject("SELECT effective_from FROM imp_eng_installation WHERE current_equipment_id=9",LocalDateTime.class));assertEquals(at,jdbc.queryForObject("SELECT effective_to FROM imp_eng_installation WHERE current_equipment_id IS NULL",LocalDateTime.class));
  verify(location,never()).maintain(any());verify(location,never()).getAddress(any(),any());
  assertEquals(Map.of("fallbackLocation","original address"),JsonUtils.parseObject(jdbc.queryForObject("SELECT location_snapshot FROM imp_eng_installation WHERE current_equipment_id=9",String.class),Map.class));
  assertNull(jdbc.queryForObject("SELECT address_id FROM imp_eng_installation WHERE current_equipment_id=9",Long.class));
  assertNull(jdbc.queryForObject("SELECT address_snapshot FROM imp_eng_installation WHERE current_equipment_id=9",String.class));
  verifyNoInteractions(location);assertEquals(2,run(task,true).unchanged());
 }
 @Test void equalInstallationTimesRetainBothOriginalRowsWithoutGuessingCurrentInstallation(){
  var location=mock(AssetLocationApi.class);var rows=List.of(installationSource(1,at),installationSource(2,at));var task=installation(rows,location);
  var context=context(false);assertEquals(new SyncCounts(2,2,0,0,0),transaction.execute(status->task.execute(context)));
  assertTrue(context.runState().warnings().contains("P33_LATEST_ORIGINAL_TIME_TIED:1"));
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));
  for(var source:rows){
   var id=source.getTarget().getId();
   assertEquals(at,jdbc.queryForObject("SELECT create_time FROM imp_eng_installation WHERE id=?",LocalDateTime.class,id));
   assertEquals(at,jdbc.queryForObject("SELECT install_time FROM imp_eng_installation WHERE id=?",LocalDateTime.class,id));
   assertEquals(at,jdbc.queryForObject("SELECT effective_from FROM imp_eng_installation WHERE id=?",LocalDateTime.class,id));
   assertEquals(at,jdbc.queryForObject("SELECT effective_to FROM imp_eng_installation WHERE id=?",LocalDateTime.class,id));
   assertEquals(source.getInstallAddress(),jdbc.queryForObject("SELECT install_location FROM imp_eng_installation WHERE id=?",String.class,id));
   assertEquals(Map.of("fallbackLocation",source.getInstallAddress()),JsonUtils.parseObject(jdbc.queryForObject("SELECT location_snapshot FROM imp_eng_installation WHERE id=?",String.class,id),Map.class));
   assertNull(jdbc.queryForObject("SELECT current_equipment_id FROM imp_eng_installation WHERE id=?",Long.class,id));
  }
  verifyNoInteractions(location);
 }
 @Test void installationPreviewDoesNotInventAnIdentityForAMissingDevice(){
  var location=mock(AssetLocationApi.class);var source=installationSource(1,at).setBarcode("UNRESOLVED-SN");var task=installation(List.of(source),location);
  var context=context(true);assertEquals(new SyncCounts(1,0,0,0,0),transaction.execute(status->task.execute(context)));
  assertNull(source.getEquipmentId());assertNull(source.getTarget());assertTrue(context.runState().warnings().contains("P33_FORMAL_DEVICE_PENDING:1"));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));assertTrue(saved.isEmpty());verifyNoInteractions(location);
  var actual=context(false);assertEquals(new SyncCounts(1,0,0,0,0),transaction.execute(status->task.execute(actual)));
  assertNull(source.getEquipmentId());assertNull(source.getTarget());assertTrue(actual.runState().warnings().contains("P33_FORMAL_DEVICE_PENDING:1"));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));assertTrue(saved.isEmpty());
  saved.put("INSTALLATION_HISTORY:1",new SyncIdentityStore.Identity("1",99L,"old-baseline"));
  assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,true)).getMessage().contains("EXISTING_INSTALLATION_PROTECTED"));verifyNoInteractions(location);
  assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,false)).getMessage().contains("EXISTING_INSTALLATION_PROTECTED"));
 }
 @Test void installationHistoryKeepsProjectValidationWithoutCurrentDeviceSelection(){
  var selected=mock(ProjectDeviceSelectionApi.class);when(selected.validateSelection(any())).thenThrow(new IllegalArgumentException("formal current selection unavailable"));
  var location=mock(AssetLocationApi.class);var task=installation(List.of(installationSource(1,at)),location);
  assertEquals(new SyncCounts(1,1,0,0,0),run(task,true));
  assertEquals(new SyncCounts(1,1,0,0,0),run(task,false));
  verify(projects,atLeastOnce()).verifyPublished(any());verifyNoInteractions(selected,location);
 }
 @Test void pendingDevicePreviewPreservesLongAddressWhileActualExecutionStillRequiresCapacity(){
  var location=mock(AssetLocationApi.class);var source=installationSource(1,at).setBarcode("UNRESOLVED-SN").setInstallAddress(" ");var task=installation(List.of(source),location);
  assertThrows(IllegalArgumentException.class,()->run(task,true));source.setInstallAddress("x".repeat(571)).setLocationSnapshot(JsonUtils.toJsonString(Map.of("fallbackLocation","x".repeat(571))));var context=context(true);
  assertEquals(new SyncCounts(1,0,0,0,0),transaction.execute(status->task.execute(context)));
  assertEquals("x".repeat(571),source.getInstallAddress());assertNull(source.getTarget());
  assertTrue(context.runState().warnings().contains("P33_INSTALL_LOCATION_CAPACITY_PENDING:1:255"));
  assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,false)).getMessage().contains("INSTALL_LOCATION_CAPACITY_REQUIRED"));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));assertTrue(saved.isEmpty());verifyNoInteractions(location);
 }
 @Test void installationSourceIncludesEmptyAddressesWhileExistingJavaFilterRemains()throws Exception{
  jdbc.execute("""
   CREATE TEMPORARY TABLE pm_project_shipment(id BIGINT PRIMARY KEY,projectId BIGINT,barcode VARCHAR(64),
   itemCode VARCHAR(64),itemModel VARCHAR(64),itemName VARCHAR(64),receiveName VARCHAR(64),emsNum VARCHAR(64),emsCompany VARCHAR(64),
   packdate VARCHAR(64),contractNo VARCHAR(64),installAddress TEXT CHARACTER SET utf8 COLLATE utf8_general_ci,chProjectId VARCHAR(64),chContractNo VARCHAR(64),
   transferProjectId VARCHAR(64),transferContractNo VARCHAR(64),transferFlag VARCHAR(8),createTime DATETIME,createBy VARCHAR(64),updateTime DATETIME,updateBy VARCHAR(64))
   """);
  var addresses=Arrays.asList(null,""," ","  ","original address","原".repeat(571),"\u3000","\t\r\n"," \u3000 ");
  for(int i=0;i<addresses.size();i++)jdbc.update("INSERT INTO pm_project_shipment(id,projectId,barcode,installAddress,transferFlag,createTime) VALUES(?,1,?,?,'-1',?)",i+1,"SN-"+i,addresses.get(i),at);
  jdbc.update("INSERT INTO pm_project_shipment(id,projectId,barcode,installAddress,transferFlag,createTime) VALUES(10,2,'OUTSIDE','outside address','-1',?)",at);
  var resource="base-sync-source/P33SourceMapper.xml";try(var input=new org.springframework.core.io.ClassPathResource(resource).getInputStream()){new org.apache.ibatis.builder.xml.XMLMapperBuilder(input,session.getConfiguration(),resource,session.getConfiguration().getSqlFragments()).parse();}
  var mapper=session.getMapper(P33InstallationSyncTask.SourceMapper.class);
  var sourceRows=mapper.selectInstallation(new P33InstallationSyncTask.ReadQuery(100,List.of("1")));
  assertEquals(addresses,sourceRows.stream().map(P33InstallationSyncTask.Source::getInstallAddress).toList());
  assertEquals(List.of(1L,2L,3L,4L,5L,6L,7L,8L,9L),sourceRows.stream().map(P33InstallationSyncTask.Source::getSourceId).toList());
  var rows=new ArrayList<P33InstallationSyncTask.Source>();var locations=mock(AssetLocationApi.class);
  var task=spy(new P33InstallationSyncTask(session.getMapper(P33InstallationSyncTask.TargetMapper.class),publications,projects));
  doAnswer(call->{rows.addAll(call.<SyncBatch<P33InstallationSyncTask.Source,P33InstallationSyncTask.Parameters>>getArgument(0).rows());return call.callRealMethod();}).when(task).syncDataInsertBefore(any());
  var context=context(true);assertEquals(new SyncCounts(2,0,0,0,0),transaction.execute(status->task.execute(context)));
  assertEquals(List.of(5L,6L),rows.stream().map(P33InstallationSyncTask.Source::getSourceId).toList());
  assertEquals(addresses.subList(4,6),rows.stream().map(P33InstallationSyncTask.Source::getInstallAddress).toList());
  assertTrue(context.runState().warnings().contains("P33_BLANK_ADDRESS_FILTERED_AFTER_READ:7"));verifyNoInteractions(locations);
  assertTrue(mapper.selectInstallation(new P33InstallationSyncTask.ReadQuery(100,List.of())).isEmpty());
  assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_shipment",Integer.class));
 }
 @Test void installationKeepsBothLongAddressCopiesWithoutCreatingAddressManagementRecords(){
  jdbc.execute("ALTER TABLE imp_eng_installation MODIFY COLUMN install_location VARCHAR(1024)"); // Connection-local temporary fixture only.
  jdbc.update("INSERT INTO ast_device(id,tenant_id,sn,status,version,source_system,source_key) VALUES(9,1,'SN-1','IN_STOCK',0,'DPPMS','SN-1')");
  var mapper=mock(P33InstallationSyncTask.TargetMapper.class,org.mockito.AdditionalAnswers.delegatesTo(session.getMapper(P33InstallationSyncTask.TargetMapper.class)));doReturn(1024).when(mapper).installLocationCapacity();
  var location=mock(AssetLocationApi.class);var source=installationSource(1,at).setInstallAddress("原始机房\r\n".repeat(80)).setLocationSnapshot(JsonUtils.toJsonString(Map.of("fallbackLocation","原始机房\r\n".repeat(80))));
  var task=spy(new P33InstallationSyncTask(mapper,publications,projects));doReturn(new ArrayList<>(List.of(source))).when(task).syncDataQuery(any());
  assertEquals(1,run(task,true).inserted());verifyNoInteractions(location);
  assertEquals(1,run(task,false).inserted());
  assertEquals(source.getInstallAddress(),jdbc.queryForObject("SELECT install_location FROM imp_eng_installation",String.class));
  var snapshot=jdbc.queryForObject("SELECT location_snapshot FROM imp_eng_installation",String.class);
  assertEquals(Map.of("fallbackLocation",source.getInstallAddress()),JsonUtils.parseObject(snapshot,Map.class));
  assertEquals("UNRESOLVED",jdbc.queryForObject("SELECT location_resolution_status FROM imp_eng_installation",String.class));
  assertNull(jdbc.queryForObject("SELECT address_id FROM imp_eng_installation",Long.class));assertNull(jdbc.queryForObject("SELECT address_snapshot FROM imp_eng_installation",String.class));
  verify(location,never()).maintain(any());verify(location,never()).getAddress(any(),any());
  verifyNoInteractions(location);
  assertEquals(1,run(task,true).unchanged());
 }
 @Test void installationRegistersDeferredTransfersInPreviewAndActualWithoutBlockingNormalRows(){
  var location=mock(AssetLocationApi.class);var source=installationSource(1,at).setChProjectId("11").setTransferPending(true);
  var normal=installationSource(2,at);var task=installation(List.of(source,normal),location);
  for(boolean preview:List.of(true,false)){
   var context=context(preview);assertEquals(new SyncCounts(2,1,0,0,0),transaction.execute(status->task.execute(context)));
   assertTrue(context.runState().warnings().contains("P33_TRANSFER_DEFERRED:1"));assertNull(source.getTarget());
  }
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));
  assertFalse(saved.containsKey("INSTALLATION_HISTORY:1"));assertTrue(saved.containsKey("INSTALLATION_HISTORY:2"));verifyNoInteractions(location);
 }
 P35SoftwareHistorySyncTask software(List<P35SoftwareHistorySyncTask.Source> batches,List<P35SoftwareHistorySyncTask.Source> observations){
  var task=spy(new P35SoftwareHistorySyncTask(session.getMapper(P35SoftwareHistorySyncTask.TargetMapper.class),publications,projects));
  doAnswer(call->{SyncContext<?> c=call.getArgument(0);emit(c.dataName().equals("SoftwareBatch")?batches:observations,call.getArgument(1));return null;}).when(task).syncDataQueryByRowHandler(any(),any());return task;
 }
 @Test void softwarePreviewKeepsRealParentPendingAndNativeInsertUsesActualBatchId(){
  var batch=new P35SoftwareHistorySyncTask.Source().setSourceId(1L).setOriginalProjectId(1L).setChangeVersion("v1").setCreateTime(at);
  var observation=new P35SoftwareHistorySyncTask.Source().setSourceId(2L).setOriginalProjectId(1L).setLogId(1L).setBarCode("SN-1").setConp("v1").setCreateTime(at);
  var task=software(List.of(batch),List.of(observation));assertEquals(2,run(task,true).inserted());assertNull(observation.getObservation().getChangeBatchId());assertNull(observation.getObservation().getId());
  assertEquals(2,run(task,false).inserted());assertEquals(jdbc.queryForObject("SELECT id FROM ast_device_software_change_batch",Long.class),jdbc.queryForObject("SELECT change_batch_id FROM ast_device_software_version_record",Long.class));assertNull(jdbc.queryForObject("SELECT device_id FROM ast_device_software_version_record",Long.class));
  assertEquals(2,run(task,true).unchanged());
  jdbc.update("UPDATE ast_device_software_version_record SET conp_version='changed'");assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,true)).getMessage().contains("EXISTING_OBSERVATION_PROTECTED"));
 }
 @Test void softwareBaseRecordFailureRollsBackTheEarlierBatch(){
  
  var batch=new P35SoftwareHistorySyncTask.Source().setSourceId(1L).setOriginalProjectId(1L).setCreateTime(at);var observation=new P35SoftwareHistorySyncTask.Source().setSourceId(2L).setOriginalProjectId(1L).setLogId(999L).setCreateTime(at);
  assertTrue(assertThrows(IllegalArgumentException.class,()->run(software(List.of(batch),List.of(observation)),false)).getMessage().contains("ORIGINAL_BATCH_NOT_IN_SAME_PUBLISHED_PROJECT"));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_software_change_batch",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_software_version_record",Integer.class));
 }
 @Test void absentPublicationBlocksEveryHistoryBeforeOpeningTheExternalSource(){
  when(publications.load(any())).thenThrow(new IllegalStateException("COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED"));var external=mock(SyncSource.class);var context=new SyncContext<Void>(1,2,3,true,10,100,at,external,identities,mock(SyncReferences.class),"Task",session,false,true);
  assertThrows(IllegalStateException.class,()->new P31MemberContactSyncTask(session.getMapper(P31MemberContactSyncTask.TargetMapper.class),publications,projects).execute(context));assertThrows(IllegalStateException.class,()->new P33InstallationSyncTask(session.getMapper(P33InstallationSyncTask.TargetMapper.class),publications,projects).execute(context));verifyNoInteractions(external);
 }
}
