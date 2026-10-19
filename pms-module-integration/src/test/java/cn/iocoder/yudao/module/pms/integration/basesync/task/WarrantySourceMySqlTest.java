package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.AbstractSyncTask;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The unchanged source XML must work with only its raw table and preserve every source row. */
@EnabledIfEnvironmentVariable(named="BASE_PRODUCT_TEMP_MYSQL_TEST",matches="true")
class WarrantySourceMySqlTest {
    @Test void rawHistoryNeedsNoDeviceShipmentProjectOrContractAndRetainsNullAndEmptySerials()throws Exception{
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{Path.of(System.getenv("BASE_PRODUCT_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader())){
            var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();
            var properties=new Properties();properties.setProperty("user",System.getenv("BASE_PRODUCT_DB_USER"));properties.setProperty("password",System.getenv("BASE_PRODUCT_DB_PASSWORD"));
            try(var connection=driver.connect("jdbc:mysql://127.0.0.1:24306/npdms_reverify_20261014?useSSL=false&serverTimezone=Asia/Shanghai",properties)){
                var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
                jdbc.execute("CREATE TEMPORARY TABLE fb_service(id VARCHAR(64),con_xb VARCHAR(25),barcode VARCHAR(50),grade VARCHAR(15),begin_date DATETIME,end_date DATETIME,warranty CHAR(1),remark TEXT,isyb INT,state INT)");
                jdbc.update("INSERT INTO fb_service VALUES('one','C','SN','G','2020-01-01','2021-01-01','1','original',1,1),('two',NULL,'SN',NULL,NULL,NULL,NULL,NULL,NULL,NULL),('three',NULL,'',NULL,NULL,NULL,'0',NULL,0,NULL),('four',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL)");
                var config=new Configuration();config.setMapUnderscoreToCamelCase(true);config.setDefaultFetchSize(Integer.MIN_VALUE);config.setLocalCacheScope(LocalCacheScope.STATEMENT);
                String resource="base-sync-source/P25SourceMapper.xml";
                try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
                var rows=new ArrayList<P25WarrantySyncTask.WarrantySyncSource>();
                try(var session=new SqlSessionFactoryBuilder().build(config).openSession(connection)){
                    var mapper=session.getMapper(P25WarrantySyncTask.SourceMapper.class);
                    var query=new AbstractSyncTask.SourceQuery<Void>(100,1,true,null);
                    assertTrue(mapper.selectDuplicateIdentity(query).isEmpty());
                    mapper.selectWarranty(query,result->rows.add(result.getResultObject()));
                    assertEquals(4,rows.size());assertEquals(2,rows.stream().filter(row->"SN".equals(row.getDeviceSn())).count());
                    assertEquals(1,rows.stream().filter(row->row.getDeviceSn()==null).count());assertEquals(1,rows.stream().filter(row->"".equals(row.getDeviceSn())).count());
                    var first=rows.stream().filter(row->"one".equals(row.getSourceId())).findFirst().orElseThrow();
                    var target=P25WarrantySyncTask.convert(first);assertEquals("C",target.getWarrantyContractNo());assertEquals("original",target.getRemark());assertEquals(12,target.getWarrantyMonths());
                    assertEquals(0,P25WarrantySyncTask.convert(rows.stream().filter(row->"three".equals(row.getSourceId())).findFirst().orElseThrow()).getWarrantyMonths());
                    jdbc.update("INSERT INTO fb_service VALUES('one','C','SN','G','2020-01-01','2021-01-01','1','original',1,1)");
                    assertEquals(List.of(1),mapper.selectDuplicateIdentity(query));
                }
            }
        }
    }
}
