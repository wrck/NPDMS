package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectDeviceSelectionQuery extends PageParam {
    private Long tenantId;
    private Long projectId;
    private Set<String> contractNumbers;
    private List<Long> deviceIds;
    private String sn;
    private String name;
    private String productModel;
    private String contractNo;
}
