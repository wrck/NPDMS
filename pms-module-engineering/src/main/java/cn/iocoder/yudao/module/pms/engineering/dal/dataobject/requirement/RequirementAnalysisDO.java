package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/** Current effective business content. Revision and form metadata belong to separate capabilities. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value="sol_requirement_analysis",autoResultMap=true)
/** P12-B1：继承统一业务基类，id/version 由基类唯一定义（EntityRevision.getVersion(): Long 契约）。 */
public class RequirementAnalysisDO extends BaseProjectBusinessEntity {
    @Override @JsonIgnore public Long getProjectId(){return super.getProjectId();}
    @NotBlank
    @BusinessModelField(name="项目背景") private String projectBackground;
    @NotBlank
    @BusinessModelField(name="项目目标") private String projectObjective;
    @NotBlank
    @BusinessModelField(name="网络拓扑") private String networkTopology;
    @BusinessModelField(name="传输需求") private String transmissionRequirement;
    @BusinessModelField(name="流量需求") private String trafficRequirement;
    @BusinessModelField(name="业务需求") private String businessRequirement;
    @BusinessModelField(name="IP规划") private String ipPlanning;
    @BusinessModelField(name="冗余需求") private String redundancyRequirement;
    @BusinessModelField(name="安全防护") private String securityProtection;
    @BusinessModelField(name="运维需求") private String operationsRequirement;
    @BusinessModelField(name="日志需求") private String loggingRequirement;
    @TableField(typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class) @BusinessModelField(name="现有传输方式") private List<String> transmissionCurrentOptions;
    @BusinessModelField(name="新建连接数") private String trafficNewConnections;
    @BusinessModelField(name="并发连接数") private String trafficConcurrency;
    @BusinessModelField(name="吞吐量") private String trafficThroughput;
    @TableField(typeHandler=cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisBusinessDetailsTypeHandler.class) @BusinessModelField(name="业务设备明细") private List<RequirementAnalysisBusinessDetail> businessDeviceDetails;
    @BusinessModelField(name="管理IP资源") private String ipManagementResources;
    @BusinessModelField(name="公网IP资源") private String ipPublicResources;
    @TableField(typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class) @BusinessModelField(name="运维管理方式") private List<String> operationsManagementOptions;
    @BusinessModelField(name="状态",writable=false) @JsonIgnore private String statusCode;
}
