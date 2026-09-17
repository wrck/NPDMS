package cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** 交底列表查询；createTime 为左闭右开区间，租户条件由服务端填充。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BriefingEntityPageQuery extends PageParam {
    private Long tenantId;
    private Long projectId;
    private String code;
    private String name;
    private String briefingType;
    private Integer status;
    private Long creatorUserId;
    private Long approverUserId;
    private LocalDateTime[] createTime;
}
