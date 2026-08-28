package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApplicableLeafTaskProgress {
    private Long taskId;
    private BigDecimal progress;
    private BigDecimal estimatedHours;
    private String status;
}
