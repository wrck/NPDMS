package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/** Current effective business content. Revision and form metadata belong to separate capabilities. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sol_requirement_analysis")
/** P12-B1：继承统一业务基类，id/version 由基类唯一定义（EntityRevision.getVersion(): Long 契约）。 */
public class RequirementAnalysisDO extends BaseBusinessEntity {
    @JsonIgnore @cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField private Long projectId;
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
    private List<String> transmissionCurrentOptions;
    private String trafficNewConnections;
    private String trafficConcurrency;
    private String trafficThroughput;
    private List<RequirementAnalysisBusinessDetail> businessDeviceDetails;
    private String ipManagementResources;
    private String ipPublicResources;
    private List<String> operationsManagementOptions;
    @JsonIgnore private String statusCode;
}
