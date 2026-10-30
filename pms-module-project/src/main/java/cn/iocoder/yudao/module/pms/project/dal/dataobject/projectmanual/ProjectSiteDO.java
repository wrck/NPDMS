package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

/** 项目与 AST 站点的时态关系；不持有 AST 表外键。 */
@TableName("proj_project_site")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectSiteDO extends BaseBusinessEntity {
    private Long projectId;
    private Long siteId;
    private Long siteVersionSnapshot;
    private Boolean primarySite;
    private String scopeStatus;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String siteCodeSnapshot;
    private String siteNameSnapshot;
    private String addressSnapshot;
}
