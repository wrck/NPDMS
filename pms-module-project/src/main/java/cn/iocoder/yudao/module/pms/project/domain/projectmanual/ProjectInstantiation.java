package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectDeliverableInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMilestoneInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目实例化载体（F-PM01）
 * <p>
 * 同时用于两处：创建时 {@link TemplateInstantiator} 生成五类实例 + 门禁引用行供批量落库；
 * 查询时聚合实例视图（阶段→任务/里程碑/交付件/门禁+门禁引用行）。
 * `source_definition_id` 为定义行 ID 映射槽——F-PM03 `getRevisionContent` 返回的
 * {@code TemplateDefinitionContent} 不含定义行 ID，故保持 NULL（不回改 F-PM03 内容模型）。
 * 引用行以 gateCode 分组保留关联，供服务层在门禁实例落库后回填 gate_id。
 */
@Data
public class ProjectInstantiation {

    /** 阶段实例（含状态：最小 sort_order 阶段 ACTIVE，其余 PENDING） */
    private List<ProjectStageInstanceDO> stages = new ArrayList<>();
    /** 任务实例（初始 PENDING_ASSIGN） */
    private List<ProjectTaskInstanceDO> tasks = new ArrayList<>();
    /** 里程碑实例（初始 PENDING） */
    private List<ProjectMilestoneInstanceDO> milestones = new ArrayList<>();
    /** 交付件实例（初始 PENDING，required 冻结快照） */
    private List<ProjectDeliverableInstanceDO> deliverables = new ArrayList<>();
    /** 门禁实例（初始 PENDING，validationSummary 由引用行生成） */
    private List<ProjectGateInstanceDO> gates = new ArrayList<>();
    /** 门禁引用行（按门禁码分组，gate_id 为落库回填槽位） */
    private Map<String, List<ProjectGateReferenceInstanceDO>> gateReferencesByGateCode = new LinkedHashMap<>();

    /**
     * 全部门禁引用行（拍平视图）。
     */
    public List<ProjectGateReferenceInstanceDO> getGateReferences() {
        List<ProjectGateReferenceInstanceDO> references = new ArrayList<>();
        gateReferencesByGateCode.values().forEach(references::addAll);
        return references;
    }
}
