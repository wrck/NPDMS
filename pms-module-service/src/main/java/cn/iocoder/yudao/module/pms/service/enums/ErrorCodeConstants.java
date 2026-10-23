package cn.iocoder.yudao.module.pms.service.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * PMS 持续服务域错误码枚举类
 *
 * service 持续服务域，使用 1-013-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 巡检规则 1-013-002-000 ==========
    ErrorCode INSPECTION_RULE_DETECTION_ID_DUPLICATE = new ErrorCode(1_013_002_003, "检测ID已存在：{}");
    ErrorCode INSPECTION_RULE_NAME_DUPLICATE = new ErrorCode(1_013_002_004, "巡检规则名称已存在：{}");
    ErrorCode INSPECTION_RULE_REVISION_NOT_EXISTS = new ErrorCode(1_013_002_005, "巡检规则修订不存在");
    ErrorCode INSPECTION_RULE_DRAFT_INVALID = new ErrorCode(1_013_002_006, "巡检规则草稿数据无效");
    ErrorCode INSPECTION_RULE_REVISION_VERSION_CONFLICT = new ErrorCode(1_013_002_007, "巡检规则修订版本冲突");
    ErrorCode INSPECTION_RULE_MANAGE_FORBIDDEN = new ErrorCode(1_013_002_008, "无巡检规则维护权限");
    ErrorCode INSPECTION_RULE_DISABLE_FORBIDDEN = new ErrorCode(1_013_002_009, "无巡检规则停用权限");
    ErrorCode INSPECTION_RULE_PUBLISH_FORBIDDEN = new ErrorCode(1_013_002_010, "无巡检规则发布权限");
    ErrorCode INSPECTION_RULE_IDEMPOTENCY_CONFLICT = new ErrorCode(1_013_002_011, "巡检规则操作幂等键冲突");
    ErrorCode INSPECTION_RULE_IDEMPOTENCY_IN_PROGRESS = new ErrorCode(1_013_002_012, "巡检规则操作正在处理中");
    ErrorCode INSPECTION_RULE_SECURITY_REVIEW_FORBIDDEN = new ErrorCode(1_013_002_013, "无巡检规则安全审核权限");

}
