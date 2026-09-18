package cn.iocoder.yudao.module.pms.project.api.workbinding.result;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;

/** Owner-owned translation from its existing result event to native object/result lookup. */
public interface BusinessResultChangeSource extends BusinessResultSource {
    Query changeQuery(BusinessOperationResultEvent event);

    default boolean declaresFormation(BusinessOperationResultEvent event) {
        return descriptor().type().resultType().equals(event.resultCode());
    }
}
