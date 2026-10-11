package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo.ArrivalSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query.ArrivalDeliveryOwnerQuery;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real database rollback at the save/sign transaction boundary, without touching a running database. */
class ArrivalSaveTransactionTest {
    @org.junit.jupiter.api.BeforeEach void tenant(){cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);}
    @org.junit.jupiter.api.AfterEach void clearTenant(){cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();}
    @Test void signatureRegistrationFailureRollsBackNewReceiptAndExistingEvidenceUpdate() {
        try (var context=new AnnotationConfigApplicationContext(Config.class)) {
            var service=context.getBean(ArrivalService.class);
            var jdbc=context.getBean(JdbcTemplate.class);
            var registration=context.getBean(ArrivalDeliveryRegistration.class);
            doThrow(new IllegalStateException("signature registration failed")).when(registration).registerSigned(any());
            var request=new ArrivalSaveReqVO();request.setProjectId(20L);request.setAttachmentUrl("/test/new.pdf");
            request.setArrivalTime(java.time.LocalDateTime.of(2026,10,9,17,0));
            assertEquals("signature registration failed", assertThrows(IllegalStateException.class,()->service.createArrival(request)).getMessage());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM receipt",Integer.class));
            jdbc.update("INSERT INTO receipt VALUES (1,20,0,6,'/test/original.pdf')");
            request.setId(1L);request.setVersion(6);
            assertEquals("signature registration failed", assertThrows(IllegalStateException.class,()->service.updateArrival(request)).getMessage());
            assertEquals(0,jdbc.queryForObject("SELECT status FROM receipt",Integer.class));
            assertEquals(6L,jdbc.queryForObject("SELECT version FROM receipt",Long.class));
            assertEquals("/test/original.pdf",jdbc.queryForObject("SELECT attachment FROM receipt",String.class));
        }
    }
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    @Import(ArrivalServiceImpl.class)
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:arrival_"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");}
        @Bean JdbcTemplate jdbc(DataSource source){var jdbc=new JdbcTemplate(source);jdbc.execute("CREATE TABLE receipt(id BIGINT PRIMARY KEY,project_id BIGINT,status INT,version BIGINT,attachment VARCHAR(500))");return jdbc;}
        @Bean PlatformTransactionManager transactions(DataSource source){return new DataSourceTransactionManager(source);}
        @Bean ArrivalDeliveryRegistration registration(){return mock(ArrivalDeliveryRegistration.class);}
        @Bean cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi devices(){return mock(cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi.class);}
        @Bean EngineeringRecordCodeGenerator codes(){var codes=mock(EngineeringRecordCodeGenerator.class);when(codes.next(any(),anyString(),any())).thenReturn("ARRIVAL-TEST");return codes;}
        @Bean cn.iocoder.yudao.module.pms.project.api.reference.ProjectCodeQueryApi projectCodes(){return mock(cn.iocoder.yudao.module.pms.project.api.reference.ProjectCodeQueryApi.class);}
        @Bean ArrivalMapper mapper(JdbcTemplate jdbc){
            var mapper=mock(ArrivalMapper.class);
            when(mapper.insert(any(ArrivalDO.class))).thenAnswer(call->{var row=(ArrivalDO)call.getArgument(0);row.setId(1L);return jdbc.update("INSERT INTO receipt VALUES (?,?,?,?,?)",row.getId(),row.getProjectId(),row.getStatus(),row.getVersion(),row.getAttachmentUrl());});
            when(mapper.selectDeliveryOwnerForUpdate(any())).thenAnswer(call->{var query=(ArrivalDeliveryOwnerQuery)call.getArgument(0);return jdbc.queryForObject("SELECT * FROM receipt WHERE id=?",(rs,n)->{var row=new ArrivalDO();row.setId(rs.getLong("id"));row.setProjectId(rs.getLong("project_id"));row.setStatus(rs.getInt("status"));row.setVersion(rs.getLong("version"));row.setAttachmentUrl(rs.getString("attachment"));return row;},query.arrivalId());});
            when(mapper.updateById(any(ArrivalDO.class))).thenAnswer(call->{var row=(ArrivalDO)call.getArgument(0);int changed=jdbc.update("UPDATE receipt SET status=?,version=version+1,attachment=? WHERE id=? AND version=?",row.getStatus(),row.getAttachmentUrl(),row.getId(),row.getVersion());if(changed==1)row.setVersion(row.getVersion()+1);return changed;});
            return mapper;
        }
    }
}
