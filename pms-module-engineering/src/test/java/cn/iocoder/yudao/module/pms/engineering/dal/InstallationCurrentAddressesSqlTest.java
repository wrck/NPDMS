package cn.iocoder.yudao.module.pms.engineering.dal;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationCurrentAddressesQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class InstallationCurrentAddressesSqlTest {
    @Test void excludesPendingHistoryOtherTenantsOtherProjectsAndUnrequestedDevices() throws Exception {
        Configuration config = new Configuration();
        try (var xml = getClass().getResourceAsStream("/mapper/installation/InstallationMapper.xml")) {
            new XMLMapperBuilder(xml, config, "installation", config.getSqlFragments()).parse();
        }
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:current_addresses;MODE=MySQL")) {
            var sql = connection.createStatement();
            sql.execute("CREATE TABLE imp_eng_installation(id BIGINT, tenant_id BIGINT, project_id BIGINT,equipment_id BIGINT,install_location VARCHAR(100),status INT,effective_from TIMESTAMP,effective_to TIMESTAMP,deleted BOOLEAN)");
            sql.execute("INSERT INTO imp_eng_installation VALUES(1,1,9,11,'current',2,CURRENT_TIMESTAMP,NULL,false),(2,1,9,11,'expired',2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,false),(3,1,9,12,'pending',0,NULL,NULL,false),(4,1,10,11,'other project',2,CURRENT_TIMESTAMP,NULL,false),(5,2,9,11,'other tenant',2,CURRENT_TIMESTAMP,NULL,false),(6,1,9,99,'not requested',2,CURRENT_TIMESTAMP,NULL,false)");
            assertEquals(List.of("current"), read(connection,config,new InstallationCurrentAddressesQuery(1L,9L,List.of(11L,12L))));
            assertEquals(List.of(), read(connection,config,new InstallationCurrentAddressesQuery(1L,9L,List.of())));
            assertEquals(List.of("other tenant"), read(connection,config,new InstallationCurrentAddressesQuery(2L,9L,List.of(11L))));
        }
    }
    private List<String> read(Connection connection, Configuration config, InstallationCurrentAddressesQuery query) throws Exception {
        var params=Map.of("query",query);
        var bound=config.getMappedStatement("cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper.selectCurrentAddresses").getBoundSql(params);
        try(var sql=connection.prepareStatement(bound.getSql().replace("deleted = 0","deleted = FALSE"))) {
            int index=1;
            for(var mapping:bound.getParameterMappings()) {
                var name=mapping.getProperty();sql.setObject(index++,bound.hasAdditionalParameter(name)?bound.getAdditionalParameter(name):config.newMetaObject(params).getValue(name));
            }
            var result=new ArrayList<String>();try(var rows=sql.executeQuery()){while(rows.next())result.add(rows.getString("install_location"));}return result;
        }
    }
}
