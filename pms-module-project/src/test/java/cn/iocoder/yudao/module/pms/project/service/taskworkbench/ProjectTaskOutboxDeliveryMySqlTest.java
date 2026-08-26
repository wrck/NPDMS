package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.service.outbox.PlatformOutboxDeliveryApiImpl;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskWorkbenchMySqlTestSupport;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectTaskOutboxDeliveryMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectTaskOutboxDeliveryMySqlTest extends TaskWorkbenchMySqlTestSupport {

    @Resource
    private PlatformOutboxDeliveryApi deliveryApi;

    private String assignedEventId;
    private String completedEventId;

    @BeforeEach
    void setUpEvents() {
        TenantContextHolder.setTenantId(0L);
        assignedEventId = "fproj007-assigned-" + UUID.randomUUID();
        completedEventId = "fproj007-completed-" + UUID.randomUUID();
        insertEvent(assignedEventId, "TaskAssigned", "Task", "991001", "{\"taskId\":991001}");
        insertEvent(completedEventId, "TaskCompleted", "Task", "991002", "{\"taskId\":991002}");
    }

    @AfterEach
    void cleanEvents() {
        jdbcTemplate.update("DELETE FROM plt_outbox_event WHERE event_id IN (?,?)",
                assignedEventId, completedEventId);
        TenantContextHolder.clear();
    }

    @Test
    void retriesWithTheSameEventIdAndStopsClaimingAfterDelivery() {
        LocalDateTime now = LocalDateTime.now();
        Set<String> eventTypes = Set.of("TaskAssigned", "TaskCompleted");
        String frozenAssignedPayload = payload(assignedEventId);
        assertEquals(Set.of(assignedEventId, completedEventId), deliveryApi.claimDue(
                        new PlatformOutboxClaimQuery(now, 100, eventTypes)).stream()
                .filter(message -> message.eventId().equals(assignedEventId)
                        || message.eventId().equals(completedEventId))
                .map(message -> message.eventId()).collect(java.util.stream.Collectors.toSet()));

        LocalDateTime retryAt = now.plusMinutes(5).withNano(0);
        deliveryApi.scheduleRetry(assignedEventId, 0, retryAt);
        assertEquals(0, claimed(retryAt.minusSeconds(1), eventTypes, assignedEventId));
        assertEquals(1, claimed(retryAt, eventTypes, assignedEventId));
        deliveryApi.markDelivered(assignedEventId, 1);
        assertEquals(0, claimed(retryAt.plusMinutes(1), eventTypes, assignedEventId));
        assertThrows(IllegalStateException.class, () -> deliveryApi.markDelivered(assignedEventId, 1));

        deliveryApi.markDelivered(completedEventId, 0);
        assertEquals("DELIVERED", status(assignedEventId));
        assertEquals("DELIVERED", status(completedEventId));
        assertEquals(frozenAssignedPayload, payload(assignedEventId));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_task WHERE id IN (991001,991002)", Long.class));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_task_assignment WHERE project_task_id IN (991001,991002)",
                Long.class));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_task_completion_evaluation "
                        + "WHERE project_task_id IN (991001,991002)", Long.class));
    }

    private long claimed(LocalDateTime dueAt, Set<String> eventTypes, String eventId) {
        return deliveryApi.claimDue(new PlatformOutboxClaimQuery(dueAt, 100, eventTypes)).stream()
                .filter(message -> eventId.equals(message.eventId())).count();
    }

    private void insertEvent(String eventId, String eventType, String aggregateType,
                             String aggregateKey, String payload) {
        jdbcTemplate.update("INSERT INTO plt_outbox_event "
                        + "(event_id,event_type,aggregate_type,aggregate_key,payload,status,"
                        + "occurred_at,retry_count,tenant_id) VALUES (?,?,?,?,?,'PENDING',?,0,0)",
                eventId, eventType, aggregateType, aggregateKey, payload, LocalDateTime.now().minusMinutes(1));
    }

    private String status(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM plt_outbox_event WHERE event_id=?", String.class, eventId);
    }

    private String payload(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT payload FROM plt_outbox_event WHERE event_id=?", String.class, eventId);
    }

    @SpringBootConfiguration
    @MapperScan("cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class, PlatformOutboxDeliveryApiImpl.class})
    static class TestApplication {
        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }
}
