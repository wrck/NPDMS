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
 * 同时用于两处：创建时 {@link TemplateInstantiator} 生成PROJ拥有的阶段、任务、里程碑、门禁及引用；
 * 查询时聚合实例视图，交付件通过ACC公开查询接口映射为兼容视图，不访问ACC Repository。
 * 任务的`source_definition_id`来自已发布定义行ID。
 * 引用行以 gateCode 分组保留关联，供服务层在门禁实例落库后回填 gate_id。
 */
@Data
public class ProjectInstantiation {

    /** 阶段实例（唯一S0阶段 ACTIVE，其余 PENDING） */
    private List<ProjectStageInstanceDO> stages = new ArrayList<>();
    /** 任务实例（初始 PENDING_ASSIGN） */
    private List<ProjectTaskInstanceDO> tasks = new ArrayList<>();
    /** 里程碑实例（初始 PENDING） */
    private List<ProjectMilestoneInstanceDO> milestones = new ArrayList<>();
    /** ACC查询接口返回的交付件兼容视图；创建输出保持为空 */
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
