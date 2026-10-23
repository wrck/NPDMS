package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.SyncCounts;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncProjectReferences;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncPublishedProjects;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL collation, Mapper and task flow; all data lives in connection-local temporary tables. */
class P33SnMatchingMySqlTest extends RemainingTaskMySqlSupport {
 private static final String FORMAL_SN="02010075Y14A004396";
 private BaseSyncPublishedProjects publications;
 private ProjectDeviceSelectionApi selection;
 private AssetLocationApi locations;

 @BeforeEach void installationFixture(){
  jdbc.execute("ALTER TABLE ast_device MODIFY COLUMN sn VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL");
  jdbc.update("INSERT INTO ast_device(id,tenant_id,sn,status,version,source_system,source_key) VALUES(9,1,?,'IN_STOCK',0,'DPPMS',?)",FORMAL_SN,FORMAL_SN);
  publications=mock(BaseSyncPublishedProjects.class);
  var member=new BaseSyncPublishedProjects.Member("1",1000L,"10","P-1","P-1",null);
  when(publications.load(any())).thenReturn(new BaseSyncPublishedProjects.Snapshot(10,Map.of("1",member),Map.of(1000L,member)));
  when(projects.verifyPublished(any())).thenReturn(Map.of("1",new BaseSyncProjectReferences.Identity("1",1000L,1L,0L,"P-1","10",null,null,null,"ACTIVE",false)));
  selection=mock(ProjectDeviceSelectionApi.class);
  locations=mock(AssetLocationApi.class);
 }

 private P33InstallationSyncTask.Source installation(long id,String sn,LocalDateTime created){
  return new P33InstallationSyncTask.Source().setSourceId(id).setOriginalProjectId(1L).setBarcode(sn)
   .setTransferFlag("-1").setTransferPending(false).setInstallAddress("original address").setLocationSnapshot(JsonUtils.toJsonString(Map.of("fallbackLocation","original address"))).setCreateTime(created);
 }
 private P33InstallationSyncTask task(List<P33InstallationSyncTask.Source> rows){
  var task=spy(new P33InstallationSyncTask(session.getMapper(P33InstallationSyncTask.TargetMapper.class),publications,projects));
  doReturn(new ArrayList<>(rows)).when(task).syncDataQuery(any());
  return task;
 }

 @Test void installationRetainsRawSnWhenFormalCollationMatches(){
  for(var raw:List.of(FORMAL_SN+" ",FORMAL_SN.toLowerCase(Locale.ROOT)+" ",FORMAL_SN)){
   var source=installation(1,raw,at);
   assertEquals(new SyncCounts(1,1,0,0,0),run(task(List.of(source)),true));
   assertEquals(raw,source.getBarcode());assertEquals(9L,source.getEquipmentId());assertEquals(9L,source.getTarget().getEquipmentId());
   assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));
  }
  verifyNoInteractions(selection);
  verifyNoInteractions(locations);assertTrue(saved.isEmpty());
 }

 @Test void installationMapsDifferentRequestedSnValuesToTheSameDevice(){
  var current=installation(1,FORMAL_SN,at);var historical=installation(2,FORMAL_SN+" ",at.minusDays(1));
  assertEquals(new SyncCounts(2,2,0,0,0),run(task(List.of(current,historical)),true));
  assertEquals(9L,current.getEquipmentId());assertEquals(9L,historical.getEquipmentId());
  assertEquals(FORMAL_SN+" ",historical.getBarcode());assertEquals(9L,current.getTarget().getCurrentEquipmentId());
  assertNull(historical.getTarget().getCurrentEquipmentId());assertEquals(at,historical.getTarget().getEffectiveTo());
  verifyNoInteractions(selection,locations);
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));
 }

 @Test void deviceLookupKeepsTenantDeletionEmptySetAndLockSemantics(){
  jdbc.update("INSERT INTO ast_device(id,tenant_id,sn,status,version,source_system,source_key) VALUES(10,2,'OTHER','IN_STOCK',0,'DPPMS','OTHER')");
  jdbc.update("INSERT INTO ast_device(id,tenant_id,sn,status,version,source_system,source_key,deleted) VALUES(11,1,'REMOVED','IN_STOCK',0,'DPPMS','REMOVED',TRUE)");
  var mapper=session.getMapper(P33InstallationSyncTask.TargetMapper.class);
  assertTrue(mapper.selectDevices(new P33InstallationSyncTask.DeviceQuery(1,List.of(),true)).isEmpty());
  assertTrue(mapper.selectDevices(new P33InstallationSyncTask.DeviceQuery(1,null,true)).isEmpty());
  assertTrue(mapper.selectDevices(new P33InstallationSyncTask.DeviceQuery(1,List.of("OTHER ","REMOVED "),true)).isEmpty());
  var matches=transaction.execute(status->mapper.selectDevices(new P33InstallationSyncTask.DeviceQuery(1,List.of(FORMAL_SN+" "),false)));
  assertEquals(1,matches.size());assertEquals(9L,matches.getFirst().getId());assertEquals(FORMAL_SN,matches.getFirst().getSn());
 }

 @Test void historicalInstallationUsesFormalSnWithoutCurrentSelectionQualification(){
  when(selection.validateSelection(any())).thenThrow(new IllegalArgumentException("current selection unavailable"));
  var source=installation(1,FORMAL_SN+" ",at);
  assertEquals(new SyncCounts(1,1,0,0,0),run(task(List.of(source)),false));
  assertEquals(FORMAL_SN+" ",source.getBarcode());assertEquals(9L,source.getEquipmentId());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));
  verifyNoInteractions(selection,locations);
 }

 @Test void aFullLookupBatchSupportsNativeSqlAndTheConfiguredSqlParser() throws Exception{
  var serials=new ArrayList<String>();for(int i=0;i<498;i++)serials.add("MISSING-"+i);
  serials.add("quoted-'\"\\-sn");serials.add(FORMAL_SN+" ");
  var query=new P33InstallationSyncTask.DeviceQuery(1,serials,true);
  var sql=session.getConfiguration().getMappedStatement(P33InstallationSyncTask.TargetMapper.class.getName()+".selectDevices").getBoundSql(Map.of("query",query)).getSql();
  assertNotNull(com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(sql));
  var matched=session.getMapper(P33InstallationSyncTask.TargetMapper.class).selectDevices(query);
  assertEquals(1,matched.size());assertEquals(9L,matched.getFirst().getId());assertEquals(FORMAL_SN,matched.getFirst().getSn());
 }

 @Test void multipleFormalCandidatesForOneRequestedSnRemainRejected(){
  jdbc.execute("ALTER TABLE ast_device DROP INDEX uk_ast_device_tenant_sn");
  jdbc.update("INSERT INTO ast_device(id,tenant_id,sn,status,version,source_system,source_key) VALUES(12,1,?,'IN_STOCK',0,'DPPMS','duplicate-fixture')",FORMAL_SN);
  assertTrue(assertThrows(IllegalArgumentException.class,()->run(task(List.of(installation(1,FORMAL_SN+" ",at))),true)).getMessage().contains("DEVICE_SERIAL_AMBIGUOUS"));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));assertTrue(saved.isEmpty());verifyNoInteractions(selection,locations);
 }

 @Test void temporaryNativeWriteAndReplayKeepRawSnInTheSourceFingerprint(){
  var source=installation(1,FORMAL_SN+" ",at);var task=task(List.of(source));
  assertEquals(1,run(task,false).inserted());assertEquals(FORMAL_SN+" ",source.getBarcode());
  assertEquals(1,run(task,true).unchanged());
  source.setBarcode(FORMAL_SN);
  assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,true)).getMessage().contains("EXISTING_INSTALLATION_PROTECTED"));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));
 }
}
