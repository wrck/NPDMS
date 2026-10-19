package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.*;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsConsolidatedTemplateTest {
    @Test void organizationConfigurationKeepsGeneratedIdsAndExistingBindings() throws Exception {
        java.nio.file.Path root=java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(root!=null && !java.nio.file.Files.isDirectory(root.resolve("docs/generated/2026-10-13-dppms-migration-mapping"))) root=root.getParent();
        assertNotNull(root);
        String payload=java.nio.file.Files.readString(root.resolve("docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/runtime-configurations/P01.json"));
        var d=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(payload).path("definition").toString(),SyncDefinition.class);
        for(String object:List.of("COMPANY","DEPARTMENT")) {
            var source=d.sources().stream().filter(s->s.object().equals(object)).findFirst().orElseThrow();
            Map<String,Object> fields=new LinkedHashMap<>();
            source.mappings().forEach(m->fields.put(m.source(),null));
            fields.put(source.sourceKey(),7);
            var snapshot=new MysqlSyncReader.Snapshot(java.time.LocalDateTime.of(2026,10,15,0,0),
                    List.of(new MysqlSyncReader.SourceRows(object,source.sourceObject(),List.of(fields))),0);
            var mapper=new SyncFieldMapper();
            var fresh=mapper.transform(d,snapshot,List.of()).getFirst();
            assertFalse(fresh.fields().containsKey("_sourcePrimaryKey"));assertNull(fresh.targetId());
            var bound=mapper.transform(d,snapshot,List.of(new DataSyncAdapter.Binding(object,"7",9007L,Map.of()))).getFirst();
            assertFalse(bound.fields().containsKey("_sourcePrimaryKey"));assertEquals(9007L,bound.targetId());
        }
    }
    @Test void organizationAndDictionaryShareTheirGroupedSqlAndCompanyAliasesUseOneAdditionalRead() {
        var tasks=mock(SyncTaskService.class);var runs=mock(SyncRunMapper.class);var bindings=mock(SyncBindingMapper.class);
        var departments=mock(SystemDepartmentMigrationApi.class);var users=mock(SystemUserMigrationApi.class);
        List<DataSyncAdapter> adapters=List.of(new EhrOrganizationAdapter(mock(ManagedOrganizationApi.class)),
                new DppmsDepartmentSyncAdapter(departments,tasks),new DppmsEhrDepartmentCompletionAdapter(departments,tasks,runs,bindings),
                new DppmsCompanyReferenceSyncAdapter(mock(ManagedOrganizationApi.class),tasks),
                new DppmsDictionaryTypeSyncAdapter(mock(SystemDictionaryTypeMigrationApi.class),users,tasks,runs),
                new DppmsDictionaryDataSyncAdapter(mock(SystemDictionaryDataMigrationApi.class),users,tasks,runs));
        var validator=new SyncDefinitionValidator(adapters);
        for(String key:List.of("P01","P08")) {
            var d=DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request(key,18L));
            assertDoesNotThrow(()->validator.validate(d));
            assertEquals("P01".equals(key)?2:1,d.sources().stream().filter(s->s.sql()!=null).count());
            assertTrue(d.sources().getFirst().sql().contains("UNION ALL"));
            assertFalse(d.clearBeforeLoad());assertFalse(d.resetMappingsBeforeLoad());
        }
    }
    @Test void deliveryInitialConfigurationUsesLocalOrdersAndRawRemoteFactsWithoutChangingOldResource() throws Exception {
        var d=DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P15",18L));
        assertEquals(5,d.sources().size());assertEquals("LOCAL_OWNER",d.sources().getFirst().readMode());
        assertEquals("com_sales_order",d.sources().getFirst().sourceObject());assertNull(d.sources().getFirst().sql());
        var engine=new cn.iocoder.yudao.module.pms.integration.sync.generic.GenericSyncEngine(
                new cn.iocoder.yudao.module.pms.integration.sync.generic.GenericTargetCatalog(),mock(GenericSyncJdbcStore.class),mock(org.springframework.context.ApplicationEventPublisher.class));
        var validator=new SyncDefinitionValidator(List.of());org.springframework.test.util.ReflectionTestUtils.setField(validator,"genericEngine",engine);
        assertDoesNotThrow(()->validator.validate(d));
        assertEquals(1,d.sources().stream().flatMap(source->source.targets().stream()).filter(target->target.table().equals("com_contract")).count());
        for(var source:d.sources().subList(1,5)) {
            assertDoesNotThrow(()->MysqlSyncReader.compile(source));assertFalse(source.sql().contains("JOIN"));
        }
        assertTrue(d.sources().get(2).targets().getFirst().mappings().stream().anyMatch(mapping->"con_id".equals(mapping.source()) && "shipment_contract_source_key".equals(mapping.target())));
        try(var input=new org.springframework.core.io.ClassPathResource("migration/dppms/tasks/P15.json").getInputStream()) {
            var original=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),SyncDefinition.class);
            assertEquals("pm_order_data_from_erp",original.sources().getFirst().sourceObject());
        }
    }
}
