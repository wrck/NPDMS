package cn.iocoder.yudao.module.pms.project.service.projectauthorization;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantDTO;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageResult;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantRevokeCommand;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard.Actor;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard.ManagementBounds;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_AUTHORIZATION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_AUTHORIZATION_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_AUTHORIZATION_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_AUTHORIZATION_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard.SCOPE_CURRENT;
import static cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard.SCOPE_DESCENDANTS;

@Service
@RequiredArgsConstructor
public class ProjectAuthorizationApplicationService {

    private static final String SUBJECT_USER = "USER";
    private static final String RESOURCE_CONTEXT = "PROJ";
    private static final String RESOURCE_TYPE = "PROJECT";

    private final AuthorizationGrantApi authorizationGrantApi;
    private final ProjectAuthorizationGuard guard;
    private final AdminUserApi adminUserApi;

    @Transactional(rollbackFor = Exception.class)
    public AuthorizationGrantDTO create(CreateCommand command, Actor actor) {
        validateCreate(command, actor);
        guard.assertCanCreate(actor, command.projectId(), command.actionCode(), command.scopeCode());
        adminUserApi.validateUser(command.subjectUserId());
        LocalDateTime effectiveFrom = command.effectiveFrom() == null
                ? LocalDateTime.now() : command.effectiveFrom();
        try {
            return authorizationGrantApi.create(new AuthorizationGrantCreateCommand(
                    actor.tenantId(), actor.actorId(), command.idempotencyKey(), command.requestDigest(),
                    SUBJECT_USER, command.subjectUserId(), RESOURCE_CONTEXT, RESOURCE_TYPE,
                    command.projectId(), command.actionCode(), command.scopeCode(), effectiveFrom,
                    command.effectiveTo(), RESOURCE_CONTEXT, "Project",
                    String.valueOf(command.projectId()), command.reason()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw translate(ex);
        }
    }

    public AuthorizationGrantPageResult page(PageQuery query, Actor actor) {
        validatePage(query, actor);
        ManagementBounds bounds = guard.assertCanQuery(actor, query.projectId(), false);
        if (SCOPE_DESCENDANTS.equals(query.scopeCode()) && !bounds.managesDescendants()) {
            return new AuthorizationGrantPageResult(List.of(), 0);
        }
        String effectiveScope = query.scopeCode() == null && !bounds.managesDescendants()
                ? SCOPE_CURRENT : query.scopeCode();
        try {
            return authorizationGrantApi.page(new AuthorizationGrantPageQuery(
                    actor.tenantId(), SUBJECT_USER, query.subjectUserId(), RESOURCE_CONTEXT,
                    RESOURCE_TYPE, query.projectId(), query.actionCode(), effectiveScope,
                    query.statusCode(), query.effectiveAt(), query.pageNo(), query.pageSize()));
        } catch (IllegalArgumentException ex) {
            throw exception(PROJECT_AUTHORIZATION_INVALID);
        }
    }

    public AuthorizationGrantDTO get(Long grantId, Actor actor) {
        requireActor(actor);
        AuthorizationGrantDTO grant = requireGrant(actor.tenantId(), grantId);
        ManagementBounds bounds = guard.assertCanQuery(actor, grant.resourceId(), true);
        if (SCOPE_DESCENDANTS.equals(grant.scopeCode()) && !bounds.managesDescendants()) {
            throw exception(PROJECT_AUTHORIZATION_NOT_FOUND);
        }
        return grant;
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthorizationGrantDTO revoke(RevokeCommand command, Actor actor) {
        validateRevoke(command, actor);
        AuthorizationGrantDTO grant = requireGrant(actor.tenantId(), command.grantId());
        guard.assertCanRevoke(actor, grant);
        try {
            return authorizationGrantApi.revoke(new AuthorizationGrantRevokeCommand(
                    actor.tenantId(), actor.actorId(), command.grantId(), command.expectedVersion(),
                    command.reason(), command.idempotencyKey(), command.requestDigest()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw translate(ex);
        }
    }

    private AuthorizationGrantDTO requireGrant(Long tenantId, Long grantId) {
        AuthorizationGrantDTO grant = authorizationGrantApi.get(tenantId, grantId);
        if (grant == null || !Objects.equals(tenantId, grant.tenantId())
                || !SUBJECT_USER.equals(grant.subjectTypeCode())
                || !RESOURCE_CONTEXT.equals(grant.resourceContextCode())
                || !RESOURCE_TYPE.equals(grant.resourceTypeCode())) {
            throw exception(PROJECT_AUTHORIZATION_NOT_FOUND);
        }
        return grant;
    }

    private ServiceException translate(RuntimeException failure) {
        String message = failure.getMessage();
        if ("IDEMPOTENCY_CONFLICT".equals(message)) {
            return exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        }
        if ("IDEMPOTENCY_IN_PROGRESS".equals(message)) {
            return exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        }
        if ("AUTHORIZATION_GRANT_NOT_FOUND".equals(message)) {
            return exception(PROJECT_AUTHORIZATION_NOT_FOUND);
        }
        if ("AUTHORIZATION_GRANT_VERSION_CONFLICT".equals(message)) {
            return exception(PROJECT_AUTHORIZATION_VERSION_CONFLICT);
        }
        if ("AUTHORIZATION_GRANT_CURRENT_CONFLICT".equals(message)) {
            return exception(PROJECT_AUTHORIZATION_CONFLICT);
        }
        return exception(PROJECT_AUTHORIZATION_INVALID);
    }

    private void validateCreate(CreateCommand command, Actor actor) {
        requireActor(actor);
        if (command == null || command.projectId() == null || command.projectId() <= 0
                || command.subjectUserId() == null || command.subjectUserId() <= 0
                || isBlank(command.actionCode()) || isBlank(command.scopeCode())
                || isBlank(command.idempotencyKey()) || isBlank(command.requestDigest())
                || command.effectiveTo() != null && command.effectiveFrom() != null
                && !command.effectiveTo().isAfter(command.effectiveFrom())) {
            throw exception(PROJECT_AUTHORIZATION_INVALID);
        }
    }

    private void validatePage(PageQuery query, Actor actor) {
        requireActor(actor);
        if (query == null || query.projectId() == null || query.projectId() <= 0
                || query.pageNo() == null || query.pageNo() < 1
                || query.pageSize() == null || query.pageSize() < 1 || query.pageSize() > 100) {
            throw exception(PROJECT_AUTHORIZATION_INVALID);
        }
    }

    private void validateRevoke(RevokeCommand command, Actor actor) {
        requireActor(actor);
        if (command == null || command.grantId() == null || command.grantId() <= 0
                || command.expectedVersion() == null || command.expectedVersion() < 0
                || isBlank(command.reason()) || isBlank(command.idempotencyKey())
                || isBlank(command.requestDigest())) {
            throw exception(PROJECT_AUTHORIZATION_INVALID);
        }
    }

    private void requireActor(Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0) {
            throw exception(PROJECT_AUTHORIZATION_INVALID);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record CreateCommand(Long projectId, Long subjectUserId, String actionCode,
                                String scopeCode, LocalDateTime effectiveFrom,
                                LocalDateTime effectiveTo, String reason,
                                String idempotencyKey, String requestDigest) {
    }

    public record PageQuery(Long projectId, Long subjectUserId, String actionCode,
                            String scopeCode, String statusCode, LocalDateTime effectiveAt,
                            Integer pageNo, Integer pageSize) {
    }

    public record RevokeCommand(Long grantId, Integer expectedVersion, String reason,
                                String idempotencyKey, String requestDigest) {
    }
}
