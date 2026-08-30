package cn.iocoder.yudao.module.pms.asset.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * PMS 资产域错误码常量
 *
 * 错误码段分配：
 * - equipment:           1_015_001_000
 * - equipment-version:   1_015_002_000
 * - equipment-config-log:1_015_003_000
 * - asset-location:       1_015_004_000
 * - asset-product-type:   1_015_005_000
 */
public interface ErrorCodeConstants {

    // ========== 设备档案 1_015_001_000 ==========
    ErrorCode AST_EQUIPMENT_NOT_EXISTS = new ErrorCode(1_015_001_000, "设备档案不存在");
    ErrorCode AST_EQUIPMENT_SERIAL_NUMBER_DUPLICATE = new ErrorCode(1_015_001_001, "设备序列号已存在");
    ErrorCode AST_EQUIPMENT_STATUS_INVALID = new ErrorCode(1_015_001_002, "设备状态不允许当前操作");
    ErrorCode AST_EQUIPMENT_SCRAPPED = new ErrorCode(1_015_001_003, "设备已报废，不允许修改");
    ErrorCode AST_EQUIPMENT_LOCATION_COMMAND_INVALID = new ErrorCode(1_015_001_004, "设备位置生效命令无效");
    ErrorCode AST_EQUIPMENT_LOCATION_CONFLICT = new ErrorCode(1_015_001_005, "设备当前位置已变更，请刷新后重试");
    ErrorCode AST_EQUIPMENT_CUSTOMER_UNAVAILABLE = new ErrorCode(1_015_001_006, "所属客户不存在或不可用于新设备关系");
    ErrorCode AST_DEVICE_ASSEMBLY_COMMAND_INVALID = new ErrorCode(1_015_001_007, "设备装配命令无效");
    ErrorCode AST_DEVICE_ASSEMBLY_DEVICE_NOT_EXISTS = new ErrorCode(1_015_001_008, "装配设备不存在或不属于当前租户");
    ErrorCode AST_DEVICE_ASSEMBLY_CYCLE = new ErrorCode(1_015_001_009, "设备装配关系不允许形成循环");

    // ========== 设备版本历史 1_015_002_000 ==========
    ErrorCode AST_EQUIPMENT_VERSION_NOT_EXISTS = new ErrorCode(1_015_002_000, "设备版本记录不存在");

    // ========== 设备配置日志 1_015_003_000 ==========
    ErrorCode AST_EQUIPMENT_CONFIG_LOG_NOT_EXISTS = new ErrorCode(1_015_003_000, "设备配置日志不存在");
    ErrorCode AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_FORBIDDEN =
            new ErrorCode(1_015_003_001, "无配置Log文件下载权限");
    ErrorCode AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID =
            new ErrorCode(1_015_003_002, "配置Log下载授权无效或已失效");

    // ========== 资产地点 1_015_004_000 ==========
    ErrorCode AST_ADDRESS_NOT_EXISTS = new ErrorCode(1_015_004_000, "地址不存在");
    ErrorCode AST_SITE_NOT_EXISTS = new ErrorCode(1_015_004_001, "站点不存在");
    ErrorCode AST_SITE_LOCATION_NOT_EXISTS = new ErrorCode(1_015_004_002, "站点位置不存在");
    ErrorCode AST_LOCATION_VERSION_CONFLICT = new ErrorCode(1_015_004_003, "地点版本已变更，请刷新后重试");
    ErrorCode AST_LOCATION_REFERENCE_INVALID = new ErrorCode(1_015_004_004, "地点结构化引用不完整");
    ErrorCode AST_SITE_CODE_DUPLICATE = new ErrorCode(1_015_004_005, "站点编码已存在");
    ErrorCode AST_SITE_LOCATION_CODE_DUPLICATE = new ErrorCode(1_015_004_006, "站点内位置编码已存在");
    ErrorCode AST_SITE_LOCATION_CYCLE = new ErrorCode(1_015_004_007, "站点位置树不允许形成循环");
    ErrorCode AST_SITE_LOCATION_CROSS_SITE = new ErrorCode(1_015_004_008, "站点位置不允许跨站点移动");
    ErrorCode AST_SITE_LOCATION_HAS_ACTIVE_CHILDREN = new ErrorCode(1_015_004_009, "存在启用的子位置，不允许停用");
    ErrorCode AST_LOCATION_SOURCE_CONFLICT = new ErrorCode(1_015_004_010, "同一来源版本指向了不同地点");
    ErrorCode AST_AREA_DEPARTMENT_MAPPING_NOT_EXISTS = new ErrorCode(1_015_004_011, "行政区划与服务办事处映射不存在");
    ErrorCode AST_AREA_DEPARTMENT_MAPPING_OVERLAP = new ErrorCode(1_015_004_012, "同一行政区划存在重叠的有效服务办事处映射");
    ErrorCode AST_AREA_DEPARTMENT_MAPPING_INVALID = new ErrorCode(1_015_004_013, "行政区划与服务办事处映射无效");
    ErrorCode AST_SITE_LOCATION_IN_USE = new ErrorCode(1_015_004_014, "站点位置仍被设备当前位置引用，不允许停用");

    ErrorCode AST_PRODUCT_TYPE_INVALID_REQUEST = new ErrorCode(1_015_005_000, "产品类型请求无效");
    ErrorCode AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED =
            new ErrorCode(1_015_005_001, "产品类型服务身份未授权");
    ErrorCode AST_PRODUCT_TYPE_SOURCE_STALE = new ErrorCode(1_015_005_002, "产品类型来源事实已过期");
    ErrorCode AST_PRODUCT_TYPE_SOURCE_CONFLICT = new ErrorCode(1_015_005_003, "产品类型来源事实冲突");
    ErrorCode AST_PRODUCT_TYPE_CODE_CONFLICT = new ErrorCode(1_015_005_004, "产品类型稳定编码冲突");
    ErrorCode AST_PRODUCT_TYPE_CROSS_TENANT_REFERENCE =
            new ErrorCode(1_015_005_005, "产品类型引用不属于当前租户");
    ErrorCode AST_PRODUCT_TYPE_IDEMPOTENCY_CONFLICT =
            new ErrorCode(1_015_005_006, "产品类型操作幂等键冲突");

}
