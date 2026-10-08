package cn.iocoder.yudao.module.pms.platform.service.businessmodel;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DefaultDeliveryListQuery;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Shared Mapper XML with MySQL bit literals and variable-length binary casts translated for H2; not MySQL or file-service acceptance. */
class DefaultDeliveryHistoryQueryTest {
    @Test void currentAndHistoryUseTheSameRowsWithDifferentValidityFiltersAndExactScope() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:delivery_history_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE plt_delivery_material(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,entity_id BIGINT,business_type_code VARCHAR(64),type_code VARCHAR(64),material_kind VARCHAR(20),source_kind VARCHAR(20),status VARCHAR(20),deleted BOOLEAN,create_time TIMESTAMP,file_reference_id BIGINT)");
        jdbc.execute("CREATE TABLE plt_file_reference(id BIGINT PRIMARY KEY,tenant_id BIGINT,owner_context VARCHAR(20),object_type VARCHAR(64))");
        jdbc.update("INSERT INTO plt_file_reference VALUES(1,7,'PLT','DEFAULT_BUSINESS_DELIVERY'),(2,8,'PLT','DEFAULT_BUSINESS_DELIVERY')");
        jdbc.update("INSERT INTO plt_delivery_material VALUES(1,7,99,11,'NOTE','REPORT','FILE','UPLOAD','ACTIVE',FALSE,'2026-10-01',1),(2,7,99,11,'NOTE','REPORT','FILE','UPLOAD','WITHDRAWN',TRUE,'2026-10-02',1),(3,7,99,12,'NOTE','REPORT','FILE','UPLOAD','ACTIVE',FALSE,'2026-10-03',1),(4,8,99,11,'NOTE','REPORT','FILE','UPLOAD','ACTIVE',FALSE,'2026-10-04',2),(5,7,99,11,'NOTE_OTHER','REPORT','FILE','UPLOAD','ACTIVE',FALSE,'2026-10-05',1),(6,7,99,11,'note','REPORT','FILE','UPLOAD','ACTIVE',FALSE,'2026-10-06',1)");
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);factory.setConfiguration(configuration);
        try(var input=new ClassPathResource("mapper/delivery/DeliveryMaterialMapper.xml").getInputStream()){
            var xml=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("b'0'","FALSE").replace("b'1'","TRUE").replace(" AS BINARY)"," AS VARBINARY)");
            factory.setMapperLocations(new org.springframework.core.io.ByteArrayResource(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        try(var session=factory.getObject().openSession()){
            var mapper=session.getMapper(DeliveryMaterialMapper.class);var query=new DefaultDeliveryListQuery();query.setTenantId(7L);query.setProjectId(99L);query.setEntityId(11L);query.setBusinessType("NOTE");query.setDeliverableType("REPORT");query.setReadableBusinessTypes(Set.of("NOTE"));query.setPageNo(1);query.setPageSize(20);
            assertEquals(List.of(1L),mapper.selectDefaultDeliveryPage(query).getList().stream().map(row->row.getId()).toList());
            query.setIncludeInactive(true);assertEquals(List.of(2L,1L),mapper.selectDefaultDeliveryPage(query).getList().stream().map(row->row.getId()).toList());
            assertEquals(2,mapper.selectDefaultDeliveryPage(query).getTotal());
            query.setReadableBusinessTypes(Set.of());assertTrue(mapper.selectDefaultDeliveryPage(query).getList().isEmpty());
            assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM plt_delivery_material",Integer.class));
        }
    }
}
