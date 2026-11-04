package cn.iocoder.yudao.module.pms.engineering.service.installation;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Execute the prepared migration in memory; no configured database or Flyway invocation. */
class InstallationOpticalModuleSeedTest {
    static final String KEY = "pms.installation.address.product.filters";
    static final String VALUE = "{\"productFilters\":[{\"productTypeNotIn\":[\"光模块\"]}]}";

    @Test void createsMissingConfigurationAndReplaysWithoutDuplicates() throws Exception {
        try (var connection = database()) {
            migrate(connection); migrate(connection);
            assertConfig(connection, VALUE, "seed_feedback20261030_optical");
        }
    }

    @Test void descriptionUpgradeIsIdempotentAndPreservesCustomizedSettings() throws Exception {
        String upgraded = "{\"productFilters\":[{\"productTypeNotIn\":[\"光模块\"],\"productDescNotLike\":\"%光模块%\"}]}";
        try (var connection = database()) {
            migrate(connection);
            script(connection, "V535__installation_address_optical_description_default.sql");
            script(connection, "V535__installation_address_optical_description_default.sql");
            assertConfig(connection, upgraded, "seed_20261103_optical_description");
        }
        try (var connection = database(); var insert = connection.prepareStatement(
                "INSERT INTO infra_config(id,config_key,value,creator,updater,deleted) VALUES (1,?,?,?,?,0)")) {
            insert.setString(1, KEY); insert.setString(2, VALUE);
            insert.setString(3, "seed_feedback20261030_optical"); insert.setString(4, "actual_user"); insert.executeUpdate();
            script(connection, "V535__installation_address_optical_description_default.sql");
            assertConfig(connection, VALUE, "actual_user");
        }
    }

    @Test void upgradesOnlyUntouchedOriginalEmptySeedAndPreservesEveryUserConfiguration() throws Exception {
        try (var connection = database()) {
            script(connection, "V521__installation_address_product_filters.sql");
            migrate(connection); migrate(connection);
            assertConfig(connection, VALUE, "seed_feedback20261030_optical");
        }
        for (String value : new String[] {"{\"productFilters\":[]}", "{\"productFilters\":[{\"productTypeIn\":[\"FW\"]}]}"}) {
            try (var connection = database(); var insert = connection.prepareStatement(
                    "INSERT INTO infra_config(id,config_key,value,creator,updater,deleted) VALUES (1,?,?,?,?,0)")) {
                insert.setString(1, KEY); insert.setString(2, value);
                insert.setString(3, "seed_tf040_address_filters"); insert.setString(4, "actual_user"); insert.executeUpdate();
                migrate(connection); migrate(connection);
                assertConfig(connection, value, "actual_user");
            }
        }
        try (var connection = database(); var insert = connection.prepareStatement(
                "INSERT INTO infra_config(id,config_key,value,creator,updater,deleted) VALUES (1,?,?,?,?,0)")) {
            String configured="{\"productFilters\":[{\"productTypeIn\":[\"FW\"]}]}";
            insert.setString(1, KEY); insert.setString(2, configured);
            insert.setString(3, "seed_tf040_address_filters"); insert.setString(4, "seed_tf040_address_filters"); insert.executeUpdate();
            migrate(connection);
            assertConfig(connection, configured, "seed_tf040_address_filters");
        }
    }

    private Connection database() throws Exception {
        var connection=DriverManager.getConnection("jdbc:h2:mem:optical_seed_"+ UUID.randomUUID()+";MODE=MySQL;NON_KEYWORDS=VALUE");
        try (var sql=connection.createStatement()) {
            sql.execute("CREATE TABLE infra_config(id BIGINT PRIMARY KEY,category VARCHAR(64),type INT,name VARCHAR(128),"
                    + "config_key VARCHAR(128),value VARCHAR(4096),visible BIT,remark VARCHAR(1024),"
                    + "creator VARCHAR(64),updater VARCHAR(64),deleted BIT,update_time TIMESTAMP)");
        }
        return connection;
    }
    private void migrate(Connection connection) throws Exception { script(connection, "V526__installation_address_optical_module_default.sql"); }
    private void script(Connection connection,String name) throws Exception {
        // H2 lacks MySQL bit literal syntax; normalize only that literal, retaining the actual migration DML.
        var sql=Files.readString(Path.of("..","sql","migrations",name)).replace("b'0'", "0");
        ScriptUtils.executeSqlScript(connection,new EncodedResource(
                new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
    }
    private void assertConfig(Connection connection,String value,String updater) throws Exception {
        try (var query=connection.prepareStatement("SELECT value,updater FROM infra_config WHERE config_key=? AND deleted=0")) {
            query.setString(1,KEY);
            try (var rows=query.executeQuery()) {
                assertTrue(rows.next()); assertEquals(value,rows.getString(1)); assertEquals(updater,rows.getString(2));
                assertFalse(rows.next());
            }
        }
    }
}
