package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 项目手工创建状态与生命周期规则（F-PM01 / PM-01）
 * <p>
 * 字符串状态码集中于此（不入库枚举）：
 * BR-1 手工与自动创建同一业务对象、统一进入 S0；
 * BR-2 手工创建必填（三维独立保存，重大项目级别可空=不限）；
 * BR-7 更新接口仅开放可编辑属性，编码/父节点/来源/模板绑定/状态不可改。
 */
public final class ProjectRules {

    // ========== 生命周期阶段（字典 pms_project_lifecycle_stage） ==========
    /** S0 立项与指派（初始待开始） */
    public static final String STATUS_S0 = "S0";
    public static final String STATUS_S1 = "S1";
    public static final String STATUS_S2 = "S2";
    public static final String STATUS_S3 = "S3";
    public static final String STATUS_S4 = "S4";
    public static final String STATUS_S5 = "S5";
    public static final String STATUS_S6 = "S6";
    /** 维护期（转维保后） */
    public static final String STATUS_MAINT = "MAINT";
    /** 创建初始状态：S0 待开始（BR-1） */
    public static final String INITIAL_STATUS = STATUS_S0;
    /** V1.8创建后的生命周期与主责指派初始状态 */
    public static final String LIFECYCLE_STATUS_ACTIVE = "ACTIVE";
    public static final String ASSIGNMENT_STATUS_UNASSIGNED = "UNASSIGNED";

    // ========== 创建来源（字典 pms_project_source_type） ==========
    public static final String SOURCE_TYPE_MANUAL = "MANUAL";
    public static final String SOURCE_TYPE_ORDER = "ORDER";
    public static final String SOURCE_TYPE_MIGRATION = "MIGRATION";

    // ========== 模板加载方式（字典 pms_template_load_method） ==========
    /** 唯一默认命中自动加载 */
    public static final String TEMPLATE_LOAD_AUTO_DEFAULT = "AUTO_DEFAULT";
    /** 多匹配时人工选择 */
    public static final String TEMPLATE_LOAD_MANUAL_SELECTED = "MANUAL_SELECTED";

    // ========== 阶段实例状态（字典 pms_project_stage_status） ==========
    public static final String STAGE_STATUS_PENDING = "PENDING";
    public static final String STAGE_STATUS_ACTIVE = "ACTIVE";
    public static final String STAGE_STATUS_DONE = "DONE";

    // ========== 任务实例状态（字典 pms_project_task_status，PRD 4.6） ==========
    public static final String TASK_STATUS_PENDING_ASSIGN = "PENDING_ASSIGN";
    public static final String TASK_STATUS_PENDING_START = "PENDING_START";
    public static final String TASK_STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String TASK_STATUS_PENDING_ACCEPT = "PENDING_ACCEPT";
    public static final String TASK_STATUS_DONE = "DONE";
    public static final String TASK_STATUS_CLOSED = "CLOSED";

    // ========== 里程碑/交付件/门禁实例状态 ==========
    public static final String MILESTONE_STATUS_PENDING = "PENDING";
    public static final String MILESTONE_STATUS_ACHIEVED = "ACHIEVED";
    public static final String DELIVERABLE_STATUS_PENDING = "PENDING";
    public static final String DELIVERABLE_STATUS_SUBMITTED = "SUBMITTED";
    public static final String DELIVERABLE_STATUS_ACCEPTED = "ACCEPTED";
    public static final String GATE_STATUS_PENDING = "PENDING";
    public static final String GATE_STATUS_PASSED = "PASSED";
    public static final String GATE_STATUS_FAILED = "FAILED";

    // ========== 成员角色（字典 pms_project_member_role，仅 PRD 已定义角色） ==========
    public static final String MEMBER_ROLE_PROJECT_MANAGER = "PROJECT_MANAGER";
    public static final String MEMBER_ROLE_SERVICE_MANAGER_L1 = "SERVICE_MANAGER_L1";
    public static final String MEMBER_ROLE_SERVICE_MANAGER_L2 = "SERVICE_MANAGER_L2";

    // ========== 组织关系角色（字典 pms_company_relation_role；PM-01 使用） ==========
    public static final String RELATION_ROLE_ORDER_OFFICE = "ORDER_OFFICE";
    /** 组织关系/成员区间记录状态 */
    public static final String RELATION_STATUS_ACTIVE = "ACTIVE";

    /** BR-7 可经更新接口修改的字段（其余业务字段一律以库内值为准） */
    private static final Set<String> EDITABLE_FIELDS = Set.of(
            "projectName", "customerId", "customerCode", "customerName",
            "contractNo", "implementationLocation");

    private ProjectRules() {
    }

    /**
     * BR-2 手工创建必填校验：项目名称、客户（至少客户编码）、创建原因、三维
     * （签约方式/项目类别/实施方式）；重大项目级别可空=不限。
     *
     * @return 缺失字段说明清单（空=校验通过）
     */
    public static List<String> validateManualCreation(ProjectMasterDO draft) {
        List<String> missing = new ArrayList<>();
        if (isBlank(draft.getProjectName())) {
            missing.add("项目名称");
        }
        // BR-2 必填清单：名称/三维/创建原因；客户编码与名称均为可选快照字段
        if (isBlank(draft.getCreationReason())) {
            missing.add("创建原因");
        }
        if (isBlank(draft.getSigningMethod())) {
            missing.add("签约方式");
        }
        if (isBlank(draft.getProjectCategory())) {
            missing.add("项目类别");
        }
        if (isBlank(draft.getImplementationMode())) {
            missing.add("实施方式");
        }
        return missing;
    }

    /**
     * PM-02 子项目创建必填校验：名称、创建原因；三维与模板继承父项目，不在子项目表单必填。
     */
    public static List<String> validateChildCreation(ProjectMasterDO draft) {
        List<String> missing = new ArrayList<>();
        if (isBlank(draft.getProjectName())) {
            missing.add("项目名称");
        }
        if (isBlank(draft.getCreationReason())) {
            missing.add("创建原因");
        }
        return missing;
    }

    /**
     * BR-7 字段是否可经更新接口修改。
     */
    public static boolean isEditableField(String fieldName) {
        return EDITABLE_FIELDS.contains(fieldName);
    }

    /**
     * BR-7 更新语义：不可变字段以库内当前值为准（更新载荷中的不可变字段值被忽略），
     * 可编辑字段保留更新载荷值。
     *
     * @param update  更新载荷（会被原地覆写不可变字段）
     * @param current 库内当前 DO
     */
    public static void applyImmutableFields(ProjectMasterDO update, ProjectMasterDO current) {
        // 身份与编码（BR-8 编码不可变）
        update.setProjectCode(current.getProjectCode());
        update.setCodeRootId(current.getCodeRootId());
        update.setProjectSequence(current.getProjectSequence());
        update.setCodeRuleVersion(current.getCodeRuleVersion());
        // 树结构（本 Feature 仅根项目，父子调整属 PM-02/PM-04）
        update.setParentId(current.getParentId());
        update.setRootId(current.getRootId());
        update.setTreePath(current.getTreePath());
        update.setTreeDepth(current.getTreeDepth());
        update.setTreeSort(current.getTreeSort());
        // 业务层级标签与权重（业务层级走拆分/移动，权重走汇总口径调整，均不走属性更新）
        update.setBusinessLevelCode(current.getBusinessLevelCode());
        update.setBusinessLevelName(current.getBusinessLevelName());
        // 负责人与组织（主负责人调整走指派链路，不走属性更新）
        update.setManagerId(current.getManagerId());
        update.setManagerEmployeeNo(current.getManagerEmployeeNo());
        update.setManagerName(current.getManagerName());
        update.setCompanyId(current.getCompanyId());
        update.setCompanyCode(current.getCompanyCode());
        update.setCompanyName(current.getCompanyName());
        update.setDepartmentId(current.getDepartmentId());
        update.setDepartmentCode(current.getDepartmentCode());
        update.setDepartmentName(current.getDepartmentName());
        // 分类与属性（四维创建时冻结；BR-6 禁止混载改写）
        update.setProjectType(current.getProjectType());
        update.setSigningMethod(current.getSigningMethod());
        update.setProjectCategory(current.getProjectCategory());
        update.setImplementationMode(current.getImplementationMode());
        update.setMajorProjectLevel(current.getMajorProjectLevel());
        update.setMarketCode(current.getMarketCode());
        update.setMarketName(current.getMarketName());
        update.setSystemCode(current.getSystemCode());
        update.setSystemName(current.getSystemName());
        update.setExpendCode(current.getExpendCode());
        update.setExpendName(current.getExpendName());
        update.setIndustryCode(current.getIndustryCode());
        update.setIndustryName(current.getIndustryName());
        update.setCustomerProjectName(current.getCustomerProjectName());
        update.setSalesType(current.getSalesType());
        update.setBusinessType(current.getBusinessType());
        update.setServiceLevelCode(current.getServiceLevelCode());
        update.setNotTrackReason(current.getNotTrackReason());
        // 创建事实（BR-2 留痕，不可经更新接口改写）
        update.setCreationReason(current.getCreationReason());
        // 模板冻结绑定与流程引用（BR-4）
        update.setLifecycleTemplateId(current.getLifecycleTemplateId());
        update.setLifecycleTemplateRevisionNo(current.getLifecycleTemplateRevisionNo());
        update.setTemplateLoadMethod(current.getTemplateLoadMethod());
        update.setProcessDefinitionKey(current.getProcessDefinitionKey());
        update.setProcessDefinitionVersion(current.getProcessDefinitionVersion());
        // 生命周期事实（阶段推进不在本 Feature）
        update.setProjectStartTime(current.getProjectStartTime());
        update.setProjectRefreshTime(current.getProjectRefreshTime());
        update.setProjectCloseTime(current.getProjectCloseTime());
        update.setSourceType(current.getSourceType());
        update.setStatus(current.getStatus());
        update.setLifecycleStatus(current.getLifecycleStatus());
        update.setCurrentStage(current.getCurrentStage());
        update.setAssignmentStatus(current.getAssignmentStatus());
        update.setLocationResolutionStatus(current.getLocationResolutionStatus());
        // 进度与权重（进度来源属 PM-11，权重走汇总口径调整，均不走属性更新）
        update.setProgress(current.getProgress());
        update.setAggregationWeight(current.getAggregationWeight());
        update.setWeightSource(current.getWeightSource());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
