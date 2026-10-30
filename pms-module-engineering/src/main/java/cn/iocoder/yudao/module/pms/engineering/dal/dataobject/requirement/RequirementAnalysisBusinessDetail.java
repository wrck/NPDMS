package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement;

import lombok.Data;

/** A row of the requirement analysis business inventory, owned by SOL. */
@Data
public class RequirementAnalysisBusinessDetail {
    private String deviceName;
    private String serialNumber;
    private String businessName;
    private String businessSubnet;
    private String businessImportance;
    private String interfaces;
    private String customerBusinessOwner;
    private String remark;
}
