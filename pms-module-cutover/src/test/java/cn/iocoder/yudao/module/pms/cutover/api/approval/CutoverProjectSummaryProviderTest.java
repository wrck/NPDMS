package cn.iocoder.yudao.module.pms.cutover.api.approval;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProjectSummaryProvider;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalWorkflowMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.CutoverApprovalProjectSummaryQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CutoverProjectSummaryProviderTest {
    private SqlSession session;
    private CutoverApprovalWorkflowMapper mapper;
    private CutoverProjectSummaryProvider provider;

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:cut_summary_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var c=source.getConnection();var sql=c.createStatement()) {
            sql.execute("CREATE TABLE cut_approval_workflow(id BIGINT PRIMARY KEY,tenant_id BIGINT,task_id BIGINT,process_instance_id VARCHAR(64),deleted INT)");
            sql.execute("CREATE TABLE cut_approval_instance(id BIGINT PRIMARY KEY,tenant_id BIGINT,task_id BIGINT,project_name VARCHAR(255),deleted INT)");
            sql.execute("INSERT INTO cut_approval_workflow VALUES(1,1,10,'allowed',0),(2,1,20,'outside',0),(3,2,30,'foreign',0),(4,1,40,'broken',0),(5,1,50,'removed',0),(6,1,60,'link-deleted',1)");
            sql.execute("INSERT INTO cut_approval_instance VALUES(1,1,10,'Frozen name',0),(2,1,20,'Outside name',0),(3,2,30,'Foreign name',0),(4,1,99,'Wrong task',0),(5,1,50,'Deleted',1),(6,1,60,'Deleted link',0)");
        }
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.setEnvironment(new Environment("summary",new JdbcTransactionFactory(),source));
        configuration.setMapUnderscoreToCamelCase(true);
        try(var xml=getClass().getResourceAsStream("/mapper/approval/CutoverApprovalWorkflowMapper.xml")) {
            new XMLMapperBuilder(xml,configuration,"summary",configuration.getSqlFragments()).parse();
        }
        session=new SqlSessionFactoryBuilder().build(configuration).openSession();
        mapper=session.getMapper(CutoverApprovalWorkflowMapper.class);
        provider=new CutoverProjectSummaryProvider(mapper);
        TenantContextHolder.setTenantId(1L);
    }
    @AfterEach void close() { if(session!=null) session.close(); TenantContextHolder.clear(); }

    @Test void onlyExactAuthorizedTenantAndIntactLinksExposeFrozenNames() {
        var result=provider.getProjectSummaries(new BpmProjectSummaryProvider.Query(1L,
                Set.of("allowed","foreign","broken","removed","link-deleted")));
        assertEquals(1,result.size());
        assertEquals("allowed",result.getFirst().processInstanceId());
        assertEquals("Frozen name",result.getFirst().projectName());
    }
    @Test void emptySetFailsClosedInProviderAndMapper() {
        assertTrue(provider.getProjectSummaries(new BpmProjectSummaryProvider.Query(1L,Set.of())).isEmpty());
        assertTrue(mapper.selectProjectSummaries(new CutoverApprovalProjectSummaryQuery(1L,Set.of())).isEmpty());
    }
    @Test void changedOrIgnoredTenantIsRejected() {
        assertThrows(IllegalArgumentException.class,()->provider.getProjectSummaries(new BpmProjectSummaryProvider.Query(2L,Set.of("foreign"))));
        TenantContextHolder.setIgnore(true);
        assertThrows(IllegalArgumentException.class,()->provider.getProjectSummaries(new BpmProjectSummaryProvider.Query(1L,Set.of("allowed"))));
    }
}
