package cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectClosurePageQuery extends PageParam {
    private Long projectId;
    private String code;
    private String name;
    private String closureType;
    private Integer status;
    private Set<Long> visibleProjectIds;
}
