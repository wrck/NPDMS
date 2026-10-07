package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventRecord;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationRequest;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.ReceiptOutcome;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 默认业务应用服务：一个实例按声明服务所有普通实体，无须每个实体专用 Service。
 * 默认仅开放创建与保存（CREATE/UPDATE）；删除、批准、完成、生效及专业领域命令
 * 没有业务依据不自动开放，由专业子类或专业服务承接。
 * 保存成功输出 CHANGED 事件；保存不默认形成完成结果。
 */
public class DefaultBusinessApplicationService
        extends AbstractBusinessApplicationService<BaseBusinessEntity> {

    private record CapabilityWrite(cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef target,
            EntityActor actor, Long version) { }
    private static final ThreadLocal<CapabilityWrite> CAPABILITY_WRITE = new ThreadLocal<>();

    private final BusinessCallerContext callerContext;
    private final BusinessModelCatalog catalog;
    private final BusinessEntityPersistenceRegistry persistence;
    private final BusinessAccessGuard guard;
    protected final OperationExecutionStore executionStore;
    private final BusinessEntitySaveSupport saveSupport;
    private final cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi extensions;
    private final cn.iocoder.yudao.module.pms.platform.support.access.DeclaredBusinessScopeSupport scopes;
    private cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort entityAccess;
    private java.util.function.Supplier<cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi> deliveryPort;
    private List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessDeletionGuard> deletionGuards = List.of();
    private jakarta.validation.Validator validator;
    public final void configureValidation(jakarta.validation.Validator validator,
            List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessDeletionGuard> guards) {
        this.validator = java.util.Objects.requireNonNull(validator);
        deletionGuards = List.copyOf(guards);
    }

    /** Production wiring is copied into thin subclasses; no business repeats forwarding methods. */
    public final void configureDefaultCapabilities(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort access,
            java.util.function.Supplier<cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi> delivery) {
        if (entityAccess != null || deliveryPort != null) throw new IllegalStateException("Default capabilities already configured");
        entityAccess = java.util.Objects.requireNonNull(access);
        deliveryPort = java.util.Objects.requireNonNull(delivery);
    }

    public final BusinessModelDescriptor model(String owner, String type) {
        var model = catalog.require(owner, type);
        guard.requireReadable(model, actor(callerContext.require()), "detail");
        return model;
    }
    public final cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews.ModelDetailVO modelView(String owner, String type) {
        return cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews.ModelDetailVO.of(
                model(owner, type), actor(callerContext.require()), guard);
    }
    public final BusinessOperationReceipt executeBound(String owner, String type, String operationCode, Long id,
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews.OperationExecuteReqVO input) {
        var model = catalog.require(owner, type);
        var operation = model.operations().stream().filter(value -> operationCode.equals(value.code())).findFirst()
                .orElseThrow(() -> new BusinessContractException("OPERATION_NOT_DECLARED", "业务未开放该操作"));
        if (input.getRevisionId() != null) throw new BusinessContractException("REVISION_TARGET_UNSUPPORTED", "Default bound CRUD uses the current entity");
        var caller = callerContext.require();
        var target = id == null ? null : cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef.current(new EntityRef(caller.tenantId(), owner, type, id));
        return execute(new BusinessOperationRequest(operation.code(), operation.version(), target, owner, type,
                input.getInput() == null ? Map.of() : input.getInput(), input.getIdempotencyKey(), input.getConcurrencyBasis(),
                input.getEntryKind() == null ? cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind.INDEPENDENT : input.getEntryKind(), input.getEntryCorrelationId()));
    }
    public final cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityData read(String owner, String type, Long id) {
        var caller = callerContext.require();
        return access().read(cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef.current(
                new EntityRef(caller.tenantId(), owner, type, id)), actor(caller), "detail");
    }
    public final cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntitySlice query(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityPageQuery query) {
        var caller = callerContext.require(); var actor = actor(caller);
        guard.requireReadable(catalog.require(query.ownerModule(), query.entityType()), actor, query.sceneCode());
        if (query.pageSize() < 1 || query.pageSize() > 200) throw new BusinessContractException("PAGE_SIZE_INVALID", "Invalid page size");
        var result = queryEntities(query, actor);
        if (result.members().size() > query.pageSize()) throw new BusinessContractException("QUERY_RESULT_INVALID", "Query hook exceeded the requested page size");
        var projected = result.members().stream().map(row -> {
            if (!caller.tenantId().equals(row.ref().tenantId()) || !query.ownerModule().equals(row.ref().ownerModule())
                    || !query.entityType().equals(row.ref().entityType())) throw new BusinessContractException("QUERY_RESULT_INVALID", "Query hook returned a different business identity");
            var data = access().read(cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef.current(row.ref()), actor, query.sceneCode());
            if (!data.available()) throw new BusinessContractException("QUERY_RESULT_INVALID", "Query hook returned an unavailable Owner");
            return data;
        }).toList();
        return new cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntitySlice(projected, result.nextCursor(), result.completeness(), result.unavailableReason());
    }
    /** Complex selection may use this business's XML; the final exit still rechecks identity, scope and readable projection. */
    protected cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntitySlice queryEntities(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityPageQuery query, EntityActor actor) {
        return access().query(query, actor);
    }
    private cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort access() {
        if (entityAccess == null) throw new BusinessContractException("DEFAULT_CAPABILITY_UNAVAILABLE", "Default access is not configured");
        return entityAccess;
    }
    private cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi deliveries() {
        if (deliveryPort == null) throw new BusinessContractException("DEFAULT_CAPABILITY_UNAVAILABLE", "Default delivery is not configured");
        return deliveryPort.get();
    }
    public final cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Scope deliveryContext(String owner, String type, String key) { return deliveries().context(owner, type, key); }
    public final cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Record uploadDelivery(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Scope scope,
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.UploadFile file, String key) { return deliveries().upload(scope, file, key); }
    public final cn.iocoder.yudao.framework.common.pojo.PageResult<cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Record> listDeliveries(Long project, String type, String business, String entity, int page, int size) { return deliveries().list(project, type, business, entity, page, size); }
    public final cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Completion deliveryCompletion(cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Scope scope) { return deliveries().completion(scope); }
    public final cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Record getDelivery(Long id) { return deliveries().get(id); }
    public final cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi.Document deliveryFile(Long id) { return deliveries().file(id); }
    public final cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Record editDelivery(Long id, Long version, String title) { return deliveries().edit(id, version, title); }
    public final void deleteDelivery(Long id, Long version) { deliveries().delete(id, version); }

    public DefaultBusinessApplicationService(BusinessCallerContext callerContext,
                                             BusinessModelCatalog catalog,
                                             BusinessEntityPersistenceRegistry persistence,
                                             BusinessAccessGuard guard,
                                             OperationExecutionStore executionStore,
                                             BusinessEventPort eventPort,
                                             OperationAuditApi auditApi,
                                             TransactionOperations transactionOperations) {
        this(callerContext, catalog, persistence, guard, executionStore, eventPort, auditApi, transactionOperations, null);
    }

    public DefaultBusinessApplicationService(BusinessCallerContext callerContext,
                                             BusinessModelCatalog catalog,
                                             BusinessEntityPersistenceRegistry persistence,
                                             BusinessAccessGuard guard,
                                             OperationExecutionStore executionStore,
                                             BusinessEventPort eventPort,
                                             OperationAuditApi auditApi,
                                             TransactionOperations transactionOperations,
                                             cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi extensions) {
        this(callerContext,catalog,persistence,guard,executionStore,eventPort,auditApi,transactionOperations,extensions,
                new cn.iocoder.yudao.module.pms.platform.support.access.DeclaredBusinessScopeSupport(List.of()));
    }

    public DefaultBusinessApplicationService(BusinessCallerContext callerContext, BusinessModelCatalog catalog,
            BusinessEntityPersistenceRegistry persistence, BusinessAccessGuard guard, OperationExecutionStore executionStore,
            BusinessEventPort eventPort, OperationAuditApi auditApi, TransactionOperations transactionOperations,
            cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi extensions,
            cn.iocoder.yudao.module.pms.platform.support.access.DeclaredBusinessScopeSupport scopes) {
        super(eventPort, auditApi, transactionOperations);
        this.scopes=scopes;
        this.extensions = extensions;
        this.saveSupport = new BusinessEntitySaveSupport(extensions);
        this.callerContext = callerContext;
        this.catalog = catalog;
        this.persistence = persistence;
        this.guard = guard;
        this.executionStore = executionStore;
    }

    /** Thin extensions inherit the same production wiring instead of repeating its dependency list. */
    protected DefaultBusinessApplicationService(DefaultBusinessApplicationService defaults) {
        super(defaults);
        callerContext = defaults.callerContext;
        catalog = defaults.catalog;
        persistence = defaults.persistence;
        guard = defaults.guard;
        executionStore = defaults.executionStore;
        saveSupport = defaults.saveSupport;
        extensions = defaults.extensions;
        scopes = defaults.scopes;
        entityAccess = defaults.entityAccess;
        deliveryPort = defaults.deliveryPort;
        validator = defaults.validator;
        deletionGuards = defaults.deletionGuards;
    }

    @Override
    protected ResolvedCaller resolveCaller(BusinessOperationRequest request) {
        ResolvedCaller caller = callerContext.require();
        if (request.entryCorrelationId() != null && !request.entryCorrelationId().isBlank()) {
            return new ResolvedCaller(caller.tenantId(), caller.userId(), request.entryCorrelationId());
        }
        return caller;
    }

    @Override
    protected void validateIdentityAndInput(ResolvedCaller caller, BusinessOperationRequest request) {
        BusinessModelDescriptor descriptor = descriptor(request);
        BusinessOperationDescriptor operation = operationOf(descriptor, request);
        if (operation.kind() == BusinessOperationDescriptor.StandardOperationKind.CREATE) {
            if (request.targetRef() != null) {
                throw new BusinessContractException("OPERATION_INPUT_INVALID",
                        "创建操作不得携带已存在目标: " + request.operationCode());
            }
            if (request.ownerModule() == null || request.entityType() == null) {
                throw new BusinessContractException("OPERATION_INPUT_INVALID",
                        "创建操作必须声明业务身份: " + request.operationCode());
            }
        } else if (request.targetRef() == null) {
            throw new BusinessContractException("OPERATION_INPUT_INVALID",
                    "保存操作必须携带目标: " + request.operationCode());
        }
        if (request.targetRef() != null && request.targetRef().isRevision()) {
            // 修订身份只用于历史读取与版本流；业务命令必须落在当前对象上，防止修订 ID 混入当前对象操作。
            throw new BusinessContractException("REVISION_TARGET_UNSUPPORTED",
                    "修订对象不能直接执行业务操作，须对当前对象操作: " + request.operationCode());
        }
        if (request.targetRef()!=null) {
            new EntityActor(caller.tenantId(),caller.userId(),caller.entryCorrelationId()).requireTenant(request.targetRef().entity());
            if(request.concurrencyBasis()==null || request.concurrencyBasis()<0)
                throw new BusinessContractException("CONCURRENCY_BASIS_REQUIRED","更新必须携带客户端并发依据");
        }
        requireWritableFields(descriptor, fixedInput(request));
        extensionPatch(request);
        if (operation.kind() == BusinessOperationDescriptor.StandardOperationKind.DELETE && request.input() != null && !request.input().isEmpty())
            throw new BusinessContractException("OPERATION_INPUT_INVALID", "Delete does not accept a content patch");
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                || request.idempotencyKey().length() > 128) {
            throw new BusinessContractException("IDEMPOTENCY_KEY_REQUIRED",
                    "业务操作必须携带幂等键: " + request.operationCode());
        }
    }

    @Override
    protected void authorizeAndCheckState(ResolvedCaller caller, BusinessOperationRequest request) {
        BusinessModelDescriptor descriptor = descriptor(request);
        guard.requireWritable(descriptor,
                new EntityActor(caller.tenantId(), caller.userId(), caller.entryCorrelationId()),
                "operation:" + request.operationCode());
        // Object scope is checked after reservation: replays authorize the committed receipt object,
        // while new commands check current/proposed ownership before any subject write.
    }

    @Override
    protected LockedAggregate<BaseBusinessEntity> lockAggregate(ResolvedCaller caller,
                                                                BusinessOperationRequest request) {
        try {
            reserveExecution(caller, request);
        } catch (ReplayedOperation replay) {
            requireCurrentReceiptAccess(caller, request, replay.receipt());
            throw replay;
        }
        return lockTarget(caller, request);
    }

    /** Read an already committed receipt; never reserves or re-executes an unknown intent. */
    public final BusinessOperationReceipt recoverReceipt(String owner,String type,String operationCode,int operationVersion,String key) {
        BusinessEntitySaveSupport.requireTransaction();
        if(key==null || key.isBlank() || key.length()>128) throw new BusinessContractException("IDEMPOTENCY_KEY_REQUIRED","Receipt recovery requires the original key");
        var caller=callerContext.require();
        var request=new BusinessOperationRequest(operationCode,operationVersion,null,owner,type,Map.of(),key,null,
                cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind.INDEPENDENT,null);
        var model=descriptor(request);operationOf(model,request);
        guard.requireWritable(model,actor(caller),"operation:"+operationCode);
        var execution=executionStore.findExisting(executionKey(caller,request),this::decodeStoredReceipt).orElse(null);
        if(execution==null || !"COMPLETED".equals(execution.status()) || execution.receipt()==null) return null;
        var receipt=execution.receipt();
        // Old Owner response protocols lack this binding and keep their native recovery contract.
        if(!operationCode.equals(receipt.operationCode()) || !Integer.valueOf(operationVersion).equals(receipt.operationVersion()))
            throw new BusinessContractException("IDEMPOTENCY_INTENT_MISMATCH","Receipt is not bound to this operation");
        requireCurrentReceiptAccess(caller,request,receipt);
        return receipt;
    }

    /** Historical receipts remain immutable; access follows the object's current ownership. */
    protected final void requireCurrentReceiptAccess(ResolvedCaller caller, BusinessOperationRequest request,
                                                     BusinessOperationReceipt receipt) {
        var model = descriptor(request);
        var ref = receipt.entityRef();
        if (ref == null || !caller.tenantId().equals(ref.tenantId())
                || !model.ownerModule().equals(ref.ownerModule()) || !model.entityType().equals(ref.entityType()))
            throw new BusinessContractException("ENTITY_IDENTITY_MISMATCH", "Receipt object identity does not match the operation");
        requireReceiptOwnerAccess(caller,request,receipt);
    }

    /** Native Owners resolve current ownership with their existing identity/lifecycle policy. */
    protected void requireReceiptOwnerAccess(ResolvedCaller caller, BusinessOperationRequest request,
                                            BusinessOperationReceipt receipt) {
        var model = descriptor(request);
        var ref = receipt.entityRef();
        boolean deleted = receipt.outcome() == ReceiptOutcome.DELETED
                && operationOf(model, request).kind() == BusinessOperationDescriptor.StandardOperationKind.DELETE;
        var query = new cn.iocoder.yudao.module.pms.platform.support.persistence.DeclaredCurrentRowQuery(caller.tenantId(), ref.entityId());
        var row = deleted
                ? cn.iocoder.yudao.module.pms.platform.support.persistence.DeclaredBusinessCurrentRows.lockDeletedForReceipt(declaration(request), query)
                : cn.iocoder.yudao.module.pms.platform.support.persistence.DeclaredBusinessCurrentRows.lock(declaration(request), query);
        if (row == null || !caller.tenantId().equals(row.getTenantId()))
            throw new BusinessContractException("ENTITY_NOT_FOUND", "Receipt object is no longer available");
        scopes.requireWritable(model, BusinessModelIntrospector.readValues(row,
                BusinessModelIntrospector.businessFields(declaration(request).entityClass())), actor(caller), false);
    }

    /** Shared reservation/replay mechanism; native Owners retain their existing scope and payload contracts. */
    protected void reserveExecution(ResolvedCaller caller, BusinessOperationRequest request) {
        OperationExecutionStore.OperationExecutionKey key = executionKey(caller, request);
        String digest = requestDigest(request);
        if (!executionStore.reserve(key, digest)) {
            OperationExecutionStore.StoredExecution existing = executionStore.findExisting(key, this::decodeStoredReceipt)
                    .orElseThrow(() -> new BusinessContractException("IDEMPOTENCY_IN_PROGRESS",
                            "幂等执行记录缺失: " + request.idempotencyKey()));
            if (!digest.equals(existing.requestDigest())) {
                throw new BusinessContractException("IDEMPOTENCY_DIGEST_CONFLICT",
                        "同幂等键载荷不同，拒绝执行: " + request.idempotencyKey());
            }
            if (!"COMPLETED".equals(existing.status()) || existing.receipt() == null) {
                throw new BusinessContractException("IDEMPOTENCY_IN_PROGRESS",
                        "同幂等键执行进行中: " + request.idempotencyKey());
            }
            throw new ReplayedOperation(existing.receipt());
        }
    }

    protected LockedAggregate<BaseBusinessEntity> lockTarget(ResolvedCaller caller,
                                                             BusinessOperationRequest request) {
        if (request.targetRef() == null) {
            scopes.requireWritable(descriptor(request),fixedInput(request),actor(caller),true);
            return new LockedAggregate<>(null, null);
        }
        BaseBusinessEntity row = persistence.<BaseBusinessEntity>mapperOf(declaration(request))
                .selectById(request.targetRef().entity().entityId());
        if (row == null || !caller.tenantId().equals(row.getTenantId())) {
            throw new BusinessContractException("ENTITY_NOT_FOUND",
                    "目标不存在: " + request.targetRef().entity());
        }
        if (request.concurrencyBasis() != null
                && !request.concurrencyBasis().equals(row.getVersion())) {
            throw new BusinessContractException("CONCURRENCY_CONFLICT",
                    "并发依据过期: 期望 " + request.concurrencyBasis() + " 实际 " + row.getVersion());
        }
        Map<String, Object> current = BusinessModelIntrospector.readValues(row,
                BusinessModelIntrospector.businessFields(declaration(request).entityClass()));
        Map<String, Object> proposed = new java.util.LinkedHashMap<>(current);
        Map<String, Object> changes = prepareChanges(request, java.util.Collections.unmodifiableMap(current));
        changes = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(changes));
        requireWritableFields(descriptor(request), changes);
        proposed.putAll(changes);
        scopes.requireWritableAfterChange(descriptor(request), current, proposed, actor(caller));
        var locked = cn.iocoder.yudao.module.pms.platform.support.persistence.DeclaredBusinessCurrentRows.lock(declaration(request),
                new cn.iocoder.yudao.module.pms.platform.support.persistence.DeclaredCurrentRowQuery(caller.tenantId(), row.getId()));
        if (locked == null || !row.getVersion().equals(locked.getVersion())
                || !current.equals(BusinessModelIntrospector.readValues(locked, BusinessModelIntrospector.businessFields(declaration(request).entityClass()))))
            throw new BusinessContractException("CONCURRENCY_CONFLICT", "Current Owner changed during scope locking");
        return new LockedAggregate<>(locked, locked.getVersion(), changes);
    }

    @Override
    protected BusinessOperationReceipt domainCommand(ResolvedCaller caller, BusinessOperationRequest request,
                                                     LockedAggregate<BaseBusinessEntity> locked) {
        BusinessEntitySaveSupport.requireTransaction();
        BusinessOperationDescriptor operation = operationOf(descriptor(request), request);
        if (operation.kind() == BusinessOperationDescriptor.StandardOperationKind.CREATE) {
            BaseBusinessEntity created = createEntity(caller, request, declaration(request), fixedInput(request));
            BusinessModelDescriptor descriptor = descriptor(request);
            EntityRef ref = new EntityRef(caller.tenantId(), descriptor.ownerModule(),
                    descriptor.entityType(), created.getId());
            return saveWithCapabilities(cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef.current(ref),
                    operationActor(caller,request), created.getVersion(), extensionPatch(request),
                    () -> new BusinessOperationReceipt(ReceiptOutcome.SAVED, ref, created.getVersion(), List.of(), null, null,request.operationCode(),request.operationVersion()));
        }
        if (operation.kind() == BusinessOperationDescriptor.StandardOperationKind.DELETE) {
            if (declaration(request).revisionMapper() != null || descriptor(request).capabilities().stream().anyMatch(cap -> cap.enabled()
                    && (cap.type() == cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityType.CONTENT_HISTORY
                        || cap.type() == cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityType.APPROVAL)))
                throw new BusinessContractException("DELETE_PROTECTED_HISTORY", "History-bearing entities require their existing deletion policy");
            if (deletionGuards.isEmpty()) throw new BusinessContractException("DELETE_PROTECTION_UNAVAILABLE", "Shared reference protection is unavailable");
            for (var protection : deletionGuards) protection.requireDeletable(request.targetRef().entity(), actor(caller));
            cn.iocoder.yudao.module.pms.platform.support.persistence.DeclaredBusinessCurrentRows.delete(declaration(request),
                    new cn.iocoder.yudao.module.pms.platform.support.persistence.DeclaredBusinessCurrentRows.DeleteCommand(caller.tenantId(),
                            request.targetRef().entity().entityId(), locked.concurrencyBasis(), caller.userId().toString()));
            return new BusinessOperationReceipt(ReceiptOutcome.DELETED, request.targetRef().entity(), Math.incrementExact(locked.concurrencyBasis()),
                    List.of(), null, null, request.operationCode(), request.operationVersion());
        }
        if (operation.kind() != BusinessOperationDescriptor.StandardOperationKind.UPDATE) {
            throw new BusinessContractException("OPERATION_NOT_DEFAULTED",
                    "操作未由默认服务开放，须由专业服务实现: " + request.operationCode());
        }
        return saveChanges(caller, request, locked, locked.changes() == null ? fixedInput(request) : locked.changes());
    }

    private EntityActor operationActor(ResolvedCaller caller,BusinessOperationRequest request) {
        String correlation=caller.entryCorrelationId();
        return new EntityActor(caller.tenantId(),caller.userId(),correlation==null || correlation.isBlank()?request.idempotencyKey():correlation);
    }

    private <T> T saveWithCapabilities(cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef target,
            EntityActor actor, Long version, BusinessEntitySaveSupport.ExtensionPatch patch,
            java.util.function.Supplier<T> fixedSave) {
        var previous=CAPABILITY_WRITE.get();
        CAPABILITY_WRITE.set(new CapabilityWrite(target,actor,version));
        try {
            var declaration=persistence.require(target.entity().ownerModule(),target.entity().entityType());
            boolean ordinary=declaration.revisionMapper()==null && declaration.descriptor().scopeBinding()!=null
                && declaration.descriptor().capabilities().stream().anyMatch(cap->cap.enabled() && cap.type()==cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityType.DYNAMIC_FORM);
            return saveSupport.save(target,actor,version,patch,()->{
                if(ordinary) {
                    if(extensions==null) throw new BusinessContractException("CAPABILITY_UNAVAILABLE","Declared form values are unavailable");
                    extensions.validateCompleteForWrite(target,actor,version);
                }
                return fixedSave.get();
            });
        }
        finally { if(previous==null) CAPABILITY_WRITE.remove(); else CAPABILITY_WRITE.set(previous); }
    }

    /** Initial capability access inspection performs no write or lock and cannot grant a business action. */
    public final void requireCapabilityWrite(cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef target,EntityActor actor) {
        var caller=callerContext.require();
        if(!caller.tenantId().equals(actor.tenantId()) || !caller.userId().equals(actor.userId())) throw new BusinessContractException("ACCESS_DENIED","Capability actor must match the trusted caller");
        actor.requireTenant(target.entity());
        if(target.isRevision()) throw new BusinessContractException("REVISION_UNSUPPORTED","Capability requires the current object");
        var declaration=persistence.require(target.entity().ownerModule(),target.entity().entityType());
        var operations=declaration.descriptor().operations().stream().filter(op->op.kind()==BusinessOperationDescriptor.StandardOperationKind.UPDATE).toList();
        if(operations.size()!=1) throw new BusinessContractException("CAPABILITY_WRITE_OPERATION_UNAVAILABLE","Capability requires one declared update operation");
        var operation=operations.getFirst();
        guard.requireWritable(declaration.descriptor(),actor,"operation:"+operation.code());
        var row=persistence.<BaseBusinessEntity>mapperOf(declaration).selectById(target.entity().entityId());
        if(row==null || !actor.tenantId().equals(row.getTenantId())) throw new BusinessContractException("ENTITY_NOT_FOUND","Capability object is unavailable");
        var values=BusinessModelIntrospector.readValues(row,BusinessModelIntrospector.businessFields(declaration.entityClass()));
        scopes.requireWritable(declaration.descriptor(),values,actor,false);
        var request=new BusinessOperationRequest(operation.code(),operation.version(),target,null,null,Map.of(),"capability-inspect",row.getVersion(),
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind.INDEPENDENT,actor.correlationId());
        validateCapabilityChange(request,java.util.Collections.unmodifiableMap(values));
    }

    /** Capability APIs retain declared operation permission, scope locks, version and thin domain validation. */
    public final void lockCapabilityTarget(cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef target,
            EntityActor actor, Long expectedVersion) {
        BusinessEntitySaveSupport.requireTransaction();
        var caller=callerContext.require();
        if(!caller.tenantId().equals(actor.tenantId()) || !caller.userId().equals(actor.userId()))
            throw new BusinessContractException("ACCESS_DENIED","Capability actor must match the trusted caller");
        actor.requireTenant(target.entity());
        if(target.isRevision()) throw new BusinessContractException("REVISION_UNSUPPORTED","No-history capability requires the current object");
        if(expectedVersion==null || expectedVersion<0) throw new BusinessContractException("CONCURRENCY_BASIS_REQUIRED","Capability write requires the object version");
        var declaration=persistence.require(target.entity().ownerModule(),target.entity().entityType());
        var permit=CAPABILITY_WRITE.get();
        boolean authorized=permit!=null && permit.target().equals(target)
                && permit.actor().tenantId().equals(actor.tenantId()) && permit.actor().userId().equals(actor.userId())
                && permit.version().equals(expectedVersion);
        if(!authorized) {
            var operations=declaration.descriptor().operations().stream()
                    .filter(op->op.kind()==BusinessOperationDescriptor.StandardOperationKind.UPDATE).toList();
            if(operations.size()!=1) throw new BusinessContractException("CAPABILITY_WRITE_OPERATION_UNAVAILABLE","Capability write requires one declared update operation");
            var operation=operations.getFirst();
            var request=new BusinessOperationRequest(operation.code(),operation.version(),target,null,null,Map.of(),
                    "capability-lock",expectedVersion,cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind.INDEPENDENT,actor.correlationId());
            validateIdentityAndInput(caller,request);
            authorizeAndCheckState(caller,request);
            var locked=lockTarget(caller,request);
            validateCapabilityChange(request,currentValues(locked));
        }
        var current=cn.iocoder.yudao.module.pms.platform.support.persistence.DeclaredBusinessCurrentRows.lock(declaration,
                new cn.iocoder.yudao.module.pms.platform.support.persistence.DeclaredCurrentRowQuery(actor.tenantId(),target.entity().entityId()));
        if(current==null || !actor.tenantId().equals(current.getTenantId())) throw new BusinessContractException("ENTITY_NOT_FOUND","Capability object is unavailable");
        if(!expectedVersion.equals(current.getVersion())) throw new BusinessContractException("CONCURRENCY_CONFLICT","Capability object version changed");
        scopes.requireWritable(declaration.descriptor(),BusinessModelIntrospector.readValues(current,
                BusinessModelIntrospector.businessFields(declaration.entityClass())),actor,false);
    }

    protected void validateCapabilityChange(BusinessOperationRequest request, Map<String,Object> values) { }

    /** All custom field changes use the same whitelist, scope locks, required validation and CAS. */
    protected final BusinessOperationReceipt saveChanges(ResolvedCaller caller, BusinessOperationRequest request,
            LockedAggregate<BaseBusinessEntity> locked, Map<String, Object> changes) {
        BusinessEntitySaveSupport.requireTransaction();
        requireWritableFields(descriptor(request), changes);
        return saveWithCapabilities(request.targetRef(), operationActor(caller,request), locked.concurrencyBasis(), extensionPatch(request),
                () -> saveFixedContent(caller, request, locked, changes));
    }

    /** Prepare the difference before acquiring all affected scope locks in their global order. */
    protected Map<String, Object> prepareChanges(BusinessOperationRequest request, Map<String, Object> currentValues) {
        return fixedInput(request);
    }

    protected final Map<String, Object> currentValues(LockedAggregate<BaseBusinessEntity> locked) {
        if (locked.aggregate() == null) return Map.of();
        return java.util.Collections.unmodifiableMap(BusinessModelIntrospector.readValues(locked.aggregate(),
                BusinessModelIntrospector.businessFields(locked.aggregate().getClass())));
    }

    private BusinessOperationReceipt saveFixedContent(ResolvedCaller caller, BusinessOperationRequest request,
            LockedAggregate<BaseBusinessEntity> locked, Map<String, Object> changes) {
        BaseBusinessEntity aggregate = locked.aggregate();
        applyWritableFields(descriptor(request), aggregate, changes);
        requireRequiredFields(descriptor(request),aggregate);
        validateEntity(request, aggregate);
        BaseMapper<BaseBusinessEntity> mapper = persistence.mapperOf(declaration(request));
        java.util.Set<String> clearedFields = changes.entrySet().stream()
                .filter(entry -> entry.getValue() == null).map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        cn.iocoder.yudao.module.pms.platform.support.persistence.DeclaredBusinessEntityWriter.update(
                mapper, aggregate, caller.tenantId(), locked.concurrencyBasis(), clearedFields);
        return new BusinessOperationReceipt(ReceiptOutcome.SAVED, request.targetRef().entity(),
                aggregate.getVersion(), List.of(), null, null,request.operationCode(),request.operationVersion());
    }

    @Override
    protected void recordOutcome(ResolvedCaller caller, BusinessOperationRequest request,
                                 BusinessOperationReceipt receipt) {
        OperationExecutionStore.OperationExecutionKey key = executionKey(caller, request);
        executionStore.complete(key, descriptor(request).entityType(), receipt.entityRef().entityId().toString(), receipt);
        // 入口关联标识可选；未提供时以幂等键作为本次办理的关联标识，审计与事件保持可追溯。
        String correlationId = caller.entryCorrelationId() != null
                ? caller.entryCorrelationId() : request.idempotencyKey();
        audit().record(caller.tenantId(), caller.userId(), correlationId,
                request.operationCode(), descriptor(request).entityType(),
                receipt.entityRef().entityId().toString(), "SUCCESS",
                Map.of("outcome", receipt.outcome().name(), "entryKind", request.entryKind().name()));
        events().append(new BusinessEventRecord(UUID.randomUUID().toString(), receipt.entityRef(),
                BusinessEventKind.CHANGED, String.valueOf(receipt.newConcurrencyBasis()),
                correlationId,
                Map.of("operation", request.operationCode(), "operationVersion", request.operationVersion()),
                0L));
    }

    @SuppressWarnings("unchecked")
    private BaseBusinessEntity createEntity(ResolvedCaller caller, BusinessOperationRequest request, BusinessModelDeclaration declaration,
                                            Map<String, Object> input) {
        BaseBusinessEntity entity;
        try {
            entity = (BaseBusinessEntity) declaration.entityClass()
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new BusinessContractException("ENTITY_NOT_CREATABLE",
                    "实体缺少无参构造: " + declaration.entityClass().getName());
        }
        entity.setTenantId(caller.tenantId());
        entity.setVersion(0L);
        applyWritableFields(declaration.descriptor(), entity, input);
        initializeEntity(request, entity);
        if (entity.getId() != null || !caller.tenantId().equals(entity.getTenantId()) || !Long.valueOf(0).equals(entity.getVersion()))
            throw new BusinessContractException("CONTROL_FIELD_CHANGED", "Business initialization cannot change identity or version");
        scopes.requireWritable(declaration.descriptor(), BusinessModelIntrospector.readValues(entity,
                BusinessModelIntrospector.businessFields(declaration.entityClass())), actor(caller), true);
        requireRequiredFields(declaration.descriptor(),entity);
        validateEntity(request, entity);
        if (persistence.<BaseBusinessEntity>mapperOf(declaration).insert(entity)!=1)
            throw new BusinessContractException("ENTITY_INSERT_FAILED","创建对象未写入一行");
        if (entity.getId() == null) {
            throw new BusinessContractException("ENTITY_ID_MISSING", "插入后实体主键缺失");
        }
        return entity;
    }

    /** Only business initialization differs; identity, permissions, validation and persistence stay common. */
    protected void initializeEntity(BusinessOperationRequest request, BaseBusinessEntity entity) { }
    protected void validateProposedValues(BusinessOperationRequest request, Map<String, Object> values) { }
    private void validateEntity(BusinessOperationRequest request, BaseBusinessEntity entity) {
        if (validator == null) validator = ValidatorHolder.VALUE;
        var violations = validator.validate(entity);
        if (!violations.isEmpty()) throw new BusinessContractException("ENTITY_CONSTRAINT_INVALID", violations.stream()
                .map(value -> value.getPropertyPath() + ": " + value.getMessage()).sorted().collect(java.util.stream.Collectors.joining("; ")));
        validateProposedValues(request, java.util.Collections.unmodifiableMap(BusinessModelIntrospector.readValues(entity,
                BusinessModelIntrospector.businessFields(entity.getClass()))));
    }
    private static final class ValidatorHolder {
        private static final jakarta.validation.Validator VALUE = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
    }
    protected final BusinessOperationReceipt currentOperationReceipt(ResolvedCaller caller, BusinessOperationRequest request,
            LockedAggregate<BaseBusinessEntity> before) {
        var row = persistence.<BaseBusinessEntity>mapperOf(declaration(request)).selectById(request.targetRef().entity().entityId());
        if (row == null || !caller.tenantId().equals(row.getTenantId()) || row.getVersion() == null
                || row.getVersion() != Math.incrementExact(before.concurrencyBasis()))
            throw new BusinessContractException("CUSTOM_OPERATION_CAS_INVALID", "Custom operation must update the same Owner once with CAS");
        scopes.requireWritable(descriptor(request), BusinessModelIntrospector.readValues(row,
                BusinessModelIntrospector.businessFields(row.getClass())), actor(caller), false);
        return new BusinessOperationReceipt(ReceiptOutcome.SAVED, request.targetRef().entity(), row.getVersion(), List.of(),
                null, null, request.operationCode(), request.operationVersion());
    }

    private void applyWritableFields(BusinessModelDescriptor descriptor, BaseBusinessEntity target,
                                     Map<String, Object> input) {
        var fields = BusinessModelIntrospector.businessFields(target.getClass());
        Map<String, Object> values = input == null ? Map.of() : input;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            BusinessModelIntrospector.IntrospectedField field = fields.stream()
                    .filter(f -> f.code().equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> new BusinessContractException("FIELD_NOT_OPEN",
                            "字段未在目录开放: " + entry.getKey()));
            if (!isWritable(descriptor, entry.getKey())) {
                throw new BusinessContractException("FIELD_NOT_WRITABLE", "字段不可写: " + entry.getKey());
            }
            setField(field, target, entry.getValue());
        }
    }

    private void requireWritableFields(BusinessModelDescriptor descriptor, Map<String, Object> input) {
        List<String> writableCodes = descriptor.fields().stream()
                .filter(BusinessFieldDescriptor::writable)
                .map(BusinessFieldDescriptor::code).toList();
        for (String key : input == null ? List.<String>of() : input.keySet()) {
            if (!writableCodes.contains(key)) {
                throw new BusinessContractException("FIELD_NOT_WRITABLE",
                        "字段未开放写入: " + key);
            }
            var field = BusinessModelIntrospector.businessFields(persistence.require(
                    descriptor.ownerModule(), descriptor.entityType()).entityClass()).stream()
                    .filter(candidate -> candidate.code().equals(key)).findFirst()
                    .orElseThrow(() -> new BusinessContractException("FIELD_NOT_OPEN", "字段未在持久化映射开放: " + key));
            convert(input.get(key), field);
        }
    }

    private boolean isWritable(BusinessModelDescriptor descriptor, String fieldCode) {
        return descriptor.fields().stream()
                .anyMatch(field -> field.code().equals(fieldCode) && field.writable());
    }

    private void setField(BusinessModelIntrospector.IntrospectedField field, Object target, Object value) {
        try {
            field.property().set(target, convert(value, field));
        } catch (IllegalAccessException | IllegalArgumentException ex) {
            throw new BusinessContractException("FIELD_VALUE_INVALID",
                    "字段值非法: " + field.code() + " " + ex.getMessage());
        }
    }

    private Object convert(Object value, BusinessModelIntrospector.IntrospectedField field) {
        return cn.iocoder.yudao.module.pms.platform.support.model.DeclaredBusinessFieldValues.convert(value, field);
    }

    private void requireRequiredFields(BusinessModelDescriptor model,BaseBusinessEntity entity) {
        var values=BusinessModelIntrospector.readValues(entity,BusinessModelIntrospector.businessFields(entity.getClass()));
        for(var field:model.fields()) {
            var value=values.get(field.code());
            if(field.required() && (value==null || value instanceof String text && text.isBlank()))
                throw new BusinessContractException("FIELD_REQUIRED","必填字段为空: "+field.code());
        }
    }
    private EntityActor actor(ResolvedCaller caller) { return new EntityActor(caller.tenantId(),caller.userId(),caller.entryCorrelationId()); }
    private Map<String,Object> authoritativeValues(ResolvedCaller caller,BusinessOperationRequest request) {
        if(request.targetRef()==null) return fixedInput(request);
        var row=persistence.<BaseBusinessEntity>mapperOf(declaration(request)).selectById(request.targetRef().entity().entityId());
        if(row==null || !caller.tenantId().equals(row.getTenantId())) throw new BusinessContractException("ENTITY_NOT_FOUND","目标不存在");
        return BusinessModelIntrospector.readValues(row,BusinessModelIntrospector.businessFields(declaration(request).entityClass()));
    }

    protected BusinessModelDescriptor descriptor(BusinessOperationRequest request) {
        String ownerModule = request.targetRef() != null
                ? request.targetRef().entity().ownerModule() : request.ownerModule();
        String entityType = request.targetRef() != null
                ? request.targetRef().entity().entityType() : request.entityType();
        return catalog.require(ownerModule, entityType);
    }

    protected BusinessModelDeclaration declaration(BusinessOperationRequest request) {
        String ownerModule = request.targetRef() != null
                ? request.targetRef().entity().ownerModule() : request.ownerModule();
        String entityType = request.targetRef() != null
                ? request.targetRef().entity().entityType() : request.entityType();
        return persistence.require(ownerModule, entityType);
    }

    protected BusinessOperationDescriptor operationOf(BusinessModelDescriptor descriptor,
                                                    BusinessOperationRequest request) {
        BusinessOperationDescriptor operation = descriptor.operations().stream()
                .filter(op -> op.code().equals(request.operationCode())).findFirst()
                .orElseThrow(() -> new BusinessContractException("OPERATION_NOT_DECLARED",
                        "操作未在目录声明: " + request.operationCode()));
        if (operation.version() != request.operationVersion()) {
            throw new BusinessContractException("OPERATION_VERSION_CONFLICT",
                    "操作版本不一致: 期望 " + operation.version() + " 请求 " + request.operationVersion());
        }
        return operation;
    }

    private Map<String, Object> fixedInput(BusinessOperationRequest request) {
        if (request.input() == null) return Map.of();
        Map<String, Object> fields = new java.util.LinkedHashMap<>(request.input());
        fields.remove("$extensions");
        return fields;
    }

    private BusinessEntitySaveSupport.ExtensionPatch extensionPatch(BusinessOperationRequest request) {
        if (request.input() == null || !request.input().containsKey("$extensions")) return null;
        boolean enabled = descriptor(request).capabilities().stream().anyMatch(cap -> cap.enabled()
                && cap.type() == cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityType.DYNAMIC_FORM);
        Object value = request.input().get("$extensions");
        if (!enabled || !(value instanceof Map<?, ?> values)
                || !java.util.Set.of("definitionRevisionId", "expectedVersion", "values").containsAll(values.keySet())
                || !values.containsKey("expectedVersion") || !values.containsKey("values"))
            throw new BusinessContractException("OPERATION_INPUT_INVALID", "扩展保存载荷或能力声明无效");
        try {
            if(!(values.get("values") instanceof Map<?,?> fields) || !(values.get("expectedVersion") instanceof Number version)
                    ) throw new IllegalArgumentException();
            Object definition=values.get("definitionRevisionId");
            if(!(definition instanceof Number) && !(definition instanceof String id && id.matches("[1-9][0-9]*"))) throw new IllegalArgumentException();
            int expected=new java.math.BigDecimal(version.toString()).intValueExact();
            long definitionId=new java.math.BigDecimal(definition.toString()).longValueExact();
            if(expected<0 || definitionId<=0) throw new IllegalArgumentException();
            Map<String,Object> patch=new java.util.LinkedHashMap<>();
            for(var field:fields.entrySet()) {
                if(!(field.getKey() instanceof String code)) throw new IllegalArgumentException();
                // Preserve explicit nulls: global JSON serialization omits them from maps.
                patch.put(code,field.getValue());
            }
            return new BusinessEntitySaveSupport.ExtensionPatch(definitionId,expected,java.util.Collections.unmodifiableMap(patch));
        } catch(RuntimeException invalid) {
            throw new BusinessContractException("OPERATION_INPUT_INVALID","扩展补丁必须携带有效定义、值与并发依据");
        }
    }

    protected String requestDigest(BusinessOperationRequest request) {
        return OperationRequestDigest.of(request);
    }

    protected BusinessOperationReceipt decodeStoredReceipt(String payload) {
        return cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(payload, BusinessOperationReceipt.class);
    }

    protected OperationExecutionStore.OperationExecutionKey executionKey(ResolvedCaller caller,
                                                                       BusinessOperationRequest request) {
        BusinessModelDescriptor descriptor = descriptor(request);
        return new OperationExecutionStore.OperationExecutionKey(caller.tenantId(),
                "biz:" + descriptor.ownerModule() + "/" + descriptor.entityType(),
                caller.userId(), request.idempotencyKey());
    }
}
