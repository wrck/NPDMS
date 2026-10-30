package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection;

import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.ServiceIssueDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true)
public class ServiceIssueSummary extends ServiceIssueDO {
    private String taskCode;
    private String taskName;
}
