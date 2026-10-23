package cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class JointTestPageQuery extends PageParam {
    private Long projectId;
    private String code;
    private String testCase;
    private Integer status;
    private Long equipmentId;
    private Long testerUserId;
    private Set<Long> visibleProjectIds;
}
