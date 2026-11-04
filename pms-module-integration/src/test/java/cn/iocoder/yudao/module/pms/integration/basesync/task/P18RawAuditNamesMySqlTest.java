package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Production source projections and native target writes on connection-local TEMPORARY tables only. */
class P18RawAuditNamesMySqlTest extends RemainingTaskMySqlSupport {
 P18ProjectContractSyncTask task;
 @BeforeEach void relationFixture() throws Exception {
  jdbc.execute("CREATE TEMPORARY TABLE pm_project_contract(id BIGINT,contractNo VARCHAR(64),projectGroupCode VARCHAR(64),createTime DATETIME(3),createBy VARCHAR(64),updateTime DATETIME(3),updateBy VARCHAR(64))");
  jdbc.execute("CREATE TEMPORARY TABLE pm_project_group_relationship(id BIGINT,projectGroupCode VARCHAR(64),projectCode VARCHAR(64),mergeBranchMark VARCHAR(64),smsProjectCode VARCHAR(64),createTime DATETIME(3),createBy VARCHAR(64),updateTime DATETIME(3),updateBy VARCHAR(64))");
  jdbc.execute("CREATE TEMPORARY TABLE com_contract(id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,company_code VARCHAR(64),contract_no VARCHAR(64),deleted TINYINT NOT NULL DEFAULT 0)");
  jdbc.execute("CREATE TEMPORARY TABLE proj_project(id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,company_code VARCHAR(64),deleted TINYINT NOT NULL DEFAULT 0)");
  jdbc.update("INSERT INTO proj_project VALUES(1000,1,'001',0)");
  jdbc.execute("""
   CREATE TEMPORARY TABLE com_project_contract_relation(
    id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,project_id BIGINT NOT NULL,contract_id BIGINT,contract_no VARCHAR(64),
    relation_role VARCHAR(32) NOT NULL DEFAULT 'RELATED',source_system VARCHAR(32) NOT NULL,source_table VARCHAR(64),source_record_key VARCHAR(128),
    effective_from DATETIME(3),effective_to DATETIME(3),status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL,create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updater VARCHAR(64) NOT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_project_contract_rel_tenant_row(tenant_id,id),UNIQUE KEY uk_project_contract(tenant_id,project_id,contract_id,relation_role),
    CHECK(effective_to IS NULL OR effective_from IS NULL OR effective_to>=effective_from),CHECK(deleted IN (0,1)))
   """);
  String resource="base-sync-source/P18SourceMapper.xml";
  try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,session.getConfiguration(),resource,session.getConfiguration().getSqlFragments()).parse();}
  // This metadata read addresses a permanent information_schema index; the fixture does not alter it.
  // The reviewed multi-source schema check is isolated from these raw audit-name cases.
  var target=spy(session.getMapper(P18ProjectContractSyncTask.TargetMapper.class));
  doReturn(0).when(target).oneSourcePerTargetIndex();
  task=spy(new P18ProjectContractSyncTask(target,org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDeviceProjects.class)));task.setMaxRetries(0);
 }
 void fixture(P18ProjectContractSyncTask.Source source){
  var id=source.getContractSourceId();
  jdbc.update("INSERT INTO pm_project_contract VALUES(?,?,'G','2020-01-01',?,'2020-01-02',?)",id,"C"+id,source.getContractCreateBy(),source.getContractUpdateBy());
  jdbc.update("INSERT INTO pm_project_group_relationship VALUES(?,'G','P-1',NULL,NULL,'2020-01-01',?,'2020-01-02',?)",id,source.getMemberCreateBy(),source.getMemberUpdateBy());
  jdbc.update("INSERT INTO com_contract VALUES(?,1,'001',?,0)",id,"C"+id);
 }
 List<P18ProjectContractSyncTask.Source> raw(){
  var query=new AbstractSyncTask.SourceQuery<P18ProjectContractSyncTask.Parameters>(100,1,true,new P18ProjectContractSyncTask.Parameters());
  var mapper=session.getMapper(P18ProjectContractSyncTask.SourceMapper.class);
  var contracts=mapper.selectContracts(query);var members=mapper.selectMembers(query);
  for(var row:contracts){var member=members.stream().filter(m->m.getMemberId().equals(row.getContractSourceId())).findFirst().orElseThrow();
   P18ProjectContractSyncTask.copyNonNull(new P18ProjectContractSyncTask.Copy(member,row));
   row.setGroupId(1L);row.setGroupProjectGroupCode("G");row.setProjectType("10");row.setOriginalProjectId(1L);row.setOriginalProjectCode("P-1");row.setOriginalCompanyCode("01");
  }
  P18ProjectContractSyncTask.canonicalize(contracts);return contracts;
 }
 SyncCounts execute(boolean preview){doAnswer(call->raw()).when(task).syncDataQuery(any());return run(task,preview);}
 @Test void nullAndEmptyRawActorsWriteEmptyStringsIntoNotNullColumns(){
  fixture(new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setContractCreateBy(null).setContractUpdateBy(null).setMemberCreateBy(null).setMemberUpdateBy(null));fixture(new P18ProjectContractSyncTask.Source().setContractSourceId(2L).setContractCreateBy("").setContractUpdateBy("").setMemberCreateBy("").setMemberUpdateBy(""));
  assertEquals(2,execute(false).inserted());
  assertEquals(List.of("",""),jdbc.queryForList("SELECT creator FROM com_project_contract_relation ORDER BY contract_id",String.class));
  assertEquals(List.of("",""),jdbc.queryForList("SELECT updater FROM com_project_contract_relation ORDER BY contract_id",String.class));
 }
 @Test void contractActorsWinMemberActorsFillGapsAndSpecialNamesStayRaw(){
  fixture(new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setContractCreateBy("contractCreator").setContractUpdateBy("contractUpdater").setMemberCreateBy("memberCreator").setMemberUpdateBy("memberUpdater"));
  fixture(new P18ProjectContractSyncTask.Source().setContractSourceId(2L).setContractCreateBy(null).setContractUpdateBy("").setMemberCreateBy("memberCreator").setMemberUpdateBy("memberUpdater"));fixture(new P18ProjectContractSyncTask.Source().setContractSourceId(3L).setContractCreateBy("sys").setContractUpdateBy("异常数据处理").setMemberCreateBy("ignored").setMemberUpdateBy("ignored"));fixture(new P18ProjectContractSyncTask.Source().setContractSourceId(4L).setContractCreateBy("  ").setContractUpdateBy(" ").setMemberCreateBy("ignored").setMemberUpdateBy("ignored"));
  execute(false);
  assertEquals(List.of("contractCreator","memberCreator","sys","  "),jdbc.queryForList("SELECT creator FROM com_project_contract_relation ORDER BY contract_id",String.class));
  assertEquals(List.of("contractUpdater","memberUpdater","异常数据处理"," "),jdbc.queryForList("SELECT updater FROM com_project_contract_relation ORDER BY contract_id",String.class));
 }
 @Test void previewWritesNothingThenNativeInsertReplaysAndProtectsChangedTarget(){
  fixture(new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setContractCreateBy("sys").setContractUpdateBy("异常数据处理").setMemberCreateBy(null).setMemberUpdateBy(null));
  assertEquals(1,execute(true).inserted());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_project_contract_relation",Integer.class));assertTrue(saved.isEmpty());
  assertEquals(1,execute(false).inserted());assertEquals(1,execute(false).unchanged());
  jdbc.update("UPDATE com_project_contract_relation SET creator='tampered'");
  var failure=assertThrows(IllegalArgumentException.class,()->execute(false));assertEquals("P18_EXISTING_RELATION_PROTECTED",failure.getMessage());
 }
}
