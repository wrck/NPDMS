package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.AuthorityCandidateDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.AuthorityCandidateMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.AuthorityCandidateOwnerFact;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.*;
import cn.iocoder.yudao.module.pms.commerce.service.authorization.CompanyScopeGuard;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** PLATFORM_MANUAL候选只追加并关联既有Owner，不创建或改写权威主档。 */
@Service
public class CommerceAuthorityCandidateService {

    private static final String PLATFORM_MANUAL = "PLATFORM_MANUAL";
    private static final String PENDING = "PENDING_RECONCILIATION";
    private static final Set<String> OBJECT_TYPES = Set.of("CONTRACT", "SALES_ORDER", "ORDER_LINE");

    private final PlatformCommandExecutionApi commandExecutionApi;
    private final AuthorityCandidateMapper candidateMapper;
    private final CompanyScopeGuard companyScopeGuard;
    private final Clock clock;

    @Autowired
    public CommerceAuthorityCandidateService(PlatformCommandExecutionApi commandExecutionApi,
                                             AuthorityCandidateMapper candidateMapper,
                                             CompanyScopeGuard companyScopeGuard) {
        this(commandExecutionApi, candidateMapper, companyScopeGuard, Clock.systemDefaultZone());
    }

    CommerceAuthorityCandidateService(PlatformCommandExecutionApi commandExecutionApi,
                                      AuthorityCandidateMapper candidateMapper,
                                      CompanyScopeGuard companyScopeGuard, Clock clock) {
        this.commandExecutionApi = commandExecutionApi;
        this.candidateMapper = candidateMapper;
        this.companyScopeGuard = companyScopeGuard;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public CandidateResult create(CreateCandidateCommand command) {
        requireTrustedActor(command == null ? null : command.tenantId(), command == null ? null : command.actorId());
        requireObjectType(command.objectType());
        requireText(command.sourceKey(), 128, "sourceKey");
        requireText(command.candidateVersion(), 128, "candidateVersion");
        requireText(command.idempotencyKey(), 128, "idempotencyKey");
        requireText(command.correlationId(), 128, "correlationId");
        JsonNode payload = requireObjectJson(command.candidatePayload(), "candidatePayload");
        JsonNode evidence = requireObjectJson(command.evidenceReference(), "evidenceReference");
        String companyCode = requireCompanyCode(payload);
        companyScopeGuard.requireCompany(command.actorId(), companyCode);

        return execute(command.tenantId(), command.actorId(), "CREATE", command.idempotencyKey(),
                digest(Map.of("objectType", command.objectType(), "sourceKey", command.sourceKey().trim(),
                        "candidateVersion", command.candidateVersion().trim(), "payload", payload,
                        "evidence", evidence)), command.correlationId(), () -> {
                    AuthorityCandidateIdentityQuery identity = new AuthorityCandidateIdentityQuery(command.tenantId(),
                            command.objectType(), PLATFORM_MANUAL, command.sourceKey().trim(),
                            command.candidateVersion().trim());
                    AuthorityCandidateDO current = candidateMapper.selectByIdentityForUpdate(identity);
                    if (current != null) {
                        if (jsonEquals(current.getCandidatePayload(), payload)
                                && jsonEquals(current.getEvidenceReference(), evidence)) {
                            return view(current);
                        }
                        throw failure(Code.SOURCE_VERSION_PAYLOAD_CONFLICT, "同候选版本载荷不一致");
                    }
                    LocalDateTime now = LocalDateTime.now(clock);
                    AuthorityCandidateDO row = new AuthorityCandidateDO();
                    row.setTenantId(command.tenantId());
                    row.setObjectType(command.objectType());
                    row.setCandidateSourceSystem(PLATFORM_MANUAL);
                    row.setCandidateSourceKey(command.sourceKey().trim());
                    row.setCandidateVersion(command.candidateVersion().trim());
                    row.setCandidatePayload(JsonUtils.toJsonString(payload));
                    row.setEvidenceReference(JsonUtils.toJsonString(evidence));
                    row.setCandidateStatus(PENDING);
                    row.setSubmittedBy(command.actorId());
                    row.setSubmittedAt(now);
                    row.setVersion(0);
                    auditBase(row, command.actorId(), now);
                    requireWrite(candidateMapper.insert(row), "候选创建失败");
                    return view(row);
                });
    }

    @Transactional(rollbackFor = Exception.class)
    public CandidateResult reconcile(DecideCandidateCommand command) {
        return decide(command, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public CandidateResult reject(DecideCandidateCommand command) {
        return decide(command, false);
    }

    @Transactional(readOnly = true)
    public List<CandidateResult> listVisible(ListCandidatesQuery query) {
        requireTrustedActor(query == null ? null : query.tenantId(), query == null ? null : query.actorId());
        if (query.pageNo() < 1 || query.pageSize() < 1 || query.pageSize() > 100) {
            throw failure(Code.INVALID_REQUEST, "分页参数非法");
        }
        String objectType = trimToNull(query.objectType());
        if (objectType != null) requireObjectType(objectType);
        String status = trimToNull(query.candidateStatus());
        if (status != null && !Set.of(PENDING, "MATCHED", "REJECTED").contains(status)) {
            throw failure(Code.INVALID_REQUEST, "candidateStatus非法");
        }
        Set<String> companyCodes = companyScopeGuard.activeCompanyCodes(query.actorId());
        if (companyCodes.isEmpty()) return List.of();
        return candidateMapper.selectVisiblePage(new AuthorityCandidateVisibleQuery(query.tenantId(), companyCodes,
                objectType, status, (query.pageNo() - 1) * query.pageSize(), query.pageSize()))
                .stream().map(this::view).toList();
    }

    private CandidateResult decide(DecideCandidateCommand command, boolean matched) {
        requireTrustedActor(command == null ? null : command.tenantId(), command == null ? null : command.actorId());
        if (command.candidateId() == null || command.candidateId() <= 0 || command.expectedVersion() == null
                || command.expectedVersion() < 0 || matched && (command.ownerId() == null || command.ownerId() <= 0)) {
            throw failure(Code.INVALID_REQUEST, "候选决定参数非法");
        }
        requireText(command.idempotencyKey(), 128, "idempotencyKey");
        requireText(command.correlationId(), 128, "correlationId");
        AuthorityCandidateDO visible = candidateMapper.selectCandidateById(
                new AuthorityCandidateIdQuery(command.tenantId(), command.candidateId()));
        if (visible == null) throw failure(Code.NOT_FOUND, "候选不存在");
        String companyCode = requireCompanyCode(requireObjectJson(visible.getCandidatePayload(), "candidatePayload"));
        companyScopeGuard.requireCompany(command.actorId(), companyCode);

        String action = matched ? "MATCH" : "REJECT";
        Map<String, Object> digestInput = new LinkedHashMap<>();
        digestInput.put("candidateId", command.candidateId());
        digestInput.put("expectedVersion", command.expectedVersion());
        digestInput.put("ownerId", matched ? command.ownerId() : null);
        return execute(command.tenantId(), command.actorId(), action, command.idempotencyKey(),
                digest(digestInput), command.correlationId(), () -> {
                    AuthorityCandidateDO current = candidateMapper.selectByIdForUpdate(
                            new AuthorityCandidateIdQuery(command.tenantId(), command.candidateId()));
                    if (current == null) throw failure(Code.NOT_FOUND, "候选不存在");
                    if (!Objects.equals(current.getVersion(), command.expectedVersion())) {
                        throw failure(Code.VERSION_CONFLICT, "候选版本已变化");
                    }
                    if (!PENDING.equals(current.getCandidateStatus())) {
                        throw failure(Code.STATE_CONFLICT, "候选已完成决定，不得覆盖历史");
                    }
                    String lockedCompany = requireCompanyCode(
                            requireObjectJson(current.getCandidatePayload(), "candidatePayload"));
                    companyScopeGuard.requireCompany(command.actorId(), lockedCompany);
                    AuthorityCandidateOwnerFact owner = null;
                    if (matched) {
                        owner = candidateMapper.selectConfirmedOwnerForUpdate(new AuthorityCandidateOwnerQuery(
                                command.tenantId(), current.getObjectType(), command.ownerId()));
                        if (owner == null || !"CONFIRMED".equals(owner.authorityStatus())) {
                            throw failure(Code.OWNER_INVALID, "目标Owner不存在或未获权威确认");
                        }
                        if (!Objects.equals(lockedCompany, trimToNull(owner.companyCode()))) {
                            throw failure(Code.COMPANY_SCOPE_MISMATCH, "候选与Owner公司不一致");
                        }
                    }
                    LocalDateTime now = LocalDateTime.now(clock);
                    AuthorityCandidateDecisionUpdate update = new AuthorityCandidateDecisionUpdate(command.tenantId(),
                            current.getId(), current.getVersion(), matched ? "MATCHED" : "REJECTED",
                            matched ? owner.ownerTable() : null, matched ? owner.ownerId() : null,
                            matched ? owner.sourceVersion() : null, command.actorId(), now);
                    requireWrite(candidateMapper.decideByVersion(update), "候选决定并发冲突");
                    current.setCandidateStatus(update.candidateStatus());
                    current.setMatchedOwnerTable(update.matchedOwnerTable());
                    current.setMatchedOwnerId(update.matchedOwnerId());
                    current.setMatchedOwnerSourceVersion(update.matchedOwnerSourceVersion());
                    current.setDecidedBy(update.decidedBy());
                    current.setDecidedAt(update.decidedAt());
                    current.setVersion(current.getVersion() + 1);
                    return view(current);
                });
    }

    private CandidateResult execute(Long tenantId, Long actorId, String action, String key, String requestDigest,
                                    String correlationId, Supplier<CandidateResult> operation) {
        PlatformCommandExecutionApi.ExecutionResult<CandidateResult> result = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(tenantId,
                        "COM:AUTHORITY:CANDIDATE:" + action, actorId, key), requestDigest,
                CandidateResult.class, operation, response -> new PlatformCommandExecutionApi.SuccessFacts(
                        "COM_AUTHORITY_CANDIDATE_" + action, "CommerceAuthorityCandidate",
                        String.valueOf(response.candidateId()), correlationId, JsonUtils.toJsonString(response), null, null));
        return switch (result.decision()) {
            case NEW, REPLAY_COMPLETED -> result.response();
            case CONFLICT -> throw failure(Code.IDEMPOTENCY_CONFLICT, "同幂等键载荷冲突");
            case IN_PROGRESS -> throw failure(Code.IDEMPOTENCY_IN_PROGRESS, "同幂等键正在处理中");
        };
    }

    private void requireTrustedActor(Long tenantId, Long actorId) {
        Long trustedTenant;
        try {
            trustedTenant = TenantContextHolder.getRequiredTenantId();
        } catch (RuntimeException ex) {
            throw failure(Code.TENANT_CONTEXT_MISMATCH, "缺少受信租户上下文");
        }
        if (!Objects.equals(trustedTenant, tenantId) || actorId == null || actorId <= 0) {
            throw failure(Code.TENANT_CONTEXT_MISMATCH, "租户或主体上下文非法");
        }
    }

    private JsonNode requireObjectJson(String value, String field) {
        try {
            JsonNode node = JsonUtils.getObjectMapper().readTree(value);
            if (node == null || !node.isObject()) throw failure(Code.INVALID_REQUEST, field + "必须为JSON对象");
            return node;
        } catch (JacksonException ex) {
            throw failure(Code.INVALID_REQUEST, field + "不是合法JSON");
        } catch (RuntimeException ex) {
            if (ex instanceof CandidateException candidateException) throw candidateException;
            throw failure(Code.INVALID_REQUEST, field + "不是合法JSON");
        }
    }

    private boolean jsonEquals(String stored, JsonNode incoming) {
        return requireObjectJson(stored, "storedJson").equals(incoming);
    }

    private String requireCompanyCode(JsonNode payload) {
        JsonNode companyNode = payload.get("companyCode");
        String companyCode = companyNode == null || !companyNode.isString() ? null : trimToNull(companyNode.asText());
        if (companyCode == null || companyCode.length() > 64) {
            throw failure(Code.INVALID_REQUEST, "candidatePayload.companyCode非法");
        }
        return companyCode;
    }

    private void requireObjectType(String objectType) {
        if (!OBJECT_TYPES.contains(objectType)) throw failure(Code.INVALID_REQUEST, "objectType非法");
    }

    private void requireText(String value, int max, String field) {
        String normalized = trimToNull(value);
        if (normalized == null || !normalized.equals(value) || normalized.length() > max) {
            throw failure(Code.INVALID_REQUEST, field + "非法");
        }
    }

    private void auditBase(AuthorityCandidateDO row, Long actorId, LocalDateTime now) {
        String actor = String.valueOf(actorId);
        row.setCreator(actor);
        row.setUpdater(actor);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        row.setDeleted(false);
    }

    private CandidateResult view(AuthorityCandidateDO row) {
        return new CandidateResult(row.getId(), row.getObjectType(), row.getCandidateSourceKey(),
                row.getCandidateVersion(), row.getCandidateStatus(), row.getMatchedOwnerTable(),
                row.getMatchedOwnerId(), row.getMatchedOwnerSourceVersion(), row.getVersion());
    }

    private void requireWrite(int affected, String message) {
        if (affected != 1) throw failure(Code.VERSION_CONFLICT, message);
    }

    private String digest(Object value) {
        try {
            byte[] bytes = JsonUtils.toJsonString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256不可用", ex);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CandidateException failure(Code code, String message) {
        return new CandidateException(code, message);
    }

    public record CreateCandidateCommand(Long tenantId, Long actorId, String objectType, String sourceKey,
                                         String candidateVersion, String candidatePayload,
                                         String evidenceReference, String idempotencyKey, String correlationId) {
    }

    public record DecideCandidateCommand(Long tenantId, Long actorId, Long candidateId, Integer expectedVersion,
                                         Long ownerId, String idempotencyKey, String correlationId) {
    }

    public record ListCandidatesQuery(Long tenantId, Long actorId, String objectType, String candidateStatus,
                                      int pageNo, int pageSize) {
    }

    public record CandidateResult(Long candidateId, String objectType, String sourceKey, String candidateVersion,
                                  String candidateStatus, String matchedOwnerTable, Long matchedOwnerId,
                                  String matchedOwnerSourceVersion, Integer version) {
    }

    public enum Code {
        INVALID_REQUEST, TENANT_CONTEXT_MISMATCH, NOT_FOUND, COMPANY_SCOPE_MISMATCH, OWNER_INVALID,
        SOURCE_VERSION_PAYLOAD_CONFLICT, VERSION_CONFLICT, STATE_CONFLICT,
        IDEMPOTENCY_CONFLICT, IDEMPOTENCY_IN_PROGRESS
    }

    public static final class CandidateException extends RuntimeException {
        private final Code code;

        public CandidateException(Code code, String message) {
            super(message);
            this.code = code;
        }

        public Code getCode() {
            return code;
        }
    }
}
