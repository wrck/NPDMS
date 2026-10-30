package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncProjectReferences;
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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native temporary-table fixture shared without inheriting test methods. */
@EnabledIfEnvironmentVariable(named="BASE_PRODUCT_TEMP_MYSQL_TEST",matches="true")
abstract class RemainingTaskMySqlSupport {
 java.sql.Connection connection;java.net.URLClassLoader loader;JdbcTemplate jdbc;SqlSessionTemplate session;
 Map<String,String> sourceCopies=new LinkedHashMap<>();
 TransactionTemplate transaction;SyncSource source;SyncIdentityStore identities;
 Map<String,SyncIdentityStore.Identity> saved;BaseSyncProjectReferences projects;
 final LocalDateTime at=LocalDateTime.of(2026,10,18,0,10);
 @BeforeEach void setup()throws Exception{
  loader=new java.net.URLClassLoader(new java.net.URL[]{Path.of(System.getenv("BASE_PRODUCT_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());
  var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();
  var properties=new Properties();properties.setProperty("user",System.getenv("BASE_PRODUCT_DB_USER"));properties.setProperty("password",System.getenv("BASE_PRODUCT_DB_PASSWORD"));
  String url=System.getenv("BASE_PRODUCT_DB_URL");
  connection=driver.connect(url==null?"jdbc:mysql://127.0.0.1:24306/npdms_reverify_20261014?useSSL=false&serverTimezone=Asia/Shanghai":url,properties);
  var data=new SingleConnectionDataSource(connection,true);jdbc=new JdbcTemplate(data);
  String ddl;try(var input=new ClassPathResource("basesync/remaining-first-wave-mysql.sql").getInputStream()){ddl=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
  for(var statement:ddl.split(";\\s*(?:\\r?\\n|$)"))if(!statement.isBlank()){
   if(!statement.stripLeading().startsWith("CREATE TEMPORARY TABLE "))throw new IllegalStateException("Only connection-local temporary DDL is permitted");jdbc.execute(statement);
  }
  try(var input=new ClassPathResource("basesync/remaining-history-mysql.sql").getInputStream()){
   for(var statement:new String(input.readAllBytes(),StandardCharsets.UTF_8).split(";\\s*(?:\\r?\\n|$)"))if(!statement.isBlank()){
    if(!statement.stripLeading().startsWith("CREATE TEMPORARY TABLE "))throw new IllegalStateException("Only temporary fixture DDL allowed");jdbc.execute(statement);
   }
  }
  try(var input=new ClassPathResource("basesync/account-foundation-mysql.sql").getInputStream()){
   for(var statement:new String(input.readAllBytes(),StandardCharsets.UTF_8).split(";\\s*(?:\\r?\\n|$)"))if(!statement.isBlank()){
    if(!statement.stripLeading().startsWith("CREATE TEMPORARY TABLE "))throw new IllegalStateException("Only temporary fixture DDL allowed");jdbc.execute(statement);
   }
  }
  var config=new Configuration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("isolated",new SpringManagedTransactionFactory(),data));
  for(var task:List.of("P01","P13","P14","P20"))for(var resource:List.of("mapper/basesync/"+task+"TargetMapper.xml","base-sync-source/"+task+"SourceMapper.xml"))
   try(var input=new ClassPathResource(resource).getInputStream()){
    String xml=new String(input.readAllBytes(),StandardCharsets.UTF_8);
    if(resource.startsWith("base-sync-source/"))for(var table:List.of("pm_project","pm_project_property_from_sms")){
     var matcher=java.util.regex.Pattern.compile("(?i)(FROM|JOIN)\\s+"+table+"\\b").matcher(xml);int occurrence=0;var output=new StringBuffer();
     while(matcher.find()){occurrence++;String selected=table;if(occurrence>1){selected=table+"_test_read_"+occurrence;sourceCopies.put(selected,table);}matcher.appendReplacement(output,matcher.group(1)+" "+selected);}matcher.appendTail(output);xml=output.toString();
    }
    new XMLMapperBuilder(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),config,resource,config.getSqlFragments()).parse();
   }
  for(var task:List.of("P04","P15","P16","P18","P19","P22","P31","P33","P35")){
   String resource="mapper/basesync/"+task+"TargetMapper.xml";
   try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
  }
  try(var input=new ClassPathResource("mapper/basesync/BaseSyncPublishedProjectsMapper.xml").getInputStream()){new XMLMapperBuilder(input,config,"independent-scope",config.getSqlFragments()).parse();}
  for(var copy:sourceCopies.entrySet())jdbc.execute("CREATE TEMPORARY TABLE `"+copy.getKey()+"` LIKE `"+copy.getValue()+"`");
  session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));transaction=new TransactionTemplate(new DataSourceTransactionManager(data));
  source=id->new SyncSource.Session(){public SqlSession sqlSession(){return session;}public <M>M mapper(Class<M> t){return session.getMapper(t);}public void close(){}};
  saved=new HashMap<>();identities=mock(SyncIdentityStore.class);
  when(identities.findByKeys(any())).thenAnswer(call->{var q=call.<SyncIdentityStore.Query>getArgument(0);var result=new HashMap<String,SyncIdentityStore.Identity>();for(var key:q.sourceKeys()){var old=saved.get(q.object()+":"+key);if(old!=null)result.put(key,old);}return result;});
  doAnswer(call->{for(var value:call.<List<SyncIdentityStore.Save>>getArgument(0))saved.put(value.object()+":"+value.sourceKey(),new SyncIdentityStore.Identity(value.sourceKey(),value.targetId(),value.fingerprint()));return null;}).when(identities).saveAll(any());
  projects=mock(BaseSyncProjectReferences.class);
  when(projects.resolve(any())).thenAnswer(call->{var q=call.<BaseSyncProjectReferences.Query>getArgument(0);var result=new HashMap<String,BaseSyncProjectReferences.Identity>();for(var key:q.canonicalKeys())result.put(key,new BaseSyncProjectReferences.Identity(key,1000L,1L,0L,"P-1","10",null,null,null,"ACTIVE",false));return result;});
 }
 @AfterEach void close()throws Exception{try{if(connection!=null)connection.close();}finally{if(loader!=null)loader.close();}}
 SyncContext<Void> context(boolean preview){return new SyncContext<>(1,2,3,preview,1,10000,at,source,identities,mock(SyncReferences.class),"Task",session,false,true);}
 SyncCounts run(AbstractSyncTask<?,?> task,boolean preview){
  for(var copy:sourceCopies.entrySet()){jdbc.update("DELETE FROM `"+copy.getKey()+"`");jdbc.update("INSERT INTO `"+copy.getKey()+"` SELECT * FROM `"+copy.getValue()+"`");}
  return transaction.execute(status->task.execute(context(preview)));
 }
 void project(){jdbc.update("INSERT INTO pm_project(projectId,projectType,projectCode,disabled) VALUES(1,'10','P-1',0)");}
 void order(String no){jdbc.update("INSERT INTO pm_order_data_from_erp(id,orderNumber,compCode,orderType,source,syncTime,contractNo,customerCode) VALUES(?,?,'01','0','SAP','2020-01-01','C','C000001')",no.equals("A")?1:2,no);}
 void line(String no,int quantity){jdbc.update("INSERT INTO pm_order_line_from_erp(id,orderNumber,lineNum,compCode,lineType,source,syncTime,itemCode,orderQuantity,openQuantity,warrantyMonth) VALUES(1,?,'1','01','0','SAP','2020-01-01','ITEM',?,1,12)",no,quantity);}
}
