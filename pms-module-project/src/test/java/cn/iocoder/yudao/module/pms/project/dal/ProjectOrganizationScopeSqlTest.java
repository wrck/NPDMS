package cn.iocoder.yudao.module.pms.project.dal;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi.Organization;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.OrganizationProjectScopeQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectOrganizationScopeSqlTest {
    record Holder(ProjectViewReadScopeApi.ReadScope projectScope) { }
    record ReadQuery(Long tenantId, Holder readScope) { }

    @Test void organizationLookupAndJoinedReadAgreeWithoutCrossMultiplyingCompaniesAndDepartments() throws Exception {
        var config = new Configuration();
        for (var resource : List.of("mapper/projectmanual/ProjectOverdueMapper.xml", "mapper/projectmanual/ProjectMasterMapper.xml",
                "mapper/authorization/ProjectViewGrantReadSql.xml", "mapper/projectscope/ProjectViewReadScopeSql.xml")) {
            try (var xml = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(xml,resource);
                new XMLMapperBuilder(xml,config,resource,config.getSqlFragments()).parse();
            }
        }
        String wrapper = "<?xml version='1.0' encoding='UTF-8'?><!DOCTYPE mapper PUBLIC '-//mybatis.org//DTD Mapper 3.0//EN' 'https://mybatis.org/dtd/mybatis-3-mapper.dtd'><mapper namespace='scope_test'><select id='visible' resultType='java.lang.Long'>WITH <include refid='cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeSql.commonTables'/> SELECT project_id AS id FROM visible_projects</select></mapper>";
        new XMLMapperBuilder(new ByteArrayInputStream(wrapper.getBytes(StandardCharsets.UTF_8)),config,"scope-test",config.getSqlFragments()).parse();
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:org_scope_"+UUID.randomUUID()+";MODE=MySQL")) {
            var sql=connection.createStatement();
            sql.execute("CREATE TABLE proj_project(id BIGINT,tenant_id BIGINT,company_id BIGINT,department_id BIGINT,creator VARCHAR(20),deleted BOOLEAN,lifecycle_status VARCHAR(30))");
            sql.execute("INSERT INTO proj_project VALUES(1,1,11,21,'0',false,'ACTIVE'),(2,1,12,22,'0',false,'NORMAL_CLOSED'),(3,1,11,22,'0',false,'ACTIVE'),(4,1,12,21,'0',false,'ACTIVE'),(5,2,11,21,'0',false,'ACTIVE'),(6,1,11,21,'0',true,'ACTIVE'),(7,1,13,23,'0',false,'ACTIVE')");
            sql.execute("ALTER TABLE proj_project ADD project_name VARCHAR(128)");
            sql.execute("ALTER TABLE proj_project ADD contract_no VARCHAR(128)");
            sql.execute("UPDATE proj_project SET project_name='Exact%_',contract_no='C-01'");
            String exact="cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.selectOfficeProjects";
            var exactQuery=cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.OfficeProjectSearchQuery.builder()
                    .tenantId(1L).organizations(List.of(new Organization(11L,21L)));
            assertEquals(Set.of(1L),ids(connection,config,exact,exactQuery.exactProjectName("Exact%_").build()));
            assertEquals(Set.of(),ids(connection,config,exact,exactQuery.exactProjectName("Exact").build()));
            assertEquals(Set.of(),ids(connection,config,exact,exactQuery.exactProjectName("exact%_").build()));
            assertEquals(Set.of(),ids(connection,config,exact,exactQuery.exactProjectName("Exact%_").exactContractNo("wrong").build()));
            assertEquals(Set.of(1L),ids(connection,config,exact,exactQuery.exactProjectName(null).exactContractNo("C-01").build()));
            // COM only provides candidates; all PROJ tenant/deletion and same-row organization predicates still apply.
            var linked=exactQuery.exactContractNo("LINKED").contractProjectIds(Set.of(1L,2L,3L,4L,5L,6L));
            assertEquals(Set.of(1L),ids(connection,config,exact,linked.build()));
            assertEquals(Set.of(),ids(connection,config,exact,linked.exactProjectName("wrong").build()));
            assertEquals(Set.of(1L),ids(connection,config,exact,linked.exactProjectName("Exact%_").build()));
            assertEquals(Set.of(),ids(connection,config,exact,linked.contractProjectIds(Set.of()).build()));
            assertEquals(Set.of(1L,2L),ids(connection,config,exact,linked.organizations(List.of(new Organization(11L,21L),new Organization(12L,22L))).contractProjectIds(Set.of(1L,2L,3L,4L,5L,6L)).build()));
            assertEquals(Set.of(1L,3L),ids(connection,config,exact,linked.organizations(List.of(new Organization(11L,null))).build()));
            assertEquals(Set.of(),ids(connection,config,exact,linked.organizations(List.of()).build()));
            exactQuery.organizations(List.of(new Organization(11L,21L))).contractProjectIds(Set.of()).exactProjectName(null);
            assertEquals(Set.of(),ids(connection,config,exact,exactQuery.exactContractNo(null).build()));
            sql.execute("CREATE TABLE proj_project_tree_path(root_project_id BIGINT,tree_version BIGINT,ancestor_project_id BIGINT,descendant_project_id BIGINT,tenant_id BIGINT,deleted BOOLEAN)");
            sql.execute("INSERT INTO proj_project_tree_path VALUES(1,7,1,1,1,false),(1,7,1,2,1,false),(1,7,1,3,1,false),(1,7,1,4,1,false),(1,7,1,7,1,false),(1,8,1,99,1,false)");
            sql.execute("CREATE TABLE proj_project_member_assignment(tenant_id BIGINT,deleted BOOLEAN,project_id BIGINT,user_id BIGINT,status VARCHAR(20),authorization_confirmed BOOLEAN,effective_from TIMESTAMP,effective_to TIMESTAMP)");
            sql.execute("CREATE TABLE plt_authorization_grant(tenant_id BIGINT,deleted BOOLEAN,subject_type_code VARCHAR(20),subject_id BIGINT,resource_context_code VARCHAR(20),resource_type_code VARCHAR(20),action_code VARCHAR(30),status_code VARCHAR(20),current_marker INT,effective_from TIMESTAMP,effective_to TIMESTAMP,resource_id BIGINT,scope_code VARCHAR(40))");
            sql.execute("INSERT INTO plt_authorization_grant VALUES(1,false,'USER',9,'PROJ','PROJECT','PROJECT_MANAGE','ACTIVE',1,'2020-01-01',NULL,7,'CURRENT_PROJECT')");
            var rows=List.of(new Organization(11L,21L),new Organization(12L,22L));
            String method="cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.selectOrganizationProjectIds";
            assertEquals(Set.of(1L),ids(connection,config,method,new OrganizationProjectScopeQuery(1L,rows,null,true)));
            assertEquals(Set.of(1L,2L), ids(connection,config,method,new OrganizationProjectScopeQuery(1L,rows,null,false)));
            assertEquals(Set.of(),ids(connection,config,method,new OrganizationProjectScopeQuery(1L,List.of(),null,false)));
            assertEquals(Set.of(),ids(connection,config,method,new OrganizationProjectScopeQuery(1L,rows,Set.of(),false)));
            assertEquals(Set.of(1L),ids(connection,config,method,new OrganizationProjectScopeQuery(1L,rows,Set.of(1L,3L),false)));
            assertEquals(Set.of(1L,3L),ids(connection,config,method,new OrganizationProjectScopeQuery(1L,List.of(new Organization(11L,null)),null,false)));
            var scope=new ProjectViewReadScopeApi.ReadScope(1L,9L,ProjectViewReadScopeApi.Mode.SCOPED,LocalDateTime.now(),List.of(new ProjectViewReadScopeApi.Tree(1L,7L)),rows);
            assertEquals(Set.of(1L,2L,7L),ids(connection,config,"scope_test.visible",new ReadQuery(1L,new Holder(scope))));
            var empty=new ProjectViewReadScopeApi.ReadScope(1L,9L,ProjectViewReadScopeApi.Mode.SCOPED,LocalDateTime.now(),scope.trees());
            assertEquals(Set.of(7L),ids(connection,config,"scope_test.visible",new ReadQuery(1L,new Holder(empty))));
            assertEquals(Set.of(),ids(connection,config,"scope_test.visible",new ReadQuery(2L,new Holder(scope))));
            var office=new ProjectViewReadScopeApi.ReadScope(1L,9L,ProjectViewReadScopeApi.Mode.SCOPED,LocalDateTime.now(),scope.trees(),rows,3L);
            assertEquals(Set.of(3L),ids(connection,config,"scope_test.visible",new ReadQuery(1L,new Holder(office))));
        }
    }

    Set<Long> ids(Connection connection,Configuration config,String method,Object query) throws Exception {
        var params=Map.of("query",query); var bound=config.getMappedStatement(method).getBoundSql(params);
        // MySQL无长度BINARY保留全部字节；H2的BINARY默认仅1字节，用VARBINARY保持同等比较语义。
        try(var prepared=connection.prepareStatement(bound.getSql().replace("b'0'","FALSE").replace("b'1'","TRUE").replace(" AS BINARY)"," AS VARBINARY)"))) {
            int i=1;
            for(var mapping:bound.getParameterMappings()) {
                String key=mapping.getProperty();
                prepared.setObject(i++,bound.hasAdditionalParameter(key)?bound.getAdditionalParameter(key):config.newMetaObject(params).getValue(key));
            }
            var result=new HashSet<Long>();try(var rows=prepared.executeQuery()){while(rows.next()) result.add(rows.getLong("id"));}return result;
        }
    }
}
