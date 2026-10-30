package cn.iocoder.yudao.module.system.service.permission;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectDeviceExportPermissionSeedTest {
    @Test void exportGrantIsIndependentTenantSafeIdempotentAndPreservesExistingHistory() throws Exception {
        var path=Path.of("../sql/migrations/V528__project_device_export_permission.sql");
        if(!Files.exists(path))path=Path.of("sql/migrations/V528__project_device_export_permission.sql");
        String seed=Files.readString(path).replace("b'0'","FALSE").replace("b'1'","TRUE");
        try(var c=DriverManager.getConnection("jdbc:h2:mem:export_seed_"+UUID.randomUUID()+";MODE=MySQL")) {
            var s=c.createStatement();
            s.execute("CREATE TABLE system_menu(id BIGINT PRIMARY KEY,name VARCHAR(100),permission VARCHAR(100),type INT,sort INT,parent_id BIGINT,path VARCHAR(100),icon VARCHAR(100),component VARCHAR(100),component_name VARCHAR(100),status INT,visible BOOLEAN,keep_alive BOOLEAN,always_show BOOLEAN,creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,deleted BOOLEAN)");
            s.execute("CREATE TABLE system_role(id BIGINT,tenant_id BIGINT,code VARCHAR(32),status INT,deleted BOOLEAN)");
            s.execute("CREATE TABLE system_role_menu(id BIGINT AUTO_INCREMENT PRIMARY KEY,role_id BIGINT,menu_id BIGINT,creator VARCHAR(64),updater VARCHAR(64),deleted BOOLEAN,tenant_id BIGINT)");
            s.execute("INSERT INTO system_role VALUES(1,1,'PROJECT_MANAGER',0,false),(2,1,'SERVICE_MANAGER',0,false),(3,2,'PROJECT_MANAGER',0,false),(4,1,'SERVICE_MANAGER_L1',0,false),(5,1,'SERVICE_MANAGER_L2',0,false),(6,1,'SERVICE_MANAGER',1,false),(7,1,'PROJECT_MANAGER',0,true),(8,1,'READONLY',0,false)");
            s.execute("INSERT INTO system_role_menu(role_id,menu_id,deleted,tenant_id,creator) VALUES(8,198771,false,1,'original'),(1,993039000001,true,1,'revoked')");
            s.execute(seed);
            assertEquals(5,count(c,"SELECT COUNT(*) FROM system_role_menu WHERE menu_id=993039000001 AND deleted=FALSE"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM system_role_menu WHERE menu_id=993039000001 AND deleted=FALSE AND role_id IN (6,7,8)"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM system_role_menu WHERE role_id=3 AND tenant_id=2 AND deleted=FALSE"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM system_role_menu WHERE creator='revoked' AND deleted=TRUE"));
            long before=count(c,"SELECT COUNT(*) FROM system_role_menu");s.execute(seed);
            assertEquals(before,count(c,"SELECT COUNT(*) FROM system_role_menu"));assertEquals(1,count(c,"SELECT COUNT(*) FROM system_menu"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM system_role_menu WHERE creator='original' AND menu_id=198771 AND deleted=FALSE"));
        }
    }
    private long count(Connection c,String sql)throws Exception {try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
}
