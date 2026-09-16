package cn.iocoder.yudao.module.pms.engineering.service.outsource;

import cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo.OutsourceRequestSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.outsource.OutsourceRequestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.outsource.OutsourceRequestMapper;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.SiteSurveyEntityService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring transaction + isolated H2 resource; does not load local profiles or business databases. */
class OutsourceSurveyExecutionTransactionTest {
    JdbcTemplate jdbc;
    OutsourceRequestService service;
    final OutsourceRequestMapper mapper = mock(OutsourceRequestMapper.class);
    final SiteSurveyEntityService surveys = mock(SiteSurveyEntityService.class);
    @BeforeEach void setup() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:outsource_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE request_fixture(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE survey_fixture(id BIGINT PRIMARY KEY, request_id BIGINT)");
        jdbc.update("INSERT INTO survey_fixture VALUES(51,NULL)");
        var target = new OutsourceRequestServiceImpl();
        ReflectionTestUtils.setField(target,"outsourceRequestMapper",mapper);
        ReflectionTestUtils.setField(target,"siteSurveyService",surveys);
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),new AnnotationTransactionAttributeSource()));
        service = (OutsourceRequestService) proxy.getProxy();
        when(mapper.insert(any(OutsourceRequestDO.class))).thenAnswer(call -> {
            ((OutsourceRequestDO)call.getArgument(0)).setId(41L); return jdbc.update("INSERT INTO request_fixture VALUES(41)");
        });
        doAnswer(call -> jdbc.update("UPDATE survey_fixture SET request_id=41 WHERE id=51"))
                .when(surveys).associateOutsourceRequest(51L,9L,41L,null);
        doAnswer(call -> jdbc.update("UPDATE survey_fixture SET request_id=NULL WHERE id=51"))
                .when(surveys).releaseDeletedOutsourceRequest(51L,41L,null);
        when(mapper.deleteById(41L)).thenAnswer(call -> jdbc.update("DELETE FROM request_fixture WHERE id=41"));
        var row = new OutsourceRequestDO(); row.setId(41L); row.setTriggerSource("SITE_SURVEY"); row.setTriggerRefId(51L); row.setStatus(0);
        when(mapper.selectById(41L)).thenReturn(row);
    }
    @AfterEach void close() { jdbc.execute("SHUTDOWN"); }
    void create() {
        var request = new OutsourceRequestSaveReqVO(); request.setProjectId(9L); request.setTriggerSource("SITE_SURVEY"); request.setTriggerRefId(51L);
        service.createOutsourceRequest(request);
    }
    @Test void rejectedExecutionRollsBackInsertedRequestInsteadOfLeavingAnOrphan() {
        doThrow(new IllegalStateException("execution changed")).when(surveys).associateOutsourceRequest(51L,9L,41L,null);
        assertThrows(IllegalStateException.class,this::create);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM request_fixture",Integer.class));
        assertNull(jdbc.queryForObject("SELECT request_id FROM survey_fixture WHERE id=51",Long.class));
    }
    @Test void failedDeletionRestoresRelationshipChangedEarlierInTheSameTransaction() {
        create();
        doThrow(new IllegalStateException("delete failed")).when(mapper).deleteById(41L);
        assertThrows(IllegalStateException.class,() -> service.deleteOutsourceRequest(41L,null));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM request_fixture",Integer.class));
        assertEquals(41L,jdbc.queryForObject("SELECT request_id FROM survey_fixture WHERE id=51",Long.class));
    }
    @Test void successfulCreationAndDeletionCommitTheOwnerRelationshipTogether() {
        create();
        assertEquals(41L,jdbc.queryForObject("SELECT request_id FROM survey_fixture WHERE id=51",Long.class));
        service.deleteOutsourceRequest(41L,null);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM request_fixture",Integer.class));
        assertNull(jdbc.queryForObject("SELECT request_id FROM survey_fixture WHERE id=51",Long.class));
    }
}
