package cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate;

import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionManualArrivalUpdate;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

/** Apply the production migration and Mapper XML only to a connection-private temporary table. */
@EnabledIfSystemProperty(named="completion.arrival.mysql",matches="true")
class CompletionManualArrivalMySqlTest {
    @Test void nullableMigrationAndScopedDateUpdatesPreserveVersionStatusAndHistory() throws Exception {
        try(var connection=DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("DB_USER"),System.getenv("DB_PASSWORD"))){
            try(var sql=connection.createStatement()){
                sql.execute("CREATE TEMPORARY TABLE acc_completion_certificate(id BIGINT PRIMARY KEY,tenant_id BIGINT,version BIGINT,status INT,deleted BIT DEFAULT b'0')");
                var migration=Files.readString(Path.of("../sql/migrations/V514__completion_manual_arrival_date.sql"));
                sql.execute(migration.substring(migration.indexOf("ALTER TABLE")));
                sql.execute("INSERT INTO acc_completion_certificate(id,tenant_id,version,status) VALUES (1,7,2,0),(2,7,2,1),(3,8,2,0)");
            }
            var config=new MybatisConfiguration();
            config.setEnvironment(new Environment("manual-arrival",new JdbcTransactionFactory(),new SingleConnectionDataSource(connection,true)));
            try(var xml=getClass().getClassLoader().getResourceAsStream("mapper/completioncertificate/CompletionCertificateMapper.xml")){
                new XMLMapperBuilder(xml,config,"mapper/completioncertificate/CompletionCertificateMapper.xml",config.getSqlFragments()).parse();
            }
            try(var session=new MybatisSqlSessionFactoryBuilder().build(config).openSession(true)){
                var mapper=session.getMapper(CompletionCertificateMapper.class);
                var date=LocalDate.of(2026,10,11);
                assertEquals(1,mapper.updateManualArrivalDate(new CompletionManualArrivalUpdate(7L,1L,2L,date)));
                try(var sql=connection.createStatement();var rows=sql.executeQuery("SELECT manual_arrival_date,version FROM acc_completion_certificate WHERE id=1")){
                    assertTrue(rows.next());assertEquals(date,rows.getDate(1).toLocalDate());assertEquals(2L,rows.getLong(2));
                }
                assertEquals(0,mapper.updateManualArrivalDate(new CompletionManualArrivalUpdate(7L,1L,1L,date)));
                assertEquals(0,mapper.updateManualArrivalDate(new CompletionManualArrivalUpdate(7L,2L,2L,date)));
                assertEquals(0,mapper.updateManualArrivalDate(new CompletionManualArrivalUpdate(7L,3L,2L,date)));
                assertEquals(1,mapper.updateManualArrivalDate(new CompletionManualArrivalUpdate(7L,1L,2L,null)));
                try(var sql=connection.createStatement();var rows=sql.executeQuery("SELECT manual_arrival_date FROM acc_completion_certificate WHERE id=1")){
                    assertTrue(rows.next());assertNull(rows.getDate(1));
                }
            }
        }
    }
}
