package cn.iocoder.yudao.module.pms.cutover.framework.runtime;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistConfigurationQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverWorkflowCoordinator;

import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverRuntimeApprovalFactApiImpl;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverRuntimeApprovalFactTransactionExecutor;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.CutoverApprovalRequestCodec;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.CutoverClosureRequestCodec;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.CutoverRuntimePlanRequestCodec;
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
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeApprovalApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeApprovalQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeApprovalSourceAssembler;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeApprovalSourceSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeApprovalSourceSnapshotCodec.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverRuntimeChecklistApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverRuntimeChecklistExportService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverRuntimeChecklistMatcher;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistWorkbookWriter;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverNavigationDecisionPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverNavigationDecisionQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverCollectionPort;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverRuntimeClosureApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverRuntimeClosureQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverRuntimePlanApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverRuntimePlanQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverRuntimePlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import java.time.Clock;
import java.util.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CutoverStageConfiguration {
    @Bean
    CutoverRuntimeChecklistApplicationService cutoverChecklistApplicationService(CutoverTaskMapper taskMapper,
            CutoverTaskDeviceScopeMapper deviceMapper,
            CutoverAssessmentMapper assessmentMapper,
            CutoverTaskStageHistoryMapper historyMapper,
            CutoverChecklistMapper checklistMapper,
            CutoverChecklistItemMapper itemMapper,
            CutoverChecklistItemResultMapper resultMapper,
            CutoverChecklistConfigurationQueryService configurationService,
            CutoverRuntimeChecklistMatcher matcher,
            CutoverProjectScopePort projectScopePort,
            CutoverCollectionPort collectionPort,
            CutoverChecklistFilePort filePort,
            PlatformCommandExecutionApi commandExecutionApi,
            CutoverNavigationDecisionQueryService navigationDecisionQueryService) {
        return new CutoverRuntimeChecklistApplicationService(taskMapper, deviceMapper, assessmentMapper, historyMapper, checklistMapper, itemMapper, resultMapper, configurationService, matcher, projectScopePort, collectionPort, filePort, commandExecutionApi, navigationDecisionQueryService, Clock.systemDefaultZone());
    }

    @Bean
    CutoverNavigationDecisionQueryService cutoverNavigationDecisionQueryService(CutoverTaskMapper taskMapper,
            CutoverConfigurationRevisionMapper revisionMapper,
            CutoverNavigationDecisionPolicy policy) {
        return new CutoverNavigationDecisionQueryService(taskMapper, revisionMapper, policy);
    }

    @Bean
    CutoverRuntimePlanApplicationService cutoverPlanApplicationService(CutoverTaskMapper taskMapper,
            CutoverPlanRevisionMapper planMapper,
            CutoverPlanStepMapper stepMapper,
            CutoverSupportArrangementMapper supportMapper,
            CutoverProjectScopePort projectScopePort,
            CutoverPlanSourcePort sourcePort,
            CutoverPlanFilePort filePort,
            CutoverRuntimePlanContentCodec codec,
            PlatformCommandExecutionApi commandExecutionApi,
            CutoverApprovalFactApi approvalFactApi,
            CutoverTaskStageHistoryMapper historyMapper, cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanContactPolicy contacts) {
        return new CutoverRuntimePlanApplicationService(taskMapper, planMapper, stepMapper, supportMapper, projectScopePort, sourcePort, filePort, codec, commandExecutionApi, approvalFactApi, historyMapper, Clock.systemDefaultZone(), contacts);
    }

    @Bean
    CutoverRuntimePlanQueryService cutoverPlanQueryService(CutoverTaskMapper taskMapper,
            CutoverPlanRevisionMapper planMapper,
            CutoverPlanStepMapper stepMapper,
            CutoverSupportArrangementMapper supportMapper,
            CutoverProjectScopePort projectScopePort,
            CutoverPlanSourcePort sourcePort,
            CutoverApprovalFactApi approvalFactApi,
            CutoverRuntimePlanContentCodec codec) {
        return new CutoverRuntimePlanQueryService(taskMapper, planMapper, stepMapper, supportMapper, projectScopePort, sourcePort, approvalFactApi, codec);
    }

    @Bean
    CutoverRuntimeApprovalSourceAssembler cutoverRuntimeApprovalSourceAssembler(CutoverTaskMapper taskMapper,
            CutoverAssessmentMapper assessmentMapper,
            CutoverChecklistMapper checklistMapper,
            CutoverChecklistItemMapper itemMapper,
            CutoverChecklistItemResultMapper resultMapper,
            CutoverPlanRevisionMapper planMapper,
            CutoverPlanStepMapper stepMapper,
            CutoverSupportArrangementMapper supportMapper,
            CutoverRuntimeApprovalSourceSnapshotCodec codec) {
        return new CutoverRuntimeApprovalSourceAssembler(taskMapper, assessmentMapper, checklistMapper, itemMapper, resultMapper, planMapper, stepMapper, supportMapper, codec);
    }

    @Bean
    CutoverRuntimeApprovalApplicationService cutoverRuntimeApprovalApplicationService(CutoverRuntimeApprovalSourceAssembler sourceAssembler,
            CutoverApprovalInstanceMapper instanceMapper,
            cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverRuntimeApprovalNodeMapper nodeMapper,
            CutoverApprovalNotificationMapper notificationMapper,
            cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverRuntimeApprovalReviewItemMapper reviewItemMapper,
            CutoverApprovalReassignmentMapper reassignmentMapper,
            CutoverTaskMapper taskMapper,
            CutoverTaskStageHistoryMapper historyMapper,
            ProjectCutoverRuntimeServiceManagerPort serviceManagerPort,
            CutoverApprovalRoleCandidatePort roleCandidatePort,
            CutoverApprovalProjectScopePort projectScopePort,
            PlatformCommandExecutionApi commandExecutionApi,
            CutoverWorkflowCoordinator workflow, cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverExecutionReadinessPolicy readiness) {
        return new CutoverRuntimeApprovalApplicationService(sourceAssembler, instanceMapper, nodeMapper, notificationMapper, reviewItemMapper, reassignmentMapper, taskMapper, historyMapper, serviceManagerPort, roleCandidatePort, projectScopePort, commandExecutionApi, () -> CutoverOwnerAdapters.actor(), Clock.systemDefaultZone(), workflow, readiness);
    }

    @Bean
    CutoverRuntimeApprovalQueryService cutoverRuntimeApprovalQueryService(CutoverApprovalInstanceMapper instanceMapper,
            cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverRuntimeApprovalNodeMapper nodeMapper,
            cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverRuntimeApprovalReviewItemMapper reviewMapper,
            CutoverTaskMapper taskMapper,
            ProjectCutoverRuntimeServiceManagerPort serviceManagerPort,
            CutoverApprovalRoleCandidatePort roleCandidatePort,
            CutoverApprovalProjectScopePort projectScopePort,
            CutoverRuntimeApprovalSourceSnapshotCodec snapshotCodec) {
        return new CutoverRuntimeApprovalQueryService(instanceMapper, nodeMapper, reviewMapper, taskMapper, serviceManagerPort, roleCandidatePort, projectScopePort, snapshotCodec);
    }

    @Bean
    CutoverRuntimeClosureApplicationService cutoverClosureApplicationService(CutoverTaskMapper taskMapper,
            CutoverApprovalInstanceMapper approvalMapper,
            CutoverPlanRevisionMapper planMapper,
            CutoverClosureMapper closureMapper,
            CutoverClosureAttachmentMapper attachmentMapper,
            CutoverCollectionEvidenceMapper evidenceMapper,
            CutoverTaskDeviceScopeMapper deviceScopeMapper,
            CutoverTaskStageHistoryMapper stageHistoryMapper,
            CutoverProjectScopePort projectScopePort,
            CutoverClosureFilePort filePort,
            PlatformCommandExecutionApi commandExecutionApi, cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverExecutionReadinessPolicy readiness) {
        return new CutoverRuntimeClosureApplicationService(taskMapper, approvalMapper, planMapper, closureMapper, attachmentMapper, evidenceMapper, deviceScopeMapper, stageHistoryMapper, projectScopePort, filePort, commandExecutionApi, Clock.systemDefaultZone(), readiness);
    }

    @Bean
    CutoverRuntimeClosureQueryService cutoverClosureQueryService(CutoverTaskMapper taskMapper,
            CutoverApprovalInstanceMapper approvalMapper,
            CutoverPlanRevisionMapper planMapper,
            CutoverClosureMapper closureMapper,
            CutoverClosureAttachmentMapper attachmentMapper,
            CutoverCollectionEvidenceMapper evidenceMapper,
            CutoverProjectScopePort projectScopePort) {
        return new CutoverRuntimeClosureQueryService(taskMapper, approvalMapper, planMapper, closureMapper, attachmentMapper, evidenceMapper, projectScopePort);
    }

    @Bean
    CutoverRuntimeApprovalFactTransactionExecutor cutoverRuntimeApprovalFactTransactionExecutor(CutoverRuntimeApprovalApplicationService applicationService,
            cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverRuntimeApprovalInstanceMapper instanceMapper) {
        return new CutoverRuntimeApprovalFactTransactionExecutor(applicationService, instanceMapper);
    }

    @Bean
    CutoverRuntimeApprovalFactApiImpl cutoverRuntimeApprovalFactApiImpl(CutoverRuntimeApprovalFactTransactionExecutor transactions) {
        return new CutoverRuntimeApprovalFactApiImpl(transactions);
    }

    @Bean
    CutoverRuntimePlanRequestCodec cutoverPlanRequestCodec() { return new CutoverRuntimePlanRequestCodec(); }

    @Bean
    CutoverClosureRequestCodec cutoverClosureRequestCodec() { return new CutoverClosureRequestCodec(); }

    @Bean
    CutoverApprovalRequestCodec cutoverApprovalRequestCodec() { return new CutoverApprovalRequestCodec(); }

    @Bean
    CutoverRuntimePlanContentCodec cutoverPlanContentCodec() { return new CutoverRuntimePlanContentCodec(); }

    @Bean
    CutoverRuntimeApprovalSourceSnapshotCodec cutoverRuntimeApprovalSourceSnapshotCodec() { return new CutoverRuntimeApprovalSourceSnapshotCodec(); }

    @Bean
    CutoverRuntimeChecklistMatcher cutoverChecklistMatcher() { return new CutoverRuntimeChecklistMatcher(); }

    @Bean
    CutoverNavigationDecisionPolicy cutoverNavigationDecisionPolicy() { return new CutoverNavigationDecisionPolicy(); }

    @Bean
    CutoverChecklistWorkbookWriter cutoverChecklistWorkbookWriter() { return new CutoverChecklistWorkbookWriter(); }

    @Bean
    CutoverRuntimeChecklistExportService cutoverChecklistExportService(CutoverRuntimeChecklistApplicationService service, CutoverChecklistWorkbookWriter writer, OperationAuditApi audit) {
        return new CutoverRuntimeChecklistExportService(service, writer, audit);
    }
}
