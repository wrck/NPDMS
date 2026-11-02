package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PackageContractReferenceMySqlTest extends RemainingTaskMySqlSupport {
    @BeforeEach void tables() {
        for (var table : List.of("com_shipment_package", "com_shipment_contract_reference")) {
            jdbc.execute("CREATE TEMPORARY TABLE fixture_"+table+" LIKE npdms_reverify_20261014."+table);
            jdbc.execute("CREATE TEMPORARY TABLE "+table+" LIKE fixture_"+table);
        }
        jdbc.update("INSERT INTO com_shipment_contract_reference(id,tenant_id,contract_no,source_system,source_record_key,creator,updater) VALUES(20,1,'C','DPPMS','parent-uuid','fixture','fixture')");
        jdbc.update("INSERT INTO com_shipment_package(id,tenant_id,package_no,shipment_contract_source_key,source_system,source_record_key,creator,updater,update_time) VALUES(10,1,'PK','parent-uuid','DPPMS','package-uuid','fixture','original','2020-01-01')");
    }
    int update(long tenant, String parentKey) {
        var context = context(false).forParameters(new P15DeliverySyncTask.Parameters(Map.of(),3));
        assertEquals(1L, context.tenantId());
        var row = new P15DeliverySyncTask.Row().setId(10L).setShipmentContractRefId(20L).setShipmentContractSourceKey(parentKey);
        if (tenant != 1) jdbc.update("UPDATE com_shipment_package SET tenant_id=? WHERE id=10",tenant);
        return session.getMapper(P15DeliverySyncTask.TargetMapper.class).updatePackageContractReference(new SyncBatch<>(context,List.of(new P15DeliverySyncTask.Source().setTarget(row))));
    }
    @Test void preciseParentUpdatesOnceAndPreservesAudit() {
        assertEquals(1, update(1,"parent-uuid"));
        assertEquals(20L,jdbc.queryForObject("SELECT shipment_contract_ref_id FROM com_shipment_package WHERE id=10",Long.class));
        assertEquals("original",jdbc.queryForObject("SELECT updater FROM com_shipment_package WHERE id=10",String.class));
        assertEquals("2020-01-01 00:00:00",jdbc.queryForObject("SELECT DATE_FORMAT(update_time,'%Y-%m-%d %H:%i:%s') FROM com_shipment_package WHERE id=10",String.class));
        assertEquals(0,update(1,"parent-uuid"));
    }
    @Test void caseMismatchTenantAndDeletedParentCannotFill() {
        assertEquals(0,update(1,"PARENT-UUID"));
        assertEquals(0,update(2,"parent-uuid"));
        jdbc.update("UPDATE com_shipment_package SET tenant_id=1 WHERE id=10");
        jdbc.update("UPDATE com_shipment_contract_reference SET deleted=TRUE WHERE id=20");
        assertEquals(0,update(1,"parent-uuid"));
    }
    @Test void failureRollsBackParentReference() {
        assertThrows(IllegalStateException.class,()->transaction.execute(status->{assertEquals(1,update(1,"parent-uuid"));throw new IllegalStateException("rollback");}));
        assertNull(jdbc.queryForObject("SELECT shipment_contract_ref_id FROM com_shipment_package WHERE id=10",Long.class));
    }
}
