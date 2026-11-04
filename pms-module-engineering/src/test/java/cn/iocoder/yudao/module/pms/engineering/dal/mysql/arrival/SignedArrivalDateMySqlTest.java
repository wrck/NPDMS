package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival;

import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query.LatestSignedArrivalQuery;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

/** Production XML on real MySQL; connection-private temporary table shadows the business table, no persistent writes. */
@EnabledIfSystemProperty(named="completion.arrival.mysql",matches="true")
class SignedArrivalDateMySqlTest {
    @Test void latestMeansArrivalDateAndExcludesUnsignedDeletedForeignTenantAndForeignProject() throws Exception {
        try(var connection=DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("DB_USER"),System.getenv("DB_PASSWORD"))){
            try(var sql=connection.createStatement()){
                sql.execute("CREATE TEMPORARY TABLE imp_eng_arrival(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,status INT,arrival_time DATETIME,deleted BIT DEFAULT b'0')");
                sql.execute("INSERT INTO imp_eng_arrival VALUES (1,7,9,1,'2026-10-01 23:00:00',b'0'),(2,7,9,1,'2026-10-20 01:00:00',b'0'),"
                        +"(3,7,9,0,'2026-10-30 01:00:00',b'0'),(4,7,9,2,'2026-10-30 01:00:00',b'0'),"
                        +"(5,7,9,1,'2026-10-30 01:00:00',b'1'),(6,8,9,1,'2026-10-30 01:00:00',b'0'),"
                        +"(7,7,10,1,'2026-10-30 01:00:00',b'0'),(8,7,9,1,NULL,b'0')");
            }
            var config=new MybatisConfiguration();
            config.setEnvironment(new Environment("signed-arrival-date-test",new JdbcTransactionFactory(),new SingleConnectionDataSource(connection,true)));
            try(var xml=getClass().getClassLoader().getResourceAsStream("mapper/arrival/ArrivalMapper.xml")){
                new XMLMapperBuilder(xml,config,"mapper/arrival/ArrivalMapper.xml",config.getSqlFragments()).parse();
            }
            try(var session=new MybatisSqlSessionFactoryBuilder().build(config).openSession(true)){
                var mapper=session.getMapper(ArrivalMapper.class);
                assertEquals(LocalDateTime.of(2026,10,20,1,0),mapper.selectLatestSignedArrivalTime(new LatestSignedArrivalQuery(7L,9L)));
                assertNull(mapper.selectLatestSignedArrivalTime(new LatestSignedArrivalQuery(7L,99L)));
            }
        }
    }
}
