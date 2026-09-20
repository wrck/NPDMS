package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proj_project_party")
public class ProjectPartyDO extends TenantBaseDO {
    private Long id;
    private Long projectId;
    private String partyRole;
    private String partyCode;
    private String partyName;
    private String status;
    private java.time.LocalDateTime effectiveFrom;
    private java.time.LocalDateTime effectiveTo;
}
