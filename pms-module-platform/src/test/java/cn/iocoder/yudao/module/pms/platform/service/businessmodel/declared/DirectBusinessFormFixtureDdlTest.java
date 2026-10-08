package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DirectBusinessFormFixtureDdlTest {
    @Test void authoritativeTablesRetainSemicolonsInsideCommentsAndExecuteAsCompleteStatements() throws Exception {
        String source=Files.readString(Path.of("../sql/migrations/V248__entity_capabilities_and_requirement_revision.sql"));
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:form_ddl_"+UUID.randomUUID()+";MODE=MySQL","sa","");var statement=connection.createStatement()) {
            for(String table:List.of("plt_entity_extension_definition","plt_entity_extension_value","plt_entity_form_binding")) {
                var ddl=DirectBusinessCrudMySqlTest.capabilityTableDdl(source,table);
                assertTrue(ddl.endsWith(");"));
                if(!table.endsWith("definition"))assertTrue(ddl.contains("'0=current; positive=Owner history record ID'"));
                // H2 does not parse MySQL bit literals; this smoke check changes that literal only.
                // The real MySQL fixture executes the extracted DDL unchanged.
                statement.execute(ddl.replace("b'0'","0"));
                try(var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
            }
        }
    }
}
