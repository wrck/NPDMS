package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.AbstractSyncTask.SourceQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class P22SnapshotCollationMySqlTest extends RemainingTaskMySqlSupport {
    @BeforeEach void snapshots() {
        jdbc.execute("CREATE TEMPORARY TABLE proj_project(id BIGINT,tenant_id BIGINT,version BIGINT,project_code VARCHAR(64),project_name VARCHAR(100),company_code VARCHAR(64),company_name VARCHAR(100),customer_code VARCHAR(64),customer_name VARCHAR(100),manager_employee_no VARCHAR(64),manager_name VARCHAR(100),department_id BIGINT,department_code VARCHAR(64) COLLATE utf8mb4_0900_ai_ci,deleted BOOLEAN,PRIMARY KEY(tenant_id,id))");
        jdbc.execute("CREATE TEMPORARY TABLE com_delivery_scope_project_version(id BIGINT,tenant_id BIGINT,project_id BIGINT,scope_version BIGINT,deleted BOOLEAN)");
        jdbc.update("INSERT INTO system_dept(id,tenant_id,code,name,status,version) VALUES(7,1,'OFFICE','office',0,3)");
        jdbc.update("INSERT INTO proj_project(id,tenant_id,version,project_code,department_id,department_code,deleted) VALUES(20,1,5,'P20',7,'OFFICE',FALSE),(21,2,8,'OTHER',7,'OFFICE',FALSE)");
        jdbc.update("INSERT INTO com_delivery_scope_project_version VALUES(1,1,20,9,FALSE)");
    }
    @Test void mixedSchemaProjectMetadataKeepsVersionWithoutLoadingAnOfficeId() {
        var mapper=session.getMapper(P22ProductScopeSyncTask.TargetMapper.class);
        var rows=mapper.selectSnapshots(new P22ProductScopeSyncTask.ProjectsQuery(1,List.of(20L,21L),true));
        assertEquals(1,rows.size());assertNull(rows.getFirst().getDepartmentId());
        assertNull(rows.getFirst().getDepartmentVersion());assertEquals(9L,rows.getFirst().getScopeVersion());
        assertTrue(mapper.selectSnapshots(new P22ProductScopeSyncTask.ProjectsQuery(1,List.of(),true)).isEmpty());
    }
    @Test void differentCaseDoesNotGuessAnOfficeMatch() {
        jdbc.update("UPDATE proj_project SET department_code='office' WHERE tenant_id=1 AND id=20");
        var row=session.getMapper(P22ProductScopeSyncTask.TargetMapper.class).selectSnapshots(new P22ProductScopeSyncTask.ProjectsQuery(1,List.of(20L),true)).getFirst();
        assertNull(row.getDepartmentId());assertNull(row.getDepartmentVersion());
    }
    @Test void sourceQueryCarriesTheOriginalProjectsDepartmentEvenWhenTheSelectedMasterDiffers() throws Exception {
        jdbc.update("INSERT INTO pm_project(projectId,projectType,projectCode,column001,compId,disabled) VALUES(1,'10','P1','RAW',1,0),(2,'10','P1','CURRENT',1,0),(3,'10','P3',NULL,1,0),(4,'10','P3','CURRENT',1,0)");
        jdbc.execute("CREATE TEMPORARY TABLE pm_project_product_line(id BIGINT,projectId BIGINT,contractNo VARCHAR(64),itemCode VARCHAR(64),itemName VARCHAR(100),projectQuantity DECIMAL(18,6),orderQuantity DECIMAL(18,6),deliverQuantity DECIMAL(18,6),openQuantity DECIMAL(18,6),orderNumber VARCHAR(64),lineNum VARCHAR(64))");
        jdbc.update("INSERT INTO pm_project_product_line(id,projectId,itemCode,projectQuantity,orderNumber,lineNum) VALUES(10,1,'ITEM',1,'ORDER','1'),(11,3,'ITEM',1,'ORDER','2')");
        String resource="base-sync-source/P22SourceMapper.xml";
        String xml;try(var input=new ClassPathResource(resource).getInputStream()){xml=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
        // Only fixture table names change: native MySQL cannot reopen a TEMPORARY table in one statement.
        var matcher=java.util.regex.Pattern.compile("(?i)(FROM|JOIN)\\s+pm_project\\b").matcher(xml);
        var output=new StringBuffer();int index=0;
        while(matcher.find()) {
            String table="pm_project";
            if(++index>1){table="p22_source_project_"+index;jdbc.execute("CREATE TEMPORARY TABLE "+table+" LIKE pm_project");jdbc.update("INSERT INTO "+table+" SELECT * FROM pm_project");}
            matcher.appendReplacement(output,matcher.group(1)+" "+table);
        }
        matcher.appendTail(output);var config=session.getConfiguration();
        new XMLMapperBuilder(new java.io.StringReader(output.toString()),config,resource,config.getSqlFragments()).parse();
        var rows=new ArrayList<P22ProductScopeSyncTask.Source>();
        session.getMapper(P22ProductScopeSyncTask.SourceMapper.class).selectProductScope(
                new SourceQuery<>(100,1,true,new P22ProductScopeSyncTask.Parameters()),result->rows.add(result.getResultObject()));
        assertEquals(2,rows.size());
        assertEquals(1L,rows.getFirst().getOriginalProjectId());assertEquals(2L,rows.getFirst().getCanonicalProjectId());
        assertEquals("RAW",rows.getFirst().getOriginalDepartmentCode());
        assertEquals(4L,rows.getLast().getCanonicalProjectId());assertNull(rows.getLast().getOriginalDepartmentCode());
    }
}
