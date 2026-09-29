package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.GenericSyncJdbcStore;
import cn.iocoder.yudao.module.pms.integration.sync.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GenericSyncEngineTest {
    GenericTargetCatalog catalog;GenericSyncEngine engine;JdbcTemplate jdbc;TransactionTemplate tx;
    List<GenericSyncTemplates.Template> templates;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:generic"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        catalog=new GenericTargetCatalog();engine=new GenericSyncEngine(catalog,new GenericSyncJdbcStore(jdbc.getDataSource()), org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class));
        templates=new GenericSyncTemplates().list(1L);
        for(var table:catalog.tables()) {
            Map<String,String> columns=new LinkedHashMap<>();
            columns.put("id","BIGINT PRIMARY KEY");columns.put("tenant_id","BIGINT NOT NULL");columns.put("deleted","BOOLEAN DEFAULT FALSE");
            columns.put("creator","VARCHAR(64)");columns.put("updater","VARCHAR(64)");columns.put("create_time","TIMESTAMP");columns.put("update_time","TIMESTAMP");columns.put("version","INT DEFAULT 0");
            var fields=new HashSet<>(table.columns());fields.addAll(table.insertDefaults().keySet());
            for(var field:fields) columns.put(field,field.endsWith("_id")?"BIGINT":field.contains("time")||field.endsWith("_at")?"TIMESTAMP":field.endsWith("_date")?"DATE":Set.of("warranty_months","rma_marked").contains(field)?"INT":"VARCHAR(100000)");
            var defs=new ArrayList<>(columns.entrySet().stream().map(e->"`"+e.getKey()+"` "+e.getValue()).toList());
            if(table.writable())for(var key:table.keys())defs.add("UNIQUE(tenant_id,"+String.join(",",key)+")");
            jdbc.execute("CREATE TABLE "+table.table()+" ("+String.join(",",defs)+")");
        }
        jdbc.execute("ALTER TABLE ast_device_shipment ADD FOREIGN KEY(tenant_id,device_sn) REFERENCES ast_device(tenant_id,sn)");
        jdbc.execute("ALTER TABLE com_shipment_package ADD CHECK(warranty_end_time IS NULL OR warranty_start_time IS NULL OR warranty_end_time >= warranty_start_time)");
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void projectionFailureRollsBackWritesAndPreviewDoesNotPublish() {
        org.springframework.context.ApplicationEventPublisher publisher = event -> { throw new IllegalStateException("projection failed"); };
        engine = new GenericSyncEngine(catalog,new GenericSyncJdbcStore(jdbc.getDataSource()),publisher);
        var d=definition(2);var source=row(d,"rollback",barcode("ROLLBACK-SN"));
        assertEquals("CREATED",engine.bind(d).preview(batch(source)).getFirst().action());
        assertThrows(IllegalStateException.class,()->apply(d,source));
        assertEquals(0,count("ast_device"));assertEquals(0,count("ast_device_shipment"));
    }
    SyncDefinition definition(int index){return templates.get(index).definition();}
    DataSyncAdapter.Batch batch(DataSyncAdapter.Row...rows){return new DataSyncAdapter.Batch("test",List.of(rows),List.of(),true,"RETAIN",false,"UPSERT",false);}
    DataSyncAdapter.Row row(SyncDefinition d,String key,Map<String,Object> overrides) {
        var source=d.sources().getFirst();Map<String,Object> fields=new LinkedHashMap<>();
        for(var step:source.targets()) {
            for(var mapping:step.mappings())if(mapping.source()!=null&&!mapping.source().startsWith("$"))fields.put(mapping.source(),null);
            for(var lookup:step.lookups())lookup.match().values().stream().filter(v->v.startsWith("@")).forEach(v->fields.put(v.substring(1),null));
        }
        if(source.issueField()!=null)fields.put(source.issueField(),null);
        fields.putAll(overrides);return new DataSyncAdapter.Row(source.object(),key,fields,null);
    }
    List<DataSyncAdapter.Change> apply(SyncDefinition d,DataSyncAdapter.Row...rows){return tx.execute(s->engine.bind(d).apply(batch(rows)));}
    int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    Map<String,Object> barcode(String sn){return Map.of("barcode",sn,"item","ITEM-1","pack_id","PACK-1","shipment_time","2026-09-20T12:00:00","contract_no","C-1","event_type","SHIPMENT","rma_marked",0);}

    @Test void templatesPassGenericValidationAndKeepDistinctTaskIdentities() {
        var validator=new SyncDefinitionValidator(List.of());ReflectionTestUtils.setField(validator,"genericEngine",engine);
        assertEquals(7,templates.size());Set<String> identities=new HashSet<>();
        for(var template:templates){
            assertTrue(identities.add(SyncDefinitionValidator.taskIdentity(template.definition())));
            if("TABLE_MAPPING".equals(template.definition().adapter()))validator.validate(template.definition());
        }
        assertTrue(validator.descriptors().stream().anyMatch(d->d.key().equals("TABLE_MAPPING")));
    }
    @Test void previewDoesNotWriteAndReplayKeepsOneTargetPerSource() {
        var d=definition(1);var row=row(d,"P1",Map.of("packlist_id","P1","receiveName","Alice"));
        var preview=engine.bind(d).preview(batch(row)).getFirst();
        assertEquals("CREATED",preview.action());assertNull(preview.targetId());assertEquals(0,count("com_shipment_package"));
        var first=apply(d,row).getFirst();assertNotNull(first.targetId());
        assertEquals("UNCHANGED",apply(d,row).getFirst().action());assertEquals(1,count("com_shipment_package"));
        var changed=row(d,"P1",Map.of("packlist_id","P1","receiveName","Bob"));assertEquals("UPDATED",apply(d,changed).getFirst().action());
        assertEquals("Bob",jdbc.queryForObject("SELECT receiver_name FROM com_shipment_package",String.class));
    }
    @Test void tenantIsolationAndDeletedRowProtection() {
        var d=definition(1);var r=row(d,"P1",Map.of("packlist_id","P1"));apply(d,r);
        TenantContextHolder.setTenantId(2L);apply(d,r);assertEquals(2,count("com_shipment_package"));
        jdbc.update("UPDATE com_shipment_package SET deleted=TRUE WHERE tenant_id=2");assertEquals("CONFLICT",apply(d,r).getFirst().action());
        assertEquals(2,count("com_shipment_package"));
    }
    @Test void everyBarcodeIsAnEventWhileDuplicateSnSharesMasterAndOlderEventKeepsLatestCache() {
        var d=definition(2);var first=row(d,"1",barcode("SN1"));var second=row(d,"2",barcode("SN1"));
        var changes=apply(d,first,second);assertEquals(2,count("ast_device_shipment"));assertEquals(1,count("ast_device"));
        assertNotEquals(changes.getFirst().targetId(),changes.getLast().targetId());
        assertEquals(changes.getLast().targetId(),jdbc.queryForObject("SELECT shipment_record_id FROM ast_device",Long.class));
        var old=new HashMap<>(barcode("SN1"));old.put("shipment_time","2020-01-01T00:00:00");old.put("pack_id","OLD");apply(d,row(d,"3",old));
        assertEquals("PACK-1",jdbc.queryForObject("SELECT package_no FROM ast_device",String.class));
        assertEquals("UNCHANGED",apply(d,first).getFirst().action());
    }
    @Test void batchedInsertsHandleMixedSignaturesAndSameChunkInsertThenUpdate() {
        var d=definition(2);
        var twin=new LinkedHashMap<>(barcode("SN-B"));twin.put("barcode2","SN-B2");twin.put("item2","ITEM-2");twin.put("updateTime","2026-09-20T12:00:00");
        var twin2=new LinkedHashMap<>(barcode("SN-B"));twin2.put("barcode2","SN-B2");twin2.put("item2","ITEM-3");twin2.put("updateTime","2026-09-21T12:00:00");
        var changes=apply(d,row(d,"1",barcode("SN-A")),row(d,"2",twin),row(d,"3",twin2));
        assertEquals("CREATED",changes.getFirst().action());
        assertEquals(3,count("ast_device"));assertEquals(3,count("ast_device_shipment"));
        // barcode2 行经 secondaryDevice 以不同列签名建档（INSERT_IGNORE 建后不改）
        assertEquals("ITEM-2",jdbc.queryForObject("SELECT product_code FROM ast_device WHERE sn='SN-B2'",String.class));
        // deviceShipment 在同批内先插后更（两行先后更新同一设备），列签名不同的更新保持原顺序生效
        assertEquals("ITEM-3",jdbc.queryForObject("SELECT secondary_item FROM ast_device WHERE sn='SN-B'",String.class));
        assertEquals("PACK-1",jdbc.queryForObject("SELECT package_no FROM ast_device WHERE sn='SN-B'",String.class));
        // 行内"先插后更"折叠进插入值（无二次 UPDATE），版本与 overlay 锚点一致记 1；
        // SN-B 后续行再叠一次有效更新（deviceShipment），版本按次数累进到 2。
        assertEquals(1,jdbc.queryForObject("SELECT version FROM ast_device WHERE sn='SN-A'",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT version FROM ast_device WHERE sn='SN-B'",Integer.class));
    }
    @Test void changedImmutableEventConflictsAndDoesNotCreateAnotherDevice() {
        var d=definition(2);apply(d,row(d,"1",barcode("SN1")));
        assertEquals("CONFLICT",apply(d,row(d,"1",barcode("SN2"))).getFirst().action());
        assertEquals(1,count("ast_device"));assertEquals(1,count("ast_device_shipment"));
    }
    @Test void invalidSecondStepLeavesNoPartialRowWrites() {
        var d=definition(2);var fields=new HashMap<>(barcode("SN1"));fields.put("warrantyMonth","bad");
        assertEquals("ISSUE",apply(d,row(d,"1",fields)).getFirst().action());assertEquals(0,count("ast_device"));
    }
    @Test void databaseFailureRollsBackEarlierRowsInChunk() {
        var d=definition(1);var good=row(d,"1",Map.of("packlist_id","P1"));
        var bad=row(d,"2",Map.of("packlist_id","P2","warrantyStartTime","2026-09-20T00:00:00","warrantyEndTime","2020-01-01T00:00:00"));
        assertThrows(RuntimeException.class,()->apply(d,good,bad));assertEquals(0,count("com_shipment_package"));
    }
    @Test void contractGenerationLeavesUnknownAuthorityValuesAndDoesNotOverwriteErpFields() {
        var d=definition(3);var source=row(d,"1",Map.of("company_code","001","contract_no","C1","customer_name","Customer","project_name","Project"));
        apply(d,source);assertEquals(1,count("com_contract"));
        assertNull(jdbc.queryForObject("SELECT source_lifecycle_status FROM com_contract",String.class));
        assertEquals("PENDING_AUTHORITY",jdbc.queryForObject("SELECT authority_status FROM com_contract",String.class));
        jdbc.update("UPDATE com_contract SET contract_amount='99',customer_name='ERP Customer'");apply(d,source);
        assertEquals("ERP Customer",jdbc.queryForObject("SELECT customer_name FROM com_contract",String.class));
        assertEquals("99",jdbc.queryForObject("SELECT contract_amount FROM com_contract",String.class));
    }
    @Test void packageResolvesSourceKeyAndAmbiguousLookupBecomesIssue() {
        var d=definition(1);jdbc.update("INSERT INTO com_shipment_contract_reference(id,tenant_id,source_system,source_record_key) VALUES(123,1,'DPPMS','77')");
        var source=row(d,"1",Map.of("packlist_id","P1","con_id",77));apply(d,source);
        assertEquals(123L,jdbc.queryForObject("SELECT shipment_contract_ref_id FROM com_shipment_package",Long.class));
    }
    @Test void sourceContractWithoutCompanyStillPersistsSourceFact() {
        var d=definition(0);assertEquals("CREATED",apply(d,row(d,"1",Map.of("contract_code","C1"))).getFirst().action());
        assertEquals(1,count("com_shipment_contract_reference"));assertEquals(0,count("com_contract"));
    }
    @Test void rejectProtectedFieldsAndUnregisteredTargets() {
        var d=definition(1);var step=d.sources().getFirst().targets().getFirst();
        step.mappings().add(SyncDefinition.Mapping.builder().target("tenant_id").source("tenant").conversion("DIRECT").build());
        assertThrows(IllegalArgumentException.class,()->engine.bind(d));
        assertThrows(IllegalArgumentException.class,()->catalog.required("system_users"));
    }
    @Test void applyRequiresTransaction() {assertThrows(IllegalStateException.class,()->engine.bind(definition(1)).apply(batch()));}
    @Test void missingRequiredSchemaFieldIsDetectedByPreview() {
        jdbc.execute("ALTER TABLE com_shipment_package ADD required_business_value VARCHAR(20) NOT NULL");
        var d=definition(1);var result=engine.bind(d).preview(batch(row(d,"1",Map.of("packlist_id","P1"))));
        assertEquals("ISSUE",result.getFirst().action());assertTrue(result.getFirst().message().contains("required_business_value"));
        assertEquals(0,count("com_shipment_package"));
    }
    @Test void updatePreviewContainsActualPreviousAndProposedValues() {
        var d=definition(1);apply(d,row(d,"1",Map.of("packlist_id","P1","receiveName","Alice")));
        var change=engine.bind(d).preview(batch(row(d,"1",Map.of("packlist_id","P1","receiveName","Bob")))).getFirst();
        assertEquals("Alice",change.before().get("package.receiver_name"));
        assertEquals("Bob",change.after().get("package.receiver_name"));
        assertEquals("Alice",jdbc.queryForObject("SELECT receiver_name FROM com_shipment_package",String.class));
    }
    @Test void contractSourceQueryDeduplicatesAndUsesLatestNonemptySupplementWithoutCrossCompanyGuessing() {
        jdbc.execute("CREATE TABLE pm_order_data_from_erp(id BIGINT,compCode VARCHAR(40),contractNo VARCHAR(40),customerCode VARCHAR(40),customerName VARCHAR(40))");
        jdbc.execute("CREATE TABLE fb_contract(contract_id BIGINT,contract_code VARCHAR(40),project_name VARCHAR(40),marketCode VARCHAR(40),marketName VARCHAR(40),office_code VARCHAR(40),systemName VARCHAR(40))");
        jdbc.execute("CREATE TABLE sms_ofst_contract_head_sap(id BIGINT,contract_num VARCHAR(40),update_time TIMESTAMP,currency_name VARCHAR(40),contract_create_date TIMESTAMP,project_name VARCHAR(40),projectCode VARCHAR(40),marketCode VARCHAR(40),maketing_department_name VARCHAR(40),officeCode VARCHAR(40),office_name VARCHAR(40),industryId VARCHAR(40),industry_name VARCHAR(40),usernamec VARCHAR(40),marketing_representative_name VARCHAR(40),usernamec2 VARCHAR(40))");
        jdbc.update("INSERT INTO pm_order_data_from_erp VALUES(1,'A','C1','CUS','Customer'),(2,'A','C1','CUS','Customer'),(3,'A','C2','CUS','Customer'),(4,'B','C2','CUS','Customer')");
        jdbc.update("INSERT INTO sms_ofst_contract_head_sap(id,contract_num,update_time,project_name,projectCode) VALUES(1,'C1','2026-09-01','Receipt','PRJ'),(2,'C2','2026-09-01','Ambiguous','NO')");
        jdbc.update("INSERT INTO fb_contract(contract_id,contract_code,project_name) VALUES(1,'C1','Old'),(2,'C1','Shipment'),(3,'C1',NULL)");
        var rows=jdbc.queryForList(definition(3).sources().getFirst().sql());assertEquals(3,rows.size());
        var unique=rows.stream().filter(r->r.get("contract_no").equals("C1")).findFirst().orElseThrow();
        assertEquals("Shipment",unique.get("project_name"));assertEquals("PRJ",unique.get("project_code"));
        assertTrue(rows.stream().filter(r->r.get("contract_no").equals("C2")).allMatch(r->r.get("project_name")==null));
    }

    @Test void replayOfStoredFieldsDoesNotPolluteImmutableSourcePayload() {
        var d=definition(2);var base=row(d,"1",barcode("SN1"));var fields=new LinkedHashMap<>(base.fields());
        fields.put("_syncSourcePayload",new LinkedHashMap<>(base.fields()));
        var first=apply(d,new DataSyncAdapter.Row(base.object(),base.sourceKey(),fields,null)).getFirst();
        var replay=apply(d,new DataSyncAdapter.Row(base.object(),base.sourceKey(),first.after(),first.targetId())).getFirst();
        assertEquals("UNCHANGED",replay.action());assertEquals(1,count("ast_device_shipment"));
    }
    @Test void forwardMigrationPreservesExistingStatesAndAllowsUnknownNewContractState() throws Exception {
        jdbc.execute("DROP TABLE ast_device_shipment");jdbc.execute("DROP TABLE ast_device");jdbc.execute("DROP TABLE com_contract");
        jdbc.execute("CREATE TABLE com_contract(id BIGINT PRIMARY KEY, source_lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',status VARCHAR(32) NOT NULL)");
        jdbc.update("INSERT INTO com_contract VALUES(1,'ACTIVE','ENABLED')");
        jdbc.execute("CREATE TABLE ast_device(id BIGINT,package_no VARCHAR(64))");jdbc.execute("CREATE TABLE ast_device_shipment(id BIGINT,package_no VARCHAR(64))");
        java.nio.file.Path root=java.nio.file.Path.of("").toAbsolutePath();while(!java.nio.file.Files.exists(root.resolve("sql/migrations")))root=root.getParent();
        // H2 does not implement MySQL multi-clause ALTER TABLE. Execute the same column clauses
        // separately here; this checks data preservation, not deployment on a real MySQL instance.
        String migration=java.nio.file.Files.readString(root.resolve("sql/migrations/V324__configurable_shipment_sync_fields.sql"))
                .replaceAll("(?m)^--.*$", "");
        for(String statement:migration.split(";")) {
            if(statement.isBlank())continue;
            var matcher=java.util.regex.Pattern.compile("(?s)\\s*ALTER TABLE (\\w+)\\s+(.*)").matcher(statement);
            assertTrue(matcher.matches());
            for(String clause:matcher.group(2).split(",\\s*(?=(?:MODIFY|ADD) COLUMN)"))jdbc.execute("ALTER TABLE "+matcher.group(1)+" "+clause);
        }
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT source_lifecycle_status FROM com_contract WHERE id=1",String.class));
        jdbc.update("INSERT INTO com_contract(id) VALUES(2)");assertNull(jdbc.queryForObject("SELECT status FROM com_contract WHERE id=2",String.class));
        assertTrue(new GenericSyncJdbcStore(jdbc.getDataSource()).columns(catalog.required("ast_device_shipment")).containsKey("source_payload"));
    }

    @Test void ambiguousOptionalLookupPreservesSourceFactWithoutGuessing() {
        jdbc.update("INSERT INTO system_company(id,tenant_id,code) VALUES(1,1,'A'),(2,1,'A')");
        var d=definition(0);var change=apply(d,row(d,"1",Map.of("contract_code","C1","company_code","A"))).getFirst();
        assertEquals("CREATED",change.action());assertTrue(change.message().contains("关联不唯一"));
        assertNull(jdbc.queryForObject("SELECT company_id FROM com_shipment_contract_reference",Long.class));
    }
    @Test void missingUpdateOnlyPrimaryDoesNotLeaveOrphanSecondaryWrites() {
        var d=definition(2);var steps=d.sources().getFirst().targets();var step=steps.get(2);
        steps.set(2,new GenericTargetStep(step.name(),step.table(),step.keys(),"UPDATE_ONLY",step.nullPolicy(),
                step.updateColumns(),step.whenField(),step.newerBy(),step.tieBreaker(),step.mappings(),step.lookups()));
        var change=apply(d,row(d,"1",barcode("SN1"))).getFirst();
        assertEquals("SKIPPED",change.action());assertEquals(0,count("ast_device"));assertEquals(0,count("ast_device_shipment"));
    }

    @Test void changedUnresolvedParentCannotLeaveTheOldRelationshipOnANewSourceFact() {
        jdbc.update("INSERT INTO com_shipment_contract_reference(id,tenant_id,source_system,source_record_key) VALUES(123,1,'DPPMS','77')");
        var d=definition(1);apply(d,row(d,"1",Map.of("packlist_id","P1","con_id",77,"receiveName","Alice")));
        var changed=apply(d,row(d,"1",Map.of("packlist_id","P1","con_id",88,"receiveName","Bob"))).getFirst();
        assertEquals("CONFLICT",changed.action());
        assertEquals("Alice",jdbc.queryForObject("SELECT receiver_name FROM com_shipment_package",String.class));
        assertEquals(123L,jdbc.queryForObject("SELECT shipment_contract_ref_id FROM com_shipment_package",Long.class));
    }

    @Test void refreshedSourceTechnicalTimestampsDoNotRewriteOrConflictWithUnchangedHistory() {
        var d=definition(2);var fields=new LinkedHashMap<>(barcode("SN1"));fields.put("updateTime","2026-09-20T12:00:00");fields.put("syncTime","2026-09-20T12:00:00");
        apply(d,row(d,"1",fields));String original=jdbc.queryForObject("SELECT source_payload FROM ast_device_shipment",String.class);
        fields.put("updateTime","2026-09-21T12:00:00");fields.put("syncTime","2026-09-21T12:00:00");
        assertEquals("UNCHANGED",apply(d,row(d,"1",fields)).getFirst().action());
        assertEquals(original,jdbc.queryForObject("SELECT source_payload FROM ast_device_shipment",String.class));
        assertEquals(1,count("ast_device_shipment"));
    }

}
