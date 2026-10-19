package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.sync.generic.GenericSyncTemplates;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CrmDirectCustomerSourceTest {
    @Test void fullViewMarksTrimmedCodeDuplicatesBeforeTheyCanFallIntoDifferentChunks() {
        var definition=CrmDirectCustomerTemplate.create(9L);var source=definition.sources().getFirst();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:crm-source-"+UUID.randomUUID()+";MODE=MSSQLServer;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE DPtech_v_account(AccountId VARCHAR(64),AccountNumber VARCHAR(64),erpCustomerCode VARCHAR(50),Name VARCHAR(200),Telephone1 VARCHAR(64),EMailAddress1 VARCHAR(128),Address1_Composite VARCHAR(500),new_mkt_unit_id VARCHAR(64),new_mkt_unit_idName VARCHAR(128),new_industry_level1_id VARCHAR(64),new_industry_level1_idName VARCHAR(128),new_industry_level2_id VARCHAR(64),new_industry_level2_idName VARCHAR(128),new_industry_level3_id VARCHAR(64),new_industry_level3_idName VARCHAR(128),new_industry_level4_id VARCHAR(64),new_industry_level4_idName VARCHAR(128),StateCode INT,ModifiedOn TIMESTAMP)");
        jdbc.update("INSERT INTO DPtech_v_account(AccountId,AccountNumber,Name,StateCode,ModifiedOn) VALUES('A',' CRM-1','客户一',0,CURRENT_TIMESTAMP),('B','CRM-1','客户二',0,CURRENT_TIMESTAMP),('C','CRM-2','客户三',0,CURRENT_TIMESTAMP)");
        var rows=jdbc.queryForList(source.sql());
        assertEquals(2,rows.stream().filter(row->"DUPLICATE_CUSTOMER_CODE".equals(row.get("migrationIssue"))).count());
        assertEquals("AccountId",source.sourceKey());assertEquals(Set.of("A","B","C"),new HashSet<>(rows.stream().map(row->row.get("AccountId")).toList()));
        var original=new GenericSyncTemplates().list(9L).stream().filter(t->"crm-customer".equals(t.key())).findFirst().orElseThrow().definition();
        assertEquals("CRM_CUSTOMER",original.adapter());assertTrue(original.sources().getFirst().sql().contains("PARTITION BY AccountNumber"));
    }
}
