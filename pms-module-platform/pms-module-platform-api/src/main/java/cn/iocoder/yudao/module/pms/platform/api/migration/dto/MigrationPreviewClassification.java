package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

/**
 * 迁移预演分类：只依据既有迁移证据（来源记录与外部键映射）判定，
 * 不做任何按名称猜测合并。
 */
public enum MigrationPreviewClassification {

    /** 无既有来源记录：目标侧尚无该身份的迁移历史，属首次接入。 */
    NO_EXISTING_SOURCE,
    /** 待导入内容校验和与既有来源记录不一致：身份相同但内容变化，需人工确认。 */
    CHECKSUM_CONFLICT,
    /** 既有来源记录尚未产生任何外部键映射（含仅登记问题未结案的记录）。 */
    UNMAPPED,
    /** 已有 RETAINED 结论：来源记录被显式保留在旧系统，不迁入目标。 */
    RETAINED,
    /** 存在单一无歧义的映射目标。 */
    MAPPED,
    /** 同一 targetRole 下存在多个不同目标，映射存在歧义。 */
    AMBIGUOUS
}
