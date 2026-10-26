package cn.iocoder.yudao.module.pms.asset.dal;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import java.sql.Statement;
import java.util.List;

/** Existing owner tables used by the actual project list SQL, not a second list projection. */
final class ProjectDeviceListSqlFixture {
    static void load(Configuration config) throws Exception {
        for (var resource : List.of("mapper/installation/InstallationProjectReadSql.xml",
                "mapper/projectscope/ProjectShipmentContextSql.xml", "mapper/contract/ProjectShipmentSourceSql.xml",
                "mapper/device/ProjectDeviceListSourceSql.xml")) {
            try (var input = ProjectDeviceListSqlFixture.class.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) throw new IllegalStateException(resource);
                var xml = new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)
                        .replace(" COLLATE utf8mb4_unicode_ci", "")
                        .replace(" COLLATE utf8mb4_0900_ai_ci", ""); // MySQL collation verified with native fixtures.
                new XMLMapperBuilder(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)),config,resource,config.getSqlFragments()).parse();
            }
        }
    }

    static void schema(Statement sql) throws Exception {
        sql.execute("CREATE TABLE IF NOT EXISTS proj_project(id BIGINT,tenant_id BIGINT,deleted BOOLEAN,creator VARCHAR(64))");
        sql.execute("ALTER TABLE proj_project ADD COLUMN IF NOT EXISTS sales_type VARCHAR(20)");
        sql.execute("ALTER TABLE proj_project ADD COLUMN IF NOT EXISTS department_code VARCHAR(255)");
        sql.execute("CREATE TABLE IF NOT EXISTS com_project_contract_relation(tenant_id BIGINT,project_id BIGINT,contract_id BIGINT,status VARCHAR(20),effective_to TIMESTAMP,deleted BOOLEAN)");
        sql.execute("ALTER TABLE com_project_contract_relation ADD COLUMN IF NOT EXISTS contract_no VARCHAR(255)");
        sql.execute("CREATE TABLE IF NOT EXISTS com_shipment_contract_reference(tenant_id BIGINT,contract_no VARCHAR(255),company_code VARCHAR(64),deleted BOOLEAN)");
        sql.execute("ALTER TABLE com_shipment_contract_reference ADD COLUMN IF NOT EXISTS source_system VARCHAR(32)");
        sql.execute("ALTER TABLE com_shipment_contract_reference ADD COLUMN IF NOT EXISTS source_record_key VARCHAR(255)");
        sql.execute("CREATE TABLE com_shipment_package(tenant_id BIGINT,source_record_key VARCHAR(255),shipment_contract_source_key VARCHAR(255),receiver_name VARCHAR(255),express_no VARCHAR(255),carrier_name VARCHAR(255),shipment_time TIMESTAMP,source_system VARCHAR(32),deleted BOOLEAN)");
        sql.execute("CREATE TABLE ast_device_shipment(tenant_id BIGINT,device_sn VARCHAR(255),secondary_sn VARCHAR(255),product_code VARCHAR(255),secondary_item VARCHAR(255),profit_center VARCHAR(255),legacy_package_key VARCHAR(255),rma_no VARCHAR(255),source_system VARCHAR(32),deleted BOOLEAN)");
        sql.execute("CREATE TABLE ast_device_relationship(tenant_id BIGINT,source_device_sn VARCHAR(255),target_device_sn VARCHAR(255),contract_no VARCHAR(255),target_item VARCHAR(255),source_system VARCHAR(32),relationship_type VARCHAR(32),deleted BOOLEAN)");
        sql.execute("CREATE TABLE ast_product_official_info(tenant_id BIGINT,product_code VARCHAR(255),product_name VARCHAR(255),deleted BOOLEAN)");
        sql.execute("CREATE TABLE imp_eng_installation(id BIGINT,tenant_id BIGINT,project_id BIGINT,equipment_id BIGINT,install_location VARCHAR(1024),status INT,deleted BOOLEAN)");
    }

    static void mysqlSchema(Statement sql) throws Exception {
        var adapter = (Statement) java.lang.reflect.Proxy.newProxyInstance(Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("execute")) throw new UnsupportedOperationException(method.getName());
                    String ddl = ((String)args[0]).replace("CREATE TABLE IF NOT EXISTS", "CREATE TEMPORARY TABLE")
                            .replace("CREATE TABLE", "CREATE TEMPORARY TABLE")
                            .replace("ADD COLUMN IF NOT EXISTS", "ADD COLUMN");
                    if (ddl.startsWith("CREATE TEMPORARY TABLE")) {
                        boolean sourceTable=ddl.contains(" ast_") || ddl.contains(" imp_");
                        ddl += " DEFAULT CHARSET=utf8mb4 COLLATE=" + (sourceTable ? "utf8mb4_unicode_ci" : "utf8mb4_0900_ai_ci");
                    }
                    return sql.execute(ddl);
                });
        schema(adapter);
    }

    static void history(Statement sql,long id,long tenant,long project,long device,String sn,String physical) throws Exception {
        String contract="H"+project, pack="P"+id;
        sql.execute("INSERT INTO com_project_contract_relation(tenant_id,project_id,contract_no,status,deleted) SELECT "+tenant+","+project+",'"+contract+"','ACTIVE',false WHERE NOT EXISTS(SELECT 1 FROM com_project_contract_relation WHERE tenant_id="+tenant+" AND project_id="+project+" AND contract_no='"+contract+"')");
        sql.execute("INSERT INTO com_shipment_contract_reference(tenant_id,contract_no,source_system,source_record_key,deleted) VALUES("+tenant+",'"+contract+"','DPPMS','"+pack+"',false)");
        sql.execute("INSERT INTO com_shipment_package(tenant_id,source_record_key,shipment_contract_source_key,source_system,deleted) VALUES("+tenant+",'"+pack+"','"+pack+"','DPPMS',false)");
        sql.execute("INSERT INTO ast_device_shipment(tenant_id,device_sn,legacy_package_key,source_system,deleted) VALUES("+tenant+",'"+sn+"','"+pack+"','DPPMS',false)");
        sql.execute("INSERT INTO imp_eng_installation(id,tenant_id,project_id,equipment_id,status,deleted) VALUES("+id+","+tenant+","+project+","+device+",2,false)");
        if (physical!=null) sql.execute("INSERT INTO ast_device_relationship(tenant_id,source_device_sn,target_device_sn,contract_no,source_system,relationship_type,deleted) VALUES("+tenant+",'"+sn+"','"+physical+"','"+contract+"','DPPMS','EXTRA_SN',false)");
    }
}
