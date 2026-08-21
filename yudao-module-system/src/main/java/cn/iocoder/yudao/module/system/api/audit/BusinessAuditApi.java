package cn.iocoder.yudao.module.system.api.audit;

import cn.iocoder.yudao.module.system.api.audit.dto.BusinessAuditCommand;

/** F-PROJ-001 追加写业务审计边界。 */
public interface BusinessAuditApi {

    void appendSuccess(BusinessAuditCommand command);

    void appendFailureAfterRollback(BusinessAuditCommand command);
}
