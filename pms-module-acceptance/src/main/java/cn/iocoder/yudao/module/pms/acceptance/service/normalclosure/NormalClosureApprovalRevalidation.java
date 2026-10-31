package cn.iocoder.yudao.module.pms.acceptance.service.normalclosure;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.normalclosure.NormalClosureMapper;
import cn.iocoder.yudao.module.pms.acceptance.api.normalclosure.NormalClosureApprovalFactApi;
import cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureExitApi.ClosureExitCommand;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.ClosureRevalidation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** ACC-owned proof for the internal approved-closure path; never an HTTP authorization grant. */
@Service @RequiredArgsConstructor
public class NormalClosureApprovalRevalidation implements NormalClosureApprovalFactApi {
    private final NormalClosureMapper applications;
    private final BpmNormalClosureApi bpm;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void require(ClosureRevalidation query) {
        if (query == null || query.context() == null || query.approval() == null) throw denied();
        var context = query.context(); var command = query.approval();
        if (context.actorId() == null || !Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId())
                || !Objects.equals(context.actorId(), command.expectedReviewerUserId())
                || context.tenantId() == null || !Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())
                || !Objects.equals(context.tenantId(), command.tenantId())
                || context.projectId() == null || !Objects.equals(context.projectId(), command.projectId())
                || context.taskId() == null || command.applicationId() == null || command.snapshotId() == null
                || command.processInstanceId() == null || command.processInstanceId().isBlank()
                || !Objects.equals(context.correlationId(), command.correlationId())
                || !("BPM_NORMAL_CLOSURE:" + command.processInstanceId()).equals(command.correlationId())) throw denied();
        requireCommand(command);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Fact requireApprovedApplicant(Query query) {
        if (query == null || query.tenantId() == null || TenantContextHolder.isIgnore()
                || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
                || query.reviewerUserId() == null || !Objects.equals(query.reviewerUserId(), SecurityFrameworkUtils.getLoginUserId())
                || query.projectId() == null || query.applicationId() == null || query.applicantUserId() == null
                || query.processInstanceId() == null || query.processInstanceId().isBlank()) throw denied();
        var application = applications.selectApplicationForUpdate(new NormalClosureMapper.ApplicationQuery(
                query.tenantId(), query.projectId(), query.applicationId()));
        if (application == null) throw denied();
        var snapshot = applications.selectSnapshot(new NormalClosureMapper.SnapshotQuery(
                query.tenantId(), query.projectId(), application.getSnapshotId()));
        if (snapshot == null) throw denied();
        requireCommand(new ClosureExitCommand(query.tenantId(), query.projectId(), query.expectedProjectVersion(),
                query.expectedTreeVersion(), query.applicantUserId(), "BPM_NORMAL_CLOSURE:" + query.processInstanceId(),
                query.applicationId(), application.getSnapshotId(), query.reviewerUserId(), application.getFromStage(),
                application.getServiceManagerUserId(), snapshot.getSourceDigest(), query.processInstanceId(), application.getVersion()));
        return new Fact(application.getSnapshotId(), application.getVersion(), application.getFromStage(),
                application.getServiceManagerUserId(), snapshot.getSourceDigest());
    }

    private void requireCommand(ClosureExitCommand command) {
        var application = applications.selectApplicationForUpdate(new NormalClosureMapper.ApplicationQuery(
                command.tenantId(), command.projectId(), command.applicationId()));
        if (application == null || Boolean.TRUE.equals(application.getDeleted())
                || !Objects.equals(application.getTenantId(), command.tenantId())
                || !Objects.equals(application.getProjectId(), command.projectId())
                || !Objects.equals(application.getId(), command.applicationId())
                || !"IN_REVIEW".equals(application.getStatus()) || !"NORMAL".equals(application.getClosureType())
                || !Objects.equals(application.getSnapshotId(), command.snapshotId())
                || !Objects.equals(application.getProcessInstanceId(), command.processInstanceId())
                || !Objects.equals(application.getApplicantUserId(), command.actorId())
                || !Objects.equals(application.getReviewerUserId(), command.expectedReviewerUserId())
                || !Objects.equals(application.getServiceManagerUserId(), command.expectedServiceManagerUserId())
                || !Objects.equals(application.getProjectVersion(), command.expectedProjectVersion())
                || !Objects.equals(application.getTreeVersion(), command.expectedTreeVersion())
                || !Objects.equals(application.getFromStage(), command.expectedFromStage())
                || !Objects.equals(application.getVersion(), command.sourceRecordRevision())) throw denied();
        var snapshot = applications.selectSnapshot(new NormalClosureMapper.SnapshotQuery(
                command.tenantId(), command.projectId(), command.snapshotId()));
        if (snapshot == null || Boolean.TRUE.equals(snapshot.getDeleted())
                || !Objects.equals(snapshot.getTenantId(), command.tenantId())
                || !Objects.equals(snapshot.getProjectId(), command.projectId())
                || !Objects.equals(snapshot.getId(), command.snapshotId()) || !Boolean.TRUE.equals(snapshot.getPassed())
                || !Objects.equals(snapshot.getProjectVersion(), command.expectedProjectVersion())
                || !Objects.equals(snapshot.getTreeVersion(), command.expectedTreeVersion())
                || !Objects.equals(snapshot.getFromStage(), command.expectedFromStage())
                || snapshot.getSourceDigest() == null || snapshot.getSourceDigest().isBlank()
                || !Objects.equals(snapshot.getSourceDigest(), command.expectedSourceDigest())) throw denied();
        var result = bpm.inspectResult(command.tenantId(), command.processInstanceId());
        NormalClosureBpmEvidence.requireResult(application, result);
        if (!"APPROVE".equals(result.status())) throw denied();
    }

    private static IllegalStateException denied() {
        return new IllegalStateException("CLOSURE_INTERNAL_REVALIDATION_FORBIDDEN");
    }
}
