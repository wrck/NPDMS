package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOwnerOperationScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisEntityCommands.*;

/** The native revision policy is an Owner hook of the common transaction/execution sequence. */
@Service
@BusinessEntityService(ownerModule = "SOL", entityType = "requirementAnalysis", nativeEntityType = "REQUIREMENT_ANALYSIS")
public class RequirementAnalysisBusinessApplicationService extends DefaultBusinessApplicationService {
    private final RequirementAnalysisDomainCommands domain;
    private final RequirementAnalysisAccess access;

    public RequirementAnalysisBusinessApplicationService(BusinessCallerContext callerContext,
            BusinessModelCatalog catalog, BusinessEntityPersistenceRegistry persistence, BusinessAccessGuard guard,
            OperationExecutionStore executionStore, BusinessEventPort eventPort, OperationAuditApi auditApi,
            PlatformTransactionManager transactionManager, RequirementAnalysisDomainCommands domain,
            RequirementAnalysisAccess access) {
        super(callerContext, catalog, persistence, guard, executionStore, eventPort, auditApi,
                new TransactionTemplate(transactionManager));
        this.domain = domain;
        this.access = access;
    }

    @Override protected void validateIdentityAndInput(ResolvedCaller caller, BusinessOperationRequest request) {
        operationOf(descriptor(request), request);
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank() || request.idempotencyKey().length() > 128)
            throw invalid("IDEMPOTENCY_KEY_REQUIRED", "业务操作必须携带幂等键");
        Set<String> permitted = switch (request.operationCode()) {
            case "create" -> Set.of("projectId", "execution");
            case "save" -> Set.of("values", "extensionDefinitionRevisionId", "expectedExtensionVersion", "extensionValues", "execution");
            case "complete", "copy" -> Set.of("reason", "execution");
            default -> throw invalid("OPERATION_NOT_DECLARED", "需求分析未开放该操作");
        };
        if (request.input() == null || !permitted.containsAll(request.input().keySet()))
            throw invalid("OPERATION_INPUT_INVALID", "不支持的需求分析操作字段");
        if ("create".equals(request.operationCode())) {
            if (request.targetRef() != null || create(request).projectId() == null || create(request).projectId() <= 0)
                throw invalid("OPERATION_INPUT_INVALID", "创建必须指定项目且不得携带已有目标");
        } else {
            if (request.targetRef() == null || !request.targetRef().isRevision()
                    || request.concurrencyBasis() == null || request.concurrencyBasis() < 0
                    || request.concurrencyBasis() > Integer.MAX_VALUE)
                throw invalid("OPERATION_INPUT_INVALID", "操作必须携带真实修订身份与并发依据");
            callerEntity(request, caller);
        }
    }

    @Override protected void authorizeAndCheckState(ResolvedCaller caller, BusinessOperationRequest request) {
        EntityActor actor = actor(caller);
        final Long projectId;
        final String sourceId;
        if ("create".equals(request.operationCode())) {
            projectId = create(request).projectId();
            sourceId = null;
            access.lockScope(projectId, actor);
        } else {
            var source = access.read(request.targetRef().revisionId(), actor);
            if (!nativeRef(request, caller).equals(source.revisionRef()))
                throw invalid("ENTITY_IDENTITY_MISMATCH", "逻辑实体与修订身份不一致");
            if (!access.isManager(source.getProjectId(), actor)) throw exception(FORBIDDEN);
            projectId = source.getProjectId();
            sourceId = source.getId().toString();
        }
        ProjectOwnerOperationScope.call("SOL", "REQUIREMENT_ANALYSIS",
                () -> new ProjectOwnerOperationScope.Declaration(caller.tenantId(), caller.userId(), projectId,
                        "SOL", "REQUIREMENT_ANALYSIS", ownerOperation(request), 1, sourceId, selection(request)),
                () -> null);
        // Replay rechecks current permissions, not the former mutable draft state.
    }

    @Override protected LockedAggregate<BaseBusinessEntity> lockAggregate(ResolvedCaller caller,
                                                                         BusinessOperationRequest request) {
        reserveExecution(caller, request);
        if ("create".equals(request.operationCode())) return new LockedAggregate<>(null, null);
        EntityActor actor = actor(caller);
        var source = access.read(request.targetRef().revisionId(), actor);
        RequirementAnalysisRevisionDO locked = ProjectOwnerOperationScope.call("SOL", "REQUIREMENT_ANALYSIS",
                () -> new ProjectOwnerOperationScope.Declaration(caller.tenantId(), caller.userId(), source.getProjectId(),
                        "SOL", "REQUIREMENT_ANALYSIS", ownerOperation(request), 1, source.getId().toString(), selection(request)),
                () -> access.lock(source.getId(), Math.toIntExact(request.concurrencyBasis()), actor, selection(request),
                        !"copy".equals(request.operationCode())));
        if (!nativeRef(request, caller).equals(locked.revisionRef()))
            throw invalid("ENTITY_IDENTITY_MISMATCH", "锁定对象与请求身份不一致");
        return new LockedAggregate<>(locked, locked.getVersion());
    }

    @Override protected BusinessOperationReceipt domainCommand(ResolvedCaller caller, BusinessOperationRequest request,
                                                               LockedAggregate<BaseBusinessEntity> locked) {
        EntityActor actor = actor(caller);
        EntityVersionProvider.Revision result = switch (request.operationCode()) {
            case "create" -> domain.create(create(request), actor, request.idempotencyKey());
            case "save" -> domain.save(nativeRef(request, caller), Math.toIntExact(request.concurrencyBasis()), patch(request), actor, request.idempotencyKey());
            case "complete" -> domain.complete(nativeRef(request, caller), Math.toIntExact(request.concurrencyBasis()), action(request), actor, request.idempotencyKey());
            case "copy" -> domain.copy(nativeRef(request, caller), Math.toIntExact(request.concurrencyBasis()), action(request), actor, request.idempotencyKey());
            default -> throw invalid("OPERATION_NOT_DECLARED", "需求分析未开放该操作");
        };
        return receipt(result);
    }

    @Override protected void recordOutcome(ResolvedCaller caller, BusinessOperationRequest request, BusinessOperationReceipt receipt) {
        String payload = revisionPayload(receipt);
        var revision = JsonUtils.parseObject(payload, EntityVersionProvider.Revision.class);
        executionStore.complete(executionKey(caller, request), "RequirementAnalysis",
                revision.ref().entity().entityId().toString(), receipt, JsonUtils.toJsonString(revision));
        audit().record(caller.tenantId(), caller.userId(), caller.entryCorrelationId(), scope(request),
                "RequirementAnalysis", revision.ref().entity().entityId().toString(), "SUCCESS", Map.of("snapshot", payload));
        // Provider retains the actual initialize/save/freeze/activate audit and events.
        // Do not emit a second CHANGED/FORMED event or treat draft save as result formation.
    }

    @Override protected OperationExecutionStore.OperationExecutionKey executionKey(ResolvedCaller caller, BusinessOperationRequest request) {
        return new OperationExecutionStore.OperationExecutionKey(caller.tenantId(), scope(request), caller.userId(), request.idempotencyKey());
    }

    @Override protected String requestDigest(BusinessOperationRequest request) {
        Object nativeIntent = "create".equals(request.operationCode()) ? create(request)
                : List.of(nativeRef(request, null), Math.toIntExact(request.concurrencyBasis()),
                        "save".equals(request.operationCode()) ? patch(request) : action(request));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(nativeIntent).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    @Override protected BusinessOperationReceipt decodeStoredReceipt(String payload) {
        return receipt(JsonUtils.parseObject(payload, EntityVersionProvider.Revision.class));
    }

    public static BusinessOperationReceipt receipt(EntityVersionProvider.Revision revision) {
        EntityRef nativeEntity = revision.ref().entity();
        EntityRef catalogEntity = new EntityRef(nativeEntity.tenantId(), "SOL", "requirementAnalysis", nativeEntity.entityId());
        return new BusinessOperationReceipt(revision.effective() ? ReceiptOutcome.EFFECTED : ReceiptOutcome.SAVED,
                catalogEntity, (long) revision.version(),
                List.of(new ResultReference(ResultReference.Kind.COMMAND, "SOL", publicRevisionPayload(revision))), null, null);
    }

    private static String publicRevisionPayload(EntityVersionProvider.Revision revision) {
        var node=(tools.jackson.databind.node.ObjectNode) JsonUtils.parseTree(JsonUtils.toJsonString(revision));
        var ref=(tools.jackson.databind.node.ObjectNode) node.get("ref");
        var entity=(tools.jackson.databind.node.ObjectNode) ref.get("entity");
        for (String field: List.of("tenantId","entityId")) entity.put(field,entity.get(field).asText());
        ref.put("revisionId",ref.get("revisionId").asText());
        for (String field: List.of("sourceRevisionId","baseEffectiveRevisionId","frozenBy"))
            if (node.hasNonNull(field)) node.put(field,node.get(field).asText());
        return node.toString();
    }

    public static String revisionPayload(BusinessOperationReceipt receipt) {
        return receipt.references().stream().filter(ref -> ref.kind() == ResultReference.Kind.COMMAND && "SOL".equals(ref.ownerModule()))
                .map(ResultReference::value).findFirst().orElseThrow(() -> invalid("RECEIPT_INVALID", "缺少原生修订回执"));
    }

    private EntityActor actor(ResolvedCaller caller) { return new EntityActor(caller.tenantId(), caller.userId(), caller.entryCorrelationId()); }
    private EntityRef callerEntity(BusinessOperationRequest request, ResolvedCaller caller) {
        EntityRef ref = request.targetRef().entity();
        if (!caller.tenantId().equals(ref.tenantId()) || !"SOL".equals(ref.ownerModule()) || !"requirementAnalysis".equals(ref.entityType()))
            throw invalid("ENTITY_IDENTITY_MISMATCH", "不支持的需求分析身份");
        return ref;
    }
    private RevisionRef nativeRef(BusinessOperationRequest request, ResolvedCaller caller) {
        EntityRef ref = caller == null ? request.targetRef().entity() : callerEntity(request, caller);
        return new RevisionRef(new EntityRef(ref.tenantId(), "SOL", "REQUIREMENT_ANALYSIS", ref.entityId()), request.targetRef().revisionId());
    }
    private Create create(BusinessOperationRequest request) { return decode(request, Create.class); }
    private Patch patch(BusinessOperationRequest request) { return decode(request, Patch.class); }
    private Action action(BusinessOperationRequest request) { return decode(request, Action.class); }
    private <T> T decode(BusinessOperationRequest request, Class<T> type) { return JsonUtils.parseObject(JsonUtils.toJsonString(request.input()), type); }
    private ProjectBusinessExecutionSelection selection(BusinessOperationRequest request) {
        return "create".equals(request.operationCode()) ? create(request).execution() : "save".equals(request.operationCode()) ? patch(request).execution() : action(request).execution();
    }
    private String scope(BusinessOperationRequest request) { return "RA_ENTITY_" + request.operationCode().toUpperCase(Locale.ROOT); }
    private String ownerOperation(BusinessOperationRequest request) { return "SOL.REQUIREMENT_ANALYSIS." + request.operationCode().toUpperCase(Locale.ROOT); }
    private static BusinessContractException invalid(String code, String message) { return new BusinessContractException(code, message); }
}
