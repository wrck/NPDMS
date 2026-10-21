package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="BASE_PRODUCT_TEMP_MYSQL_TEST",matches="true")
class P14BusinessKeysMySqlTest extends RemainingTaskMySqlSupport {
    P14SalesOrderSyncTask task(){return new P14SalesOrderSyncTask(session.getMapper(P14SalesOrderSyncTask.TargetMapper.class));}

    @Test void differentHeaderAndLineTypesAreReadWithoutInventingParentOrWriteCounts(){
        order("A");line("A",-2);
        jdbc.update("UPDATE pm_order_line_from_erp SET lineType=1,openQuantity=0");
        var c=context(true);
        assertEquals(new SyncCounts(2,1,0,0,0),transaction.execute(s->task().execute(c)));
        assertTrue(c.runState().warnings().contains("P14_ORDER_REFERENCE_PENDING:1"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_sales_order",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_sales_order_line",Integer.class));
        verify(identities,never()).saveAll(any());
        var failure=assertThrows(IllegalArgumentException.class,()->run(task(),false));
        assertEquals("P14_MANDATORY_ORDER_MISSING",failure.getMessage());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_sales_order",Integer.class));
        assertEquals(-2,jdbc.queryForObject("SELECT orderQuantity FROM pm_order_line_from_erp",Integer.class));
    }

    @Test void entirelyMissingParentIsAlsoOnlyAReadOnlyPreviewDependency(){
        line("A",3);var c=context(true);
        assertEquals(new SyncCounts(1,0,0,0,0),transaction.execute(s->task().execute(c)));
        assertTrue(c.runState().warnings().contains("P14_ORDER_REFERENCE_PENDING:1"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_sales_order_line",Integer.class));
        verify(identities,never()).saveAll(any());
        assertThrows(IllegalArgumentException.class,()->run(task(),false));
    }

    @Test void maximumExecutionIsApprovedButOtherHeaderConflictsRemainRejected(){
        order("A");line("A",3);
        jdbc.update("UPDATE pm_order_data_from_erp SET orderExecNumber='EXEC-1',customInfo=JSON_OBJECT('ignored',1)");
        jdbc.update("INSERT INTO pm_order_data_from_erp(id,orderNumber,compCode,orderType,source,syncTime,contractNo,customerCode,orderExecNumber,customInfo) VALUES(3,'A','01','0','SAP','2020-01-01','C','C000001','EXEC-3',JSON_OBJECT('ignored',2))");
        assertEquals(new SyncCounts(2,2,0,0,0),run(task(),true));
        run(task(),false);
        assertEquals("EXEC-3",jdbc.queryForObject("SELECT execution_no FROM com_sales_order",String.class));
        assertNull(jdbc.queryForObject("SELECT custom_info FROM com_sales_order",String.class));
        assertTrue(jdbc.queryForObject("SELECT source_record_key FROM com_sales_order",String.class).startsWith("BASE_P14|ORDER|"));
        assertTrue(jdbc.queryForObject("SELECT source_record_key FROM com_sales_order_line",String.class).startsWith("BASE_P14|LINE|"));
        jdbc.update("UPDATE pm_order_data_from_erp SET contractNo='DIFFERENT' WHERE id=3");
        var failure=assertThrows(IllegalArgumentException.class,()->run(task(),true));
        assertEquals("P14_DUPLICATE_BUSINESS_KEY_CONFLICT",failure.getMessage());
    }

    @Test void lineLookupUsesLineTypeEvenWhenStoredParentOrderTypeDiffers(){
        order("A");line("A",-2);jdbc.update("UPDATE pm_order_line_from_erp SET lineType=1,openQuantity=0");
        jdbc.update("INSERT INTO com_sales_order_line(id,tenant_id,order_id,source_system,source_record_key,source_version,company_code,order_type,order_no,line_no,line_type,unit_scale,quantity_status,status,creator,updater) VALUES(99,1,98,'SAP','manual','2020','001','0','A','1','1',6,'PENDING_AUTHORITY','ENABLED','manual','manual')");
        var found=session.getMapper(P14SalesOrderSyncTask.TargetMapper.class).selectLines(new P14SalesOrderSyncTask.LinesQuery(1,List.of(new P14SalesOrderSyncTask.LineKey("SAP","001","1","A","1")),true));
        assertEquals(1,found.size());
        var failure=assertThrows(IllegalArgumentException.class,()->run(task(),true));
        assertEquals("P14_EXISTING_TARGET_PROTECTED",failure.getMessage());
        assertEquals("manual",jdbc.queryForObject("SELECT creator FROM com_sales_order_line",String.class));
    }

    @Test void lineNumbersStillPreserveEveryDetailWithinTheApprovedOrderLineGroup(){
        order("A");line("A",3);
        jdbc.update("INSERT INTO pm_order_line_from_erp(id,orderNumber,lineNum,compCode,lineType,source,syncTime,itemCode,orderQuantity,openQuantity,warrantyMonth) VALUES(2,'A','2','01','0','SAP','2020-01-01','ITEM-2',4,1,12)");
        assertEquals(new SyncCounts(3,3,0,0,0),run(task(),true));run(task(),false);
        assertEquals(List.of("1","2"),jdbc.queryForList("SELECT line_no FROM com_sales_order_line ORDER BY line_no",String.class));
        assertEquals(List.of("ITEM","ITEM-2"),jdbc.queryForList("SELECT product_code FROM com_sales_order_line ORDER BY line_no",String.class));
    }

    @Test void unknownLineTypeAndInvalidPositiveQuantitiesStillFailDuringPreview(){
        order("A");line("A",3);jdbc.update("UPDATE pm_order_line_from_erp SET lineType=2");
        assertThrows(IllegalArgumentException.class,()->run(task(),true));
        jdbc.update("UPDATE pm_order_line_from_erp SET lineType=0,orderQuantity=-1");
        assertThrows(IllegalArgumentException.class,()->run(task(),true));
        verify(identities,never()).saveAll(any());
    }
}
