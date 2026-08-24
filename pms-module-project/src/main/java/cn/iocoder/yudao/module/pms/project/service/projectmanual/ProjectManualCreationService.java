package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerResult;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectInstantiation;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecision;

import java.util.List;

/**
 * 项目手工创建 Service（F-PM01 / PM-01）
 * <p>
 * 工程管理部手工创建项目：BR-2 必填校验 → 平台编码分配（BR-8）→ 模板选择与冻结
 * （BR-3/BR-4：无匹配/同优先级多匹配且未人工选择时阻断）→ 按冻结版本实例化五要素 →
 * 可选服务经理指派与下单办事处关系；创建与实例化单事务（失败整体回滚）。
 */
public interface ProjectManualCreationService {

    /**
     * 手工创建根项目（单事务）。
     *
     * @param draft                      项目草稿（BR-2 必填字段由服务校验）
     * @param orderOfficeCompanyCode     下单办事处公司编码（空=不登记）
     * @param orderOfficeDepartmentCode  下单办事处部门编码（可空）
     * @param manualTemplateId           人工选择模板ID（空=四维自动匹配）
     * @param serviceManagerUserId       可选一级服务经理用户ID（空=暂存后人工指派）
     * @return 已落库项目主档（含自增ID与回填后的 code_root_id/root_id）
     */
    ProjectMasterDO createProject(ProjectMasterDO draft, String orderOfficeCompanyCode,
                                  String orderOfficeDepartmentCode, Long templateRevisionId,
                                  String candidateWatermark, Long serviceManagerUserId);

    /** 使用应用层已在同一事务内确定的模板匹配决策创建根项目。 */
    ProjectMasterDO createProject(ProjectMasterDO draft, String orderOfficeCompanyCode,
                                  String orderOfficeDepartmentCode, TemplateMatchDecision matchDecision,
                                  Long serviceManagerUserId);

    /**
     * 更新可编辑属性（BR-7：名称/客户/合同号/实施地点；编码、父节点、来源、模板绑定、状态不可改，
     * 更新载荷中的不可变字段被忽略）。
     */
    void updateProject(ProjectMasterDO update, ProjectAccessActor actor);

    /**
     * 查询项目主档。
     */
    ProjectMasterDO getProject(Long id, ProjectAccessActor actor);

    /**
     * 分页查询（简单条件：名称/编码/状态/三维）。
     */
    PageResult<ProjectMasterDO> getProjectPage(PageParam pageParam, String projectName, String projectCode,
                                               String status, String signingMethod, String projectCategory,
                                               String implementationMode, ProjectAccessActor actor);

    /**
     * 实例视图：阶段→任务/里程碑/交付件/门禁+门禁引用行（按冻结版本只读）。
     */
    ProjectInstantiation getInstances(Long projectId, ProjectAccessActor actor);

    /** 创建事务内读取刚完成初始化的实例，不作为用户查询入口。 */
    ProjectInstantiation getInstancesForCreation(Long projectId, Long tenantId);

    /**
     * 成员区间列表（当前有效+历史）。
     */
    List<ProjectMemberAssignmentDO> getMemberAssignments(Long projectId, ProjectAccessActor actor);

    /**
     * 指派一级服务经理（SERVICE_MANAGER_L1）：旧有效区间关闭+新区间开启，同事务留痕。
     *
     * @param command 包含Project期望版本、角色责任范围与幂等事实的指派命令
     */
    AssignServiceManagerResult assignServiceManager(AssignServiceManagerCommand command);

    record ProjectAccessActor(Long tenantId, Long actorId) {
    }
}
