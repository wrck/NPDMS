package cn.iocoder.yudao.module.pms.project.api.organization;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectOrganizationClearMapper;
import cn.iocoder.yudao.module.system.api.organization.OrganizationClearGuard;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ProjectOrganizationClearGuardTest {
    @Test void includesHistoryButNeverOtherTenantOrEmptyScope() throws Exception {
        var database=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            try(var connection=database.getConnection();var sql=connection.createStatement()) {
                for(String table:Set.of("proj_project","proj_project_company_department_relation","proj_project_member_assignment")) {
                    sql.execute("CREATE TABLE "+table+" (tenant_id BIGINT,company_id BIGINT,department_id BIGINT,deleted BOOLEAN)");
                    sql.execute("INSERT INTO "+table+" VALUES(1,10,20,TRUE),(2,10,20,FALSE)");
                }
            }
            var config=new Configuration(new Environment("clear-test",new JdbcTransactionFactory(),database));
            try(var xml=getClass().getClassLoader().getResourceAsStream("mapper/projectmanual/ProjectOrganizationClearMapper.xml")) {
                assertNotNull(xml);new XMLMapperBuilder(xml,config,"clear",config.getSqlFragments()).parse();
            }
            try(var session=new SqlSessionFactoryBuilder().build(config).openSession()) {
                var mapper=session.getMapper(ProjectOrganizationClearMapper.class);
                var scope=new OrganizationClearGuard.Scope(1L,Set.of(10L),Set.of());
                assertEquals(3,mapper.selectReferenceCount(scope));
                assertThrows(IllegalArgumentException.class,()->new ProjectOrganizationClearGuard(mapper).check(scope));
                assertEquals(0,mapper.selectReferenceCount(new OrganizationClearGuard.Scope(3L,Set.of(10L),Set.of(20L))));
                assertEquals(0,mapper.selectReferenceCount(new OrganizationClearGuard.Scope(1L,Set.of(),Set.of())));
            }
        } finally {database.shutdown();}
    }
}
