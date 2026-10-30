package cn.iocoder.yudao.module.system.service.permission;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ServiceManagerPermissionSeedTest {
    @Test void sameTenantSupersetIsAdditiveIdempotentAndPreservesRevokedHistory() throws Exception {
        var path=Path.of("../sql/migrations/V517__service_manager_project_manager_permission_superset.sql");
        if (!Files.exists(path)) path=Path.of("sql/migrations/V517__service_manager_project_manager_permission_superset.sql");
        String seed=Files.readString(path).replace("b'0'","FALSE");
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:sm_superset_"+UUID.randomUUID()+";MODE=MySQL")) {
            var s=connection.createStatement();
            s.execute("CREATE TABLE system_role(id BIGINT,tenant_id BIGINT,code VARCHAR(32),status INT,deleted BOOLEAN)");
            s.execute("CREATE TABLE system_menu(id BIGINT,status INT,deleted BOOLEAN)");
            s.execute("CREATE TABLE system_role_menu(id BIGINT AUTO_INCREMENT PRIMARY KEY,role_id BIGINT,menu_id BIGINT,creator VARCHAR(64),updater VARCHAR(64),deleted BOOLEAN,tenant_id BIGINT)");
            s.execute("INSERT INTO system_role VALUES(1,1,'PROJECT_MANAGER',0,false),(2,1,'SERVICE_MANAGER',0,false),(3,2,'PROJECT_MANAGER',0,false),(4,2,'SERVICE_MANAGER',0,false),(5,1,'SERVICE_MANAGER_L1',0,false),(6,1,'SERVICE_MANAGER_L2',1,false),(7,1,'SERVICE_MANAGER',0,true),(8,1,'OTHER',0,false)");
            s.execute("INSERT INTO system_menu VALUES(10,0,false),(11,0,false),(12,1,false),(13,0,true),(14,0,false),(15,0,false),(16,0,false)");
            s.execute("INSERT INTO system_role_menu(role_id,menu_id,deleted,tenant_id) VALUES(1,10,false,1),(1,11,false,1),(1,12,false,1),(1,13,false,1),(1,16,true,1),(2,10,false,1),(2,11,true,1),(2,15,false,1),(3,14,false,2),(1,14,false,2)");
            s.execute(seed);
            assertEquals(Set.of(10L,11L,15L),menus(connection,2));
            assertEquals(Set.of(14L),menus(connection,4));
            assertEquals(Set.of(10L,11L),menus(connection,5));
            for(long role:List.of(6L,7L,8L))assertEquals(Set.of(),menus(connection,role));
            var before=count(connection,"SELECT COUNT(*) FROM system_role_menu");
            s.execute(seed);assertEquals(before,count(connection,"SELECT COUNT(*) FROM system_role_menu"));
            assertEquals(1,count(connection,"SELECT COUNT(*) FROM system_role_menu WHERE role_id=2 AND menu_id=11 AND deleted=TRUE"));
        }
    }
    Set<Long> menus(Connection c,long role)throws Exception {
        var ids=new HashSet<Long>();try(var p=c.prepareStatement("SELECT menu_id FROM system_role_menu WHERE role_id=? AND deleted=FALSE")){
            p.setLong(1,role);try(var r=p.executeQuery()){while(r.next())ids.add(r.getLong(1));}}return ids;
    }
    long count(Connection c,String sql)throws Exception {try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
}
