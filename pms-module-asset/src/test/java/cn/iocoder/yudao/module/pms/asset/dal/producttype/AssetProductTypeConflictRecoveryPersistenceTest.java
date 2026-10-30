package cn.iocoder.yudao.module.pms.asset.dal.producttype;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeSourceMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeSourceMappingMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AssetProductTypeConflictRecoveryPersistenceTest {
    @Test void resolvedSourceReallyClearsItsFourConflictColumns() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:conflict_" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")) {
            connection.createStatement().execute("""
                    CREATE TABLE ast_product_type_source_mapping (
                    id BIGINT PRIMARY KEY, tenant_id BIGINT, source_system VARCHAR(32), source_key VARCHAR(128),
                    source_version VARCHAR(128), source_updated_at TIMESTAMP, payload_hash VARCHAR(64), product_type_id BIGINT,
                    mapping_status VARCHAR(24), conflict_product_type_code VARCHAR(64), conflict_source_version VARCHAR(128),
                    conflict_source_updated_at TIMESTAMP, conflict_payload_hash VARCHAR(64), synced_at TIMESTAMP, version INT,
                    creator VARCHAR(64), create_time TIMESTAMP, updater VARCHAR(64), update_time TIMESTAMP, deleted BOOLEAN DEFAULT FALSE,
                    CHECK ((mapping_status='CONFLICT' AND conflict_product_type_code IS NOT NULL AND conflict_source_version IS NOT NULL
                    AND conflict_source_updated_at IS NOT NULL AND conflict_payload_hash IS NOT NULL) OR
                    (mapping_status<>'CONFLICT' AND conflict_product_type_code IS NULL AND conflict_source_version IS NULL
                    AND conflict_source_updated_at IS NULL AND conflict_payload_hash IS NULL)))
                    """);
            var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
            config.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
            config.setEnvironment(new Environment("memory", new JdbcTransactionFactory(), new SingleConnectionDataSource(connection, true)));
            var plugins = new MybatisPlusInterceptor(); plugins.addInnerInterceptor(new OptimisticLockerInnerInterceptor()); config.addInterceptor(plugins);
            config.addMapper(AssetProductTypeSourceMappingMapper.class);
            try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(false)) {
                var mapper = session.getMapper(AssetProductTypeSourceMappingMapper.class);
                var row = new AssetProductTypeSourceMappingDO(); row.setId(1L); row.setTenantId(7L); row.setVersion(1);
                row.setMappingStatus("CONFLICT"); row.setConflictProductTypeCode("QA"); row.setConflictSourceVersion("v1");
                row.setConflictSourceUpdatedAt(LocalDateTime.of(2026,10,28,0,0)); row.setConflictPayloadHash("a".repeat(64));
                assertEquals(1, mapper.insert(row)); session.commit();
                row.setMappingStatus("RESOLVED"); row.setConflictProductTypeCode(null); row.setConflictSourceVersion(null);
                row.setConflictSourceUpdatedAt(null); row.setConflictPayloadHash(null);
                assertDoesNotThrow(() -> assertEquals(1, mapper.updateById(row))); session.commit();
                var saved = mapper.selectById(1L); assertEquals("RESOLVED", saved.getMappingStatus());
                assertNull(saved.getConflictProductTypeCode()); assertNull(saved.getConflictSourceVersion());
                assertNull(saved.getConflictSourceUpdatedAt()); assertNull(saved.getConflictPayloadHash());
            }
        }
    }
}
