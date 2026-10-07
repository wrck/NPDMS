package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationRequest;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import java.util.Map;

/** Default CRUD plus controlled business differences; shared safety and transaction steps stay final. */
public class ExtensibleBusinessApplicationService extends DefaultBusinessApplicationService {
    protected ExtensibleBusinessApplicationService(DefaultBusinessApplicationService defaults) { super(defaults); }

    @Override
protected final ResolvedCaller resolveCaller(BusinessOperationRequest request) {
        return super.resolveCaller(request);
    }

    @Override
protected final void validateIdentityAndInput(ResolvedCaller caller, BusinessOperationRequest request) {
        super.validateIdentityAndInput(caller, request);
    }

    @Override
protected final void authorizeAndCheckState(ResolvedCaller caller, BusinessOperationRequest request) {
        super.authorizeAndCheckState(caller, request);
    }

    @Override
protected final LockedAggregate<BaseBusinessEntity> lockAggregate(ResolvedCaller caller, BusinessOperationRequest request) {
        return super.lockAggregate(caller, request);
    }

    @Override
protected final void reserveExecution(ResolvedCaller caller, BusinessOperationRequest request) {
        super.reserveExecution(caller, request);
    }

    @Override
protected final LockedAggregate<BaseBusinessEntity> lockTarget(ResolvedCaller caller, BusinessOperationRequest request) {
        return super.lockTarget(caller, request);
    }

    @Override
protected final void recordOutcome(ResolvedCaller caller, BusinessOperationRequest request, BusinessOperationReceipt receipt) {
        super.recordOutcome(caller, request, receipt);
    }


    @Override
protected final BusinessModelDescriptor descriptor(BusinessOperationRequest request) {
        return super.descriptor(request);
    }

    @Override
protected final BusinessModelDeclaration declaration(BusinessOperationRequest request) {
        return super.declaration(request);
    }

    @Override
protected final BusinessOperationDescriptor operationOf(BusinessModelDescriptor descriptor, BusinessOperationRequest request) {
        return super.operationOf(descriptor, request);
    }

    @Override
protected final String requestDigest(BusinessOperationRequest request) {
        return super.requestDigest(request);
    }

    @Override
protected final BusinessOperationReceipt decodeStoredReceipt(String payload) {
        return super.decodeStoredReceipt(payload);
    }

    @Override
protected final OperationExecutionStore.OperationExecutionKey executionKey(ResolvedCaller caller, BusinessOperationRequest request) {
        return super.executionKey(caller, request);
    }


    @Override protected final BusinessOperationReceipt domainCommand(ResolvedCaller caller, BusinessOperationRequest request,
            LockedAggregate<BaseBusinessEntity> locked) {
        validateBusinessOperation(request, detachedValues(currentValues(locked)));
        if (operationOf(descriptor(request), request).kind() != BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND)
            return super.domainCommand(caller, request, locked);
        return saveChanges(caller, request, locked, locked.changes());
    }

    @Override protected final Map<String, Object> prepareChanges(BusinessOperationRequest request, Map<String, Object> values) {
        return operationOf(descriptor(request), request).kind() == BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND
                ? customOperationChanges(request, detachedValues(values)) : super.prepareChanges(request, values);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detachedValues(Map<String, Object> values) {
        // Hooks cannot mutate collections/objects still attached to the persisted aggregate.
        return java.util.Collections.unmodifiableMap(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(values), Map.class));
    }

    @Override protected final void validateCapabilityChange(BusinessOperationRequest request, Map<String,Object> values) {
        validateBusinessOperation(request,detachedValues(values));
    }

    /** Optional domain validation, executed against the current aggregate inside the shared transaction. */
    protected void validateBusinessOperation(BusinessOperationRequest request, Map<String, Object> currentValues) { }

    /** Return only the business difference; the framework validates and persists it. */
    protected Map<String, Object> customOperationChanges(BusinessOperationRequest request, Map<String, Object> currentValues) {
        throw new BusinessContractException("OPERATION_NOT_DEFAULTED", "Custom operation requires an implementation: " + request.operationCode());
    }
}
