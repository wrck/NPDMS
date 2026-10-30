package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;

/** Five connection-local tables for the eight seeded P04 tests; no source-directory fixture. */
abstract class P04AccountMySqlSupport {
 java.sql.Connection connection;java.net.URLClassLoader loader;JdbcTemplate jdbc;SqlSessionTemplate session;
 TransactionTemplate transaction;SyncSource source;SyncIdentityStore identities;
 Map<String,SyncIdentityStore.Identity> saved;
 final LocalDateTime at=LocalDateTime.of(2026,10,18,0,10);

 @BeforeEach void setup()throws Exception{
  loader=new java.net.URLClassLoader(new java.net.URL[]{Path.of(System.getenv("BASE_PRODUCT_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());
  var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();
  var properties=new Properties();properties.setProperty("user",System.getenv("BASE_PRODUCT_DB_USER"));properties.setProperty("password",System.getenv("BASE_PRODUCT_DB_PASSWORD"));
  String url=System.getenv("BASE_PRODUCT_DB_URL");
  connection=driver.connect(url==null?"jdbc:mysql://127.0.0.1:24306/npdms_reverify_20261014?useSSL=false&serverTimezone=Asia/Shanghai":url,properties);
  var data=new SingleConnectionDataSource(connection,true);jdbc=new JdbcTemplate(data);
  try(var input=new ClassPathResource("basesync/p04-account-mysql.sql").getInputStream()){
   for(var statement:new String(input.readAllBytes(),StandardCharsets.UTF_8).split(";\\s*(?:\\r?\\n|$)"))if(!statement.isBlank()){
    if(!statement.stripLeading().startsWith("CREATE TEMPORARY TABLE "))throw new IllegalStateException("Only connection-local temporary DDL is permitted");
    jdbc.execute(statement);
   }
  }
  var config=new Configuration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("isolated",new SpringManagedTransactionFactory(),data));
  String resource="mapper/basesync/P04TargetMapper.xml";
  try(var input=new ClassPathResource(resource).getInputStream()){
   new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();
  }
  session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));transaction=new TransactionTemplate(new DataSourceTransactionManager(data));
  source=id->new SyncSource.Session(){public SqlSession sqlSession(){return session;}public <M>M mapper(Class<M> t){return session.getMapper(t);}public void close(){}};
  saved=new HashMap<>();identities=mock(SyncIdentityStore.class);
  when(identities.findByKeys(any())).thenAnswer(call->{var q=call.<SyncIdentityStore.Query>getArgument(0);var result=new HashMap<String,SyncIdentityStore.Identity>();for(var key:q.sourceKeys()){var old=saved.get(q.object()+":"+key);if(old!=null)result.put(key,old);}return result;});
  doAnswer(call->{for(var value:call.<List<SyncIdentityStore.Save>>getArgument(0))saved.put(value.object()+":"+value.sourceKey(),new SyncIdentityStore.Identity(value.sourceKey(),value.targetId(),value.fingerprint()));return null;}).when(identities).saveAll(any());
 }
 @AfterEach void close()throws Exception{try{if(connection!=null)connection.close();}finally{if(loader!=null)loader.close();}}
 SyncContext<Void> context(boolean preview){return new SyncContext<>(1,2,3,preview,1,10000,at,source,identities,mock(SyncReferences.class),"Task",session,false,true);}
 SyncCounts run(AbstractSyncTask<?,?> task,boolean preview){return transaction.execute(status->task.execute(context(preview)));}
}
