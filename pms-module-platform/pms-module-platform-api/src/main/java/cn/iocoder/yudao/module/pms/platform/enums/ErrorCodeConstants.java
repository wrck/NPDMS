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

}
