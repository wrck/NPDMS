package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 项目与 AST 站点的时态关系；不持有 AST 表外键。 */
@TableName("proj_project_site")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectSiteDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long projectId;
    private Long siteId;
    private Integer siteVersionSnapshot;
    private Boolean primarySite;
    private String scopeStatus;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String siteCodeSnapshot;
    private String siteNameSnapshot;
    private String addressSnapshot;
    private Integer version;
}
