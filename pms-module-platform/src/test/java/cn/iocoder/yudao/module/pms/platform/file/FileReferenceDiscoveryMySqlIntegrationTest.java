package cn.iocoder.yudao.module.pms.platform.file;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;

/** Same discovery acceptance cases against an explicitly named, exclusive MySQL database. */
@EnabledIfSystemProperty(named = "npdms.file.discovery.mysql.exclusive", matches = "true")
class FileReferenceDiscoveryMySqlIntegrationTest extends FileReferenceDiscoveryPersistenceTest {
    @Override protected DataSource createDatabase() {
        String database = System.getProperty("npdms.file.discovery.mysql.database", "");
        String port = System.getProperty("npdms.file.discovery.mysql.port", "");
        if (!database.matches("npdms_file_discovery_[a-zA-Z0-9_]+") || !port.matches("[1-9][0-9]{3,4}")) {
            throw new IllegalStateException("Explicit task-owned MySQL database and port required");
        }
        return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8", "root", "");
    }
}
