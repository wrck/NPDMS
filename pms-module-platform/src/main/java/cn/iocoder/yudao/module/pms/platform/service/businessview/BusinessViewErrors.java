package cn.iocoder.yudao.module.pms.platform.service.businessview;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
/** PM-03 / F-PLT-003: local, previously unused PLT 004 namespace. */
public final class BusinessViewErrors {
    private BusinessViewErrors() { }
    public static final ErrorCode INVALID = new ErrorCode(1_010_004_000, "业务视图参数或字段组合无效");
    public static final ErrorCode NOT_FOUND = new ErrorCode(1_010_004_001, "业务视图注册不存在");
    public static final ErrorCode VERSION_CONFLICT = new ErrorCode(1_010_004_002, "业务视图版本已变化");
    public static final ErrorCode STATE_INVALID = new ErrorCode(1_010_004_003, "业务视图状态不允许该操作");
    public static final ErrorCode UNAVAILABLE = new ErrorCode(1_010_004_004, "业务视图组件或精确引用不可用");
    public static final ErrorCode IDENTITY_CONFLICT = new ErrorCode(1_010_004_005, "业务视图身份或修订已存在");
    public static final ErrorCode DRAFT_EXISTS = new ErrorCode(1_010_004_006, "业务视图已存在草稿");
    public static final ErrorCode KEY_CONFLICT = new ErrorCode(1_010_004_007, "业务视图幂等键与请求冲突");
    public static final ErrorCode IN_PROGRESS = new ErrorCode(1_010_004_008, "业务视图命令正在处理");
}
