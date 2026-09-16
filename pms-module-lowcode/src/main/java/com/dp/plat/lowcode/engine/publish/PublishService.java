package com.dp.plat.lowcode.engine.publish;

import com.dp.plat.lowcode.entity.LowCodePublishRecord;

import java.util.List;

public interface PublishService {
    /** 提交发布申请 */
    LowCodePublishRecord submitForPublish(String configType, Long configId, String changeLog, Long applicantId, String applicant);

    /** 校验配置完整性 */
    List<String> validate(String configType, Long configId);

    /** 审批通过 → PUBLISHED */
    LowCodePublishRecord approve(Long publishId, Long approverId, String approver);

    /** 审批拒绝 */
    LowCodePublishRecord reject(Long publishId, String reason, Long approverId, String approver);

    /** 回滚到指定发布版本 */
    LowCodePublishRecord rollback(Long publishId, Long userId, String userName);

    /** 查询发布记录 */
    List<LowCodePublishRecord> listByConfig(String configType, Long configId);

    List<LowCodePublishRecord> listPending();
}
