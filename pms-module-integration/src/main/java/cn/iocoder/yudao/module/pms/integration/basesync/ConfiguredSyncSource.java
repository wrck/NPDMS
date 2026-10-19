package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import cn.iocoder.yudao.module.pms.integration.api.basesync.SyncSource;
import cn.iocoder.yudao.module.pms.integration.sync.SyncConnectionService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.mapping.ResultSetType;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/** Reuses connection configuration only; legacy resolve/reader/adapter methods are never invoked. */
@Component
public final class ConfiguredSyncSource implements SyncSource {
    private final SyncConnectionService connections;
    private final ExternalDataSourceApi dataSources;
    private final SqlSessionFactory mysqlSessions;
    private final SqlSessionFactory otherSessions;

    public ConfiguredSyncSource(SyncConnectionService connections, ExternalDataSourceApi dataSources) {
        this.connections = connections;
        this.dataSources = dataSources;
        // Connector/J's native forward-only streaming does not require changing saved connection URLs.
        mysqlSessions = sourceSessions(Integer.MIN_VALUE);
        otherSessions = sourceSessions(500);
    }

    private static SqlSessionFactory sourceSessions(int fetchSize) {
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultStatementTimeout(60);
        configuration.setDefaultFetchSize(fetchSize);
        configuration.setDefaultResultSetType(ResultSetType.FORWARD_ONLY);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.setCacheEnabled(false);
        configuration.setLogImpl(NoLoggingImpl.class);
        try {
            for (var resource : new PathMatchingResourcePatternResolver().getResources("classpath*:base-sync-source/*Mapper.xml")) {
                try (var input = resource.getInputStream()) {
                    new XMLMapperBuilder(input, configuration, resource.toString(), configuration.getSqlFragments()).parse();
                }
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("无法加载基类同步的源查询", error);
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    @Override public Session open(long connectionId) {
        var reference = connections.required(connectionId);
        java.sql.Connection connection = null;
        try {
            connection = dataSources.openReadOnly(reference.getDataSourceId());
            var factory = "MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())
                    ? mysqlSessions : otherSessions;
            var session = factory.openSession(connection);
            return new Session() {
                @Override public org.apache.ibatis.session.SqlSession sqlSession() { return session; }
                @Override public <M> M mapper(Class<M> type) { return session.getMapper(type); }
                @Override public void close() { session.close(); }
            };
        } catch (Exception error) {
            if (connection != null) try { connection.close(); } catch (java.sql.SQLException close) { error.addSuppressed(close); }
            // JDBC error messages may contain connection details; do not expose them in the business API.
            throw new IllegalStateException("无法打开已配置的同步连接");
        }
    }
}
