package cn.iocoder.yudao.module.pms.customer.service.customer;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerLifecycleStatus;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerSourceType;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerExternalMappingDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerExternalMappingMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query.CurrentCustomerMappingQuery;
import cn.iocoder.yudao.module.pms.customer.domain.customer.CustomerFieldOwnershipRules;
import cn.iocoder.yudao.module.pms.customer.domain.customer.CustomerLifecycleAction;
import cn.iocoder.yudao.module.pms.customer.domain.customer.CustomerRules;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.CreateCustomerCommand;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.CustomerCommandResult;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.CustomerLifecycleCommand;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.UpdateCustomerCommand;
import cn.iocoder.yudao.module.pms.customer.service.guard.CustomerDeletionGuardService;
import cn.iocoder.yudao.module.pms.customer.service.history.CustomerHistoryService;
import cn.iocoder.yudao.module.pms.customer.service.outbox.CustomerOutboxPayloadFactory;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerClassificationAccessService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerClassificationInput;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerVisibleScope;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.Decision;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.IdempotencyScope;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.SuccessFacts;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_DELETE_GUARD_BLOCKED;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_EXTERNAL_MAPPING_DUPLICATE;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_SCOPE_DENIED;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;

@Service
public class CustomerApplicationServiceImpl implements CustomerApplicationService {

    public static final String CREATE_SCOPE = "POST:/pms/customers";
    public static final String UPDATE_SCOPE = "PUT:/pms/customers/{id}";
    public static final String DISABLE_SCOPE = "POST:/pms/customers/{id}/actions/disable";
    public static final String DELETE_SCOPE = "POST:/pms/customers/{id}/actions/delete";
    public static final String RESTORE_SCOPE = "POST:/pms/customers/{id}/actions/restore";
    private static final String CRM_SOURCE_SYSTEM = "CRM";

    @Resource
    private PlatformCommandExecutionApi platformCommandExecutionApi;
    @Resource
    private CustomerMasterMapper customerMasterMapper;
    @Resource
    private CustomerExternalMappingMapper customerExternalMappingMapper;
    @Resource
    private CustomerHistoryService customerHistoryService;
    @Resource
    private CustomerDeletionGuardService deletionGuardService;
    @Resource
    private CustomerOutboxPayloadFactory outboxPayloadFactory;
    @Resource
    private CustomerClassificationAccessService classificationAccessService;
    @Resource
    private CustomerScopeContextService scopeContextService;

    @Override
    public CustomerCommandResult create(CreateCustomerCommand command) {
        Actor actor = actor(command == null ? null : command.tenantId());
        validateCreate(command);
        CustomerRules.validateTemporaryCustomer(command.sourceType(), command.sourceKey(), command.sourceVersion(),
                command.temporaryReason(), command.reconciliationPending());
        String requestDigest = digestCreate(command);
        var execution = platformCommandExecutionApi.execute(
                new IdempotencyScope(actor.tenantId(), CREATE_SCOPE, actor.actorId(), command.idempotencyKey()),
                requestDigest, CustomerCommandResult.class,
                () -> createOnce(command, actor),
                result -> createFacts(command, actor, result));
        return result(execution);
    }

    @Override
    public CustomerCommandResult update(UpdateCustomerCommand command) {
        Actor actor = actor(command == null ? null : command.tenantId());
        validateUpdate(command);
        String requestDigest = digestUpdate(command);
        var execution = platformCommandExecutionApi.execute(
                new IdempotencyScope(actor.tenantId(), UPDATE_SCOPE, actor.actorId(), command.idempotencyKey()),
                requestDigest, CustomerCommandResult.class,
                () -> updateOnce(command, actor),
                result -> updateFacts(command, actor, result));
        return result(execution);
    }

    @Override
    public CustomerCommandResult disable(CustomerLifecycleCommand command) {
        return executeLifecycle(command, CustomerLifecycleAction.DISABLE, CustomerLifecycleStatus.DISABLED,
                false, false, DISABLE_SCOPE, "CUSTOMER_DISABLE", "DISABLED");
    }

    @Override
    public CustomerCommandResult delete(CustomerLifecycleCommand command) {
        return executeLifecycle(command, CustomerLifecycleAction.DELETE, CustomerLifecycleStatus.DELETED,
                false, true, DELETE_SCOPE, "CUSTOMER_DELETE", "DELETED");
    }

    @Override
    public CustomerCommandResult restore(CustomerLifecycleCommand command) {
        return executeLifecycle(command, CustomerLifecycleAction.RESTORE, CustomerLifecycleStatus.ENABLED,
                true, false, RESTORE_SCOPE, "CUSTOMER_RESTORE", "RESTORED");
    }

    private CustomerCommandResult createOnce(CreateCustomerCommand command, Actor actor) {
        if (customerMasterMapper.selectByTenantIdAndCode(actor.tenantId(), command.code()) != null) {
            throw exception(CUSTOMER_CODE_DUPLICATE);
        }
        if (hasSourceIdentity(command) && customerExternalMappingMapper.selectCurrent(
                CurrentCustomerMappingQuery.bySource(actor.tenantId(), CRM_SOURCE_SYSTEM, command.sourceKey())) != null) {
            throw exception(CUSTOMER_EXTERNAL_MAPPING_DUPLICATE);
        }
        var classification = classificationAccessService.validate(actor.tenantId(),
                new CustomerClassificationInput(command.departmentCode(), command.marketCode(), command.systemCode(),
                        command.expendCode(), command.industryCode()),
                scopeContextService.resolve(actor.tenantId(), actor.actorId()));
        CustomerMasterDO customer = new CustomerMasterDO();
        customer.setTenantId(actor.tenantId());
        customer.setCode(command.code());
        customer.setName(command.name());
        customer.setShortName(command.shortName());
        customer.setLifecycleStatus(CustomerLifecycleStatus.ENABLED.name());
        customer.setSourceType(command.sourceType().name());
        customer.setSourceKey(command.sourceKey());
        customer.setSourceVersion(command.sourceVersion());
        customer.setSyncStatus(command.sourceType() == CustomerSourceType.CRM_SYNC ? "SYNCED" : "NOT_APPLICABLE");
        customer.setDataAsOf(LocalDateTime.now());
        customer.setReconciliationPending(command.reconciliationPending());
        customer.setTemporaryReason(command.temporaryReason());
        customer.setDepartmentCode(classification.departmentCode());
        customer.setDepartmentName(classification.departmentName());
        customer.setMarketCode(classification.marketCode());
        customer.setMarketName(classification.marketName());
        customer.setSystemCode(classification.systemCode());
        customer.setSystemName(classification.systemName());
        customer.setExpendCode(classification.expendCode());
        customer.setExpendName(classification.expendName());
        customer.setIndustryCode(classification.industryCode());
        customer.setIndustryName(classification.industryName());
        customer.setRemark(command.remark());
        customer.setVersion(0);
        customerMasterMapper.insert(customer);
        if (hasSourceIdentity(command)) {
            insertMapping(customer, command);
        }
        customerHistoryService.appendCreate(customer, actor.actorId(), command.idempotencyKey());
        return new CustomerCommandResult(customer.getId(), 0L, false);
    }

    private CustomerCommandResult updateOnce(UpdateCustomerCommand command, Actor actor) {
        CustomerMasterDO current = customerMasterMapper.selectById(command.customerId());
        if (current == null || !Objects.equals(current.getTenantId(), actor.tenantId())) {
            throw exception(CUSTOMER_NOT_EXISTS);
        }
        boolean crmMapped = customerExternalMappingMapper.selectCurrentByCustomerId(
                actor.tenantId(), command.customerId()) != null;
        CustomerFieldOwnershipRules.validateBusinessUpdate(command.changedFields(), crmMapped);
        if (!Objects.equals(command.expectedVersion(), Long.valueOf(current.getVersion()))) {
            throw exception(CUSTOMER_VERSION_CONFLICT);
        }
        boolean updateClassification = command.changedFields().contains("classification");
        var classification = updateClassification
                ? classificationAccessService.validate(actor.tenantId(),
                        new CustomerClassificationInput(command.departmentCode(), command.marketCode(),
                                command.systemCode(), command.expendCode(), command.industryCode()),
                        scopeContextService.resolve(actor.tenantId(), actor.actorId()))
                : null;
        CustomerPlatformUpdate update = new CustomerPlatformUpdate(
                actor.tenantId(), command.customerId(), command.name(), command.shortName(), command.remark(),
                classification == null ? null : classification.departmentCode(),
                classification == null ? null : classification.departmentName(),
                classification == null ? null : classification.marketCode(),
                classification == null ? null : classification.marketName(),
                classification == null ? null : classification.systemCode(),
                classification == null ? null : classification.systemName(),
                classification == null ? null : classification.expendCode(),
                classification == null ? null : classification.expendName(),
                classification == null ? null : classification.industryCode(),
                classification == null ? null : classification.industryName(),
                command.changedFields().contains("name"), command.changedFields().contains("shortName"),
                command.changedFields().contains("remark"), updateClassification, command.expectedVersion());
        if (customerMasterMapper.updatePlatformFieldsByVersion(update) != 1) {
            throw exception(CUSTOMER_VERSION_CONFLICT);
        }
        customerHistoryService.appendUpdate(current, command, actor.actorId(), command.idempotencyKey());
        return new CustomerCommandResult(command.customerId(), command.expectedVersion() + 1L, false);
    }

    private CustomerCommandResult executeLifecycle(CustomerLifecycleCommand command, CustomerLifecycleAction action,
                                                   CustomerLifecycleStatus targetStatus, boolean expectedDeleted,
                                                   boolean targetDeleted, String scope, String auditAction,
                                                   String eventAction) {
        Actor actor = actor(command == null ? null : command.tenantId());
        validateLifecycle(command);
        String requestDigest = digestLifecycle(command, action);
        var execution = platformCommandExecutionApi.execute(
                new IdempotencyScope(actor.tenantId(), scope, actor.actorId(), command.idempotencyKey()),
                requestDigest, CustomerCommandResult.class,
                () -> lifecycleOnce(command, actor, action, targetStatus, expectedDeleted, targetDeleted),
                result -> lifecycleFacts(command, actor, result, auditAction, eventAction));
        return result(execution);
    }

    private CustomerCommandResult lifecycleOnce(CustomerLifecycleCommand command, Actor actor,
                                                CustomerLifecycleAction action, CustomerLifecycleStatus targetStatus,
                                                boolean expectedDeleted, boolean targetDeleted) {
        CustomerMasterDO current = action == CustomerLifecycleAction.RESTORE
                ? customerMasterMapper.selectIncludingDeleted(actor.tenantId(), command.customerId())
                : customerMasterMapper.selectById(command.customerId());
        if (current == null || !Objects.equals(current.getTenantId(), actor.tenantId())) {
            throw exception(CUSTOMER_NOT_EXISTS);
        }
        CustomerVisibleScope scope = scopeContextService.resolve(actor.tenantId(), actor.actorId());
        if (hasClassification(current)) {
            classificationAccessService.validate(actor.tenantId(),
                    new CustomerClassificationInput(current.getDepartmentCode(), current.getMarketCode(),
                            current.getSystemCode(), current.getExpendCode(), current.getIndustryCode()), scope);
        } else if (!scope.all()) {
            throw exception(CUSTOMER_SCOPE_DENIED);
        }
        CustomerLifecycleStatus currentStatus = CustomerLifecycleStatus.valueOf(current.getLifecycleStatus());
        CustomerRules.validateTransition(currentStatus, action);
        if (!Objects.equals(command.expectedVersion(), Long.valueOf(current.getVersion()))) {
            throw exception(CUSTOMER_VERSION_CONFLICT);
        }
        if (action == CustomerLifecycleAction.DELETE
                && !deletionGuardService.check(actor.tenantId(), command.customerId()).allowed()) {
            throw exception(CUSTOMER_DELETE_GUARD_BLOCKED);
        }
        CustomerLifecycleUpdate update = new CustomerLifecycleUpdate(
                actor.tenantId(), command.customerId(), currentStatus.name(), targetStatus.name(),
                expectedDeleted, targetDeleted, command.expectedVersion());
        if (customerMasterMapper.updateLifecycleByVersion(update) != 1) {
            throw exception(CUSTOMER_VERSION_CONFLICT);
        }
        customerHistoryService.appendLifecycle(
                current, targetStatus, command.reason(), actor.actorId(), command.idempotencyKey());
        return new CustomerCommandResult(command.customerId(), command.expectedVersion() + 1L, false);
    }

    private void insertMapping(CustomerMasterDO customer, CreateCustomerCommand command) {
        CustomerExternalMappingDO mapping = new CustomerExternalMappingDO();
        mapping.setTenantId(customer.getTenantId());
        mapping.setCustomerId(customer.getId());
        mapping.setSourceSystem(CRM_SOURCE_SYSTEM);
        mapping.setSourceKey(command.sourceKey());
        mapping.setSourceVersion(command.sourceVersion());
        mapping.setEffectiveFrom(customer.getDataAsOf());
        customerExternalMappingMapper.insert(mapping);
    }

    private SuccessFacts createFacts(CreateCustomerCommand command, Actor actor, CustomerCommandResult result) {
        CustomerMasterDO eventCustomer = eventCustomer(result, command.sourceType().name());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("customerId", result.customerId());
        detail.put("codeDigest", sha256(command.code()));
        detail.put("nameDigest", sha256(command.name()));
        detail.put("sourceType", command.sourceType().name());
        detail.put("reconciliationPending", command.reconciliationPending());
        return new SuccessFacts("CUSTOMER_CREATE", "Customer", String.valueOf(result.customerId()),
                actor.correlationId(), JsonUtils.toJsonString(detail), "CustomerUpdated",
                outboxPayloadFactory.customerUpdated(eventCustomer, "CREATED"));
    }

    private SuccessFacts updateFacts(UpdateCustomerCommand command, Actor actor, CustomerCommandResult result) {
        CustomerMasterDO eventCustomer = eventCustomer(result, null);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("customerId", result.customerId());
        detail.put("changedFields", command.changedFields());
        detail.put("version", result.version());
        return new SuccessFacts("CUSTOMER_UPDATE", "Customer", String.valueOf(result.customerId()),
                actor.correlationId(), JsonUtils.toJsonString(detail), "CustomerUpdated",
                outboxPayloadFactory.customerUpdated(eventCustomer, "UPDATED"));
    }

    private SuccessFacts lifecycleFacts(CustomerLifecycleCommand command, Actor actor, CustomerCommandResult result,
                                        String auditAction, String eventAction) {
        CustomerMasterDO eventCustomer = eventCustomer(result, null);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("customerId", result.customerId());
        detail.put("reasonDigest", sha256(command.reason()));
        detail.put("version", result.version());
        return new SuccessFacts(auditAction, "Customer", String.valueOf(result.customerId()),
                actor.correlationId(), JsonUtils.toJsonString(detail), "CustomerUpdated",
                outboxPayloadFactory.customerUpdated(eventCustomer, eventAction));
    }

    private CustomerMasterDO eventCustomer(CustomerCommandResult result, String sourceType) {
        CustomerMasterDO customer = new CustomerMasterDO();
        customer.setId(result.customerId());
        customer.setVersion(Math.toIntExact(result.version()));
        customer.setSourceType(sourceType);
        return customer;
    }

    private CustomerCommandResult result(PlatformCommandExecutionApi.ExecutionResult<CustomerCommandResult> execution) {
        if (execution.decision() == Decision.CONFLICT) {
            throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        }
        if (execution.decision() == Decision.IN_PROGRESS) {
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        }
        CustomerCommandResult response = execution.response();
        return execution.decision() == Decision.REPLAY_COMPLETED
                ? new CustomerCommandResult(response.customerId(), response.version(), true)
                : response;
    }

    private Actor actor(Long commandTenantId) {
        Long tenantId = TenantContextHolder.getTenantId();
        var loginUser = SecurityFrameworkUtils.getLoginUser();
        if (tenantId == null && loginUser != null) {
            tenantId = loginUser.getTenantId();
        }
        Long actorId = loginUser == null ? null : loginUser.getId();
        if (!Objects.equals(tenantId, commandTenantId) || actorId == null) {
            throw new IllegalArgumentException("客户命令身份上下文不完整");
        }
        return new Actor(tenantId, actorId, correlationId());
    }

    private void validateCreate(CreateCustomerCommand command) {
        if (command == null || isBlank(command.code()) || isBlank(command.name()) || command.sourceType() == null
                || isBlank(command.departmentCode()) || isBlank(command.marketCode()) || isBlank(command.systemCode())
                || isBlank(command.expendCode()) || isBlank(command.industryCode()) || isBlank(command.idempotencyKey())) {
            throw new IllegalArgumentException("客户创建命令不完整");
        }
        if (command.sourceType() == CustomerSourceType.CRM_SYNC
                && (isBlank(command.sourceKey()) || isBlank(command.sourceVersion()))) {
            throw new IllegalArgumentException("CRM 客户来源身份不完整");
        }
    }

    private void validateUpdate(UpdateCustomerCommand command) {
        Set<String> fields = command == null ? null : command.changedFields();
        if (command == null || command.customerId() == null || command.expectedVersion() == null
                || fields == null || fields.isEmpty() || isBlank(command.idempotencyKey())) {
            throw new IllegalArgumentException("客户更新命令不完整");
        }
        Set<String> supported = Set.of("name", "shortName", "remark", "classification");
        if (!supported.containsAll(fields)) {
            throw new IllegalArgumentException("客户更新字段不受支持: " + fields);
        }
        if (fields.contains("classification")
                && (isBlank(command.departmentCode()) || isBlank(command.marketCode())
                || isBlank(command.systemCode()) || isBlank(command.expendCode())
                || isBlank(command.industryCode()))) {
            throw new IllegalArgumentException("客户分类更新命令不完整");
        }
    }

    private void validateLifecycle(CustomerLifecycleCommand command) {
        if (command == null || command.customerId() == null || command.expectedVersion() == null
                || isBlank(command.reason()) || isBlank(command.idempotencyKey())) {
            throw new IllegalArgumentException("客户生命周期命令不完整");
        }
    }

    private boolean hasSourceIdentity(CreateCustomerCommand command) {
        return command.sourceType() == CustomerSourceType.CRM_SYNC;
    }

    private String digestCreate(CreateCustomerCommand command) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tenantId", command.tenantId());
        request.put("code", command.code());
        request.put("name", command.name());
        request.put("shortName", nullable(command.shortName()));
        request.put("remark", nullable(command.remark()));
        request.put("sourceType", command.sourceType().name());
        request.put("sourceKey", nullable(command.sourceKey()));
        request.put("sourceVersion", nullable(command.sourceVersion()));
        request.put("temporaryReason", nullable(command.temporaryReason()));
        request.put("reconciliationPending", command.reconciliationPending());
        request.put("departmentCode", command.departmentCode());
        request.put("marketCode", command.marketCode());
        request.put("systemCode", command.systemCode());
        request.put("expendCode", command.expendCode());
        request.put("industryCode", command.industryCode());
        return sha256(JsonUtils.toJsonString(request));
    }

    private String digestUpdate(UpdateCustomerCommand command) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tenantId", command.tenantId());
        request.put("customerId", command.customerId());
        request.put("name", nullable(command.name()));
        request.put("shortName", nullable(command.shortName()));
        request.put("remark", nullable(command.remark()));
        request.put("departmentCode", nullable(command.departmentCode()));
        request.put("marketCode", nullable(command.marketCode()));
        request.put("systemCode", nullable(command.systemCode()));
        request.put("expendCode", nullable(command.expendCode()));
        request.put("industryCode", nullable(command.industryCode()));
        request.put("changedFields", command.changedFields().stream().sorted().toList());
        request.put("expectedVersion", command.expectedVersion());
        return sha256(JsonUtils.toJsonString(request));
    }

    private String digestLifecycle(CustomerLifecycleCommand command, CustomerLifecycleAction action) {
        return sha256(JsonUtils.toJsonString(Map.of(
                "tenantId", command.tenantId(),
                "customerId", command.customerId(),
                "action", action.name(),
                "reason", command.reason(),
                "expectedVersion", command.expectedVersion())));
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }

    private String correlationId() {
        String traceId = cn.iocoder.yudao.framework.common.util.monitor.TracerUtils.getTraceId();
        return isBlank(traceId) ? "customer-command" : traceId;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }

    private boolean hasClassification(CustomerMasterDO customer) {
        return !isBlank(customer.getDepartmentCode())
                && !isBlank(customer.getMarketCode())
                && !isBlank(customer.getSystemCode())
                && !isBlank(customer.getExpendCode())
                && !isBlank(customer.getIndustryCode());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Actor(Long tenantId, Long actorId, String correlationId) {
    }
}
