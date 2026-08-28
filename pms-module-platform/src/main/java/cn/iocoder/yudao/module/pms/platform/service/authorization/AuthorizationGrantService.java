package cn.iocoder.yudao.module.pms.platform.service.authorization;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantDTO;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageResult;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantQuery;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantRevokeCommand;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.authorization.AuthorizationGrantDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.AuthorizationGrantMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.query.AuthorizationGrantKeyQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.query.AuthorizationGrantPageCriteria;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.query.AuthorizationGrantRevokeUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.query.EffectiveAuthorizationGrantQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthorizationGrantService implements AuthorizationGrantApi {

    static final String SUBJECT_USER = "USER";
    static final String RESOURCE_CONTEXT_PROJ = "PROJ";
    static final String RESOURCE_PROJECT = "PROJECT";
    static final String ACTION_VIEW = "PROJECT_VIEW";
    static final String ACTION_MANAGE = "PROJECT_MANAGE";
    static final String SCOPE_CURRENT = "CURRENT_PROJECT";
    static final String SCOPE_DESCENDANTS = "PROJECT_AND_DESCENDANTS";
    static final String STATUS_ACTIVE = "ACTIVE";
    static final int MAX_PAGE_SIZE = 100;

    private final AuthorizationGrantMapper grantMapper;
    private final PlatformCommandExecutionApi commandExecutionApi;

    @Override
    public AuthorizationGrantDTO create(AuthorizationGrantCreateCommand command) {
        validateCreate(command);
        var result = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "PLT:AUTHORIZATION_GRANT:CREATE", command.actorId(), command.idempotencyKey()),
                command.requestDigest(), AuthorizationGrantDTO.class,
                () -> createGrant(command),
                grant -> successFacts("AUTHORIZATION_GRANT_CREATE", grant, command.idempotencyKey(),
                        command.reason()));
        return requireCompleted(result);
    }

    @Override
    public AuthorizationGrantDTO revoke(AuthorizationGrantRevokeCommand command) {
        validateRevoke(command);
        var result = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "PLT:AUTHORIZATION_GRANT:REVOKE", command.actorId(), command.idempotencyKey()),
                command.requestDigest(), AuthorizationGrantDTO.class,
                () -> revokeGrant(command),
                grant -> successFacts("AUTHORIZATION_GRANT_REVOKE", grant, command.idempotencyKey(),
                        command.reason()));
        return requireCompleted(result);
    }

    @Override
    public AuthorizationGrantDTO get(Long tenantId, Long grantId) {
        requireTenant(tenantId);
        requirePositive(grantId, "grantId");
        AuthorizationGrantDO grant = grantMapper.selectByTenantAndId(tenantId, grantId);
        return grant == null ? null : toDTO(grant);
    }

    @Override
    public List<AuthorizationGrantDTO> listEffective(AuthorizationGrantQuery query) {
        validateEffectiveQuery(query);
        if (query.resourceIds().isEmpty()) {
            return List.of();
        }
        EffectiveAuthorizationGrantQuery criteria = new EffectiveAuthorizationGrantQuery(
                query.tenantId(), query.subjectTypeCode(), query.subjectId(),
                query.resourceContextCode(), query.resourceTypeCode(), Set.copyOf(query.resourceIds()),
                query.actionCode(), query.effectiveAt());
        return grantMapper.selectListEffective(criteria).stream().map(this::toDTO).toList();
    }

    @Override
    public AuthorizationGrantPageResult page(AuthorizationGrantPageQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("授权分页查询不能为空");
        }
        requireTenant(query.tenantId());
        int pageNo = query.pageNo() == null ? 1 : query.pageNo();
        int pageSize = query.pageSize() == null ? 20 : query.pageSize();
        if (pageNo < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("授权分页参数无效");
        }
        validateOptionalPageCodes(query);
        if (query.subjectId() != null) {
            requirePositive(query.subjectId(), "subjectId");
        }
        if (query.resourceId() != null) {
            requirePositive(query.resourceId(), "resourceId");
        }
        AuthorizationGrantPageCriteria criteria = new AuthorizationGrantPageCriteria(
                query.tenantId(), query.subjectTypeCode(), query.subjectId(),
                query.resourceContextCode(), query.resourceTypeCode(), query.resourceId(),
                query.actionCode(), query.scopeCode(), query.statusCode(), query.effectiveAt(),
                (long) (pageNo - 1) * pageSize, pageSize);
        long total = grantMapper.selectCountPage(criteria);
        if (total == 0) {
            return new AuthorizationGrantPageResult(List.of(), 0);
        }
        return new AuthorizationGrantPageResult(
                grantMapper.selectListPage(criteria).stream().map(this::toDTO).toList(), total);
    }

    private AuthorizationGrantDTO createGrant(AuthorizationGrantCreateCommand command) {
        LocalDateTime now = LocalDateTime.now();
        AuthorizationGrantKeyQuery key = new AuthorizationGrantKeyQuery(
                command.tenantId(), command.subjectTypeCode(), command.subjectId(),
                command.resourceContextCode(), command.resourceTypeCode(), command.resourceId(),
                command.actionCode(), command.scopeCode(), now);
        grantMapper.expireCurrentByKey(key);

        AuthorizationGrantDO grant = new AuthorizationGrantDO();
        grant.setTenantId(command.tenantId());
        grant.setSubjectTypeCode(command.subjectTypeCode());
        grant.setSubjectId(command.subjectId());
        grant.setResourceContextCode(command.resourceContextCode());
        grant.setResourceTypeCode(command.resourceTypeCode());
        grant.setResourceId(command.resourceId());
        grant.setActionCode(command.actionCode());
        grant.setScopeCode(command.scopeCode());
        grant.setEffectiveFrom(toDatabaseTime(command.effectiveFrom()));
        grant.setEffectiveTo(toDatabaseTime(command.effectiveTo()));
        grant.setStatusCode(STATUS_ACTIVE);
        grant.setSourceContextCode(command.sourceContextCode());
        grant.setSourceObjectType(blankToNull(command.sourceObjectType()));
        grant.setSourceObjectId(blankToNull(command.sourceObjectId()));
        grant.setGrantedBy(command.actorId());
        grant.setGrantedAt(now);
        grant.setVersion(0);
        grant.setCurrentMarker(1);
        try {
            if (grantMapper.insert(grant) != 1) {
                throw new IllegalStateException("AUTHORIZATION_GRANT_CREATE_FAILED");
            }
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("AUTHORIZATION_GRANT_CURRENT_CONFLICT", ex);
        }
        return toDTO(grant);
    }

    private AuthorizationGrantDTO revokeGrant(AuthorizationGrantRevokeCommand command) {
        AuthorizationGrantDO current = grantMapper.selectByTenantAndId(command.tenantId(), command.grantId());
        if (current == null) {
            throw new IllegalStateException("AUTHORIZATION_GRANT_NOT_FOUND");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = grantMapper.revoke(new AuthorizationGrantRevokeUpdate(
                command.tenantId(), command.grantId(), command.expectedVersion(), command.actorId(),
                now, command.reason().trim()));
        if (updated != 1) {
            throw new IllegalStateException("AUTHORIZATION_GRANT_VERSION_CONFLICT");
        }
        AuthorizationGrantDO revoked = grantMapper.selectByTenantAndId(command.tenantId(), command.grantId());
        if (revoked == null) {
            throw new IllegalStateException("AUTHORIZATION_GRANT_NOT_FOUND");
        }
        return toDTO(revoked);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            String operationCode, AuthorizationGrantDTO grant, String correlationId, String reason) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("grantId", grant.id());
        detail.put("subjectId", grant.subjectId());
        detail.put("resourceId", grant.resourceId());
        detail.put("actionCode", grant.actionCode());
        detail.put("scopeCode", grant.scopeCode());
        detail.put("statusCode", grant.statusCode());
        if (!isBlank(reason)) {
            detail.put("reason", reason.trim());
        }
        return new PlatformCommandExecutionApi.SuccessFacts(
                operationCode, "AuthorizationGrant", String.valueOf(grant.id()), correlationId,
                JsonUtils.toJsonString(detail), null, null);
    }

    private AuthorizationGrantDTO requireCompleted(
            PlatformCommandExecutionApi.ExecutionResult<AuthorizationGrantDTO> result) {
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw new IllegalStateException("IDEMPOTENCY_CONFLICT");
        }
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || result.response() == null) {
            throw new IllegalStateException("IDEMPOTENCY_IN_PROGRESS");
        }
        return result.response();
    }

    private void validateCreate(AuthorizationGrantCreateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("授权创建命令不能为空");
        }
        requireTenant(command.tenantId());
        requirePositive(command.actorId(), "actorId");
        requirePositive(command.subjectId(), "subjectId");
        requirePositive(command.resourceId(), "resourceId");
        requireText(command.idempotencyKey(), "idempotencyKey", 128);
        requireDigest(command.requestDigest());
        requireCode(command.subjectTypeCode(), SUBJECT_USER, "subjectTypeCode");
        requireCode(command.resourceContextCode(), RESOURCE_CONTEXT_PROJ, "resourceContextCode");
        requireCode(command.resourceTypeCode(), RESOURCE_PROJECT, "resourceTypeCode");
        requireOneOf(command.actionCode(), "actionCode", ACTION_VIEW, ACTION_MANAGE);
        requireOneOf(command.scopeCode(), "scopeCode", SCOPE_CURRENT, SCOPE_DESCENDANTS);
        requireCode(command.sourceContextCode(), RESOURCE_CONTEXT_PROJ, "sourceContextCode");
        if (command.effectiveFrom() == null
                || command.effectiveTo() != null && !command.effectiveTo().isAfter(command.effectiveFrom())) {
            throw new IllegalArgumentException("授权生效区间无效");
        }
        if (command.reason() != null && command.reason().length() > 500) {
            throw new IllegalArgumentException("授权原因过长");
        }
        validateOptionalText(command.sourceObjectType(), "sourceObjectType", 64);
        validateOptionalText(command.sourceObjectId(), "sourceObjectId", 128);
    }

    private void validateRevoke(AuthorizationGrantRevokeCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("授权撤销命令不能为空");
        }
        requireTenant(command.tenantId());
        requirePositive(command.actorId(), "actorId");
        requirePositive(command.grantId(), "grantId");
        if (command.expectedVersion() == null || command.expectedVersion() < 0) {
            throw new IllegalArgumentException("expectedVersion无效");
        }
        requireText(command.reason(), "reason", 500);
        requireText(command.idempotencyKey(), "idempotencyKey", 128);
        requireDigest(command.requestDigest());
    }

    private void validateEffectiveQuery(AuthorizationGrantQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("有效授权查询不能为空");
        }
        requireTenant(query.tenantId());
        requirePositive(query.subjectId(), "subjectId");
        requireCode(query.subjectTypeCode(), SUBJECT_USER, "subjectTypeCode");
        requireCode(query.resourceContextCode(), RESOURCE_CONTEXT_PROJ, "resourceContextCode");
        requireCode(query.resourceTypeCode(), RESOURCE_PROJECT, "resourceTypeCode");
        requireOneOf(query.actionCode(), "actionCode", ACTION_VIEW, ACTION_MANAGE);
        if (query.resourceIds() == null) {
            throw new IllegalArgumentException("resourceIds不能为空");
        }
        if (query.resourceIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("resourceIds包含无效值");
        }
        if (query.effectiveAt() == null) {
            throw new IllegalArgumentException("effectiveAt不能为空");
        }
    }

    private AuthorizationGrantDTO toDTO(AuthorizationGrantDO grant) {
        return new AuthorizationGrantDTO(
                grant.getId(), grant.getTenantId(), grant.getSubjectTypeCode(), grant.getSubjectId(),
                grant.getResourceContextCode(), grant.getResourceTypeCode(), grant.getResourceId(),
                grant.getActionCode(), grant.getScopeCode(), grant.getEffectiveFrom(), grant.getEffectiveTo(),
                grant.getStatusCode(), grant.getSourceContextCode(), grant.getSourceObjectType(),
                grant.getSourceObjectId(), grant.getGrantedBy(), grant.getGrantedAt(), grant.getRevokedBy(),
                grant.getRevokedAt(), grant.getRevokeReason(), grant.getVersion());
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + "无效");
        }
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || tenantId < 0) {
            throw new IllegalArgumentException("tenantId无效");
        }
        Long contextTenantId = TenantContextHolder.getTenantId();
        if (contextTenantId != null && !contextTenantId.equals(tenantId)) {
            throw new IllegalArgumentException("tenantId与当前租户上下文不一致");
        }
    }

    private void requireCode(String actual, String expected, String field) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(field + "无效");
        }
    }

    private void requireOneOf(String actual, String field, String first, String second) {
        if (!first.equals(actual) && !second.equals(actual)) {
            throw new IllegalArgumentException(field + "无效");
        }
    }

    private void requireText(String value, String field, int maxLength) {
        if (isBlank(value) || value.length() > maxLength) {
            throw new IllegalArgumentException(field + "无效");
        }
    }

    private void validateOptionalText(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(field + "无效");
        }
    }

    private void validateOptionalPageCodes(AuthorizationGrantPageQuery query) {
        if (query.subjectTypeCode() != null) {
            requireCode(query.subjectTypeCode(), SUBJECT_USER, "subjectTypeCode");
        }
        if (query.resourceContextCode() != null) {
            requireCode(query.resourceContextCode(), RESOURCE_CONTEXT_PROJ, "resourceContextCode");
        }
        if (query.resourceTypeCode() != null) {
            requireCode(query.resourceTypeCode(), RESOURCE_PROJECT, "resourceTypeCode");
        }
        if (query.actionCode() != null) {
            requireOneOf(query.actionCode(), "actionCode", ACTION_VIEW, ACTION_MANAGE);
        }
        if (query.scopeCode() != null) {
            requireOneOf(query.scopeCode(), "scopeCode", SCOPE_CURRENT, SCOPE_DESCENDANTS);
        }
        if (query.statusCode() != null
                && !Set.of(STATUS_ACTIVE, "REVOKED", "EXPIRED").contains(query.statusCode())) {
            throw new IllegalArgumentException("statusCode无效");
        }
    }

    private void requireDigest(String digest) {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestDigest无效");
        }
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private LocalDateTime toDatabaseTime(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.SECONDS);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
