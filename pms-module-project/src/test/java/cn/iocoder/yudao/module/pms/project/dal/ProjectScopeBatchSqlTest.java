package cn.iocoder.yudao.module.pms.project.dal;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectScopeBatchSqlTest {
    @Test void batchQueriesKeepLatestActiveTenantVersionAndExactRootVersionPairsAndFailClosed() throws Exception {
        var config=new Configuration();
        for(String mapper:List.of("ProjectTreeVersionMapper","ProjectTreePathMapper")) {
            try(var xml=getClass().getResourceAsStream("/mapper/projecttree/"+mapper+".xml")) {
                new XMLMapperBuilder(xml,config,mapper,config.getSqlFragments()).parse();
            }
        }
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:scope_batch;MODE=MySQL")) {
            var sql=connection.createStatement();
            sql.execute("CREATE TABLE proj_project_tree_version(id BIGINT,root_project_id BIGINT,tree_version BIGINT,status VARCHAR(20),tenant_id BIGINT,deleted BOOLEAN,change_batch_id VARCHAR(50),node_count INT,path_count INT,activated_at TIMESTAMP,failed_reason VARCHAR(50),version INT,creator VARCHAR(30),create_time TIMESTAMP,updater VARCHAR(30),update_time TIMESTAMP)");
            sql.execute("INSERT INTO proj_project_tree_version(id,root_project_id,tree_version,status,tenant_id,deleted) VALUES(1,1,7,'ACTIVE',0,false),(2,1,8,'ACTIVE',0,false),(3,1,8,'ACTIVE',0,false),(4,1,9,'FAILED',0,false),(5,1,10,'ACTIVE',0,true),(6,1,11,'ACTIVE',1,false),(7,10,4,'ACTIVE',0,false),(8,20,1,'FAILED',0,false)");
            assertEquals(Set.of(3L,7L),ids(connection,config,"ProjectTreeVersionMapper.selectLatestActiveBatch",new ActiveProjectTreeVersionsQuery(0L,Set.of(1L,10L,20L))));
            assertEquals(Set.of(),ids(connection,config,"ProjectTreeVersionMapper.selectLatestActiveBatch",new ActiveProjectTreeVersionsQuery(0L,Set.of())));
            sql.execute("CREATE TABLE proj_project_tree_path(id BIGINT,tenant_id BIGINT,root_project_id BIGINT,tree_version BIGINT,ancestor_project_id BIGINT,descendant_project_id BIGINT,deleted BOOLEAN)");
            sql.execute("INSERT INTO proj_project_tree_path VALUES(1,0,1,8,1,1,false),(2,0,1,8,1,2,false),(3,0,1,8,2,3,false),(4,0,1,7,1,99,false),(5,0,10,4,10,10,false),(6,0,10,4,11,12,false),(7,1,1,8,1,98,false),(8,0,1,8,1,97,true),(9,0,10,8,10,96,false),(10,0,1,8,11,95,false)");
            var trees=List.of(new ProjectScopeRootPathsQuery.TreeVersion(1L,8L),new ProjectScopeRootPathsQuery.TreeVersion(10L,4L));
            assertEquals(Set.of(1L,2L,5L),ids(connection,config,"ProjectTreePathMapper.selectScopeRootPaths",new ProjectScopeRootPathsQuery(0L,trees)));
            assertEquals(Set.of(),ids(connection,config,"ProjectTreePathMapper.selectScopeRootPaths",new ProjectScopeRootPathsQuery(0L,List.of())));
            var subtrees=List.of(new ProjectScopeDescendantsQuery.Subtree(1L,8L,Set.of(2L)),new ProjectScopeDescendantsQuery.Subtree(10L,4L,Set.of(11L)));
            assertEquals(Set.of(3L,6L),ids(connection,config,"ProjectTreePathMapper.selectScopeDescendants",new ProjectScopeDescendantsQuery(0L,subtrees)));
            assertEquals(Set.of(),ids(connection,config,"ProjectTreePathMapper.selectScopeDescendants",new ProjectScopeDescendantsQuery(0L,List.of())));
            assertEquals(Set.of(),ids(connection,config,"ProjectTreePathMapper.selectScopeDescendants",new ProjectScopeDescendantsQuery(0L,List.of(new ProjectScopeDescendantsQuery.Subtree(1L,8L,Set.of())))));
        }
    }
    private Set<Long> ids(Connection connection,Configuration config,String method,Object query) throws Exception {
        var params=Map.of("query",query);
        var bound=config.getMappedStatement("cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree."+method).getBoundSql(params);
        try(var prepared=connection.prepareStatement(bound.getSql().replace("b'0'","FALSE"))) {
            int i=1;
            for(var mapping:bound.getParameterMappings()) {
                var key=mapping.getProperty();
                prepared.setObject(i++,bound.hasAdditionalParameter(key)?bound.getAdditionalParameter(key):config.newMetaObject(params).getValue(key));
            }
            var ids=new HashSet<Long>();
            try(var rows=prepared.executeQuery()){while(rows.next())ids.add(rows.getLong("id"));}
            return ids;
        }
    }
}
