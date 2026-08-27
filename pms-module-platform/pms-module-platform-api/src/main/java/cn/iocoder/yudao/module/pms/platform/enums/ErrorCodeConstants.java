package cn.iocoder.yudao.module.pms.platform.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * PLATFORM 模块错误码，使用 1-010-000-000 段。
 */
public interface ErrorCodeConstants {

    ErrorCode FILE_COMMAND_INVALID = new ErrorCode(1_010_001_000, "文件命令参数无效");
    ErrorCode FILE_PROVIDER_UNAVAILABLE = new ErrorCode(1_010_001_001, "文件业务授权Provider不可用");
    ErrorCode FILE_SCOPE_FORBIDDEN = new ErrorCode(1_010_001_002, "无权访问该业务文件范围");
    ErrorCode FILE_SCOPE_VERSION_CONFLICT = new ErrorCode(1_010_001_003, "文件业务范围版本已变化");
    ErrorCode FILE_POLICY_NOT_MATCHED = new ErrorCode(1_010_001_004, "未匹配到可用的文件用途策略");
    ErrorCode FILE_POLICY_AMBIGUOUS = new ErrorCode(1_010_001_005, "文件用途策略同优先级多命中");
    ErrorCode FILE_SIZE_EXCEEDED = new ErrorCode(1_010_001_006, "文件大小超过允许上限");
    ErrorCode FILE_MEDIA_TYPE_INVALID = new ErrorCode(1_010_001_007, "文件媒体类型不符合用途策略");
    ErrorCode FILE_DIGEST_MISMATCH = new ErrorCode(1_010_001_008, "文件摘要校验不一致");
    ErrorCode FILE_SECURITY_SCAN_REJECTED = new ErrorCode(1_010_001_009, "文件安全扫描未通过");
    ErrorCode FILE_SECURITY_SCAN_UNAVAILABLE = new ErrorCode(1_010_001_010, "文件安全扫描Provider不可用");
    ErrorCode FILE_UPLOAD_SESSION_NOT_FOUND = new ErrorCode(1_010_001_011, "文件上传会话不存在");
    ErrorCode FILE_UPLOAD_SESSION_STATE_INVALID = new ErrorCode(1_010_001_012, "文件上传会话状态不允许当前操作");
    ErrorCode FILE_UPLOAD_SESSION_EXPIRED = new ErrorCode(1_010_001_013, "文件上传会话已失效");
    ErrorCode FILE_ARTIFACT_NOT_FOUND = new ErrorCode(1_010_001_014, "文件Artifact不存在");
    ErrorCode FILE_VERSION_NOT_FOUND = new ErrorCode(1_010_001_015, "文件版本不存在");
    ErrorCode FILE_VERSION_UNAVAILABLE = new ErrorCode(1_010_001_016, "文件版本当前不可用");
    ErrorCode FILE_FACT_VERSION_CONFLICT = new ErrorCode(1_010_001_017, "文件事实版本已变化");
    ErrorCode FILE_REFERENCE_NOT_FOUND = new ErrorCode(1_010_001_018, "文件业务引用不存在");
    ErrorCode FILE_REFERENCE_VERSION_CONFLICT = new ErrorCode(1_010_001_019, "文件业务引用版本已变化");
    ErrorCode FILE_ACCESS_GRANT_INVALID = new ErrorCode(1_010_001_020, "文件短时访问授权无效或已失效");
    ErrorCode FILE_ARCHIVE_CONFLICT = new ErrorCode(1_010_001_021, "文件归档事实冲突");
    ErrorCode FILE_STORAGE_RECEIPT_CONFLICT = new ErrorCode(1_010_001_022, "文件存储回执存在冲突");
    ErrorCode FILE_STORAGE_COMPENSATION_FAILED = new ErrorCode(1_010_001_023, "文件存储补偿失败，需进入对账");

    ErrorCode PLATFORM_COMMAND_KEY_CONFLICT = new ErrorCode(1_010_002_000,
            "幂等键已绑定不同请求（PMS-PLATFORM-COMMAND-KEY-CONFLICT）");
    ErrorCode PLATFORM_COMMAND_IN_PROGRESS = new ErrorCode(1_010_002_001,
            "相同幂等请求正在处理中（PMS-PLATFORM-COMMAND-IN-PROGRESS）");

    ErrorCode DYNAMIC_FORM_TEMPLATE_NOT_FOUND = new ErrorCode(1_010_003_000, "动态表单模板不存在");
    ErrorCode DYNAMIC_FORM_TEMPLATE_CODE_CONFLICT = new ErrorCode(1_010_003_001, "动态表单模板编码已存在");
    ErrorCode DYNAMIC_FORM_TEMPLATE_DISABLED = new ErrorCode(1_010_003_002, "动态表单模板已停用");
    ErrorCode DYNAMIC_FORM_CURRENT_REVISION_CHANGED = new ErrorCode(1_010_003_003, "动态表单当前发布修订已变化");
    ErrorCode DYNAMIC_FORM_DRAFT_ALREADY_EXISTS = new ErrorCode(1_010_003_004, "动态表单模板已存在草稿修订");
    ErrorCode DYNAMIC_FORM_REVISION_NOT_DRAFT = new ErrorCode(1_010_003_005, "动态表单修订不是草稿");
    ErrorCode DYNAMIC_FORM_SCHEMA_INVALID = new ErrorCode(1_010_003_006, "动态表单结构无效");
    ErrorCode DYNAMIC_FORM_FIELD_KEY_DUPLICATE = new ErrorCode(1_010_003_007, "动态表单字段编码重复");
    ErrorCode DYNAMIC_FORM_INSTANCE_NOT_FOUND = new ErrorCode(1_010_003_008, "动态表单实例不存在");
    ErrorCode DYNAMIC_FORM_INSTANCE_FIELD_UNKNOWN = new ErrorCode(1_010_003_009, "动态表单实例包含未知字段");
    ErrorCode DYNAMIC_FORM_FILE_FIELD_REQUIRES_FILE_API = new ErrorCode(1_010_003_010,
            "受控文件字段必须通过统一文件接口修改");
    ErrorCode DYNAMIC_FORM_VERSION_CONFLICT = new ErrorCode(1_010_003_011, "动态表单版本已变化");

}
