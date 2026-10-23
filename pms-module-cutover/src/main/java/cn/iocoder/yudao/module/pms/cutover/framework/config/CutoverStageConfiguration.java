package cn.iocoder.yudao.module.pms.cutover.framework.config;

import cn.iocoder.yudao.module.pms.cutover.framework.adapter.CutoverOwnerAdapters;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistConfigurationQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverWorkflowCoordinator;

import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApiImpl;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactTransactionExecutor;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.controller.business.approval.CutoverApprovalRequestCodec;
import cn.iocoder.yudao.module.pms.cutover.controller.business.closure.CutoverClosureRequestCodec;
import cn.iocoder.yudao.module.pms.cutover.controller.business.plan.CutoverPlanRequestCodec;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNotificationMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalReassignmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalReviewItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.projection.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemResultMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureAttachmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverCollectionEvidenceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalSourceAssembler;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistExportService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistMatcher;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistWorkbookWriter;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverNavigationDecisionPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverNavigationDecisionQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverCollectionPort;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import java.time.Clock;
import java.util.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CutoverStageConfiguration {
    @Bean
    CutoverChecklistApplicationService cutoverChecklistApplicationService(CutoverTaskMapper taskMapper,
            CutoverTaskDeviceScopeMapper deviceMapper,
            CutoverAssessmentMapper assessmentMapper,
            CutoverTaskStageHistoryMapper historyMapper,
            CutoverChecklistMapper checklistMapper,
            CutoverChecklistItemMapper itemMapper,
            CutoverChecklistItemResultMapper resultMapper,
            CutoverChecklistConfigurationQueryService configurationService,
            CutoverChecklistMatcher matcher,
            CutoverProjectScopePort projectScopePort,
            CutoverCollectionPort collectionPort,
            CutoverChecklistFilePort filePort,
            PlatformCommandExecutionApi commandExecutionApi,
            CutoverNavigationDecisionQueryService navigationDecisionQueryService) {
        return new CutoverChecklistApplicationService(taskMapper, deviceMapper, assessmentMapper, historyMapper, checklistMapper, itemMapper, resultMapper, configurationService, matcher, projectScopePort, collectionPort, filePort, commandExecutionApi, navigationDecisionQueryService, Clock.systemDefaultZone());
    }

    @Bean
    CutoverNavigationDecisionQueryService cutoverNavigationDecisionQueryService(CutoverTaskMapper taskMapper,
            CutoverConfigurationRevisionMapper revisionMapper,
            CutoverNavigationDecisionPolicy policy) {
        return new CutoverNavigationDecisionQueryService(taskMapper, revisionMapper, policy);
    }

    @Bean
    CutoverPlanApplicationService cutoverPlanApplicationService(CutoverTaskMapper taskMapper,
            CutoverPlanRevisionMapper planMapper,
            CutoverPlanStepMapper stepMapper,
            CutoverSupportArrangementMapper supportMapper,
            CutoverProjectScopePort projectScopePort,
            CutoverPlanSourcePort sourcePort,
            CutoverPlanFilePort filePort,
            CutoverPlanContentCodec codec,
            PlatformCommandExecutionApi commandExecutionApi,
            CutoverApprovalFactApi approvalFactApi,
            CutoverTaskStageHistoryMapper historyMapper, cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanContactPolicy contacts) {
        return new CutoverPlanApplicationService(taskMapper, planMapper, stepMapper, supportMapper, projectScopePort, sourcePort, filePort, codec, commandExecutionApi, approvalFactApi, historyMapper, Clock.systemDefaultZone(), contacts);
    }

    @Bean
    CutoverPlanQueryService cutoverPlanQueryService(CutoverTaskMapper taskMapper,
            CutoverPlanRevisionMapper planMapper,
            CutoverPlanStepMapper stepMapper,
            CutoverSupportArrangementMapper supportMapper,
            CutoverProjectScopePort projectScopePort,
            CutoverPlanSourcePort sourcePort,
            CutoverApprovalFactApi approvalFactApi,
            CutoverPlanContentCodec codec) {
        return new CutoverPlanQueryService(taskMapper, planMapper, stepMapper, supportMapper, projectScopePort, sourcePort, approvalFactApi, codec);
    }

    @Bean
    CutoverApprovalSourceAssembler cutoverApprovalSourceAssembler(CutoverTaskMapper taskMapper,
            CutoverAssessmentMapper assessmentMapper,
            CutoverChecklistMapper checklistMapper,
            CutoverChecklistItemMapper itemMapper,
            CutoverChecklistItemResultMapper resultMapper,
            CutoverPlanRevisionMapper planMapper,
            CutoverPlanStepMapper stepMapper,
            CutoverSupportArrangementMapper supportMapper,
            CutoverApprovalSourceSnapshotCodec codec) {
        return new CutoverApprovalSourceAssembler(taskMapper, assessmentMapper, checklistMapper, itemMapper, resultMapper, planMapper, stepMapper, supportMapper, codec);
    }

    @Bean
    CutoverApprovalApplicationService cutoverApprovalApplicationService(CutoverApprovalSourceAssembler sourceAssembler,
            CutoverApprovalInstanceMapper instanceMapper,
            cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper nodeMapper,
            CutoverApprovalNotificationMapper notificationMapper,
            cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalReviewItemMapper reviewItemMapper,
            CutoverApprovalReassignmentMapper reassignmentMapper,
            CutoverTaskMapper taskMapper,
            CutoverTaskStageHistoryMapper historyMapper,
            ProjectCutoverServiceManagerPort serviceManagerPort,
            CutoverApprovalRoleCandidatePort roleCandidatePort,
            CutoverApprovalProjectScopePort projectScopePort,
            PlatformCommandExecutionApi commandExecutionApi,
            CutoverWorkflowCoordinator workflow, cn.iocoder.yudao.module.pms.cutover.service.task.CutoverExecutionReadinessPolicy readiness) {
        return new CutoverApprovalApplicationService(sourceAssembler, instanceMapper, nodeMapper, notificationMapper, reviewItemMapper, reassignmentMapper, taskMapper, historyMapper, serviceManagerPort, roleCandidatePort, projectScopePort, commandExecutionApi, () -> CutoverOwnerAdapters.actor(), Clock.systemDefaultZone(), workflow, readiness);
    }

    @Bean
    CutoverApprovalQueryService cutoverApprovalQueryService(CutoverApprovalInstanceMapper instanceMapper,
            cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper nodeMapper,
            cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalReviewItemMapper reviewMapper,
            CutoverTaskMapper taskMapper,
            ProjectCutoverServiceManagerPort serviceManagerPort,
            CutoverApprovalRoleCandidatePort roleCandidatePort,
            CutoverApprovalProjectScopePort projectScopePort,
            CutoverApprovalSourceSnapshotCodec snapshotCodec) {
        return new CutoverApprovalQueryService(instanceMapper, nodeMapper, reviewMapper, taskMapper, serviceManagerPort, roleCandidatePort, projectScopePort, snapshotCodec);
    }

    @Bean
    CutoverClosureApplicationService cutoverClosureApplicationService(CutoverTaskMapper taskMapper,
            CutoverApprovalInstanceMapper approvalMapper,
            CutoverPlanRevisionMapper planMapper,
            CutoverClosureMapper closureMapper,
            CutoverClosureAttachmentMapper attachmentMapper,
            CutoverCollectionEvidenceMapper evidenceMapper,
            CutoverTaskDeviceScopeMapper deviceScopeMapper,
            CutoverTaskStageHistoryMapper stageHistoryMapper,
            CutoverProjectScopePort projectScopePort,
            CutoverClosureFilePort filePort,
            PlatformCommandExecutionApi commandExecutionApi, cn.iocoder.yudao.module.pms.cutover.service.task.CutoverExecutionReadinessPolicy readiness) {
        return new CutoverClosureApplicationService(taskMapper, approvalMapper, planMapper, closureMapper, attachmentMapper, evidenceMapper, deviceScopeMapper, stageHistoryMapper, projectScopePort, filePort, commandExecutionApi, Clock.systemDefaultZone(), readiness);
    }

    @Bean
    CutoverClosureQueryService cutoverClosureQueryService(CutoverTaskMapper taskMapper,
            CutoverApprovalInstanceMapper approvalMapper,
            CutoverPlanRevisionMapper planMapper,
            CutoverClosureMapper closureMapper,
            CutoverClosureAttachmentMapper attachmentMapper,
            CutoverCollectionEvidenceMapper evidenceMapper,
            CutoverProjectScopePort projectScopePort) {
        return new CutoverClosureQueryService(taskMapper, approvalMapper, planMapper, closureMapper, attachmentMapper, evidenceMapper, projectScopePort);
    }

    @Bean
    CutoverApprovalFactTransactionExecutor cutoverApprovalFactTransactionExecutor(CutoverApprovalApplicationService applicationService,
            cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper instanceMapper) {
        return new CutoverApprovalFactTransactionExecutor(applicationService, instanceMapper);
    }

    @Bean
    CutoverApprovalFactApiImpl cutoverApprovalFactApiImpl(CutoverApprovalFactTransactionExecutor transactions) {
        return new CutoverApprovalFactApiImpl(transactions);
    }

    @Bean
    CutoverPlanRequestCodec cutoverPlanRequestCodec() { return new CutoverPlanRequestCodec(); }

    @Bean
    CutoverClosureRequestCodec cutoverClosureRequestCodec() { return new CutoverClosureRequestCodec(); }

    @Bean
    CutoverApprovalRequestCodec cutoverApprovalRequestCodec() { return new CutoverApprovalRequestCodec(); }

    @Bean
    CutoverPlanContentCodec cutoverPlanContentCodec() { return new CutoverPlanContentCodec(); }

    @Bean
    CutoverApprovalSourceSnapshotCodec cutoverApprovalSourceSnapshotCodec() { return new CutoverApprovalSourceSnapshotCodec(); }

    @Bean
    CutoverChecklistMatcher cutoverChecklistMatcher() { return new CutoverChecklistMatcher(); }

    @Bean
    CutoverNavigationDecisionPolicy cutoverNavigationDecisionPolicy() { return new CutoverNavigationDecisionPolicy(); }

    @Bean
    CutoverChecklistWorkbookWriter cutoverChecklistWorkbookWriter() { return new CutoverChecklistWorkbookWriter(); }

    @Bean
    CutoverChecklistExportService cutoverChecklistExportService(CutoverChecklistApplicationService service, CutoverChecklistWorkbookWriter writer, OperationAuditApi audit) {
        return new CutoverChecklistExportService(service, writer, audit);
    }
}
