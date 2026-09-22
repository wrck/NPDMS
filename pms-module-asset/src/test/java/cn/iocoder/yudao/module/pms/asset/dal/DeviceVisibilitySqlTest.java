package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceVisibilityQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DeviceVisibilitySqlTest {
    @Test void independentContractAndProjectGrantsUseSameSqlForListAndDetail() throws Exception {
        Configuration configuration = new Configuration();
        try (var xml = getClass().getResourceAsStream("/mapper/device/DeviceQueryMapper.xml")) {
            new XMLMapperBuilder(xml, configuration, "device", configuration.getSqlFragments()).parse();
        }
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:device_visibility;MODE=MySQL;DB_CLOSE_DELAY=-1")) {
            try (Statement sql = connection.createStatement()) {
                sql.execute("CREATE TABLE ast_device(id BIGINT,tenant_id BIGINT,sn VARCHAR(40),project_id BIGINT,contract_no VARCHAR(40),deleted BOOLEAN)");
                sql.execute("CREATE TABLE ast_device_project_relationship(tenant_id BIGINT,device_sn VARCHAR(40),project_id BIGINT,effective_from TIMESTAMP,effective_to TIMESTAMP,deleted BOOLEAN)");
                sql.execute("INSERT INTO ast_device VALUES (1,1,'project-visible',10,'hidden',false),(2,1,'project-denied',20,'C-1',false),(3,1,'contract-visible',NULL,'C-1',false),(4,1,'contract-denied',NULL,'hidden',false),(5,2,'other-tenant',NULL,'C-1',false),(6,1,'effective-project-denied',NULL,'C-1',false),(7,1,'expired-project',NULL,'C-1',false),(8,1,'no-contract',NULL,NULL,false)");
                sql.execute("INSERT INTO ast_device_project_relationship VALUES (1,'effective-project-denied',20,DATEADD('DAY',-1,CURRENT_TIMESTAMP),NULL,false),(1,'expired-project',20,DATEADD('DAY',-2,CURRENT_TIMESTAMP),DATEADD('DAY',-1,CURRENT_TIMESTAMP),false)");
            }
            try (var sql = connection.createStatement()) {
                sql.execute("ALTER TABLE ast_device ADD company_id BIGINT");
                sql.execute("ALTER TABLE ast_device ADD department_id BIGINT");
                sql.execute("ALTER TABLE ast_device ADD department_code VARCHAR(40)");
                sql.execute("ALTER TABLE ast_device ADD organization_source VARCHAR(20)");
                sql.execute("UPDATE ast_device SET company_id=20,department_id=2,department_code='D2',organization_source='PROJECT' WHERE id=2");
                sql.execute("UPDATE ast_device SET company_id=10,department_id=1,department_code='D1',organization_source='CONTRACT' WHERE id IN(3,6,7)");
            }
            assertEquals(5, number(connection, configuration, "selectVisibleDeviceCount",
                    new VisibleDevicePageQuery(1L,Set.of(10L),null,null,null,null,1,10,null,null,Set.of("C-1"), java.util.List.of())));
            assertEquals(1, number(connection, configuration, "selectVisibleDeviceCount",
                    new VisibleDevicePageQuery(1L,Set.of(),null,null,null,null,1,10,null,null,Set.of(),java.util.List.of(new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant(20L,2L,"D2")))));
            assertEquals(3, number(connection, configuration, "selectVisibleDeviceCount",
                    new VisibleDevicePageQuery(1L,Set.of(),null,null,null,null,1,10,null,null,Set.of(),java.util.List.of(new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant(10L,null,null)))));
            for (long id=1;id<=8;id++) {
                assertEquals(Set.of(1L,2L,3L,6L,7L).contains(id) ? 1 : 0,
                        number(connection, configuration, "existsVisibleDevice", new DeviceVisibilityQuery(1L,id,Set.of(10L),Set.of("C-1"), java.util.List.of())), "device="+id);
            }
            assertEquals(4, number(connection, configuration, "selectVisibleDeviceCount",
                    new VisibleDevicePageQuery(1L,Set.of(),null,null,null,null,1,10,null,null,Set.of("C-1"), java.util.List.of())));
            assertEquals(0, number(connection, configuration, "selectVisibleDeviceCount",
                    new VisibleDevicePageQuery(1L,Set.of(),null,null,null,null,1,10,null,null,Set.of(), java.util.List.of())));
            var grants=java.util.List.of(new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant(10L,null,null),
                    new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant(20L,2L,"D2"));
            assertEquals(1,number(connection,configuration,"selectContractCandidates",
                    new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceContractCandidateQuery(1L,null,null,null,null,null,null,null,grants)));
            assertEquals(0,number(connection,configuration,"selectContractCandidates",
                    new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceContractCandidateQuery(1L,2L,null,null,null,null,null,null,grants)));
            assertEquals(0,number(connection,configuration,"selectVisibleDeviceCount",
                    new VisibleDevicePageQuery(1L,Set.of(),null,null,null,null,1,10,null,null,Set.of(),java.util.List.of(
                            new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant(20L,1L,"D1"),
                            new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant(10L,2L,"D2")))));
        }
    }
    private int number(Connection connection, Configuration config, String statement, Object query) throws Exception {
        var parameters = Map.of("query",query);
        var sql = config.getMappedStatement("cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper."+statement).getBoundSql(parameters);
        String text=sql.getSql().replace(" AS BINARY)"," AS VARBINARY(255))").replace("b'0'", "FALSE");
        if (statement.equals("selectContractCandidates")) text="SELECT COUNT(*) FROM ("+text+") candidates";
        try (PreparedStatement prepared = connection.prepareStatement(text)) {
            int index=1;
            for (var mapping:sql.getParameterMappings()) {
                String name=mapping.getProperty();
                prepared.setObject(index++,sql.hasAdditionalParameter(name)?sql.getAdditionalParameter(name):config.newMetaObject(parameters).getValue(name));
            }
            try (ResultSet rows=prepared.executeQuery()) { rows.next(); return rows.getInt(1); }
        }
    }
}
