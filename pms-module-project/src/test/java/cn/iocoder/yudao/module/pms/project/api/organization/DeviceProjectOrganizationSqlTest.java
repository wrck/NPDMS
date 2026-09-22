package cn.iocoder.yudao.module.pms.project.api.organization;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.DeviceOrganizationProjectQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DeviceProjectOrganizationSqlTest {
    @Test void keepsCompanyDepartmentPairAndFailsClosedForEmptyGrant() throws Exception {
        var config=new Configuration();
        try(var xml=getClass().getResourceAsStream("/mapper/projectmanual/DeviceProjectOrganizationMapper.xml")) {
            new XMLMapperBuilder(xml,config,"project-organization",config.getSqlFragments()).parse();
        }
        try(var db=DriverManager.getConnection("jdbc:h2:mem:project_org;MODE=MySQL")) {
            try(var sql=db.createStatement()) {
                sql.execute("CREATE TABLE proj_project(id BIGINT,tenant_id BIGINT,company_id BIGINT,department_id BIGINT,deleted INT)");
                sql.execute("INSERT INTO proj_project VALUES(1,1,1,11,0),(2,1,1,12,0),(3,1,2,11,0),(4,2,1,11,0)");
            }
            assertEquals(Set.of(1L,2L),ids(db,config,List.of(new DeviceOrganizationProjectQuery.Grant(1L,null))));
            assertEquals(Set.of(1L),ids(db,config,List.of(new DeviceOrganizationProjectQuery.Grant(1L,11L))));
            assertTrue(ids(db,config,List.of()).isEmpty());
        }
    }
    private Set<Long> ids(Connection db,Configuration config,List<DeviceOrganizationProjectQuery.Grant> grants) throws Exception {
        var params=Map.of("query",new DeviceOrganizationProjectQuery(1L,grants));
        var bound=config.getMappedStatement("cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.DeviceProjectOrganizationMapper.selectVisibleProjectIds").getBoundSql(params);
        try(var sql=db.prepareStatement(bound.getSql())) {
            int i=1;
            for(var mapping:bound.getParameterMappings()) {
                String name=mapping.getProperty();
                sql.setObject(i++,bound.hasAdditionalParameter(name)?bound.getAdditionalParameter(name):config.newMetaObject(params).getValue(name));
            }
            Set<Long> result=new HashSet<>();
            try(var rows=sql.executeQuery()) { while(rows.next()) result.add(rows.getLong(1)); }
            return result;
        }
    }
}
