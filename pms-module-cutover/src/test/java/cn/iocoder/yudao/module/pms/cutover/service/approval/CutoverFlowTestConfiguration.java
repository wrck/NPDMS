package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalProjectScopePort;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApiImpl;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactTransactionExecutor;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalRules;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.CutoverApprovalNotificationProviderExecutor;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.CutoverApprovalNotificationService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external.CutoverExternalApprovalNotificationPort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external.CutoverExternalApprovalNotificationService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external.CutoverExternalApprovalNotificationTransactionExecutor;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external.ExternalApprovalNotificationRequest;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external.ExternalApprovalNotificationResult;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.*;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanRules;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.CutoverPlanCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.SubmitCutoverPlanResult;
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@org.springframework.context.annotation.Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan({"cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.task",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class})
class CutoverFlowTestConfiguration {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC); }
        @Bean CutoverApprovalPositiveLoopMySqlTest.CurrentActor actor() { return new CutoverApprovalPositiveLoopMySqlTest.CurrentActor(); }
        @Bean CutoverApprovalPositiveLoopMySqlTest.ControlledOwners owners() { return new CutoverApprovalPositiveLoopMySqlTest.ControlledOwners(); }
        @Bean ProjectCutoverServiceManagerPort manager() { return new ProjectCutoverServiceManagerPort() {
            public ServiceManagerFact inspectCurrent(long tenant, long project, LocalDateTime at) { return new ServiceManagerFact(Outcome.FOUND, tenant, project, 301L, "SERVICE_MANAGER", 1, 2L, at); }
            public ServiceManagerRevalidation lockAndRevalidate(ServiceManagerFact expected) { return new ServiceManagerRevalidation(Revalidation.VALID, expected); }
        }; }
        @Bean CutoverApprovalRoleCandidatePort candidates() { return CutoverApprovalControlledPorts.roleCandidates(); }
        @Bean CutoverApprovalProjectScopePort approvalScope() {
            return new CutoverApprovalProjectScopePort() {
                public ProjectScopeFact inspect(CutoverApprovalProjectScopePort.ScopeQuery query) {
        long tenant = query.tenantId();
        long project = query.projectId();
        long user = query.subjectUserId();
        String action = query.requiredAction();

                    boolean allowed = (user == 8L && "ACTION_EDIT".equals(action))
                            || (user == 202L && "ACTION_VIEW".equals(action));
                    return new ProjectScopeFact(tenant, project, user, action, allowed, 11L);
                }
                public ProjectScopeRevalidation lockAndRevalidate(ProjectScopeFact expected) {
                    return new ProjectScopeRevalidation(Revalidation.VALID, expected);
                }
            };
        }
        @Bean CutoverApprovalSourceSnapshotCodec approvalCodec() { return new CutoverApprovalSourceSnapshotCodec(); }
        @Bean CutoverApprovalSourceAssembler sourceAssembler(CutoverTaskMapper tasks, CutoverAssessmentMapper assessments,
                CutoverChecklistMapper checklists, CutoverChecklistItemMapper items,
                CutoverChecklistItemResultMapper results, CutoverPlanRevisionMapper plans,
                CutoverPlanStepMapper steps, CutoverSupportArrangementMapper supports,
                CutoverApprovalSourceSnapshotCodec codec) {
            return new CutoverApprovalSourceAssembler(tasks, assessments, checklists, items, results, plans,
                    steps, supports, codec);
        }
        @Bean CutoverApprovalApplicationService approvalService(CutoverApprovalSourceAssembler assembler,
                CutoverApprovalInstanceMapper instances, CutoverApprovalNodeMapper nodes,
                CutoverApprovalNotificationMapper notifications, CutoverApprovalReviewItemMapper reviews,
                CutoverApprovalReassignmentMapper reassignments, CutoverTaskMapper tasks,
                CutoverTaskStageHistoryMapper history, ProjectCutoverServiceManagerPort manager,
                CutoverApprovalRoleCandidatePort candidates, CutoverApprovalProjectScopePort scope,
                PlatformCommandExecutionApi platform, CutoverApprovalPositiveLoopMySqlTest.CurrentActor actor, Clock clock) {
            return new CutoverApprovalApplicationService(assembler, instances, nodes, notifications, reviews,
                    reassignments, tasks, history, manager, candidates, scope, platform, actor::current, clock, org.mockito.Mockito.mock(CutoverWorkflowCoordinator.class), org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.service.task.CutoverExecutionReadinessPolicy.class));
        }
        @Bean CutoverApprovalFactTransactionExecutor approvalTransactions(CutoverApprovalApplicationService service,
                CutoverApprovalInstanceMapper instances) { return new CutoverApprovalFactTransactionExecutor(service, instances); }
        @Bean cn.iocoder.yudao.module.pms.cutover.service.taskbusiness.CutoverApprovalTaskEvents approvalTaskEvents() {
            return org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.service.taskbusiness.CutoverApprovalTaskEvents.class);
        }
        @Bean CutoverApprovalFactApi approvalApi(CutoverApprovalFactTransactionExecutor executor) {
            return new CutoverApprovalFactApiImpl(executor);
        }
        @Bean CutoverPlanApplicationService planService(CutoverTaskMapper tasks, CutoverPlanRevisionMapper plans,
                CutoverPlanStepMapper steps, CutoverSupportArrangementMapper supports,
                CutoverTaskStageHistoryMapper history, CutoverApprovalPositiveLoopMySqlTest.ControlledOwners owners, CutoverApprovalFactApi approvals,
                PlatformCommandExecutionApi platform, Clock clock) {
            return new CutoverPlanApplicationService(tasks, plans, steps, supports, owners, owners, owners,
                    new CutoverPlanContentCodec(), platform, approvals, history, clock, org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanContactPolicy.class));
        }
}
