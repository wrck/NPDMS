package cn.iocoder.yudao.module.pms.platform.service.delivery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryTypeMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryTypeCodeLockQuery;
import com.baomidou.mybatisplus.core.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class DeliveryCatalogLockTest {
 @Test void catalogMutationWaitsUntilCompletionTransactionCommits() throws Exception {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:delivery_lock_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  try(var c=ds.getConnection();var sql=c.createStatement()) {
   sql.execute("CREATE TABLE plt_delivery_type(id BIGINT PRIMARY KEY,type_code VARCHAR(100),name VARCHAR(100),category VARCHAR(100),allowed_media_json VARCHAR(500),max_size_bytes BIGINT,enabled BOOLEAN,version BIGINT,tenant_id BIGINT,deleted BIT)");
   sql.execute("INSERT INTO plt_delivery_type VALUES(1,'DOC','doc','DOC','[]',1000,TRUE,1,1,0)");
  }
  var cfg=new MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);
  cfg.setEnvironment(new Environment("isolated-delivery",new JdbcTransactionFactory(),ds));
  cfg.addMapper(DeliveryTypeMapper.class);
  String resource="mapper/delivery/DeliveryTypeMapper.xml";
  try(var in=getClass().getClassLoader().getResourceAsStream(resource)) {new XMLMapperBuilder(in,cfg,resource,cfg.getSqlFragments()).parse();}
  var factory=new MybatisSqlSessionFactoryBuilder().build(cfg);
  var executor=Executors.newSingleThreadExecutor();
  try(var session=factory.openSession(false)) {
   var mapper=session.getMapper(DeliveryTypeMapper.class);
   var row=mapper.selectCodeForUpdate(new DeliveryTypeCodeLockQuery(1L,"DOC"));
   assertTrue(row.getEnabled());assertEquals(1000L,row.getMaxSizeBytes());
   assertNull(mapper.selectCodeForUpdate(new DeliveryTypeCodeLockQuery(2L,"DOC")));
   var started=new CountDownLatch(1);
   var change=executor.submit(()->{try(var c=ds.getConnection();var sql=c.createStatement()) {started.countDown();return sql.executeUpdate("UPDATE plt_delivery_type SET enabled=FALSE,max_size_bytes=10,version=2 WHERE id=1");}});
   assertTrue(started.await(2,TimeUnit.SECONDS));
   assertThrows(TimeoutException.class,()->change.get(200,TimeUnit.MILLISECONDS));
   session.commit(true);assertEquals(1,change.get(2,TimeUnit.SECONDS));
   session.clearCache();
   var changed=mapper.selectCodeForUpdate(new DeliveryTypeCodeLockQuery(1L,"DOC"));
   assertFalse(changed.getEnabled());assertEquals(10L,changed.getMaxSizeBytes());assertEquals(Integer.valueOf(2),changed.getVersion());
   session.commit(true);
  } finally {executor.shutdownNow();}
 }
}
