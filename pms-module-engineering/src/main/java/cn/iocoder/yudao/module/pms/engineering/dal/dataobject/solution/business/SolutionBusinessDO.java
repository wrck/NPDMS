package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.business;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName("sol_eng_solution")
@ProjectBusinessModel(ownerModule="SOL",entityType="solution",stableCode="SOL_SOLUTION",name="实施方案",permissionPrefix="pms:sol-solution",nativeEntityType="ImplementationSolution")
public class SolutionBusinessDO extends BaseProjectBusinessEntity {
    @BusinessModelField(name="方案编码",writable=false) private String code;
    @NotBlank @BusinessModelField(name="方案名称") private String name;
    @Pattern(regexp="IMPLEMENTATION") @BusinessModelField(name="方案类型") private String solutionType;
    @BusinessModelField(name="文档模板",writable=false) private Long templateId;
    @BusinessModelField(name="模板版本",writable=false) private Long templateVersionId;
    private String templateSnapshot;
    @BusinessModelField(name="章节数据",listVisible=false) private String sectionData;
    @BusinessModelField(name="项目背景",listVisible=false) private String background;
    @BusinessModelField(name="项目目标",listVisible=false) private String target;
    @BusinessModelField(name="项目团队",listVisible=false) private String team;
    @BusinessModelField(name="项目清单",listVisible=false) private String inventory;
    @BusinessModelField(name="历史进度计划",writable=false,listVisible=false) private String plan;
    @BusinessModelField(name="网络拓扑",listVisible=false) private String topology;
    @BusinessModelField(name="接口互联",listVisible=false) private String interfacePlan;
    @BusinessModelField(name="IP规划",listVisible=false) private String ipPlan;
    @BusinessModelField(name="版本标签") private String versionLabel;
    @BusinessModelField(name="配置脚本",listVisible=false) private String script;
    @BusinessModelField(name="质量保障",listVisible=false) private String quality;
    @BusinessModelField(name="风险管控",listVisible=false) private String risk;
    @BusinessModelField(name="运维交付",listVisible=false) private String oAndM;
    @BusinessModelField(name="审核级别",writable=false) private Integer reviewLevel;
    @BusinessModelField(name="状态",writable=false) private Integer status;
    @BusinessModelField(name="审核人",writable=false) private Long approvedBy;
    @BusinessModelField(name="审核时间",writable=false) private LocalDateTime approvedTime;
    @BusinessModelField(name="审核意见",writable=false) private String approvalOpinion;
    @BusinessModelField(name="批准基线",writable=false) private Integer baselineVersion;
    @BusinessModelField(name="方案补充信息",listVisible=false) private String remark;
}
