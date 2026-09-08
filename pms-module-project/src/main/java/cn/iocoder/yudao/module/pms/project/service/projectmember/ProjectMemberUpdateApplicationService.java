package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationAuthorizationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManagerAssignmentApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** PM-01：复用既有两类写命令，通过外层事务保持联合指派全有或全无。 */
@Service
@RequiredArgsConstructor
public class ProjectMemberUpdateApplicationService {
    private static final String SCOPE = "POST:/api/v1/pms/projects/{id}/actions/update-members";
    private final ProjectCreationAuthorizationService functionAuthorization;
    private final ProjectAuthorizationGuard projectAuthorization;
    private final PlatformCommandExecutionApi commandApi;
    private final ProjectManagerAssignmentApplicationService serviceManagers;
    private final ProjectManagerMemberApplicationService projectManagers;

    // REQUIRED使两个已有应用服务与审计/Outbox共用物理事务。
    // https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html
    @Transactional(rollbackFor = Exception.class)
    public ProjectMemberUpdateResult update(ProjectMemberUpdateCommand command,
                                            ProjectManagerMemberApplicationService.Actor actor) {
        if (command == null || actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.userId() == null || actor.userId() <= 0
                || actor.correlationId() == null || actor.correlationId().isBlank()
                || command.projectId() == null || command.projectId() <= 0
                || command.expectedVersion() == null || command.expectedVersion() < 0
                || command.reason() == null || command.reason().isBlank() || command.reason().length() > 500
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 128) {
            throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "成员操作参数无效");
        }
        functionAuthorization.assertCanAssign(actor.userId());
        projectAuthorization.assertCanAssign(new ProjectAuthorizationGuard.Actor(actor.tenantId(), actor.userId()), command.projectId());
        String digest = DigestUtil.sha256Hex(JsonUtils.toJsonString(new Object[]{command.projectId(),
                command.expectedVersion(), command.serviceManager(), new TreeSet<>(command.addUserIds()),
                new TreeSet<>(command.removeUserIds()), command.primaryUserId(), command.reason().trim()}));
        var execution = commandApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), SCOPE, actor.userId(), command.idempotencyKey()), digest,
                ProjectMemberUpdateResult.class, () -> updateOnce(command, actor, digest),
                result -> new PlatformCommandExecutionApi.SuccessFacts("PROJECT_MEMBERS_UPDATE", "Project",
                        String.valueOf(command.projectId()), actor.correlationId(),
                        JsonUtils.toJsonString(Map.of("reason", command.reason().trim(), "result", result)), null, null));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return execution.response();
    }

    private ProjectMemberUpdateResult updateOnce(ProjectMemberUpdateCommand command,
            ProjectManagerMemberApplicationService.Actor actor, String digest) {
        int version = command.expectedVersion();
        AssignServiceManagerResult serviceResult = null;
        var selection = command.serviceManager();
        // 子键只在外层首次执行回调内产生；外层同键重放不会再次进入此处。
        if (selection != null) {
            serviceResult = serviceManagers.assign(new AssignServiceManagerCommand(command.projectId(), version,
                    selection.levelCode(), selection.managerId(), selection.siteId(), selection.assignmentType(),
                    selection.departmentId(), selection.departmentCode(), command.reason(), UUID.randomUUID().toString(), digest),
                    new ProjectManagerAssignmentApplicationService.Actor(actor.tenantId(), actor.userId(), actor.correlationId()));
            version = serviceResult.version();
        }
        var managerResult = projectManagers.updateAuthorized(new ProjectManagerMemberCommand(command.projectId(), version,
                command.addUserIds(), command.removeUserIds(), command.primaryUserId(), command.reason(),
                UUID.randomUUID().toString()), actor);
        var serviceProjection = serviceResult == null ? null : new ProjectMemberUpdateResult.ServiceManager(
                serviceResult.assignmentId(), serviceResult.effectiveFrom(),
                serviceResult.previousPrimaryManagerId(), serviceResult.currentPrimaryManagerId());
        return new ProjectMemberUpdateResult(serviceProjection, managerResult);
    }
}
