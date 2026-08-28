package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DynamicFormBusinessInstanceApiImpl implements DynamicFormBusinessInstanceApi {

    private final DynamicFormBusinessInstanceService service;

    @Override
    @Transactional(readOnly = true)
    public DynamicFormRevisionFact inspectRevisionForUsage(DynamicFormRevisionUsageQuery query) {
        return service.inspectRevisionForUsage(query);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public DynamicFormRevisionFact lockAndRevalidateRevisionForUsage(DynamicFormRevisionRevalidationQuery query) {
        return service.lockAndRevalidateRevisionForUsage(query);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public DynamicFormInstanceFact createBusinessInstance(DynamicFormInstanceCreateCommand command) {
        return service.createBusinessInstance(command);
    }

    @Override
    @Transactional(readOnly = true)
    public DynamicFormInstanceFact inspectInstance(DynamicFormInstanceQuery query) {
        return service.inspectInstance(query);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public DynamicFormInstanceFact patchInstanceValues(DynamicFormInstancePatchCommand command) {
        return service.patchInstanceValues(command);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public DynamicFormInstanceFact cloneBusinessInstance(DynamicFormInstanceCloneCommand command) {
        return service.cloneBusinessInstance(command);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public DynamicFormInstanceFact lockAndRevalidateInstance(DynamicFormInstanceRevalidationQuery query) {
        return service.lockAndRevalidateInstance(query);
    }
}
