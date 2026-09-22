package cn.iocoder.yudao.module.pms.commerce.dal;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.DeviceContractScopeQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeviceContractScopeSqlTest {
    @Test void rejectsAmbiguousAndForeignTenantContractsWhileKeepingCompanyAndProjectScope() throws Exception {
        var config = new Configuration();
        try (var xml=getClass().getResourceAsStream("/mapper/contract/ContractMapper.xml")) {
            new XMLMapperBuilder(xml,config,"contract",config.getSqlFragments()).parse();
        }
        try (var db=DriverManager.getConnection("jdbc:h2:mem:contract_visibility;MODE=MySQL")) {
            try (var statement=db.createStatement()) {
                statement.execute("CREATE TABLE com_contract(id BIGINT,tenant_id BIGINT,contract_no VARCHAR(40),company_code VARCHAR(40),deleted INT)");
                statement.execute("CREATE TABLE com_project_contract_relation(tenant_id BIGINT,contract_id BIGINT,project_id BIGINT,deleted INT,status VARCHAR(20),effective_to TIMESTAMP)");
                statement.execute("INSERT INTO com_contract VALUES(1,1,'AMBIGUOUS','001',0),(2,1,'AMBIGUOUS','002',0),(3,1,'COMPANY','001',0),(4,1,'HIDDEN','002',0),(5,1,'PROJECT','003',0),(6,2,'COMPANY','002',0)");
                statement.execute("INSERT INTO com_project_contract_relation VALUES(1,5,10,0,'ACTIVE',NULL)");
            }
            try(var statement=db.createStatement()) {
                statement.execute("ALTER TABLE com_contract ADD company_id BIGINT");
                statement.execute("ALTER TABLE com_contract ADD company_name VARCHAR(40)");
                statement.execute("ALTER TABLE com_contract ADD department_code VARCHAR(40)");
                statement.execute("ALTER TABLE com_contract ADD department_name VARCHAR(40)");
                statement.execute("UPDATE com_contract SET company_id=CASE company_code WHEN '001' THEN 1 WHEN '002' THEN 2 ELSE 3 END,company_name=company_code,department_code='D1',department_name='Dept 1'");
                statement.execute("CREATE TABLE com_shipment_contract_reference(contract_no VARCHAR(40),company_id BIGINT,company_code VARCHAR(40),company_name VARCHAR(40),department_id BIGINT,department_code VARCHAR(40),department_name VARCHAR(40),tenant_id BIGINT,deleted INT)");
                statement.execute("INSERT INTO com_shipment_contract_reference VALUES('COMPANY',1,'001','001',11,'D1','Dept 1',1,0),('REF-ONLY',1,'001','001',12,'D2','Dept 2',1,0)");
                statement.execute("INSERT INTO com_contract VALUES(7,1,'SHADOWED','001',0,1,'001','D1','Dept 1'),(8,1,'UNKNOWN','001',0,1,'001','D1','Dept 1')");
                statement.execute("INSERT INTO com_shipment_contract_reference VALUES('SHADOWED',2,'002','002',21,'D1','Other',1,0),('UNKNOWN',NULL,NULL,NULL,NULL,NULL,NULL,1,0)");
            }
            var companyGrant=new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.DeviceContractOrganizationScopeQuery.Grant(1L,null,null);
            var departmentGrant=new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.DeviceContractOrganizationScopeQuery.Grant(1L,11L,"D1");
            assertEquals(Set.of("COMPANY","REF-ONLY"),numbers(db,config,new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.DeviceContractOrganizationScopeQuery(1L,List.of(companyGrant))));
            assertEquals(Set.of("COMPANY"),numbers(db,config,new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.DeviceContractOrganizationScopeQuery(1L,List.of(departmentGrant))));
            assertTrue(numbers(db,config,new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.DeviceContractOrganizationScopeQuery(1L,List.of())).isEmpty());
            assertEquals(Set.of("COMPANY","PROJECT"),numbers(db,config,new DeviceContractScopeQuery(1L,List.of("001"),List.of(10L))));
            assertEquals(Set.of("PROJECT"),numbers(db,config,new DeviceContractScopeQuery(1L,List.of(),List.of(10L))));
            assertTrue(numbers(db,config,new DeviceContractScopeQuery(1L,List.of(),List.of())).isEmpty());
            assertEquals(Set.of("COMPANY"),numbers(db,config,new DeviceContractScopeQuery(1L,List.of("001"),List.of(10L),Set.of("COMPANY","SHADOWED","AMBIGUOUS","UNKNOWN"))));
            assertTrue(numbers(db,config,new DeviceContractScopeQuery(1L,List.of("001"),List.of(10L),Set.of())).isEmpty());
        }
    }
    private Set<String> numbers(Connection db,Configuration config,Object query) throws Exception {
        var parameters=Map.of("query",query);
        var bound=config.getMappedStatement("cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper."+(query instanceof DeviceContractScopeQuery ? "selectVisibleDeviceContractNumbers" : "selectOrganizationVisibleContractNumbers")).getBoundSql(parameters);
        try(var statement=db.prepareStatement(bound.getSql().replace(" AS BINARY)"," AS VARBINARY(255))"))) {
            int i=1;
            for(var mapping:bound.getParameterMappings()) {
                String name=mapping.getProperty();
                statement.setObject(i++,bound.hasAdditionalParameter(name)?bound.getAdditionalParameter(name):config.newMetaObject(parameters).getValue(name));
            }
            Set<String> result=new HashSet<>();
            try(var rows=statement.executeQuery()) { while(rows.next()) result.add(rows.getString(1)); }
            return result;
        }
    }
}
