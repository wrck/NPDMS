package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class P01CompanyCompletionTest {
    static final LocalDateTime AT=LocalDateTime.of(2026,7,12,0,0);
    static P01OrganizationSyncTask.Company ehr(long id,String code,String name){
        return new P01OrganizationSyncTask.Company().setId(id).setCode(code).setName(name).setDisabled(0).setSourceTable("ehr_company").setOriginalCode(code);
    }
    static P01OrganizationSyncTask.Fnd fnd(long id,String code,String name){
        return new P01OrganizationSyncTask.Fnd().setId(id).setCode(code).setName(name).setStatus(1).setCreateTime(AT);
    }
    static SyncContext<Void> context(){return new SyncContext<>(1,2,3,true,37,10000,AT,mock(SyncSource.class),
        mock(SyncIdentityStore.class),mock(SyncReferences.class),"BASE_P01",mock(SqlSession.class),false,true);}
    static P01OrganizationSyncTask.Input input(List<P01OrganizationSyncTask.Fnd> companyCompletions){
        var companies=new ArrayList<>(List.of(ehr(1,"001","公司一"),ehr(3,"003","公司三"),ehr(4,"002","公司二")));
        var departments=new ArrayList<P01OrganizationSyncTask.Department>();
        departments.add(new P01OrganizationSyncTask.Department().setId(10000L).setCode("160000").setName("基础部门").setGrade("1").setCompanyId(4L));
        long id=20000;
        for(var code:List.of("167010","167020","167050","162007003","162012003","162022003","310409001","310422001"))
            departments.add(new P01OrganizationSyncTask.Department().setId(id++).setCode(code).setName("补齐部门").setParentId(10000L).setCompanyId(4L));
        departments.add(new P01OrganizationSyncTask.Department().setId(541L).setCode("160700002").setName("停用例外").setDisabled(1));
        departments.add(new P01OrganizationSyncTask.Department().setId(391L).setCode("310500001").setName("停用例外").setDisabled(1));
        var fnd=new ArrayList<P01OrganizationSyncTask.Fnd>();
        for(var code:List.of("160300","160700002","162028","162029","163120","163140","163220","163230","163240","163320",
            "165030","165040","165050","167060","280700","310500001","310500001001","310500001002","310500001003","310500001004","310500001005",
            "310500001006","310500001007","310500001008","310500002","310500002001","310500002002","999999","163130","167010","167020","167050"))
            fnd.add(fnd(id++,code,"既定FND部门"));
        return new P01OrganizationSyncTask.Input(companies,departments,fnd,List.of(),companyCompletions,context());
    }
    @Test void approvedFnd004CompletesFourCompaniesWithoutChangingEhrDepartmentCompanyReferences(){
        var rows=P01OrganizationSyncTask.plan(input(List.of(fnd(4,"004","迪普科技（香港）有限公司"))));
        var companies=rows.stream().filter(s->"COMPANY".equals(s.getKind())).toList();assertEquals(4,companies.size());
        var added=companies.stream().filter(s->"004".equals(s.getCode())).findFirst().orElseThrow();
        assertEquals("迪普科技（香港）有限公司",added.getName());assertEquals(0,added.getStatus());assertEquals(1,added.getRank());
        assertEquals("fnd_company:4",added.getSourceId());
        assertEquals("002",rows.stream().filter(s->"160000".equals(s.getCode())).findFirst().orElseThrow().getCompanyCode());
        var original=new HashMap<String,String>();companies.stream().filter(s->!"004".equals(s.getCode())).forEach(s->original.put(s.getCode(),s.getSourceId()+":"+s.getName()));
        assertEquals(Map.of("001","1:公司一","003","3:公司三","002","4:公司二"),original);
    }
    @Test void wholeEhr004DefinitionWinsAndAbsentCompletionDoesNotBlockExistingDirectory(){
        var input=input(List.of(fnd(4,"004","FND名称").setStatus(0)));
        input.companies().add(ehr(99,"004","EHR名称"));
        var chosen=P01OrganizationSyncTask.plan(input).stream().filter(s->"COMPANY".equals(s.getKind())&&"004".equals(s.getCode())).findFirst().orElseThrow();
        assertEquals("EHR名称",chosen.getName());assertEquals(0,chosen.getStatus());assertEquals(0,chosen.getRank());assertEquals("99",chosen.getSourceId());
        assertEquals(3,P01OrganizationSyncTask.plan(input(List.of())).stream().filter(s->"COMPANY".equals(s.getKind())).count());
    }
    @Test void unreviewedFndCompaniesAreNotAdded(){
        assertEquals(3,P01OrganizationSyncTask.plan(input(List.of(fnd(7,"007","未审核公司")))).stream().filter(s->"COMPANY".equals(s.getKind())).count());
    }
    @Test void companyCompletionSqlOnlyReadsTheApprovedCodeAndUsesSourceName() throws Exception {
        var configuration=new Configuration();String resource="base-sync-source/P01SourceMapper.xml";
        try(var stream=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(stream,configuration,resource,configuration.getSqlFragments()).parse();}
        var sql=configuration.getMappedStatement(P01OrganizationSyncTask.SourceMapper.class.getName()+".selectCompanyCompletions")
            .getBoundSql(new AbstractSyncTask.SourceQuery<Void>(10001,1,true,null)).getSql();
        assertTrue(sql.startsWith("SELECT "));assertTrue(sql.contains("FROM fnd_company WHERE code IN ('04','004')"));
        assertTrue(sql.contains("CASE code WHEN '04' THEN '004' ELSE code END AS code,name,status"));
        assertFalse(sql.contains("迪普"));assertFalse(sql.toUpperCase(Locale.ROOT).contains("INSERT"));assertFalse(sql.toUpperCase(Locale.ROOT).contains("CREATE"));
    }
    @Test void companyCompletionIsReadOncePerRunAlongsideExistingSourceQueries(){
        var fixture=input(List.of(fnd(4,"004","公司四")));fixture.companies().add(ehr(2,"002","T公司二").setSourceTable("t_company").setOriginalCode("02"));var source=mock(SyncSource.class);var session=mock(SyncSource.Session.class);
        var sourceMapper=mock(P01OrganizationSyncTask.SourceMapper.class);when(source.open(3)).thenReturn(session);when(session.mapper(P01OrganizationSyncTask.SourceMapper.class)).thenReturn(sourceMapper);
        when(sourceMapper.selectCompanies(any())).thenReturn(fixture.companies());when(sourceMapper.selectDepartments(any())).thenReturn(fixture.departments());
        when(sourceMapper.selectFnd(any())).thenReturn(fixture.fnd());
        when(sourceMapper.selectCompanyCompletions(any())).thenReturn(fixture.companyCompletions());
        var context=new SyncContext<Void>(1,2,3,true,37,10000,AT,source,mock(SyncIdentityStore.class),mock(SyncReferences.class),"BASE_P01",mock(SqlSession.class),false,true);
        new P01OrganizationSyncTask(mock(P01OrganizationSyncTask.TargetMapper.class)).createParameters(context);
        verify(sourceMapper,times(1)).selectCompanyCompletions(any());verify(sourceMapper,times(1)).selectCompanies(any());
    }
    @Test void tCompanyDefinitionCompletes004WithoutPollutingEhrCompanyIdReferences(){
        var input=input(List.of());input.companies().add(ehr(4,"004","T公司四").setSourceTable("t_company").setOriginalCode("04"));
        var rows=P01OrganizationSyncTask.plan(input);var company=rows.stream().filter(s->"COMPANY".equals(s.getKind())&&"004".equals(s.getCode())).findFirst().orElseThrow();
        assertEquals("T公司四",company.getName());assertEquals("t_company:4",company.getSourceId());assertEquals(1,company.getRank());
        assertEquals("002",rows.stream().filter(s->"160000".equals(s.getCode())).findFirst().orElseThrow().getCompanyCode());
    }
    @Test void ehrCompanyWholeDefinitionWinsOverSameCodeFromTCompany(){
        var input=input(List.of());input.companies().add(0,ehr(2,"002","T名称").setDisabled(1).setSourceTable("t_company").setOriginalCode("02"));
        var chosen=P01OrganizationSyncTask.plan(input).stream().filter(s->"COMPANY".equals(s.getKind())&&"002".equals(s.getCode())).findFirst().orElseThrow();
        assertEquals("公司二",chosen.getName());assertEquals(0,chosen.getStatus());assertEquals("4",chosen.getSourceId());assertEquals(0,chosen.getRank());
    }
    @Test void companyUnionMapsAllFourCodesAndBindsOneGlobalLimit() throws Exception {
        var configuration=new Configuration();String resource="base-sync-source/P01SourceMapper.xml";
        try(var stream=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(stream,configuration,resource,configuration.getSqlFragments()).parse();}
        var bound=configuration.getMappedStatement(P01OrganizationSyncTask.SourceMapper.class.getName()+".selectCompanies")
            .getBoundSql(new AbstractSyncTask.SourceQuery<Void>(10001,1,true,null));var sql=bound.getSql();
        OrganizationBackfillSqlTest.parseWithRuntimeStack(sql);assertTrue(sql.contains("FROM ehr_company UNION ALL SELECT"));assertTrue(sql.endsWith("FROM t_company LIMIT ?"));
        for(var pair:Map.of("01","001","02","002","03","003","04","004").entrySet())assertTrue(sql.contains("WHEN '"+pair.getKey()+"' THEN '"+pair.getValue()+"'"));
        assertTrue(sql.contains("CASE CAST(state AS UNSIGNED) WHEN 1 THEN 0 WHEN 0 THEN 1 ELSE NULL END"));
        assertEquals(1,bound.getParameterMappings().size());assertEquals("limit",bound.getParameterMappings().getFirst().getProperty());
        assertFalse(configuration.hasStatement(P01OrganizationSyncTask.SourceMapper.class.getName()+".selectCompanyAliases"));
    }
}
