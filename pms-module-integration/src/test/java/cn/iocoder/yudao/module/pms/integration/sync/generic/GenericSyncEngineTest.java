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
            for(var field:fields) columns.put(field,field.endsWith("_id")?"BIGINT":field.contains("time")||field.endsWith("_at")?"TIMESTAMP":field.endsWith("_date")?"DATE":Set.of("warranty_months","warranty_years","rma_marked").contains(field)?"INT":"VARCHAR(100000)");
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
            for(var lookup:step.lookups()==null?List.<GenericTargetStep.Lookup>of():step.lookups())lookup.match().values().stream().filter(v->v.startsWith("@")).forEach(v->fields.put(v.substring(1),null));
        }
        if(source.issueField()!=null)fields.put(source.issueField(),null);
        fields.putAll(overrides);return new DataSyncAdapter.Row(source.object(),key,fields,null);
    }
    List<DataSyncAdapter.Change> apply(SyncDefinition d,DataSyncAdapter.Row...rows){return tx.execute(s->engine.bind(d).apply(batch(rows)));}
    int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    Map<String,Object> barcode(String sn){return Map.of("barcode",sn,"item","ITEM-1","pack_id","PACK-1","shipment_time","2026-09-20T12:00:00","contract_no","C-1","event_type","SHIPMENT","rma_marked",0);}

    @Test void deliveryPreviewReusesOnlyCommittedPlannedParentsAcrossChunks() {
        var d=DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P15",1L));
        d.sources().getFirst().readMode("LOCAL_OWNER").sourceObject("com_sales_order");
        var packages=d.sources().stream().filter(source->"PACKAGE".equals(source.object())).findFirst().orElseThrow();
        var dictionaries=org.mockito.Mockito.mock(cn.iocoder.yudao.module.system.api.dict.DictDataApi.class);
        ReflectionTestUtils.setField(engine,"dictionaries",dictionaries);
        var facts=org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentSourceFactsApi.class);
        ReflectionTestUtils.setField(engine,"shipmentFacts",facts);
        var adapter=engine.bind(d);
        var shipment=d.sources().stream().filter(source->"SHIPMENT_CONTRACT".equals(source.object())).findFirst().orElseThrow();
        var event=d.sources().stream().filter(source->"DEVICE_SN".equals(source.object())).findFirst().orElseThrow();
        var parent=row(d.toBuilder().sources(List.of(shipment)).build(),"RAW-C",Map.of("contract_code","C1"));
        var pack=row(d.toBuilder().sources(List.of(packages)).build(),"PACK-1",Map.of("packlist_id","PACK-1","con_id","RAW-C","packdate","2020-01-01T12:00:00"));
        var incoming=row(d.toBuilder().sources(List.of(event)).build(),"SN-1",barcode("SN-1"));
        tx.executeWithoutResult(status->assertEquals("CREATED",adapter.preview(batch(parent)).getFirst().action()));
        tx.executeWithoutResult(status->assertEquals("CREATED",adapter.preview(batch(pack)).getFirst().action()));
        var planned=tx.execute(status->adapter.preview(batch(incoming))).getFirst();
        assertEquals("CREATED",planned.action(),planned.message());
        assertEquals("C1",planned.after().get("event.contract_no"));
        org.mockito.Mockito.verifyNoInteractions(facts);
        assertEquals(0,count("com_shipment_package"));assertEquals(0,count("ast_device_shipment"));
        var isolated=engine.bind(d);
        tx.executeWithoutResult(status->{isolated.preview(batch(parent));status.setRollbackOnly();});
        tx.executeWithoutResult(status->isolated.preview(batch(pack)));
        var noParent=tx.execute(status->isolated.preview(batch(incoming))).getFirst();
        assertEquals("ISSUE",noParent.action());assertTrue(noParent.message().contains("本地包裹或原发货时间缺失"));
    }

    @Test void deliveryFirstImportCarriesShipmentHistoryBeforeP30CreatesDeviceMasters() {
        // V351 removes SN foreign keys: history may precede the separate device-base task.
        var foreignKeys=jdbc.queryForList("SELECT constraint_name FROM information_schema.table_constraints WHERE table_name='ast_device_shipment' AND constraint_type='FOREIGN KEY'",String.class);
        for(var name:foreignKeys)jdbc.execute("ALTER TABLE ast_device_shipment DROP CONSTRAINT \""+name.replace("\"","\"\"")+"\"");
        var d=DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P15",1L));
        ReflectionTestUtils.setField(engine,"dictionaries",org.mockito.Mockito.mock(cn.iocoder.yudao.module.system.api.dict.DictDataApi.class));
        var facts=org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentSourceFactsApi.class);
        ReflectionTestUtils.setField(engine,"shipmentFacts",facts);
        org.mockito.Mockito.when(facts.read(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                new cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentSourceFactsApi.Fact("PACK-1",java.time.LocalDateTime.of(2020,1,1,12,0),"C1")));
        var source=d.sources().stream().filter(item->"DEVICE_SN".equals(item.object())).findFirst().orElseThrow();
        var fields=new LinkedHashMap<>(barcode("SN-FIRST"));fields.put("id",1L);
        var incoming=row(d.toBuilder().sources(List.of(source)).build(),"1",fields);
        var result=tx.execute(status->engine.bind(d).apply(batch(incoming))).getFirst();
        assertEquals("CREATED",result.action(),result.message());assertEquals(0,count("ast_device"));assertEquals(1,count("ast_device_shipment"));
        assertEquals("C1",jdbc.queryForObject("SELECT contract_no FROM ast_device_shipment WHERE source_key='1'",String.class));
    }

    @Test void deliveryOldEventsRetainFrozenParentsButEveryOriginalFieldAndTargetStillRemainProtected() {
        var d=DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P15",1L));
        var dictionaries=org.mockito.Mockito.mock(cn.iocoder.yudao.module.system.api.dict.DictDataApi.class);
        var facts=org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentSourceFactsApi.class);
        ReflectionTestUtils.setField(engine,"dictionaries",dictionaries);ReflectionTestUtils.setField(engine,"shipmentFacts",facts);
        org.mockito.Mockito.when(facts.read(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                new cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentSourceFactsApi.Fact("PACK-1",java.time.LocalDateTime.of(2020,1,1,12,0),"C1")));
        jdbc.update("INSERT INTO ast_device(id,tenant_id,sn) VALUES(1,1,'SN-OLD')");
        var source=d.sources().stream().filter(item->"DEVICE_SN".equals(item.object())).findFirst().orElseThrow();
        var initial=row(d.toBuilder().sources(List.of(source)).build(),"1",barcode("SN-OLD"));
        var raw=new LinkedHashMap<>(initial.fields());raw.remove("shipment_time");raw.remove("contract_no");
        raw.put("id",1L);raw.put("unmappedOriginal","first");raw.put("updateTime","2020-01-01 10:00:00.000000");
        var incoming=new LinkedHashMap<>(raw);incoming.put("_syncSourcePayload",new LinkedHashMap<>(raw));
        var fresh=new DataSyncAdapter.Row("DEVICE_SN","1",incoming,null);
        var first=tx.execute(status->engine.bind(d).apply(batch(fresh))).getFirst();
        assertEquals("CREATED",first.action(),first.message());
        String frozenPayload=jdbc.queryForObject("SELECT source_payload FROM ast_device_shipment WHERE id=?",String.class,first.targetId());
        assertTrue(frozenPayload.contains("C1"));
        var binding=new DataSyncAdapter.Binding("DEVICE_SN","1",first.targetId(),first.after());
        org.mockito.Mockito.clearInvocations(facts);
        var replayRaw=new LinkedHashMap<>(raw);replayRaw.put("updateTime",java.sql.Timestamp.valueOf("2020-01-01 10:00:00"));
        var replayFields=new LinkedHashMap<>(replayRaw);replayFields.put("_syncSourcePayload",new LinkedHashMap<>(replayRaw));
        var replay=new DataSyncAdapter.Row("DEVICE_SN","1",replayFields,first.targetId());
        var sameBatch=new DataSyncAdapter.Batch("test",List.of(replay),List.of(binding),true,"RETAIN",false,"UPSERT",false);
        var unchanged=tx.execute(status->engine.bind(d).apply(sameBatch)).getFirst();
        assertEquals("UNCHANGED",unchanged.action(),unchanged.message());org.mockito.Mockito.verifyNoInteractions(facts);
        assertEquals(frozenPayload,jdbc.queryForObject("SELECT source_payload FROM ast_device_shipment WHERE id=?",String.class,first.targetId()));
        var changedRaw=new LinkedHashMap<>(replayRaw);changedRaw.put("unmappedOriginal","changed");
        var changedFields=new LinkedHashMap<>(changedRaw);changedFields.put("_syncSourcePayload",changedRaw);
        var changed=new DataSyncAdapter.Row("DEVICE_SN","1",changedFields,first.targetId());
        var conflict=tx.execute(status->engine.bind(d).apply(new DataSyncAdapter.Batch("test",List.of(changed),List.of(binding),true,"RETAIN",false,"UPSERT",false))).getFirst();
        assertEquals("CONFLICT",conflict.action());assertTrue(conflict.message().contains("unmappedOriginal"));
        var missingRaw=new LinkedHashMap<>(replayRaw);missingRaw.remove("unmappedOriginal");
        var missingFields=new LinkedHashMap<>(missingRaw);missingFields.put("_syncSourcePayload",missingRaw);
        var missing=new DataSyncAdapter.Row("DEVICE_SN","1",missingFields,first.targetId());
        assertEquals("CONFLICT",tx.execute(status->engine.bind(d).apply(new DataSyncAdapter.Batch("test",List.of(missing),List.of(binding),true,"RETAIN",false,"UPSERT",false))).getFirst().action());
        jdbc.update("UPDATE ast_device_shipment SET shipment_time='2022-01-01 12:00:00' WHERE id=?",first.targetId());
        assertEquals("CONFLICT",tx.execute(status->engine.bind(d).apply(sameBatch)).getFirst().action());
        assertEquals(frozenPayload,jdbc.queryForObject("SELECT source_payload FROM ast_device_shipment WHERE id=?",String.class,first.targetId()));
    }

    @Test void deliveryReadsCarrierDictionaryOnceAndRetainsAmbiguousOriginalCode() {
        var d=definition(1).toBuilder().taskKey("delivery-full-sync").build();
        var source=d.sources().getFirst();var step=source.targets().getFirst();
        var mappings=step.mappings().stream().map(mapping->"carrier_name".equals(mapping.target())
                ?SyncDefinition.Mapping.builder().source("emsCompany").target("carrier_name").conversion("LOCAL_CARRIER_NAME").build():mapping).toList();
        source.targets(List.of(new GenericTargetStep(step.name(),step.table(),step.keys(),step.mode(),step.nullPolicy(),
                step.updateColumns(),step.whenField(),step.newerBy(),step.tieBreaker(),mappings,step.lookups(),step.sameVersionColumns())));
        var dictionaries=org.mockito.Mockito.mock(cn.iocoder.yudao.module.system.api.dict.DictDataApi.class);
        org.mockito.Mockito.when(dictionaries.getDictDataList("01")).thenReturn(List.of(
                new cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO().setDictType("01").setValue("0").setLabel("A"),
                new cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO().setDictType("01").setValue("0").setLabel("B"),
                new cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO().setDictType("01").setValue("2").setLabel("C")));
        ReflectionTestUtils.setField(engine,"dictionaries",dictionaries);
        var adapter=engine.bind(d);
        var ambiguous=adapter.preview(batch(row(d,"P1",Map.of("packlist_id","P1","emsCompany","0")))).getFirst();
        assertEquals("CREATED",ambiguous.action());assertEquals("0",ambiguous.after().get("package.carrier_name"));
        assertTrue(ambiguous.message().contains("本地物流名称有歧义"));
        var exact=adapter.preview(batch(row(d,"P2",Map.of("packlist_id","P2","emsCompany","2")))).getFirst();
        assertEquals("C",exact.after().get("package.carrier_name"));
        org.mockito.Mockito.verify(dictionaries,org.mockito.Mockito.times(1)).getDictDataList("01");
    }

    @Test void contractYearsAreNumericAndEmptySourceClearsOnlyThatField() {
        var d=definition(0);
        apply(d,row(d,"YEAR-C",Map.of("contract_code","C","warranty","3 ","customer_name","original")));
        assertEquals(3,jdbc.queryForObject("SELECT warranty_years FROM com_shipment_contract_reference",Integer.class));
        var preview=engine.bind(d).preview(batch(row(d,"YEAR-C",Map.of("contract_code","C","warranty","  "))));
        assertEquals("UPDATED",preview.getFirst().action());
        assertEquals(3,jdbc.queryForObject("SELECT warranty_years FROM com_shipment_contract_reference",Integer.class));
        apply(d,row(d,"YEAR-C",Map.of("contract_code","C","warranty","  ")));
        assertNull(jdbc.queryForObject("SELECT warranty_years FROM com_shipment_contract_reference",Integer.class));
        assertEquals("original",jdbc.queryForObject("SELECT customer_name FROM com_shipment_contract_reference",String.class));
        assertEquals("UNCHANGED",apply(d,row(d,"YEAR-C",Map.of("contract_code","C"))).getFirst().action());
        apply(d,row(d,"YEAR-C",Map.of("contract_code","C","warranty","0 ")));
        assertEquals(0,jdbc.queryForObject("SELECT warranty_years FROM com_shipment_contract_reference",Integer.class));
    }
    @Test void invalidContractYearsDoNotCreateOrPartiallyUpdateReferences() {
        var d=definition(0);
        for(String value:List.of("-1","3.5","1e1","2147483648")) {
            assertEquals("ISSUE",apply(d,row(d,"BAD-YEAR",Map.of("contract_code","C","warranty",value))).getFirst().action());
            assertEquals(0,count("com_shipment_contract_reference"));
        }
    }
    @Test void templatesPassGenericValidationAndKeepDistinctTaskIdentities() {
        var validator=new SyncDefinitionValidator(List.of());ReflectionTestUtils.setField(validator,"genericEngine",engine);
        assertEquals(8,templates.size());Set<String> identities=new HashSet<>();
        for(var template:templates){
            assertTrue(identities.add(SyncDefinitionValidator.taskIdentity(template.definition())));
            if("TABLE_MAPPING".equals(template.definition().adapter()))validator.validate(template.definition());
        }
        assertTrue(validator.descriptors().stream().anyMatch(d->d.key().equals("TABLE_MAPPING")));
    }
    @Test void evidenceReuseIsOptInAndRejectsIncompatibleExecutionModes() {
        var validator=new SyncDefinitionValidator(List.of());ReflectionTestUtils.setField(validator,"genericEngine",engine);
        var d=definition(1).toBuilder().readStrategy("STREAMING_CURSOR").evidencePolicy("REUSE_UNCHANGED").build();
        assertDoesNotThrow(()->validator.validate(d));
        for(var invalid:List.of(d.toBuilder().readStrategy("SNAPSHOT").build(),
                d.toBuilder().missingPolicy("DISABLE").build(),d.toBuilder().clearBeforeLoad(true).build(),
                d.toBuilder().resetMappingsBeforeLoad(true).build(),d.toBuilder().evidencePolicy("SKIP_ALL").build()))
            assertThrows(IllegalArgumentException.class,()->validator.validate(invalid));
        var json=cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(d);
        var roundTrip=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(json,SyncDefinition.class);
        assertEquals("REUSE_UNCHANGED",roundTrip.effectiveEvidencePolicy());
        var legacy=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseMap(json);
        legacy.remove("evidencePolicy");
        assertEquals("FULL",cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(legacy),SyncDefinition.class).effectiveEvidencePolicy());
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
    @Test void existingTargetSkipIsReportedWithoutChangingTheTargetInPreviewOrApply() {
        var d=definition(1);
        var first=apply(d,row(d,"P1",Map.of("packlist_id","P1","receiveName","Alice"))).getFirst();
        var source=d.sources().getFirst();var steps=source.targets();
        var step=source.primaryTarget()==null?steps.getLast():steps.stream().filter(s->s.name().equals(source.primaryTarget())).findFirst().orElseThrow();
        steps.set(steps.indexOf(step),new GenericTargetStep(step.name(),step.table(),step.keys(),"INSERT_IGNORE",step.nullPolicy(),
                step.updateColumns(),step.whenField(),step.newerBy(),step.tieBreaker(),step.mappings(),step.lookups()));
        var changed=row(d,"P1",Map.of("packlist_id","P1","receiveName","Bob"));
        assertEquals("SKIPPED",engine.bind(d).preview(batch(changed)).getFirst().action());
        var skipped=apply(d,changed).getFirst();
        assertEquals("SKIPPED",skipped.action());assertEquals(first.targetId(),skipped.targetId());
        assertEquals("Alice",jdbc.queryForObject("SELECT receiver_name FROM com_shipment_package",String.class));
    }
    @Test void bindingKeepsOnlyTheSourceTimeWhileTheChangeRetainsReconciliationFacts() {
        var d=definition(1);var adapter=engine.bind(d);
        var fields=new LinkedHashMap<>(row(d,"P1",Map.of("packlist_id","P1","receiveName","Alice")).fields());
        fields.put("_sourceUpdatedAt","2026-10-06T12:00:00");
        var change=tx.execute(s->adapter.apply(batch(new DataSyncAdapter.Row(d.sources().getFirst().object(),"P1",fields,null)))).getFirst();
        assertEquals(Map.of("_sourceUpdatedAt","2026-10-06T12:00:00"),adapter.bindingFields(change));
        assertTrue(change.after().containsKey("_targets"));assertTrue(change.after().containsKey("receiveName"));
        assertEquals(Map.of(),adapter.bindingFields(new DataSyncAdapter.Change(change.object(),"P2",null,"CREATED",Map.of(),Map.of("payload","large-source"),null)));
    }
    @Test void fullDeliveryTemplateOrdersDependenciesAndPreservesEveryBarcodeField() {
        var d=templates.stream().filter(t->"delivery-full-sync".equals(t.key())).findFirst().orElseThrow().definition();
        assertEquals(List.of("CONTRACT","SHIPMENT_CONTRACT","PACKAGE","DEVICE_SN","DEVICE_RELATION"),
                d.sources().stream().map(SyncDefinition.Source::object).toList());
        assertEquals("CHECKPOINT_KEY",d.effectiveRestartPolicy());
        assertEquals(Set.of("com_contract","com_shipment_contract_reference","com_shipment_package",
                "ast_device_shipment","ast_device_relationship"),new HashSet<>(engine.bind(d).writeTables()));
        var event=d.sources().get(3).targets().stream().filter(t->"event".equals(t.name())).findFirst().orElseThrow();
        assertTrue(event.lookups()==null || event.lookups().isEmpty());
        assertTrue(event.mappings().stream().map(SyncDefinition.Mapping::source).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet())
                .containsAll(List.of("item","barcode2","item2","com_barcode","isRMA","orderNumber","lineNum",
                        "profitCenter","soleAgentSuffix","warrantyStartDate","warrantyMonth","rmaBarcode","updateTime","syncTime","uuid")));
    }
    @Test void batchedLookupsAvoidPerRowRoundTripsAndReuseSchemasAcrossChunks() {
        var store=org.mockito.Mockito.spy(new GenericSyncJdbcStore(jdbc.getDataSource()));
        engine=new GenericSyncEngine(catalog,store,org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class));
        var d=definition(1);var sink=engine.bind(d);
        jdbc.update("INSERT INTO com_shipment_contract_reference(id,tenant_id,source_system,source_record_key) VALUES(100,1,'DPPMS','parent')");
        var rows=java.util.stream.IntStream.range(0,2000).mapToObj(i->row(d,"P"+i,Map.of("packlist_id","P"+i,"con_id","parent"))).toArray(DataSyncAdapter.Row[]::new);
        tx.execute(s->sink.apply(batch(rows)));
        tx.execute(s->sink.apply(batch(rows)));
        assertEquals(2000,count("com_shipment_package"));
        assertEquals(2000,jdbc.queryForObject("SELECT COUNT(*) FROM com_shipment_package WHERE shipment_contract_ref_id=100",Integer.class));
        org.mockito.Mockito.verify(store,org.mockito.Mockito.never()).find(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(store,org.mockito.Mockito.times(1)).columns(catalog.required("com_shipment_package"));
    }
    @Test void missingShipmentContractKeyRetainsRawIssueAndContinuesWithoutBusinessBinding() {
        var full=templates.stream().filter(t->"delivery-full-sync".equals(t.key())).findFirst().orElseThrow().definition();
        var source=full.sources().get(1);
        var d=full.toBuilder().sources(List.of(source)).build();
        jdbc.update("INSERT INTO com_contract(id,tenant_id,company_code,contract_no,project_name) VALUES(100,1,'001','C1','Keep')");
        var invalid=row(d,"invalid-source-key:evidence",Map.of("contract_id","","company_code","01",
                "contract_code","C1","project_name","Invalid",source.issueField(),"SOURCE_KEY_MISSING"));
        var valid=row(d,"VALID-1",Map.of("contract_id","VALID-1","company_code","01","contract_code","C1","project_name","Valid"));
        var changes=apply(d,invalid,valid);
        assertEquals("ISSUE",changes.getFirst().action());assertNull(changes.getFirst().targetId());
        assertEquals("",changes.getFirst().after().get("contract_id"));
        assertEquals("SOURCE_KEY_MISSING",changes.getFirst().message());
        assertEquals("CREATED",changes.getLast().action());
        assertEquals(1,count("com_shipment_contract_reference"));
        assertEquals("VALID-1",jdbc.queryForObject("SELECT source_record_key FROM com_shipment_contract_reference",String.class));
        assertEquals("Valid",jdbc.queryForObject("SELECT project_name FROM com_contract WHERE id=100",String.class));
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
    @Test void fractionalIntegerInOneRowIsAnIssueWithoutLeakingItsEarlierWrites() {
        var d=definition(2);var invalid=new HashMap<>(barcode("BAD-FRACTION"));invalid.put("warrantyMonth","1.5");
        var changes=apply(d,row(d,"1",invalid),row(d,"2",barcode("GOOD-INTEGER")));
        assertEquals(List.of("ISSUE","CREATED"),changes.stream().map(DataSyncAdapter.Change::action).toList());
        assertEquals(1,count("ast_device"));assertEquals(1,count("ast_device_shipment"));
        assertEquals("GOOD-INTEGER",jdbc.queryForObject("SELECT sn FROM ast_device",String.class));
    }
    @Test void databaseFailureRollsBackEarlierRowsInChunk() {
        var d=definition(1);var good=row(d,"1",Map.of("packlist_id","P1"));
        var bad=row(d,"2",Map.of("packlist_id","P2","warrantyStartTime","2026-09-20T00:00:00","warrantyEndTime","2020-01-01T00:00:00"));
        assertThrows(RuntimeException.class,()->apply(d,good,bad));assertEquals(0,count("com_shipment_package"));
    }
    @Test void contractGenerationLeavesUnknownAuthorityValuesAndDoesNotOverwriteErpFields() {
        var d=definition(3);var source=row(d,"1",Map.of("company_code","01","contract_no","C1","customer_name","Customer","project_name","Project"));
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
    @Test void packagePreservesCarrierCodeAndDecodedNameWithoutRewritingUnchangedRecords() {
        var d=definition(1);
        var source=row(d,"PACK-CARRIER",Map.of("packlist_id","PACK-CARRIER","emsCompany","0","carrier_name","顺丰"));
        assertEquals("CREATED",apply(d,source).getFirst().action());
        var before=jdbc.queryForMap("SELECT * FROM com_shipment_package");
        assertEquals("0",before.get("carrier_code"));
        assertEquals("顺丰",before.get("carrier_name"));
        assertEquals("UNCHANGED",apply(d,source).getFirst().action());
        assertEquals(before,jdbc.queryForMap("SELECT * FROM com_shipment_package"));
    }
    @Test void packageCarrierCodesAreNotCompanyCodesAndCannotOverwriteAnotherTenant() {
        var d=definition(1);
        jdbc.update("INSERT INTO com_shipment_package(id,tenant_id,source_system,source_record_key,package_no,carrier_code,carrier_name) VALUES(50,2,'DPPMS','PACK','PACK','0','另一租户')");
        apply(d,row(d,"PACK",Map.of("packlist_id","PACK","emsCompany","01","carrier_name","01")));
        assertEquals("01",jdbc.queryForObject("SELECT carrier_code FROM com_shipment_package WHERE tenant_id=1",String.class));
        assertEquals("另一租户",jdbc.queryForObject("SELECT carrier_name FROM com_shipment_package WHERE tenant_id=2",String.class));
    }
    @Test void sourceContractWithoutCompanyStillPersistsSourceFact() {
        var d=definition(0);assertEquals("CREATED",apply(d,row(d,"1",Map.of("contract_code","C1"))).getFirst().action());
        assertEquals(1,count("com_shipment_contract_reference"));assertEquals(0,count("com_contract"));
    }
    @Test void approvedCompanyRuleResolvesCanonicalCompanyAndKeepsSourceValue() {
        var d=definition(0);
        for(int company=1;company<=4;company++) {
            String raw="0"+company, canonical="00"+company;
            jdbc.update("INSERT INTO system_company(id,tenant_id,code) VALUES(?,1,?)",company,canonical);
            var original=row(d,"C"+company,Map.of("contract_code","C"+company,"company_code",raw));
            var result=apply(d,original).getFirst();
            assertEquals("CREATED",result.action());
            assertEquals(canonical,jdbc.queryForObject("SELECT company_code FROM com_shipment_contract_reference WHERE source_record_key=?",String.class,"C"+company));
            assertEquals((long)company,jdbc.queryForObject("SELECT company_id FROM com_shipment_contract_reference WHERE source_record_key=?",Long.class,"C"+company));
            assertEquals(raw,original.fields().get("company_code"));
        }
    }
    @Test void companyConvertedUpdateOnlyLookupLinksExactContractWithoutChangingFacts() {
        var base=definition(0);var first=base.sources().getFirst();var step=first.targets().stream()
                .filter(target->"com_shipment_contract_reference".equals(target.table())).findFirst().orElseThrow();
        var mappings=new ArrayList<SyncDefinition.Mapping>();
        mappings.add(SyncDefinition.Mapping.builder().source("$SOURCE_KEY").target("source_record_key").conversion("STRING").build());
        mappings.add(SyncDefinition.Mapping.builder().source("$SOURCE_SYSTEM").target("source_system").conversion("DIRECT").build());
        mappings.add(SyncDefinition.Mapping.builder().source("company_code").target("company_code").conversion("DPPMS_COMPANY_CODE").build());
        var lookup=new GenericTargetStep.Lookup("com_contract",Map.of("contract_no","@contract_code","company_code","company_code"),Map.of("contract_id","id"),"SKIP_STEP");
        var link=new GenericTargetStep("shipmentContractLink",step.table(),step.keys(),"UPDATE_ONLY","IGNORE",List.of("contract_id"),"company_code",null,null,mappings,List.of(lookup));
        var d=base.toBuilder().sources(List.of(first.toBuilder().targets(List.of(link)).build())).build();
        jdbc.update("INSERT INTO com_contract(id,tenant_id,contract_no,company_code) VALUES(10,1,'C1','001'),(20,1,'C1','002')");
        jdbc.update("INSERT INTO com_shipment_contract_reference(id,tenant_id,source_system,source_record_key,company_code,customer_name) VALUES(30,1,'DPPMS','REF','002','Historical customer')");
        var source=row(d,"REF",Map.of("contract_code","C1","company_code","02","normalized_company_code","02"));
        assertEquals("UPDATED",apply(d,source).getFirst().action());
        assertEquals(20L,jdbc.queryForObject("SELECT contract_id FROM com_shipment_contract_reference WHERE id=30",Long.class));
        assertEquals("Historical customer",jdbc.queryForObject("SELECT customer_name FROM com_shipment_contract_reference WHERE id=30",String.class));
        assertEquals("02",source.fields().get("company_code"));
        var persisted=jdbc.queryForMap("SELECT * FROM com_shipment_contract_reference WHERE id=30");
        var rejected=apply(d,row(d,"REF",Map.of("contract_code","C1","company_code","05"))).getFirst();
        assertEquals("ISSUE",rejected.action());assertNull(rejected.targetId());
        assertTrue(rejected.message().contains("DPPMS_COMPANY_CODE_UNMAPPED"));
        assertEquals(persisted,jdbc.queryForMap("SELECT * FROM com_shipment_contract_reference WHERE id=30"));
        assertEquals(1,count("com_shipment_contract_reference"));assertEquals(2,count("com_contract"));
    }
    @Test void unknownCompanyCannotWriteEitherMasterOrReference() {
        for(var raw:List.of("05","A")) {
            var d=definition(0);
            var failure=apply(d,row(d,raw,Map.of("contract_code","C-"+raw,"company_code",raw))).getFirst();
            assertEquals("ISSUE",failure.action());assertNull(failure.targetId());
            assertTrue(failure.message().contains("DPPMS_COMPANY_CODE_UNMAPPED"));
        }
        assertEquals(0,count("com_contract"));assertEquals(0,count("com_shipment_contract_reference"));
    }
    @Test void serializedTemplatePreservesTheOptionalNullCompanyRule() {
        var encoded=cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(definition(0));
        var definition=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(encoded,SyncDefinition.class);
        assertEquals("CREATED",apply(definition,row(definition,"NULL-COMPANY",Map.of("contract_code","C-NULL"))).getFirst().action());
        assertEquals(0,count("com_contract"));assertEquals(1,count("com_shipment_contract_reference"));
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
        jdbc.execute("CREATE TABLE fb_contract(contract_id VARCHAR(64),contract_code VARCHAR(40),project_name VARCHAR(40),marketCode VARCHAR(40),marketName VARCHAR(40),office_code VARCHAR(40),systemName VARCHAR(40))");
        jdbc.execute("CREATE TABLE sms_ofst_contract_head_sap(id BIGINT,contract_num VARCHAR(40),update_time TIMESTAMP,currency_name VARCHAR(40),contract_money_amount DECIMAL(20,2),contract_create_date TIMESTAMP,project_name VARCHAR(40),projectCode VARCHAR(40),marketCode VARCHAR(40),maketing_department_name VARCHAR(40),officeCode VARCHAR(40),office_name VARCHAR(40),industryId VARCHAR(40),industry_name VARCHAR(40),usernamec VARCHAR(40),marketing_representative_name VARCHAR(40),usernamec2 VARCHAR(40))");
        jdbc.update("INSERT INTO pm_order_data_from_erp VALUES(1,'A','C1','CUS','Customer'),(2,'A','C1','CUS','Customer'),(3,'A','C2','CUS','Customer'),(4,'B','C2','CUS','Customer')");
        jdbc.update("INSERT INTO sms_ofst_contract_head_sap(id,contract_num,update_time,project_name,projectCode) VALUES(1,'C1','2026-09-01','Receipt','PRJ'),(2,'C2','2026-09-01','Ambiguous','NO')");
        jdbc.update("INSERT INTO fb_contract(contract_id,contract_code,project_name) VALUES(1,'C1','Old'),(2,'C1','Shipment'),(3,'C1',NULL)");
        // MySQL's unbounded CAST AS CHAR is CHAR(1) in H2; use H2's equivalent text cast for this fixture.
        var rows=jdbc.queryForList(definition(3).sources().getFirst().sql().replace(" AS CHAR)"," AS VARCHAR)"));assertEquals(3,rows.size());
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
        jdbc.update("INSERT INTO system_company(id,tenant_id,code) VALUES(1,1,'001'),(2,1,'001')");
        var d=definition(0);var change=apply(d,row(d,"1",Map.of("contract_code","C1","company_code","01"))).getFirst();
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

    @Test void aFailedRowDoesNotLeakStagedDeviceIntoTheNextRow() {
        var d=definition(2);
        var bad=new HashMap<>(barcode("SHARED-SN"));bad.put("warrantyMonth","bad");
        var changes=apply(d,row(d,"bad",bad),row(d,"good",barcode("SHARED-SN")));
        assertEquals(List.of("ISSUE","CREATED"),changes.stream().map(DataSyncAdapter.Change::action).toList());
        assertEquals(1,count("ast_device"));assertEquals(1,count("ast_device_shipment"));
    }
    @Test void sourceResolvableLookupsArePrefetchedInOneDistinctKeyBatch() {
        var store=org.mockito.Mockito.spy(new GenericSyncJdbcStore(jdbc.getDataSource()));
        engine=new GenericSyncEngine(catalog,store,event->{});
        var d=definition(1);
        List<DataSyncAdapter.Row> rows=new ArrayList<>();
        for(int index=0;index<40;index++) {
            jdbc.update("INSERT INTO com_shipment_contract_reference(id,tenant_id,source_system,source_record_key) VALUES(?,1,'DPPMS',?)",1000+index,Integer.toString(index));
            rows.add(row(d,"P"+index,Map.of("packlist_id","P"+index,"con_id",Integer.toString(index))));
        }
        var changes=apply(d,rows.toArray(DataSyncAdapter.Row[]::new));
        assertEquals(40,changes.size());assertEquals(40,count("com_shipment_package"));
        org.mockito.Mockito.verify(store,org.mockito.Mockito.never()).find(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(store).findByKeys(org.mockito.ArgumentMatchers.argThat(table->table.table().equals("com_shipment_contract_reference")),
                org.mockito.ArgumentMatchers.argThat(keys->keys.size()==40),org.mockito.ArgumentMatchers.eq(true));
        assertEquals(40,jdbc.queryForObject("SELECT COUNT(*) FROM com_shipment_package WHERE shipment_contract_ref_id IS NOT NULL",Integer.class));
    }
    @Test void targetSchemaIsLoadedOnceForAReusedExecutionAcrossChunks() {
        var store=org.mockito.Mockito.spy(new GenericSyncJdbcStore(jdbc.getDataSource()));
        engine=new GenericSyncEngine(catalog,store,event->{});
        var d=definition(1);var execution=engine.bind(d);
        tx.executeWithoutResult(status->execution.apply(batch(row(d,"P1",Map.of("packlist_id","P1")))));
        tx.executeWithoutResult(status->execution.apply(batch(row(d,"P2",Map.of("packlist_id","P2")))));
        org.mockito.Mockito.verify(store,org.mockito.Mockito.times(2)).columns(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(store).columns(org.mockito.ArgumentMatchers.argThat(t->t.table().equals("com_shipment_package")));
        org.mockito.Mockito.verify(store).columns(org.mockito.ArgumentMatchers.argThat(t->t.table().equals("com_shipment_contract_reference")));
        assertEquals(2,count("com_shipment_package"));
    }
    @Test void savedPreRenameLookupUsesCurrentProductColumnWithoutMutatingTask() {
        jdbc.update("INSERT INTO com_sales_order_line(id,tenant_id,order_no,line_no,product_code) VALUES(101,1,'O1','1','ITEM-1')");
        var d=definition(2);var source=d.sources().getFirst();var steps=new ArrayList<>(source.targets());
        var event=steps.get(2);var lookup=event.lookups().getFirst();var legacy=new LinkedHashMap<>(lookup.match());
        legacy.put("item_code",legacy.remove("product_code"));
        steps.set(2,new GenericTargetStep(event.name(),event.table(),event.keys(),event.mode(),event.nullPolicy(),
                event.updateColumns(),event.whenField(),event.newerBy(),event.tieBreaker(),event.mappings(),
                List.of(new GenericTargetStep.Lookup(lookup.table(),legacy,lookup.outputs(),lookup.onMissing()))));
        d=d.toBuilder().sources(List.of(source.toBuilder().targets(steps).build())).build();
        var fields=new HashMap<>(barcode("LEGACY-SN"));fields.put("orderNumber","O1");fields.put("lineNum","1");
        assertEquals("CREATED",apply(d,row(d,"1",fields)).getFirst().action());
        assertEquals(101L,jdbc.queryForObject("SELECT order_line_id FROM ast_device_shipment",Long.class));
        assertTrue(d.sources().getFirst().targets().get(2).lookups().getFirst().match().containsKey("item_code"));
        assertFalse(catalog.required("com_sales_order_line").columns().contains("item_code"));
    }
    @Test void repeatedCompositeLookupKeysDoNotManufactureAmbiguity() {
        jdbc.update("INSERT INTO com_sales_order_line(id,tenant_id,order_no,line_no,product_code) VALUES(101,1,'O1','1','ITEM-1'),(102,1,'O2','2','ITEM-1')");
        var d=definition(2);List<DataSyncAdapter.Row> rows=new ArrayList<>();
        for(int i=0;i<3;i++) {
            var fields=new HashMap<>(barcode("SN-"+i));
            fields.put("orderNumber",i==1?"O2":"O1");fields.put("lineNum",i==1?"2":"1");
            rows.add(row(d,Integer.toString(i),fields));
        }
        var changes=apply(d,rows.toArray(DataSyncAdapter.Row[]::new));
        assertTrue(changes.stream().allMatch(change->change.message()==null));
        assertEquals(List.of(101L,102L,101L),jdbc.queryForList("SELECT order_line_id FROM ast_device_shipment ORDER BY source_key",Long.class));
    }


    @Test void earlierSourceTargetsAreStillBatchPrefetchedForAPackageOnlyChunk() {
        var store=org.mockito.Mockito.spy(new GenericSyncJdbcStore(jdbc.getDataSource()));
        engine=new GenericSyncEngine(catalog,store,event->{});
        var packages=definition(1);
        var complete=packages.toBuilder().sources(List.of(definition(0).sources().getFirst(),packages.sources().getFirst())).build();
        List<DataSyncAdapter.Row> rows=new ArrayList<>();
        for(int i=1;i<=6;i++) {
            jdbc.update("INSERT INTO com_shipment_contract_reference(id,tenant_id,source_system,source_record_key) VALUES(?,1,'DPPMS',?)",100+i,Integer.toString(i));
            rows.add(row(packages,"P"+i,Map.of("packlist_id","P"+i,"con_id",Integer.toString(i))));
        }
        assertTrue(apply(complete,rows.toArray(DataSyncAdapter.Row[]::new)).stream().allMatch(c->"CREATED".equals(c.action())));
        assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM com_shipment_package WHERE shipment_contract_ref_id IS NOT NULL",Integer.class));
        org.mockito.Mockito.verify(store,org.mockito.Mockito.never()).find(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(store).findByKeys(org.mockito.ArgumentMatchers.argThat(t->t.table().equals("com_shipment_contract_reference")),
                org.mockito.ArgumentMatchers.argThat(keys->keys.size()==6),org.mockito.ArgumentMatchers.eq(true));
    }
    @Test void numericSourceReferenceMatchesOnlyItsExactTextIdentity() {
        jdbc.update("INSERT INTO com_shipment_contract_reference(id,tenant_id,source_system,source_record_key) VALUES(101,1,'DPPMS','1'),(102,1,'DPPMS','01'),(103,1,'DPPMS','1x')");
        var store=org.mockito.Mockito.spy(new GenericSyncJdbcStore(jdbc.getDataSource()));
        engine=new GenericSyncEngine(catalog,store,event->{});
        var d=definition(1);var r=row(d,"P1",Map.of("packlist_id","P1","con_id",1));
        assertEquals("CREATED",apply(d,r).getFirst().action());
        assertEquals(101L,jdbc.queryForObject("SELECT shipment_contract_ref_id FROM com_shipment_package",Long.class));
        assertEquals(1,r.fields().get("con_id"));
        org.mockito.Mockito.verify(store).findByKeys(org.mockito.ArgumentMatchers.argThat(t->t.table().equals("com_shipment_contract_reference")),
                org.mockito.ArgumentMatchers.argThat(keys->keys.size()==1 && "1".equals(keys.getFirst().get("source_record_key"))),org.mockito.ArgumentMatchers.eq(true));
    }
    @Test void sameChunkParentWriteRemainsVisibleToALaterPackageRow() {
        var contracts=definition(0);var packages=definition(1);
        var complete=packages.toBuilder().sources(List.of(contracts.sources().getFirst(),packages.sources().getFirst())).build();
        var changes=apply(complete,row(contracts,"77",Map.of("contractNum","C77")),
                row(packages,"P77",Map.of("packlist_id","P77","con_id",77)));
        assertEquals(List.of("CREATED","CREATED"),changes.stream().map(DataSyncAdapter.Change::action).toList());
        assertEquals(changes.getFirst().targetId(),jdbc.queryForObject("SELECT shipment_contract_ref_id FROM com_shipment_package",Long.class));
    }


    @Test void prefetchSkipsNonKeyTimestampConversion() {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        Object timestamp=new Object() {
            @Override public String toString() { calls.incrementAndGet();return "2026-09-20T12:00:00"; }
        };
        var d=definition(2);
        var values=new HashMap<String,Object>(barcode("PREFETCH-SN"));values.put("shipment_time",timestamp);
        var source=row(d,"prefetch-1",values);
        Object schemas=ReflectionTestUtils.invokeMethod(engine.bind(d),"schemas");
        ReflectionTestUtils.invokeMethod(engine,"prefillOverlay",d,batch(source),new HashMap<>(),false,schemas);
        assertEquals(0,calls.get(),"Prefetch must not convert non-key timestamps; planner owns full validation");
    }

    @Test void prefetchIncludesMappedLookupInputsButNotSourceReferences() {
        var step=definition(1).sources().getFirst().targets().getFirst();
        step.lookups().add(new GenericTargetStep.Lookup("com_shipment_contract_reference",
            Map.of("source_record_key","non_key_lookup_input","source_system","@source_system"),
            Map.of("shipment_contract_ref_id","id"),"RETAIN"));
        Set<String> columns=ReflectionTestUtils.invokeMethod(GenericSyncEngine.class,"prefetchColumns",step);
        assertTrue(columns.containsAll(step.keys()));
        assertTrue(columns.contains("non_key_lookup_input"));
        assertFalse(columns.contains("@source_system"));
        assertFalse(columns.contains("shipment_contract_ref_id"));
    }

    @Test void skippedPrefetchFieldStillFailsRowValidation() {
        var d=definition(2);var values=new HashMap<String,Object>(barcode("INVALID-PREFETCH-TIME"));
        values.put("shipment_time","not-a-time");
        var change=apply(d,row(d,"invalid-prefetch-1",values)).getFirst();
        assertEquals("ISSUE",change.action());
        assertEquals(0,count("ast_device"));assertEquals(0,count("ast_device_shipment"));
    }

    @Test void genericOrdersValidateAndRoundTripSameVersionColumns() {
        var orders=DppmsOrderSyncTemplate.create(1L);
        var validator=new SyncDefinitionValidator(List.of());ReflectionTestUtils.setField(validator,"genericEngine",engine);
        assertDoesNotThrow(()->validator.validate(orders));
        var json=cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(orders);
        var restored=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(json,SyncDefinition.class);
        for(int index=0;index<2;index++) {
            var before=orders.sources().get(index).targets().getFirst();var after=restored.sources().get(index).targets().getFirst();
            assertFalse(before.sameVersionColumns().isEmpty());assertEquals(before.sameVersionColumns(),after.sameVersionColumns());
            assertEquals(before.newerBy(),after.newerBy());assertEquals(before.keys(),after.keys());
        }
        assertDoesNotThrow(()->validator.validate(restored));
        // Existing tasks using the original constructor remain explicitly opted out.
        var legacy=definition(1).sources().getFirst().targets().getFirst();
        assertNull(legacy.sameVersionColumns());assertDoesNotThrow(()->engine.validate(definition(1)));
    }

    @Test void genericOrderPreviewResolvesSameBatchParentWithoutWritingRealTargetIds() {
        var orders=DppmsOrderSyncTemplate.create(1L);var time=java.time.LocalDateTime.of(2026,10,1,10,0);
        var header=genericOrderRow(false,11,time,5);var line=genericOrderRow(true,21,time,5);
        var preview=engine.bind(orders).preview(batch(header,line));
        assertEquals(List.of("CREATED","CREATED"),preview.stream().map(DataSyncAdapter.Change::action).toList());
        assertTrue(preview.stream().allMatch(change->change.targetId()==null));
        assertTrue(preview.stream().allMatch(change->Boolean.TRUE.equals(change.after().get("_previewVirtualIds"))));
        assertTrue(((Number)preview.getLast().after().get("line.order_id")).longValue()<0);
        assertEquals(0,count("com_sales_order"));assertEquals(0,count("com_sales_order_line"));
        var applied=apply(orders,header,line);
        assertEquals(List.of("CREATED","CREATED"),applied.stream().map(DataSyncAdapter.Change::action).toList());
        assertTrue(applied.stream().allMatch(change->change.targetId()!=null && change.targetId()>0));
        assertEquals(applied.getFirst().targetId(),jdbc.queryForObject("SELECT order_id FROM com_sales_order_line",Long.class));
        assertEquals(1,count("com_sales_order"));assertEquals(1,count("com_sales_order_line"));
    }

    @Test void genericOrderBusinessIdentitySurvivesNewRawIdsAndNewerQuantity() {
        var orders=DppmsOrderSyncTemplate.create(1L);var time=java.time.LocalDateTime.of(2026,10,1,10,0);
        var first=apply(orders,genericOrderRow(false,11,time,5),genericOrderRow(true,21,time,5));
        var reloaded=apply(orders,genericOrderRow(false,111,time.plusDays(1),8),genericOrderRow(true,221,time.plusDays(1),8));
        assertEquals(List.of("UPDATED","UPDATED"),reloaded.stream().map(DataSyncAdapter.Change::action).toList());
        assertEquals(first.stream().map(DataSyncAdapter.Change::targetId).toList(),reloaded.stream().map(DataSyncAdapter.Change::targetId).toList());
        assertEquals(1,count("com_sales_order"));assertEquals(1,count("com_sales_order_line"));
        assertEquals(new java.math.BigDecimal("8"),jdbc.queryForObject("SELECT order_qty FROM com_sales_order_line",java.math.BigDecimal.class));
        assertEquals(first.getFirst().targetId(),jdbc.queryForObject("SELECT order_id FROM com_sales_order_line",Long.class));
        assertEquals("ORDER-BUSINESS-KEY",jdbc.queryForObject("SELECT source_record_key FROM com_sales_order",String.class));
        assertEquals("LINE-BUSINESS-KEY",jdbc.queryForObject("SELECT source_record_key FROM com_sales_order_line",String.class));
    }

    @Test void genericOrderSameVersionQuantityConflictCannotWrite() {
        var orders=DppmsOrderSyncTemplate.create(1L);var time=java.time.LocalDateTime.of(2026,10,1,10,0);
        apply(orders,genericOrderRow(false,11,time,5),genericOrderRow(true,21,time,5));
        var before=jdbc.queryForMap("SELECT * FROM com_sales_order_line");
        var conflict=apply(orders,genericOrderRow(true,221,time,9)).getFirst();
        assertEquals("CONFLICT",conflict.action());assertTrue(conflict.message().contains("SOURCE_VERSION_PAYLOAD_CONFLICT"));
        assertEquals(before,jdbc.queryForMap("SELECT * FROM com_sales_order_line"));
        assertEquals(1,count("com_sales_order"));assertEquals(1,count("com_sales_order_line"));
    }

    @Test void genericOrderJsonTextPreservesObjectsAndComparesSameVersionContent() {
        var orders=DppmsOrderSyncTemplate.create(1L);var time=java.time.LocalDateTime.of(2026,10,1,10,0);
        var line=genericOrderRow(true,21,time,5);var fields=new LinkedHashMap<>(line.fields());
        fields.put("customInfo","{\"inventTransId\":\"10082799\",\"marker\":\"same\"}");
        var source=new DataSyncAdapter.Row(line.object(),line.sourceKey(),fields,null);
        var created=apply(orders,genericOrderRow(false,11,time,5),source);
        assertEquals("CREATED",created.getLast().action());
        var stored=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(
                jdbc.queryForObject("SELECT custom_info FROM com_sales_order_line",String.class));
        assertTrue(stored.isObject());assertEquals("10082799",stored.get("inventTransId").asText());

        jdbc.update("UPDATE com_sales_order_line SET custom_info=?",
                "{ \"marker\" : \"same\", \"inventTransId\" : \"10082799\" }");
        var before=jdbc.queryForMap("SELECT * FROM com_sales_order_line");
        assertEquals("UNCHANGED",apply(orders,source).getFirst().action());
        assertEquals(before,jdbc.queryForMap("SELECT * FROM com_sales_order_line"));

        var changed=new LinkedHashMap<>(fields);
        changed.put("customInfo","{\"inventTransId\":\"changed\",\"marker\":\"same\"}");
        var conflict=apply(orders,new DataSyncAdapter.Row(line.object(),line.sourceKey(),changed,null)).getFirst();
        assertEquals("CONFLICT",conflict.action());assertTrue(conflict.message().contains("SOURCE_VERSION_PAYLOAD_CONFLICT"));
        assertEquals(before,jdbc.queryForMap("SELECT * FROM com_sales_order_line"));

        var invalid=new LinkedHashMap<>(fields);invalid.put("customInfo","{");
        assertEquals("ISSUE",apply(orders,new DataSyncAdapter.Row(line.object(),line.sourceKey(),invalid,null)).getFirst().action());
        assertEquals(before,jdbc.queryForMap("SELECT * FROM com_sales_order_line"));
        assertEquals(1,count("com_sales_order_line"));
    }

    @Test void genericOrderOlderVersionCannotOverwriteNewerQuantity() {
        var orders=DppmsOrderSyncTemplate.create(1L);var time=java.time.LocalDateTime.of(2026,10,2,10,0);
        apply(orders,genericOrderRow(false,11,time,8),genericOrderRow(true,21,time,8));
        var before=jdbc.queryForMap("SELECT * FROM com_sales_order_line");
        var old=apply(orders,genericOrderRow(true,221,time.minusDays(1),3)).getFirst();
        assertEquals("UNCHANGED",old.action());assertEquals(before,jdbc.queryForMap("SELECT * FROM com_sales_order_line"));
        assertEquals(1,count("com_sales_order_line"));
    }

    @Test void genericOrderSourceIssueCannotCreateTargets() {
        var orders=DppmsOrderSyncTemplate.create(1L);var time=java.time.LocalDateTime.of(2026,10,1,10,0);
        var source=genericOrderRow(false,11,time,5);var fields=new LinkedHashMap<>(source.fields());
        fields.put("migrationIssue","DUPLICATE_BUSINESS_KEY_CONFLICT");
        var invalid=new DataSyncAdapter.Row(source.object(),source.sourceKey(),fields,null);
        var preview=engine.bind(orders).preview(batch(invalid)).getFirst();
        assertEquals("ISSUE",preview.action());assertEquals("DUPLICATE_BUSINESS_KEY_CONFLICT",preview.message());
        assertEquals("ISSUE",apply(orders,invalid).getFirst().action());
        assertEquals(0,count("com_sales_order"));assertEquals(0,count("com_sales_order_line"));
    }

    @Test void genericOrderParentPrecheckConflictCannotCreateChildRows() {
        var orders=DppmsOrderSyncTemplate.create(1L);var time=java.time.LocalDateTime.of(2026,10,1,10,0);
        var header=genericOrderRow(false,11,time,5);var headerFields=new LinkedHashMap<>(header.fields());
        headerFields.put("migrationIssue","DUPLICATE_BUSINESS_KEY_CONFLICT");
        var line=genericOrderRow(true,21,time,5);var lineFields=new LinkedHashMap<>(line.fields());
        lineFields.put("migrationIssue","PARENT_SOURCE_BUSINESS_KEY_CONFLICT");
        var invalidHeader=new DataSyncAdapter.Row(header.object(),header.sourceKey(),headerFields,null);
        var invalidLine=new DataSyncAdapter.Row(line.object(),line.sourceKey(),lineFields,null);
        var preview=engine.bind(orders).preview(batch(invalidHeader,invalidLine));
        assertEquals(List.of("ISSUE","ISSUE"),preview.stream().map(DataSyncAdapter.Change::action).toList());
        assertEquals("PARENT_SOURCE_BUSINESS_KEY_CONFLICT",preview.getLast().message());
        assertEquals(List.of("ISSUE","ISSUE"),apply(orders,invalidHeader,invalidLine).stream().map(DataSyncAdapter.Change::action).toList());
        assertEquals(0,count("com_sales_order"));assertEquals(0,count("com_sales_order_line"));
        // An independently valid line is now carried even while its exact local parent is unavailable.
        var missingParent=apply(orders,line).getFirst();assertEquals("CREATED",missingParent.action());
        assertTrue(missingParent.message().contains("关联未命中"));assertEquals(1,count("com_sales_order_line"));
        assertNull(jdbc.queryForObject("SELECT order_id FROM com_sales_order_line",Long.class));
    }

    private DataSyncAdapter.Row genericOrderRow(boolean line,long rawId,java.time.LocalDateTime time,int quantity) {
        var d=DppmsOrderSyncTemplate.create(1L);var source=d.sources().get(line?1:0);Map<String,Object> fields=new LinkedHashMap<>();
        for(var step:source.targets()) {
            for(var mapping:step.mappings())if(mapping.source()!=null && !mapping.source().startsWith("$"))fields.put(mapping.source(),null);
            for(var lookup:step.lookups())lookup.match().values().stream().filter(v->v.startsWith("@")).forEach(v->fields.put(v.substring(1),null));
        }
        fields.put("migrationIssue",null);fields.put("source","D365");fields.put("company_code","001");fields.put("orderNumber","ORDER-1");
        fields.put(line?"lineType":"orderType","0");fields.put("migrationVersion",time);fields.put("sourceVersion",time.toString());
        fields.put("syncReceivedAt",time);fields.put("customInfo","{}");fields.put("id",rawId);
        fields.put("originalRows",List.of(Map.of("id",rawId,"orderQuantity",quantity)));fields.put("originalRowCount",1);
        var key=line?"LINE-BUSINESS-KEY":"ORDER-BUSINESS-KEY";
        fields.put("migrationSourceKey",key);fields.put("sourceRecordKey",key);
        if(line) {
            fields.put("lineNum","1");fields.put("itemCode","PRODUCT-1");fields.put("itemDesc","Product");
            fields.put("orderQuantity",quantity);fields.put("openQuantity",2);fields.put("deliveredQuantity",quantity-2);fields.put("warrantyMonth",12);
        }
        return new DataSyncAdapter.Row(source.object(),key,fields,null);
    }

    @Test void newerShipmentChangesOldSourcesDerivedCurrentProjectionOnce() {
        var d=definition(2);
        var oldSource=row(d,"1",barcode("RECONCILE-SN"));
        var initial=apply(d,oldSource).getFirst();
        assertEquals("CREATED",initial.action());
        var boundOld=new DataSyncAdapter.Row(oldSource.object(),oldSource.sourceKey(),oldSource.fields(),initial.targetId());
        var values=new HashMap<String,Object>(barcode("RECONCILE-SN"));
        values.put("shipment_time","2026-10-02T12:00:00");values.put("pack_id","PACK-NEW");
        var deltaSource=row(d,"4000001",values);
        var changed=apply(d,boundOld,deltaSource);
        assertEquals("UNCHANGED",changed.getFirst().action());assertEquals("CREATED",changed.getLast().action());
        var boundDelta=new DataSyncAdapter.Row(deltaSource.object(),deltaSource.sourceKey(),deltaSource.fields(),changed.getLast().targetId());
        var reconciled=apply(d,boundOld,boundDelta);
        assertEquals(List.of("UNCHANGED","UNCHANGED"),reconciled.stream().map(DataSyncAdapter.Change::action).toList());
        assertEquals(changed.getFirst().targetId(),reconciled.getFirst().targetId());
        assertNotEquals(changed.getFirst().after().get("deviceShipment.shipment_record_id"),
            reconciled.getFirst().after().get("deviceShipment.shipment_record_id"));
        assertEquals(changed.getLast().targetId(),reconciled.getFirst().after().get("deviceShipment.shipment_record_id"));
        var stable=apply(d,boundOld,boundDelta);
        assertEquals(reconciled.getFirst().after(),stable.getFirst().after());
        assertEquals(reconciled.getLast().after(),stable.getLast().after());
        assertEquals(1,count("ast_device"));assertEquals(2,count("ast_device_shipment"));
    }
}
