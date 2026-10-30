package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The real Mapper operates only on connection-local MySQL temporary tables. */
@EnabledIfEnvironmentVariable(named="BASE_PRODUCT_TEMP_MYSQL_TEST",matches="true")
class LicenseDeviceReferenceMySqlTest extends RemainingTaskMySqlSupport {
    @TempDir Path directory;
    P37LicenseHistorySyncTask.TargetMapper mapper;
    P37LicenseHistorySyncTask task;

    @BeforeEach void licenseFixture() throws Exception {
        jdbc.execute("CREATE TEMPORARY TABLE ast_device_license_record(id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,device_id BIGINT,contract_id BIGINT,device_sn VARCHAR(255),contract_no VARCHAR(32),contract_type VARCHAR(16),product_code VARCHAR(255),product_model VARCHAR(256),license_status VARCHAR(255),creator VARCHAR(64),updater VARCHAR(64),create_time DATETIME,update_time DATETIME,deleted BOOLEAN,license_code_ciphertext TEXT)");
        for(var resource:List.of("mapper/basesync/P37TargetMapper.xml","base-sync-source/P37SourceMapper.xml"))
            try(var input=new ClassPathResource(resource).getInputStream()){
                new XMLMapperBuilder(input,session.getConfiguration(),resource,session.getConfiguration().getSqlFragments()).parse();
            }
        mapper=session.getMapper(P37LicenseHistorySyncTask.TargetMapper.class);
        var approval=java.nio.file.Files.createFile(directory.resolve("approval.json.gz"));
        task=spy(new P37LicenseHistorySyncTask(mapper,Base64.getEncoder().encodeToString(new byte[32]),"TEST",approval.toString()));
        jdbc.update("INSERT INTO ast_device(id,tenant_id,sn,status,source_system,project_id,customer_id,project_assignment_version,customer_assignment_version,deleted) VALUES(11,1,'SN-1','IN_STOCK','DPPMS',91,92,3,4,0),(12,2,'CROSS','IN_STOCK','DPPMS',NULL,NULL,0,0,0),(13,1,'DELETED','IN_STOCK','DPPMS',NULL,NULL,0,0,1),(14,1,'KEEP','IN_STOCK','DPPMS',NULL,NULL,0,0,0)");
    }

    P37LicenseHistorySyncTask.LicenseSource seed(long id,String sn,Long deviceId) {
        var row=new P37LicenseHistorySyncTask.LicenseSource().setSourceId(id).setSn(sn).setContract("C-1").setItem("ITEM").setStatus("ACTIVE");
        var envelope=task.encrypt(new P37LicenseHistorySyncTask.Capture(1,Long.toString(id),"synthetic".toCharArray()));
        row.setEnvelope(envelope);
        var target=new P37LicenseHistorySyncTask.LicenseTarget().setId(100+id).setTenantId(1L).setDeviceId(deviceId)
                .setContractId(701L).setDeviceSn(sn).setContractNo("C-1").setProductCode("ITEM").setLicenseStatus("ACTIVE")
                .setCreator("old").setUpdater("old").setCreateTime(at).setUpdateTime(at).setDeleted(false)
                .setLicenseCodeCiphertext(JsonUtils.toJsonString(new P37LicenseHistorySyncTask.Sealed("AES-256-GCM",envelope)));
        jdbc.update("INSERT INTO ast_device_license_record(id,tenant_id,device_id,contract_id,device_sn,contract_no,product_code,license_status,creator,updater,create_time,update_time,deleted,license_code_ciphertext) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                target.getId(),1,deviceId,701,sn,"C-1","ITEM","ACTIVE","old","old",at,at,false,target.getLicenseCodeCiphertext());
        saved.put("LICENSE_HISTORY:"+id,new SyncIdentityStore.Identity(Long.toString(id),target.getId(),P37LicenseHistorySyncTask.fingerprint(row,target)));
        row.setEnvelope(null);
        return row;
    }

    void emitRows(List<P37LicenseHistorySyncTask.LicenseSource> rows){
        doAnswer(call->{ResultHandler<P37LicenseHistorySyncTask.LicenseSource> handler=call.getArgument(1);for(var row:rows){var result=new DefaultResultContext<P37LicenseHistorySyncTask.LicenseSource>();result.nextResultObject(row);handler.handleResult(result);}return null;})
                .when(task).syncDataQueryByRowHandler(any(),any());
    }
    SyncCounts execute(boolean preview){
        var runtime=context(preview).forBatchSize(10).forStages(List.of("DeviceReference"));
        return transaction.execute(status->task.execute(runtime));
    }

    @Test void exactTenantSnBackfillsOnlyEmptyLicenseReferenceAndPreservesOwnerHistory() throws Exception {
        var rows=List.of(seed(1,"SN-1",null),seed(2,"sn-1",null),seed(3,"CROSS",null),seed(4,"DELETED",null),seed(5,"KEEP",777L),seed(6,null,null));
        String ciphertext=jdbc.queryForObject("SELECT license_code_ciphertext FROM ast_device_license_record WHERE id=101",String.class);
        String before=saved.get("LICENSE_HISTORY:1").fingerprint();emitRows(rows);
        var preview=execute(true);assertEquals(1,preview.updated());assertEquals(5,preview.unchanged());
        assertNull(jdbc.queryForObject("SELECT device_id FROM ast_device_license_record WHERE id=101",Long.class));assertEquals(before,saved.get("LICENSE_HISTORY:1").fingerprint());
        var result=execute(false);assertEquals(1,result.updated());assertEquals(5,result.unchanged());
        assertEquals(11L,jdbc.queryForObject("SELECT device_id FROM ast_device_license_record WHERE id=101",Long.class));
        assertEquals(777L,jdbc.queryForObject("SELECT device_id FROM ast_device_license_record WHERE id=105",Long.class));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_license_record WHERE id IN (102,103,104,106) AND device_id IS NULL",Integer.class));
        assertEquals(ciphertext,jdbc.queryForObject("SELECT license_code_ciphertext FROM ast_device_license_record WHERE id=101",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_license_record WHERE id=101 AND contract_id=701 AND creator='old' AND updater='old' AND update_time=?",Integer.class,at));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device WHERE id=11 AND project_id=91 AND customer_id=92 AND project_assignment_version=3 AND customer_assignment_version=4",Integer.class));
        assertNotEquals(before,saved.get("LICENSE_HISTORY:1").fingerprint());
        assertEquals(0,execute(false).updated());
        var config=session.getConfiguration();var q=new P37LicenseHistorySyncTask.TargetMapper.DeviceQuery(1,List.of("SN-1"),false);
        assertNotNull(com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(config.getMappedStatement(P37LicenseHistorySyncTask.TargetMapper.class.getName()+".selectDevices").getBoundSql(Map.of("query",q)).getSql()));
        var stage=context(false).forParameters(new P37LicenseHistorySyncTask.Parameters()).forData("DeviceReference",false,true);
        assertNotNull(com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(config.getMappedStatement(P37LicenseHistorySyncTask.TargetMapper.class.getName()+".updateDeviceReferences").getBoundSql(new SyncBatch<>(stage,List.of(rows.getFirst()))).getSql()));
    }

    @Test void changedHistoryOrMissingBindingFailsBeforeWriting(){
        var row=seed(1,"SN-1",null);emitRows(List.of(row));
        jdbc.update("UPDATE ast_device_license_record SET license_status='CHANGED' WHERE id=101");
        assertThrows(IllegalStateException.class,()->execute(false));
        assertNull(jdbc.queryForObject("SELECT device_id FROM ast_device_license_record WHERE id=101",Long.class));
        jdbc.update("UPDATE ast_device_license_record SET license_status='ACTIVE' WHERE id=101");saved.clear();
        assertThrows(IllegalStateException.class,()->execute(false));
        assertNull(jdbc.queryForObject("SELECT device_id FROM ast_device_license_record WHERE id=101",Long.class));
    }

    @Test void bindingConflictRollsBackLicenseReferenceAndDoesNotAlterDevice(){
        var row=seed(1,"SN-1",null);emitRows(List.of(row));
        doThrow(new IllegalStateException("binding changed")).when(identities).saveAll(any());
        assertThrows(IllegalStateException.class,()->execute(false));
        assertNull(jdbc.queryForObject("SELECT device_id FROM ast_device_license_record WHERE id=101",Long.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device WHERE id=11 AND project_id=91 AND customer_id=92",Integer.class));
    }

    @Test void changedCiphertextFailsBeforeLinking(){
        var row=seed(1,"SN-1",null);emitRows(List.of(row));
        jdbc.update("UPDATE ast_device_license_record SET license_code_ciphertext='{}' WHERE id=101");
        assertThrows(IllegalStateException.class,()->execute(false));
        assertNull(jdbc.queryForObject("SELECT device_id FROM ast_device_license_record WHERE id=101",Long.class));
    }

    @Test void referenceSourceQueryDoesNotReadLicenseCode(){
        jdbc.execute("CREATE TEMPORARY TABLE pm_project_license_info_from_license(id BIGINT,sn VARCHAR(255),specModel VARCHAR(256),`contract` VARCHAR(32),contractType VARCHAR(16),item VARCHAR(255),status VARCHAR(255),licenseCode VARCHAR(256))");
        jdbc.update("INSERT INTO pm_project_license_info_from_license VALUES(1,'SN-1',NULL,'C-1',NULL,'ITEM','ACTIVE','synthetic-secret')");
        var rows=new ArrayList<P37LicenseHistorySyncTask.LicenseSource>();
        session.getMapper(P37LicenseHistorySyncTask.SourceMapper.class).selectDeviceReference(new AbstractSyncTask.SourceQuery<>(10,1,true,new P37LicenseHistorySyncTask.Parameters()),result->rows.add(result.getResultObject()));
        assertEquals(1,rows.size());assertNull(rows.getFirst().getLicenseCode());assertEquals("SN-1",rows.getFirst().getSn());
    }
}
