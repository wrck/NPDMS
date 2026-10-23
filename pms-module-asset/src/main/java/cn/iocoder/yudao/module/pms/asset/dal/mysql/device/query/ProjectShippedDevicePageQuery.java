package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import lombok.Builder;
import lombok.Value;

/** 当前项目已发货序列号选择分页。 */
@Value
@Builder
public class ProjectShippedDevicePageQuery {
    Long tenantId;
    Long projectId;
    String keyword;
    int pageNo;
    int pageSize;

    public long getOffset() { return (long) (pageNo - 1) * pageSize; }
}
