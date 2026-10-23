package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.module.pms.integration.basesync.task.P30DeviceBaseSyncTask;
import cn.iocoder.yudao.module.pms.integration.basesync.task.P33InstallationSyncTask;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Production Mapper SELECTs against existing devices only; no schema or data writes. */
class FormalSnReadOnlyMySqlTest {
 private Connection connection;private URLClassLoader loader;private Configuration configuration;
 private long deviceId;private String sn;
 @BeforeEach void connect()throws Exception{
  assumeTrue("true".equals(System.getenv("NPDMS_BINDING_ROLLBACK_TEST")));
  String url=System.getenv("NPDMS_TEST_MYSQL_URL");
  assertNotNull(url);assertTrue(url.startsWith("jdbc:mysql://172.17.90.178:13306/npdms?"));
  var properties=new Properties();properties.setProperty("user",System.getenv("NPDMS_TEST_MYSQL_USERNAME"));properties.setProperty("password",System.getenv("NPDMS_TEST_MYSQL_PASSWORD"));
  properties.setProperty("characterEncoding","UTF-8");properties.setProperty("connectionCollation","utf8mb4_unicode_ci");
  loader=new URLClassLoader(new java.net.URL[]{Path.of(System.getenv("BASE_PRODUCT_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());
  var driver=(Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();connection=driver.connect(url,properties);connection.setAutoCommit(false);
  configuration=new Configuration();
  for(var name:List.of("P30TargetMapper","P33TargetMapper")){
   var resource="mapper/basesync/"+name+".xml";try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();}
  }
  try(var statement=connection.createStatement();var rs=statement.executeQuery("SELECT id,sn FROM ast_device WHERE tenant_id=1 AND deleted=FALSE AND sn REGEXP '[A-Za-z]' ORDER BY id LIMIT 1")){
   assertTrue(rs.next());deviceId=rs.getLong(1);sn=rs.getString(2);
  }
 }
 @AfterEach void close()throws Exception{try{if(connection!=null){connection.rollback();connection.close();}}finally{if(loader!=null)loader.close();}}
 private List<Map<String,Object>> select(boolean p30,long tenant,List<String> serials,boolean preview,boolean explain)throws Exception{
  var statement=configuration.getMappedStatement(p30?P30DeviceBaseSyncTask.TargetMapper.class.getName()+".selectExisting":P33InstallationSyncTask.TargetMapper.class.getName()+".selectDevices");
  Object query=p30?new P30DeviceBaseSyncTask.TargetMapper.SerialsQuery(tenant,serials,preview):new P33InstallationSyncTask.DeviceQuery(tenant,serials,preview);
  var parameters=Map.of("query",query);var bound=statement.getBoundSql(parameters);
  assertNotNull(com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(bound.getSql()));
  var result=new ArrayList<Map<String,Object>>();
  try(var sql=connection.prepareStatement((explain?"EXPLAIN ":"")+bound.getSql())){
   new DefaultParameterHandler(statement,parameters,bound).setParameters(sql);
   try(var rs=sql.executeQuery()){while(rs.next()){
    var row=new HashMap<String,Object>();
    if(explain){row.put("table",rs.getString("table"));row.put("key",rs.getString("key"));}
    else{row.put("id",rs.getLong("id"));row.put("requested_sn",rs.getString("requested_sn"));}
    result.add(row);
   }}
  }return result;
 }
 @Test void bothLookupsRetainRawCaseAndTrailingSpaceUnderFormalSnIdentity()throws Exception{
  var serials=List.of(sn,sn+" ",sn.toLowerCase(Locale.ROOT)+" ",sn.toUpperCase(Locale.ROOT));
  for(boolean p30:List.of(true,false)){
   var rows=select(p30,1,serials,true,false);assertEquals(serials.size(),rows.size());
   assertEquals(new HashSet<>(serials),new HashSet<>(rows.stream().map(r->r.get("requested_sn")).toList()));
   assertTrue(rows.stream().allMatch(r->Objects.equals(deviceId,r.get("id"))));
  }
 }
 @Test void emptyNullAndOtherTenantNeverBroadenEitherLookup()throws Exception{
  for(boolean p30:List.of(true,false)){
   assertTrue(select(p30,1,List.of(),true,false).isEmpty());assertTrue(select(p30,1,null,true,false).isEmpty());
   assertTrue(select(p30,Long.MAX_VALUE,List.of(sn),true,false).isEmpty());
  }
 }
 @Test void nonPreviewLookupStillUsesNativeRowLocks()throws Exception{
  for(boolean p30:List.of(true,false)){var rows=select(p30,1,List.of(sn+" "),false,false);assertEquals(1,rows.size());assertEquals(deviceId,rows.getFirst().get("id"));}
 }
 @Test void maximumRunBatchAndQuotedInputUseTheFormalSnIndex()throws Exception{
  var serials=new ArrayList<String>();for(int i=0;i<1998;i++)serials.add("readonly-missing-"+i);
  serials.add("readonly-quoted-'\"\\-"+UUID.randomUUID());serials.add(sn+" ");
  for(boolean p30:List.of(true,false)){
   var rows=select(p30,1,serials,true,false);assertEquals(1,rows.size());assertEquals(deviceId,rows.getFirst().get("id"));
   var plan=select(p30,1,serials,true,true);assertTrue(plan.stream().anyMatch(r->"d".equals(r.get("table"))&&"uk_ast_device_tenant_sn".equals(r.get("key"))));
  }
 }
 @Test void sourceRankingKeepsWholePartitionQualificationAndLatestRawShipment()throws Exception{
  var statement=configuration.getMappedStatement(P30DeviceBaseSyncTask.TargetMapper.class.getName()+".selectDeviceBase");
  var parameters=Map.of("tenantId",1L,"limit",100);var bound=statement.getBoundSql(parameters);
  assertNotNull(com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(bound.getSql()));
  String fixture="""
   WITH ast_device_shipment AS (
    SELECT id,tenant_id,raw_sn COLLATE utf8mb4_unicode_ci device_sn,source_system,shipment_time,deleted,
           NULL source_key,NULL contract_no,NULL package_no,NULL product_code
    FROM (VALUES
     ROW(1,1,'mixed','DPPMS',1,0),ROW(2,1,'mixed','OTHER',2,0),
     ROW(3,1,'other','OTHER',1,0),
     ROW(4,1,'tie','DPPMS',3,0),ROW(5,1,'tie','OTHER',3,0),
     ROW(6,1,'Case','DPPMS',1,0),ROW(7,1,'case','OTHER',2,0),
     ROW(8,1,'pad','DPPMS',1,0),ROW(9,1,'pad ','OTHER',2,0),
     ROW(10,2,'mixed','DPPMS',99,0),ROW(11,1,'mixed','DPPMS',100,1),
     ROW(12,1,'null-time','DPPMS',NULL,0),ROW(13,1,'null-time','OTHER',5,0)
    ) input_rows(id,tenant_id,raw_sn,source_system,shipment_time,deleted)
   )
   """;
  var actual=new HashMap<Long,String>();
  try(var sql=connection.prepareStatement(fixture+bound.getSql())){
   new DefaultParameterHandler(statement,parameters,bound).setParameters(sql);
   try(var rs=sql.executeQuery()){while(rs.next())actual.put(rs.getLong("id"),rs.getString("device_sn"));}
  }
  assertEquals(Map.of(2L,"mixed",5L,"tie",7L,"case",9L,"pad ",13L,"null-time"),actual);
 }
 @Test void installationQueriesDoNotRetainPriorBatchResultsInTheSessionCache(){
  var executor=configuration.newExecutor(new org.apache.ibatis.transaction.jdbc.JdbcTransaction(connection),org.apache.ibatis.session.ExecutorType.SIMPLE);
  var sqlSession=new org.apache.ibatis.session.defaults.DefaultSqlSession(configuration,executor,false);
  String statement=P33InstallationSyncTask.TargetMapper.class.getName()+".selectExisting";
  var firstQuery=Map.of("query",new P33InstallationSyncTask.TargetQuery(1,List.of(),List.of(),1,true));
  var secondQuery=Map.of("query",new P33InstallationSyncTask.TargetQuery(1,List.of(),List.of(),2,true));
  // The fixture owns rollback/connection closure; this session must not close its shared connection.
  List<P33InstallationSyncTask.Row> first=sqlSession.selectList(statement,firstQuery);
  List<P33InstallationSyncTask.Row> second=sqlSession.selectList(statement,secondQuery);
  List<P33InstallationSyncTask.Row> third=sqlSession.selectList(statement,firstQuery);
  assertTrue(first.isEmpty());assertTrue(second.isEmpty());assertTrue(third.isEmpty());
  assertNotSame(first,third,"P33 prior batch list must not remain in the MyBatis session cache after another batch query");
 }
 @Test void sourceWindowsShareOneSortWithoutExecutingTheFullSourceSelect()throws Exception{
  var statement=configuration.getMappedStatement(P30DeviceBaseSyncTask.TargetMapper.class.getName()+".selectDeviceBase");
  var parameters=Map.of("tenantId",1L,"limit",5000001);var bound=statement.getBoundSql(parameters);
  try(var sql=connection.prepareStatement("EXPLAIN FORMAT=JSON "+bound.getSql())){
   new DefaultParameterHandler(statement,parameters,bound).setParameters(sql);
   try(var rs=sql.executeQuery()){
    assertTrue(rs.next());var plan=new com.fasterxml.jackson.databind.ObjectMapper().readTree(rs.getString(1));
    int windows=0,sorts=0;
    for(var group:plan.findValues("windows")){for(var window:group){windows++;if(window.path("using_filesort").asBoolean())sorts++;}}
    assertEquals(2,windows);assertEquals(1,sorts);
   }
  }
 }
}
