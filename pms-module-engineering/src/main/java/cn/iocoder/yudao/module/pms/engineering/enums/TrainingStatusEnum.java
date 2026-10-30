package cn.iocoder.yudao.module.pms.engineering.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 现场培训记录状态（ACC-01）
 */
@Getter
@RequiredArgsConstructor
public enum TrainingStatusEnum {

    DRAFT(0, "草稿"),
    ISSUED(1, "已外发"),
    CONFIRMED(2, "客户已确认"),
    VOID(3, "已作废");

    private final Integer status;
    private final String label;

}
