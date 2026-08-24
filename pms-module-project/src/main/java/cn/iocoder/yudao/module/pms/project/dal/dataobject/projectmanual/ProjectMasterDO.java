package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目主档 DO（F-PM01 / V57 `proj_project`）
 * <p>
 * 双重唯一（ADR-0020）：`uk(tenant_id, project_code)` 编码租户内唯一；
 * `uk(tenant_id, code_root_id, project_sequence)` 序号在编码命名空间根内唯一。
 * 根项目 `code_root_id=id`、`project_sequence=0`；AUTO_INCREMENT 下无法单语句满足自引用，
 * 由服务层同事务两段写入（INSERT 占位 0 后 UPDATE 回填 id）。
 * `version` 列暂不接 MyBatis-Plus @Version 拦截器：并发由 uk + 行锁保障。
 */
@TableName("proj_project")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectMasterDO extends TenantBaseDO {

    /**
     * 项目ID
     */
    @TableId
    private Long id;
    /**
     * 项目编码（租户内唯一，创建后不可变）
     */
    private String projectCode;
    /**
     * 创建时冻结的编码命名空间根项目ID（根项目=自身ID）
     */
    private Long codeRootId;
    /**
     * 编码命名空间内永久流水号：0=自身建立独立命名空间，>0=子项目（不回收复用）
     */
    private Integer projectSequence;
    /**
     * 编码生成规则版本（创建时冻结，ADR-0020）
     */
    private String codeRuleVersion;
    /**
     * 项目名称
     */
    private String projectName;
    /**
     * 父项目ID（NULL=根项目）
     */
    private Long parentId;
    /**
     * 项目树根节点项目ID（可由父子关系重建）
     */
    private Long rootId;
    /**
     * 祖先路径缓存（可由父子关系重建）
     */
    private String treePath;
    /**
     * 节点层级深度（根=0）
     */
    private Integer treeDepth;
    /**
     * 同父下排序值（小者优先）
     */
    private Integer treeSort;
    /**
     * 业务层级标签编码（与结构层级 tree_depth 分离；字典 pms_project_business_level，前向扩列）
     */
    private String businessLevelCode;
    /**
     * 业务层级标签名称（前向扩列）
     */
    private String businessLevelName;
    /**
     * 直接客户主档ID
     */
    private Long customerId;
    /**
     * 客户编码
     */
    private String customerCode;
    /**
     * 客户名称
     */
    private String customerName;
    /**
     * 当前主负责人用户ID
     */
    private Long managerId;
    /**
     * 负责人工号
     */
    private String managerEmployeeNo;
    /**
     * 负责人姓名
     */
    private String managerName;
    /**
     * 当前主责公司主档ID
     */
    private Long companyId;
    /**
     * 公司编码
     */
    private String companyCode;
    /**
     * 公司名称
     */
    private String companyName;
    /**
     * 当前主责部门主档ID
     */
    private Long departmentId;
    /**
     * 部门编码
     */
    private String departmentCode;
    /**
     * 部门名称
     */
    private String departmentName;
    /**
     * 项目类型编码（字典约束）
     */
    private String projectType;
    /**
     * 签约方式（字典 pms_signing_method；SDS 08 四维分列，前向扩列）
     */
    private String signingMethod;
    /**
     * 项目类别（字典 pms_project_category）
     */
    private String projectCategory;
    /**
     * 实施方式（字典 pms_implementation_method）
     */
    private String implementationMode;
    /**
     * 重大项目级别（CRM权威来源属性映射，NULL=不限）
     */
    private String majorProjectLevel;
    /**
     * 市场部编码
     */
    private String marketCode;
    /**
     * 市场部名称
     */
    private String marketName;
    /**
     * 系统部编码
     */
    private String systemCode;
    /**
     * 系统部名称
     */
    private String systemName;
    /**
     * 拓展部编码
     */
    private String expendCode;
    /**
     * 拓展部名称
     */
    private String expendName;
    /**
     * 行业编码
     */
    private String industryCode;
    /**
     * 行业名称
     */
    private String industryName;
    /**
     * 客户项目名称
     */
    private String customerProjectName;
    /**
     * 销售类型编码（字典约束）
     */
    private String salesType;
    /**
     * 业务类型编码（字典约束）
     */
    private String businessType;
    /**
     * 服务级别编码
     */
    private String serviceLevelCode;
    /**
     * 不跟踪原因
     */
    private String notTrackReason;
    /**
     * 手工登记合同号（正式商业关系随 INT-02 接管；前向扩列）
     */
    private String contractNo;
    /**
     * 实施地点（多地点拆分属 PM-02；前向扩列）
     */
    private String implementationLocation;
    /**
     * 地点解析状态：RESOLVED=已绑定站点，UNRESOLVED=仅保留兼容文本。
     */
    private String locationResolutionStatus;
    /**
     * 手工创建原因（BR-2 必填，应用层校验；前向扩列）
     */
    private String creationReason;
    /**
     * 冻结的生命周期模板ID（proj_project_template）
     */
    private Long lifecycleTemplateId;
    /**
     * 冻结的模板版本号（创建时快照；前向扩列）
     */
    private Integer lifecycleTemplateRevisionNo;
    /**
     * 模板加载方式：AUTO_DEFAULT唯一默认命中/MANUAL_SELECTED人工选择（前向扩列）
     */
    private String templateLoadMethod;
    /**
     * 冻结的流程定义引用（取自绑定版本，创建时快照；前向扩列）
     */
    private String processDefinitionKey;
    /**
     * 冻结的流程定义版本引用（前向扩列）
     */
    private String processDefinitionVersion;
    /**
     * 项目开始时间
     */
    private LocalDateTime projectStartTime;
    /**
     * 项目刷新时间
     */
    private LocalDateTime projectRefreshTime;
    /**
     * 项目关闭时间
     */
    private LocalDateTime projectCloseTime;
    /**
     * 创建来源：MANUAL手工/ORDER订单/MIGRATION迁移（字典 pms_project_source_type）
     */
    private String sourceType;
    /**
     * 项目状态（字典 pms_project_lifecycle_stage，初始 S0）
     */
    private String status;
    /**
     * V1.8生命周期状态：ACTIVE/NORMAL_CLOSED/EXCEPTION_CLOSED
     */
    private String lifecycleStatus;
    /**
     * V1.8当前阶段：S0～S6
     */
    private String currentStage;
    /**
     * V1.8主责指派状态：UNASSIGNED/ASSIGNED
     */
    private String assignmentStatus;
    /**
     * V1.7兼容读字段；F-PROJ-002新写命令以版本化进度事实和快照为真值。
     */
    private BigDecimal progress;
    /**
     * V1.7兼容读字段；正式权重写入进度策略版本项。
     */
    private BigDecimal aggregationWeight;
    /**
     * V1.7兼容读字段；不得作为F-PROJ-002策略写命令输入。
     */
    private String weightSource;
    /**
     * 乐观锁版本列：暂不接 @Version 拦截器，并发由 uk + 行锁保障
     */
    private Integer version;
}
