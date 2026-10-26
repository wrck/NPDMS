package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeviceNameFilterSqlTest {
    @Test void namesRemainAnIntersectionWithTenantVisibilityAndLogicalDeletion() throws Exception {
        var config = new Configuration();
        ProjectDeviceListSqlFixture.load(config);
        try (var xml = getClass().getResourceAsStream("/mapper/device/DeviceQueryMapper.xml")) {
            new XMLMapperBuilder(xml, config, "device", config.getSqlFragments()).parse();
        }
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:device_names;MODE=MySQL")) {
            var sql = connection.createStatement();
            sql.execute("CREATE TABLE ast_device(id BIGINT,tenant_id BIGINT,project_id BIGINT,customer_id BIGINT,department_name VARCHAR(100),sn VARCHAR(30),contract_no VARCHAR(30),deleted BOOLEAN)");
            ProjectDeviceListSqlFixture.schema(sql);
            sql.execute("CREATE TABLE ast_device_project_relationship(tenant_id BIGINT,device_sn VARCHAR(30),project_id BIGINT,effective_from TIMESTAMP,effective_to TIMESTAMP,deleted BOOLEAN)");
            sql.execute("INSERT INTO ast_device VALUES (1,1,10,100,'杭州','SN1','C1',false),(2,1,10,200,'北京','SN2','C2',false),(3,1,20,100,'杭州','SN3','C3',false),(4,2,10,100,'杭州','SN4','C4',false),(5,1,10,100,'杭州','SN5','C5',true)");
            var base = VisibleDevicePageQuery.builder().tenantId(1L).visibleProjectIds(Set.of(10L))
                    .visibleContractNumbers(Set.of()).organizationGrants(List.of()).pageNo(1).pageSize(10).build();
            assertEquals(2, count(connection, config, base));
            assertEquals(1, count(connection, config, base.toBuilder().matchingProjectIds(Set.of(10L))
                    .matchingCustomerIds(Set.of(100L)).departmentName("杭州").build()));
            assertEquals(0, count(connection, config, base.toBuilder().matchingProjectIds(Set.of(20L)).build()));
            assertEquals(0, count(connection, config, base.toBuilder().matchingCustomerIds(Set.of()).build()));
            assertEquals(0, count(connection, config, base.toBuilder().departmentName("广州").build()));
        }
    }

    private long count(Connection connection, Configuration config, VisibleDevicePageQuery query) throws Exception {
        var params = Map.of("query", query);
        var bound = config.getMappedStatement("cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper.selectVisibleDeviceCount").getBoundSql(params);
        try (var statement = connection.prepareStatement(bound.getSql().replace("FORCE INDEX (idx_ast_device_visibility_cover)", "").replace("b'0'", "FALSE"))) {
            int i = 1;
            for (var parameter : bound.getParameterMappings()) {
                String key = parameter.getProperty();
                statement.setObject(i++, bound.hasAdditionalParameter(key) ? bound.getAdditionalParameter(key) : config.newMetaObject(params).getValue(key));
            }
            try (var result = statement.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }
}
