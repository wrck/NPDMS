package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper=true)
public class DefaultDeliveryListQuery extends PageParam {
    private boolean includeInactive;
    private Long tenantId;
    private Long projectId;
    private String deliverableType;
    private String businessType;
    private Long entityId;
    private java.util.Set<String> readableBusinessTypes;
    public long getOffset() {return (long)(getPageNo()-1)*getPageSize();}
}
