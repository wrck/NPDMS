package cn.iocoder.yudao.module.pms.platform.api.dynamicform;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstanceCloneCommand;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstanceCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstanceFact;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstancePatchCommand;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstanceQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstanceRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionFact;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionUsageQuery;

public interface DynamicFormBusinessInstanceApi {
    DynamicFormRevisionFact inspectRevisionForUsage(DynamicFormRevisionUsageQuery query);
    DynamicFormRevisionFact lockAndRevalidateRevisionForUsage(DynamicFormRevisionRevalidationQuery query);
    DynamicFormInstanceFact createBusinessInstance(DynamicFormInstanceCreateCommand command);
    DynamicFormInstanceFact inspectInstance(DynamicFormInstanceQuery query);
    DynamicFormInstanceFact patchInstanceValues(DynamicFormInstancePatchCommand command);
    DynamicFormInstanceFact cloneBusinessInstance(DynamicFormInstanceCloneCommand command);
    DynamicFormInstanceFact lockAndRevalidateInstance(DynamicFormInstanceRevalidationQuery query);
}
