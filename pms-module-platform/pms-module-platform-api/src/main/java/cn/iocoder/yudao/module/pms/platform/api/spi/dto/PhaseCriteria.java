package cn.iocoder.yudao.module.pms.platform.api.spi.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 阶段进入条件（迁移自源工程 pms-common com.dp.plat.common.dto.PhaseCriteria，
 * 结构化 JSON）
 */
@Data
public class PhaseCriteria implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否需要前置阶段完成 */
    private Boolean requirePreviousPhaseComplete;

    /** 是否需要审批通过 */
    private Boolean requireApproval;
}