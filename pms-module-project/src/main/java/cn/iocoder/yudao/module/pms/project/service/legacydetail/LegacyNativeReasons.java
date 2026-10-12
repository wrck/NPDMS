package cn.iocoder.yudao.module.pms.project.service.legacydetail;

/** Reasons describe the missing business proof; they never invent a native lifecycle or user. */
public final class LegacyNativeReasons {
    private LegacyNativeReasons() {}
    public static String forDomain(String domain) {
        return switch (domain) {
            case "maintenance" -> "USER_DEFERRED_MAINTENANCE_RECORD";
            case "warranties" -> "DERIVED_WARRANTY_NOT_INDEPENDENTLY_MIGRATED";
            case "members" -> "USER_MAPPING_NOT_APPROVED";
            case "tasks", "state" -> "STAGE_MAPPING_NOT_APPROVED";
            case "groups" -> "GROUP_IS_CONTRACT_BRIDGE";
            case "software-changes" -> "SOFTWARE_BATCH_METADATA_RETAINED";
            case "questionnaires", "questionnaire-answers", "questionnaire-templates",
                 "questionnaire-questions", "questionnaire-options", "callback-questionnaires" -> "QUESTIONNAIRE_MAPPING_NOT_APPROVED";
            case "approvals" -> "NATIVE_PROCESS_HISTORY_UNPROVEN";
            case "notifications" -> "RECIPIENT_MAPPING_NOT_APPROVED";
            default -> "EXTRA_HISTORY_PENDING_ARBITRATION";
        };
    }
    public static String text(String code) {
        if (code == null) return null;
        return switch (code) {
            case "USER_DEFERRED_MAINTENANCE_RECORD" -> "按需求方裁决，维护记录本轮暂不迁入；不是维保记录，仅保留裁决和迁移方案";
            case "DERIVED_WARRANTY_NOT_INDEPENDENTLY_MIGRATED" -> "按需求方裁决，维保信息由正式发货和保修期限计算，不独立同步；原计算材料及既有记录保留";
            case "EXTRA_HISTORY_PENDING_ARBITRATION" -> "按需求方本轮裁决，此额外历史领域逐项登记待裁决；来源证据保留，尚未完成业务承接";
            case "QUANTITY_UNIT_NOT_AUTHORITATIVE" -> "此前承接版本因单位缺失而保留历史";
            case "PLAN_EVENT_MAPPING_NOT_APPROVED" -> "此前承接版本未承接回款计划";
            case "CONTRACT_COLLECTION_PLAN_SYNC_REQUIRED" -> "需通过合同回款计划迁移任务承接并核验正式计划；原项目快照不直接创建计划";
            case "FACTORY_VERSION_ENTITY_UNAVAILABLE" -> "此前承接版本未接入现有出厂版本实体";
            case "USER_MAPPING_NOT_APPROVED" -> "旧人员尚无确认的系统用户映射";
            case "STAGE_MAPPING_NOT_APPROVED" -> "旧阶段、任务尚无确认的当前阶段映射";
            case "GROUP_IS_CONTRACT_BRIDGE" -> "原项目组用于合同关联，不能直接生成项目树或组合";
            case "SOFTWARE_BATCH_METADATA_RETAINED" -> "变更批次元数据保留历史；逐设备版本另行承接";
            case "QUESTIONNAIRE_MAPPING_NOT_APPROVED" -> "旧问卷尚无有效模板及评分规则映射";
            case "NATIVE_PROCESS_HISTORY_UNPROVEN" -> "旧审批意见不生成当前流程历史或审批结果";
            case "RECIPIENT_MAPPING_NOT_APPROVED" -> "旧通知缺少确认的系统收件人映射";
            case "NATIVE_SOURCE_IDENTITY_UNRESOLVED" -> "现有业务实体的来源身份未精确匹配";
            case "NATIVE_SOURCE_IDENTITY_AMBIGUOUS" -> "来源身份命中多个业务实体，尚待核对";
            case "SHIPMENT_SOURCE_IDENTITY_UNRESOLVED" -> "发货来源身份、设备或合同未精确匹配";
            case "DEVICE_CURRENT_OWNER_CONFLICT", "LATER_SHIPMENT_CONFLICT" -> "设备当前归属或后续发货事实冲突";
            case "DEVICE_NOT_RECONCILED" -> "设备尚未完成来源身份核对及项目关联";
            case "TARGET_IDENTITY_CONFLICT" -> "目标身份冲突或已经删除，需核对后承接";
            case "ADDRESS_UNRESOLVED", "INSTALLATION_TRANSFER_UNRESOLVED" -> "安装地址或原设备转移关系尚待核对";
            case "SHIPMENT_SOURCE_CONTENT_CHANGED", "FACTORY_SOURCE_CONTENT_CHANGED", "INSTALLATION_SOURCE_CONTENT_CHANGED", "PROJECT_SOURCE_CONTENT_CHANGED", "COMMERCE_SOURCE_CONTENT_CHANGED", "SOFTWARE_SOURCE_CONTENT_CHANGED", "WARRANTY_SOURCE_CONTENT_CHANGED" -> "该来源已经承接，后续内容变化需另行核对";
            default -> "此类资料尚无确认的业务实体承接规则，保留历史";
        };
    }
}
