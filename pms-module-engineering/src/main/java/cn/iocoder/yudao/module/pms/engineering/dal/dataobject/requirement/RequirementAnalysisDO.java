package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Current effective business content. Revision and form metadata belong to separate capabilities. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sol_requirement_analysis")
public class RequirementAnalysisDO extends TenantBaseDO {
    @TableId @JsonIgnore private Long id;
    @JsonIgnore private Long projectId;
    @NotBlank
    private String projectBackground;
    @NotBlank
    private String projectObjective;
    @NotBlank
    private String networkTopology;
    private String transmissionRequirement;
    private String trafficRequirement;
    private String businessRequirement;
    private String ipPlanning;
    private String redundancyRequirement;
    private String securityProtection;
    private String operationsRequirement;
    private String loggingRequirement;
    @JsonIgnore private String statusCode;
    @Version @JsonIgnore private Integer version;
}
