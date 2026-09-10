package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ProjectMemberPageQuery {
    private Long tenantId;
    private Long projectId;
    private PageParam page;
    private LocalDateTime effectiveAt;
    private String state;
    private String role;
    private String keyword;
}
