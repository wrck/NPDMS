package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.projection;

import lombok.Data;

@Data
public class ProjectContractFactRow {
    private Long projectId;
    private boolean currentFlag;
    private String contractNo;
}
