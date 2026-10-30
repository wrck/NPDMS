package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.projection;

import lombok.Data;

@Data
public class CutoverApprovalProjectSummary {
    private String processInstanceId;
    private String projectName;
}
