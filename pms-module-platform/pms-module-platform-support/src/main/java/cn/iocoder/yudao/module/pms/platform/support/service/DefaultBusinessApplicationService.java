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
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private final BusinessCallerContext callerContext;
    private final BusinessModelCatalog catalog;
    private final BusinessEntityPersistenceRegistry persistence;
    private final BusinessAccessGuard guard;
    private final OperationExecutionStore executionStore;

    public DefaultBusinessApplicationService(BusinessCallerContext callerContext,
                                             BusinessModelCatalog catalog,
                                             BusinessEntityPersistenceRegistry persistence,
                                             BusinessAccessGuard guard,
                                             OperationExecutionStore executionStore,
                                             BusinessEventPort eventPort,
                                             OperationAuditApi auditApi,
                                             TransactionOperations transactionOperations) {
        super(eventPort, auditApi, transactionOperations);
        this.callerContext = callerContext;
        this.catalog = catalog;
        this.persistence = persistence;
        this.guard = guard;
        this.executionStore = executionStore;
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
        requireWritableFields(descriptor, request.input());
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
    }

    @Override
    @SuppressWarnings("unchecked")
    protected LockedAggregate<BaseBusinessEntity> lockAggregate(ResolvedCaller caller,
                                                                BusinessOperationRequest request) {
        OperationExecutionStore.OperationExecutionKey key = executionKey(caller, request);
        String digest = OperationRequestDigest.of(request);
        if (!executionStore.reserve(key, digest)) {
            OperationExecutionStore.StoredExecution existing = executionStore.findExisting(key)
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
        if (request.targetRef() == null) {
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
        return new LockedAggregate<>(row, row.getVersion());
    }

    @Override
    protected BusinessOperationReceipt domainCommand(ResolvedCaller caller, BusinessOperationRequest request,
                                                     LockedAggregate<BaseBusinessEntity> locked) {
        BusinessOperationDescriptor operation = operationOf(descriptor(request), request);
        if (operation.kind() == BusinessOperationDescriptor.StandardOperationKind.CREATE) {
            BaseBusinessEntity created = createEntity(caller, declaration(request), request.input());
            BusinessModelDescriptor descriptor = descriptor(request);
            EntityRef ref = new EntityRef(caller.tenantId(), descriptor.ownerModule(),
                    descriptor.entityType(), created.getId());
            return new BusinessOperationReceipt(ReceiptOutcome.SAVED, ref, created.getVersion(),
                    List.of(), null, null);
        }
        if (operation.kind() != BusinessOperationDescriptor.StandardOperationKind.UPDATE) {
            throw new BusinessContractException("OPERATION_NOT_DEFAULTED",
                    "操作未由默认服务开放，须由专业服务实现: " + request.operationCode());
        }
        BaseBusinessEntity aggregate = locked.aggregate();
        applyWritableFields(descriptor(request), aggregate, request.input());
        BaseMapper<BaseBusinessEntity> mapper = persistence.mapperOf(declaration(request));
        Long expected = locked.concurrencyBasis();
        if (expected != null) {
            // 生产装配（yudao-server PmsMybatisConfiguration）注册了乐观锁插件：
            // 实体 version 保持旧值，插件负责 SET version+1 并追加 WHERE version=旧值；
            // 这里只保留 id 与租户条件，不能重复手写 version 条件（会与插件条件相乘恒为空）。
            QueryWrapper<BaseBusinessEntity> condition = new QueryWrapper<>();
            condition.eq("id", aggregate.getId()).eq("tenant_id", caller.tenantId());
            if (mapper.update(aggregate, condition) != 1) {
                throw new BusinessContractException("CONCURRENCY_CONFLICT",
                        "并发依据过期: " + request.targetRef().entity());
            }
        } else {
            if (mapper.updateById(aggregate) != 1) {
                throw new BusinessContractException("ENTITY_NOT_FOUND",
                        "目标不存在: " + request.targetRef().entity());
            }
        }
        return new BusinessOperationReceipt(ReceiptOutcome.SAVED, request.targetRef().entity(),
                aggregate.getVersion(), List.of(), null, null);
    }

    @Override
    protected void recordOutcome(ResolvedCaller caller, BusinessOperationRequest request,
                                 BusinessOperationReceipt receipt) {
        OperationExecutionStore.OperationExecutionKey key = executionKey(caller, request);
        executionStore.complete(key, request.entityType(), receipt.entityRef().entityId().toString(), receipt);
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
    private BaseBusinessEntity createEntity(ResolvedCaller caller, BusinessModelDeclaration declaration,
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
        persistence.<BaseBusinessEntity>mapperOf(declaration).insert(entity);
        if (entity.getId() == null) {
            throw new BusinessContractException("ENTITY_ID_MISSING", "插入后实体主键缺失");
        }
        return entity;
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
        }
    }

    private boolean isWritable(BusinessModelDescriptor descriptor, String fieldCode) {
        return descriptor.fields().stream()
                .anyMatch(field -> field.code().equals(fieldCode) && field.writable());
    }

    private void setField(BusinessModelIntrospector.IntrospectedField field, Object target, Object value) {
        try {
            field.property().set(target, convert(value, field.type()));
        } catch (IllegalAccessException | IllegalArgumentException ex) {
            throw new BusinessContractException("FIELD_VALUE_INVALID",
                    "字段值非法: " + field.code() + " " + ex.getMessage());
        }
    }

    private Object convert(Object value, EntityField.Type type) {
        if (value == null) {
            return null;
        }
        return switch (type) {
            case NUMBER -> value instanceof Number ? value : new BigDecimal(value.toString());
            case TEXT, TEXT_LIST, OBJECT_LIST -> value;
            case BOOLEAN -> value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
            case DATE -> value instanceof LocalDate ? value : LocalDate.parse(value.toString());
            case DATETIME -> value instanceof LocalDateTime ? value : LocalDateTime.parse(value.toString());
        };
    }

    private BusinessModelDescriptor descriptor(BusinessOperationRequest request) {
        String ownerModule = request.targetRef() != null
                ? request.targetRef().entity().ownerModule() : request.ownerModule();
        String entityType = request.targetRef() != null
                ? request.targetRef().entity().entityType() : request.entityType();
        return catalog.require(ownerModule, entityType);
    }

    private BusinessModelDeclaration declaration(BusinessOperationRequest request) {
        String ownerModule = request.targetRef() != null
                ? request.targetRef().entity().ownerModule() : request.ownerModule();
        String entityType = request.targetRef() != null
                ? request.targetRef().entity().entityType() : request.entityType();
        return persistence.require(ownerModule, entityType);
    }

    private BusinessOperationDescriptor operationOf(BusinessModelDescriptor descriptor,
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

    private OperationExecutionStore.OperationExecutionKey executionKey(ResolvedCaller caller,
                                                                       BusinessOperationRequest request) {
        BusinessModelDescriptor descriptor = descriptor(request);
        return new OperationExecutionStore.OperationExecutionKey(caller.tenantId(),
                "biz:" + descriptor.ownerModule() + "/" + descriptor.entityType(),
                caller.userId(), request.idempotencyKey());
    }
}
