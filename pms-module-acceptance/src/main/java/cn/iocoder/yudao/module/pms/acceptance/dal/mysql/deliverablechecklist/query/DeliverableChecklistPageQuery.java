package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeliverableChecklistPageQuery extends PageParam {

    private Long tenantId;
    private Set<Long> visibleProjectIds;
    private Long projectId;
    private String code;
    private String name;
    private Long acceptanceId;
    private String deliverableType;
    private Integer status;

}
