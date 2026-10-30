package cn.iocoder.yudao.module.pms.customer.dal;

import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerExternalMappingDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerFieldHistoryDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.classification.CustomerMarketRelationDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.location.CustomerLocationReferenceDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.security.CustomerScopeSliceDO;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CustomerSchemaContractTest {

    @Test
    void mapsCustomerOwnedTables() throws Exception {
        assertTable(CustomerMasterDO.class, "cus_customer_master");
        assertTable(CustomerExternalMappingDO.class, "cus_customer_external_mapping");
        assertTable(CustomerFieldHistoryDO.class, "cus_customer_field_history");
        assertTable(CustomerLocationReferenceDO.class, "cus_customer_location_reference");
        assertTable(CustomerMarketRelationDO.class, "cus_market_relation");
        assertTable(CustomerScopeSliceDO.class, "cus_customer_scope_slice");
        // P12-B4：id/version 由 BaseBusinessEntity 基类唯一定义，子类不再声明同名字段。
        assertTrue(BaseBusinessEntity.class.isAssignableFrom(CustomerMasterDO.class));
        assertNotNull(BaseBusinessEntity.class.getDeclaredField("id").getAnnotation(TableId.class));
        assertNotNull(BaseBusinessEntity.class.getDeclaredField("version").getAnnotation(Version.class));
        assertNotNull(CustomerMasterDO.class.getDeclaredField("contactPhone"));
        assertNotNull(CustomerMasterDO.class.getDeclaredField("contactEmail"));
        assertNotNull(CustomerMasterDO.class.getDeclaredField("departmentCode"));
        assertNotNull(CustomerMasterDO.class.getDeclaredField("departmentName"));
        assertNotNull(CustomerMasterDO.class.getDeclaredField("marketCode"));
        assertNotNull(CustomerMasterDO.class.getDeclaredField("systemCode"));
        assertNotNull(CustomerMasterDO.class.getDeclaredField("expendCode"));
        assertNotNull(CustomerMasterDO.class.getDeclaredField("industryCode"));
    }

    private static void assertTable(Class<?> type, String tableName) {
        TableName annotation = type.getAnnotation(TableName.class);
        assertNotNull(annotation);
        assertEquals(tableName, annotation.value());
    }
}
