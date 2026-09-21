package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.ProjectDeviceSelectionMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectDeviceSelectionQuery;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 执行实际生产Mapper，验证项目/合同OR条件不会越过租户、删除和筛选边界。 */
class ProjectDeviceSelectionMapperTest {
    @Test void selectionAndSaveValidationUseTheSameScopeInSql() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:selection_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE ast_device (id BIGINT PRIMARY KEY, tenant_id BIGINT, project_id BIGINT, "
                    + "customer_id BIGINT, sn VARCHAR(128), name VARCHAR(255), product_code VARCHAR(128), "
                    + "product_model VARCHAR(255), contract_no VARCHAR(128), status VARCHAR(32), deleted INT DEFAULT 0)");
            sql.execute("INSERT INTO ast_device (id,tenant_id,project_id,sn,name,contract_no,deleted) VALUES "
                    + "(1,1,10,'PROJECT-1','设备一','OTHER',0),"
                    + "(2,1,20,'CONTRACT-2','设备二','C-10',0),"
                    + "(3,1,20,'OTHER-3','设备三','OTHER',0),"
                    + "(4,2,10,'TENANT-4','跨租户','C-10',0),"
                    + "(5,1,10,'DELETED-5','已删除','C-10',1),"
                    + "(6,1,20,'BLANK-6','空合同','',0)");
        }
        var config = new MybatisConfiguration();
        config.setEnvironment(new Environment("selection", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        config.addInterceptor(interceptor);
        config.addMapper(ProjectDeviceSelectionMapper.class);
        try (var xml = getClass().getResourceAsStream("/mapper/device/ProjectDeviceSelectionMapper.xml")) {
            new XMLMapperBuilder(xml, config, "selection-xml", config.getSqlFragments()).parse();
        }
        try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
            var mapper = session.getMapper(ProjectDeviceSelectionMapper.class);
            var query = new ProjectDeviceSelectionQuery(); query.setTenantId(1L); query.setProjectId(10L);
            query.setContractNumbers(Set.of("C-10")); query.setPageSize(1); query.setPageNo(1);
            var first = mapper.selectSelectionPage(query);
            assertEquals(2, first.getTotal()); assertEquals(2L, first.getList().getFirst().getId());
            query.setPageNo(2);
            assertEquals(1L, mapper.selectSelectionPage(query).getList().getFirst().getId());
            query.setPageNo(1); query.setSn("CONTRACT");
            assertEquals(1, mapper.selectSelectionPage(query).getTotal());
            query.setDeviceIds(List.of(1L, 2L, 3L, 4L, 5L));
            assertEquals(List.of(1L, 2L), mapper.selectSelectionForUpdate(query).stream().map(row -> row.getId()).toList());
            query.setDeviceIds(List.of());
            assertTrue(mapper.selectSelectionForUpdate(query).isEmpty());
            query.setSn(null); query.setContractNumbers(Set.of()); query.setPageSize(20);
            assertEquals(List.of(1L), mapper.selectSelectionPage(query).getList().stream().map(row -> row.getId()).toList());
            query.setProjectId(null);
            assertTrue(mapper.selectSelectionPage(query).getList().isEmpty());
        }
    }
}
