package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.SyncCounts;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual source/target XML on connection-local tables; no formal cache writes. */
class ProjectCachePreviewMySqlTest extends RemainingTaskMySqlSupport {
    P21ProjectCacheSyncTask.TargetMapper mapper;
    P21ProjectCacheSyncTask task;

    @BeforeEach void cacheFixture() throws Exception {
        jdbc.execute("CREATE TEMPORARY TABLE pm_project_product_lease_line_from_crm(projectCode VARCHAR(100),orderExecNumber VARCHAR(100),productFirstName VARCHAR(100),productName VARCHAR(100),productSubCode VARCHAR(100),productSubModel VARCHAR(100),productSubName VARCHAR(100),num VARCHAR(100),memo VARCHAR(100),leaseDuration VARCHAR(100),dataSource VARCHAR(25))");
        jdbc.execute("CREATE TEMPORARY TABLE pm_project_product_config_level_info_from_crm(projectCode VARCHAR(100),orderExecNumber VARCHAR(100),itemGroup VARCHAR(100),itemCode VARCHAR(100),parentCode VARCHAR(100),quantity VARCHAR(100),bomPaths VARCHAR(100),itemModel VARCHAR(100),itemDesc VARCHAR(100),`level` VARCHAR(100),dataSource VARCHAR(25))");
        createTarget("proj_project_lease_product",P21ProjectCacheSyncTask.LeaseCacheRow.class);
        createTarget("proj_project_product_configuration",P21ProjectCacheSyncTask.ConfigurationCacheRow.class);
        jdbc.update("INSERT INTO pm_project_product_lease_line_from_crm(projectCode,productSubCode,num,leaseDuration) VALUES('P-1','ITEM','2.25',NULL),('P-1','ITEM','2.25',NULL)");
        jdbc.update("INSERT INTO pm_project_product_config_level_info_from_crm(projectCode,itemGroup,itemCode,quantity,`level`) VALUES('P-1','1.0','CHILD','3.125','2.0')");
        jdbc.update("INSERT INTO proj_project_lease_product(id,tenant_id,project_id,project_code) VALUES(1,1,101,'PROTECTED-LEASE'),(2,2,202,'OTHER-TENANT')");
        jdbc.update("INSERT INTO proj_project_product_configuration(id,tenant_id,project_id,project_code) VALUES(3,1,101,'PROTECTED-CONFIG'),(4,2,202,'OTHER-TENANT')");
        var configuration=session.getConfiguration();
        for(var resource:new String[]{"base-sync-source/P21SourceMapper.xml","mapper/basesync/P21TargetMapper.xml"})
            try(var input=new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
            }
        mapper=mock(P21ProjectCacheSyncTask.TargetMapper.class,org.mockito.AdditionalAnswers.delegatesTo(session.getMapper(P21ProjectCacheSyncTask.TargetMapper.class)));
        // INFORMATION_SCHEMA does not expose connection-local tables. The fixture itself has
        // the old NOT NULL project_id columns; model that structure result explicitly.
        doReturn(0).when(mapper).nullableColumns();
        task=new P21ProjectCacheSyncTask(mapper);
    }

    void createTarget(String name,Class<?> rowType) {
        var columns=new java.util.ArrayList<String>();
        for(var field:rowType.getDeclaredFields()) {
            String column=field.getName().replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
            Class<?> type=field.getType();
            String sqlType=type==Long.class?"BIGINT":type==Integer.class?"INT":type==Boolean.class?"BOOLEAN":type==BigDecimal.class?"DECIMAL(24,6)":type==LocalDateTime.class?"DATETIME(3)":"VARCHAR(100)";
            columns.add("`"+column+"` "+sqlType+(column.equals("id")?" PRIMARY KEY":""));
        }
        jdbc.execute("CREATE TEMPORARY TABLE `"+name+"`("+String.join(",",columns)+",project_id BIGINT NOT NULL)");
    }

    void protectedRowsRemain() {
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_lease_product",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_product_configuration",Integer.class));
        assertEquals("PROTECTED-LEASE",jdbc.queryForObject("SELECT project_code FROM proj_project_lease_product WHERE id=1",String.class));
        assertEquals("PROTECTED-CONFIG",jdbc.queryForObject("SELECT project_code FROM proj_project_product_configuration WHERE id=3",String.class));
        assertTrue(saved.isEmpty());
        verify(mapper,never()).deleteLease(any());verify(mapper,never()).deleteConfiguration(any());
        verify(mapper,never()).insertLease(any());verify(mapper,never()).insertConfiguration(any());
    }

    @Test void previewReadsBothSourcesAndKeepsDuplicatesWithoutWritingOldSchema() {
        var context=context(true);
        var counts=transaction.execute(status->task.execute(context));
        assertEquals(new SyncCounts(3,3,0,0,2),counts);
        assertTrue(context.runState().warnings().contains("P21_EXECUTION_SCHEMA_REQUIRED:project_id_nullable"));
        protectedRowsRemain();
    }

    @Test void executionStillFailsBeforeAnyCacheDeleteOrInsertWhenSchemaIsNotReady() {
        assertThrows(IllegalStateException.class,()->run(task,false));
        protectedRowsRemain();
    }

    @Test void schemaWarningDoesNotHideInvalidSourcePrecisionDuringPreview() {
        jdbc.update("UPDATE pm_project_product_lease_line_from_crm SET num='1.0000001'");
        assertThrows(IllegalArgumentException.class,()->run(task,true));
        protectedRowsRemain();
    }
}
