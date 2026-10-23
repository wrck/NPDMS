package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncProjectReferences;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Connection-local TEMP fixtures only; the original self-join SQL is also parsed without rewriting. */
class PublishedTreeBatchMySqlTest extends RemainingTaskMySqlSupport {
 private static final String RESOURCE="mapper/basesync/BaseSyncProjectReferencesMapper.xml";
 private static final String STATEMENT=BaseSyncProjectReferences.ReferenceMapper.class.getName()+".selectCurrentTreeProjectIds";
 @BeforeEach void treeFixture()throws Exception{
  jdbc.execute("CREATE TEMPORARY TABLE proj_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,root_id BIGINT,deleted BOOLEAN)");
  jdbc.execute("CREATE TEMPORARY TABLE proj_project_tree_version(id BIGINT PRIMARY KEY,tenant_id BIGINT,root_project_id BIGINT,tree_version BIGINT,status VARCHAR(32),deleted BOOLEAN)");
  jdbc.execute("CREATE TEMPORARY TABLE proj_project_tree_version_newer_fixture LIKE proj_project_tree_version");
  jdbc.execute("CREATE TEMPORARY TABLE proj_project_tree_path(id BIGINT PRIMARY KEY,tenant_id BIGINT,root_project_id BIGINT,tree_version BIGINT,ancestor_project_id BIGINT,descendant_project_id BIGINT,distance INT,deleted BOOLEAN)");
  String xml;try(var in=new ClassPathResource(RESOURCE).getInputStream()){xml=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
  var original=new Configuration();parse(original,xml,"original-published-tree");
  var bound=original.getMappedStatement(STATEMENT).getBoundSql(Map.of("query",new BaseSyncProjectReferences.ProjectIdsQuery(1,List.of(10L,11L),true)));
  assertNotNull(com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(bound.getSql()));
  // MySQL cannot reopen the same TEMP table under two aliases. Only the newer alias
  // uses a connection-local clone; select() refreshes it from the original before each query.
  String fixture=xml.replace("LEFT JOIN proj_project_tree_version newer_version","LEFT JOIN proj_project_tree_version_newer_fixture newer_version");
  assertNotEquals(xml,fixture);parse(session.getConfiguration(),fixture,"fixture-published-tree");
 }
 private void parse(Configuration config,String xml,String name){new XMLMapperBuilder(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),config,name,config.getSqlFragments()).parse();}
 private record ProjectFixture(long id,long tenant,Long root,boolean deleted){}
 private void project(ProjectFixture row){jdbc.update("INSERT INTO proj_project VALUES(?,?,?,?)",row.id(),row.tenant(),row.root(),row.deleted());}
 private record VersionFixture(long id,long tenant,long root,long version,String status,boolean deleted){}
 private void version(VersionFixture row){jdbc.update("INSERT INTO proj_project_tree_version VALUES(?,?,?,?,?,?)",row.id(),row.tenant(),row.root(),row.version(),row.status(),row.deleted());}
 private record PathFixture(long id,long tenant,long root,long version,long ancestor,long child,boolean deleted){}
 private void path(PathFixture row){jdbc.update("INSERT INTO proj_project_tree_path VALUES(?,?,?,?,?,?,?,?)",row.id(),row.tenant(),row.root(),row.version(),row.ancestor(),row.child(),row.ancestor()==row.child()?0:1,row.deleted());}
 private List<Long> select(BaseSyncProjectReferences.ProjectIdsQuery query){
  jdbc.update("DELETE FROM proj_project_tree_version_newer_fixture");jdbc.update("INSERT INTO proj_project_tree_version_newer_fixture SELECT * FROM proj_project_tree_version");
  session.clearCache();return session.getMapper(BaseSyncProjectReferences.ReferenceMapper.class).selectCurrentTreeProjectIds(query);
 }
 @Test void rootAndChildRequireCurrentRootToProjectPaths(){
  project(new ProjectFixture(10,1,10L,false));project(new ProjectFixture(11,1,10L,false));version(new VersionFixture(1,1,10,1,"ACTIVE",false));
  path(new PathFixture(1,1,10,1,10,10,false));path(new PathFixture(2,1,10,1,10,11,false));
  assertEquals(Set.of(10L,11L),new HashSet<>(select(new BaseSyncProjectReferences.ProjectIdsQuery(1,List.of(10L,11L),true))));
  jdbc.update("DELETE FROM proj_project_tree_path WHERE id=2");path(new PathFixture(3,1,10,1,11,11,false));
  assertEquals(List.of(10L),select(new BaseSyncProjectReferences.ProjectIdsQuery(1,List.of(10L,11L),true)));
 }
 @Test void olderVersionPathCannotReplaceLatestActivePath(){
  project(new ProjectFixture(10,1,10L,false));version(new VersionFixture(1,1,10,1,"ACTIVE",false));version(new VersionFixture(2,1,10,2,"ACTIVE",false));path(new PathFixture(1,1,10,1,10,10,false));
  assertTrue(select(new BaseSyncProjectReferences.ProjectIdsQuery(1,List.of(10L),true)).isEmpty());path(new PathFixture(2,1,10,2,10,10,false));assertEquals(List.of(10L),select(new BaseSyncProjectReferences.ProjectIdsQuery(1,List.of(10L),true)));
 }
 @Test void inactiveAndDeletedNewerVersionsDoNotHideLatestActiveVersion(){
  project(new ProjectFixture(10,1,null,false));version(new VersionFixture(1,1,10,1,"ACTIVE",false));version(new VersionFixture(2,1,10,2,"FAILED",false));version(new VersionFixture(3,1,10,3,"ACTIVE",true));path(new PathFixture(1,1,10,1,10,10,false));
  assertEquals(List.of(10L),select(new BaseSyncProjectReferences.ProjectIdsQuery(1,List.of(10L),true)));
 }
 @Test void tenantDeletedPathMissingTreeAndEmptyIdsStayExcluded(){
  project(new ProjectFixture(10,1,10L,false));project(new ProjectFixture(20,2,20L,false));project(new ProjectFixture(30,1,30L,false));project(new ProjectFixture(40,1,40L,true));
  version(new VersionFixture(1,1,10,1,"ACTIVE",false));version(new VersionFixture(2,2,20,1,"ACTIVE",false));version(new VersionFixture(4,1,40,1,"ACTIVE",false));
  path(new PathFixture(1,1,10,1,10,10,true));path(new PathFixture(2,2,20,1,20,20,false));path(new PathFixture(3,1,30,1,30,30,false));path(new PathFixture(4,1,40,1,40,40,false));
  assertTrue(select(new BaseSyncProjectReferences.ProjectIdsQuery(1,List.of(10L,20L,30L,40L),true)).isEmpty());
  assertTrue(select(new BaseSyncProjectReferences.ProjectIdsQuery(1,List.of(),true)).isEmpty());
  jdbc.update("UPDATE proj_project_tree_path SET deleted=FALSE WHERE id=1");
  assertEquals(List.of(10L),select(new BaseSyncProjectReferences.ProjectIdsQuery(1,List.of(10L,20L),true)));
  jdbc.update("UPDATE proj_project_tree_path SET tenant_id=2 WHERE id=1");assertTrue(select(new BaseSyncProjectReferences.ProjectIdsQuery(1,List.of(10L),true)).isEmpty());
 }
}
