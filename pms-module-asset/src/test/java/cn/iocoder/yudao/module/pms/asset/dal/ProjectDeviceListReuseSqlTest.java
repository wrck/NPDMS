package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectShipmentPageContextQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi;
import org.junit.jupiter.api.Test;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ProjectDeviceListReuseSqlTest {
    @Test void oldBorrowInstallationDualSnRmaAndTransferRulesUseExistingRowsWithoutAssigningDevices() throws Exception {
        DeviceJoinedPageSqlTest.withDatabase(sql -> {
            sql.execute("UPDATE proj_project SET sales_type='14',department_code='PC-A' WHERE id=11 AND tenant_id=1");
            sql.execute("INSERT INTO com_project_contract_relation(tenant_id,project_id,contract_no,status,deleted) VALUES(1,11,'BORROW-L','ACTIVE',false),(1,11,'TRANSFER-C','ACTIVE',false)");
            sql.execute("INSERT INTO ast_device(id,tenant_id,sn,deleted) VALUES(201,1,'INSTALLED',false),(202,1,'MATCHED',false),(203,1,'WRONG_CENTER',false),(204,1,'RMA',false),(205,1,'LITERAL_NULL',false),(206,1,'TRANSFERRED',false),(207,1,'NO_SHIPMENT',false)");
            shipment(sql,201,"INSTALLED","BORROW","OTHER",null);
            shipment(sql,202,"MATCHED","BORROW","PC-A",null);
            shipment(sql,203,"WRONG_CENTER","BORROW","OTHER",null);
            shipment(sql,204,"RMA","BORROW","PC-A","RMA-1");
            shipment(sql,205,"LITERAL_NULL","BORROW","PC-A","null");
            shipment(sql,206,"TRANSFERRED","TRANSFER","OTHER",null);
            sql.execute("INSERT INTO imp_eng_installation(id,tenant_id,project_id,equipment_id,status,deleted) VALUES(301,1,11,201,2,false),(302,1,11,201,2,false),(306,1,11,206,2,false),(307,1,11,207,2,false)");
            sql.execute("INSERT INTO ast_device_relationship VALUES(1,'INSTALLED','PHYSICAL','BORROW','P2','DPPMS','EXTRA_SN',false),(1,'TRANSFERRED','PHYSICAL-T','TRANSFER','P3','DPPMS','EXTRA_SN',false),(2,'INSTALLED','FOREIGN','BORROW','P4','DPPMS','EXTRA_SN',false)");
            sql.execute("INSERT INTO ast_product_official_info VALUES(1,'P2','physical product',false),(1,'P3','transfer product',false)");
        }, mapper -> {
            var q=DeviceJoinedPageSqlTest.query(DeviceJoinedPageSqlTest.scope(ProjectViewReadScopeApi.Mode.SCOPED)).toBuilder().projectId(11L).build();
            assertEquals(Set.of(1L,201L,202L,205L,206L),DeviceJoinedPageSqlTest.ids(mapper,q));
            var page=mapper.selectJoinedDevicePage(q);
            assertEquals(5,page.getTotal());
            assertTrue(page.getList().stream().filter(d->d.deviceId()>=201).allMatch(d->d.projectId()==null));
            assertEquals(Set.of(201L),DeviceJoinedPageSqlTest.ids(mapper,q.toBuilder().sn("PHYSICAL").build()));
            var facts=mapper.selectProjectShipmentContext(new ProjectShipmentPageContextQuery(1L,11L,List.of(201L,206L)));
            assertEquals(3,facts.size());
            assertTrue(facts.stream().allMatch(f->f.installAddress()==null));
            assertEquals(Set.of(301L,302L,306L),facts.stream().map(f->f.installationId()).collect(java.util.stream.Collectors.toSet()));
            var transferred=facts.stream().filter(f->f.deviceId()==206L).findFirst().orElseThrow();
            assertEquals("PHYSICAL-T",transferred.physicalSn());assertEquals("transfer product",transferred.physicalProductName());
            assertTrue(mapper.selectProjectShipmentContext(new ProjectShipmentPageContextQuery(1L,11L,List.of())).isEmpty());
        });
    }

    @Test void blankBorrowProfitCenterKeepsOriginalContractSuffixAndDoesNotInventAFilter() throws Exception {
        DeviceJoinedPageSqlTest.withDatabase(sql -> {
            sql.execute("UPDATE proj_project SET sales_type='14',department_code='   ' WHERE id=11 AND tenant_id=1");
            sql.execute("INSERT INTO com_project_contract_relation(tenant_id,project_id,contract_no,status,deleted) VALUES(1,11,'BORROW-L','ACTIVE',false)");
            sql.execute("INSERT INTO ast_device(id,tenant_id,sn,deleted) VALUES(208,1,'RAW-CONTRACT',false),(209,1,'STRIPPED-CONTRACT',false)");
            shipment(sql,208,"RAW-CONTRACT","BORROW-L","ANY",null);
            shipment(sql,209,"STRIPPED-CONTRACT","BORROW","ANY",null);
        }, mapper -> {
            var query=DeviceJoinedPageSqlTest.query(DeviceJoinedPageSqlTest.scope(ProjectViewReadScopeApi.Mode.SCOPED)).toBuilder().projectId(11L).build();
            assertEquals(Set.of(1L,208L),DeviceJoinedPageSqlTest.ids(mapper,query));
        });
    }

    @Test void borrowProfitCenterUsesExistingDepartmentMappingAndRejectsOtherDepartments() throws Exception {
        DeviceJoinedPageSqlTest.withDatabase(sql -> {
            sql.execute("UPDATE proj_project SET sales_type='14',department_code='162102' WHERE id=11 AND tenant_id=1");
            sql.execute("INSERT INTO com_project_contract_relation(tenant_id,project_id,contract_no,status,deleted) VALUES(1,11,'BORROW-L','ACTIVE',false)");
            sql.execute("INSERT INTO ast_device(id,tenant_id,sn,deleted) VALUES(210,1,'MAPPED-CENTER',false),(211,1,'OTHER-CENTER',false)");
            shipment(sql,210,"MAPPED-CENTER","BORROW","311202",null);
            shipment(sql,211,"OTHER-CENTER","BORROW","311203",null);
        }, mapper -> {
            var query=DeviceJoinedPageSqlTest.query(DeviceJoinedPageSqlTest.scope(ProjectViewReadScopeApi.Mode.SCOPED)).toBuilder().projectId(11L).build();
            assertEquals(Set.of(1L,210L),DeviceJoinedPageSqlTest.ids(mapper,query));
        });
    }

    private static void shipment(Statement sql,long key,String sn,String contract,String center,String rma) throws Exception {
        sql.execute("INSERT INTO com_shipment_contract_reference(tenant_id,contract_no,source_system,source_record_key,deleted) VALUES(1,'"+contract+"','DPPMS','R"+key+"',false)");
        sql.execute("INSERT INTO com_shipment_package(tenant_id,source_record_key,shipment_contract_source_key,source_system,deleted) VALUES(1,'P"+key+"','R"+key+"','DPPMS',false)");
        sql.execute("INSERT INTO ast_device_shipment(tenant_id,device_sn,profit_center,legacy_package_key,rma_no,source_system,deleted) VALUES(1,'"+sn+"','"+center+"','P"+key+"',"+(rma==null?"NULL":"'"+rma+"'")+",'DPPMS',false)");
    }
}
