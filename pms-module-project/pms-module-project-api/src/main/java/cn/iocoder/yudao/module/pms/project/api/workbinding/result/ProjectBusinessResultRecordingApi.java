package cn.iocoder.yudao.module.pms.project.api.workbinding.result;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;

/** Appends technical recovery metadata inside the existing Owner transaction, never an independent write. */
public interface ProjectBusinessResultRecordingApi {
    void record(BusinessOperationResultEvent event);
}
