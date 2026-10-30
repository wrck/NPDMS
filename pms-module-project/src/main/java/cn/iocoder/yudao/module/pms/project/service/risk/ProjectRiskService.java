package cn.iocoder.yudao.module.pms.project.service.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.risk.vo.ProjectRiskPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.risk.vo.ProjectRiskSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.risk.ProjectRiskDO;

import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;

/**
 * PMS 项目风险 Service 接口（FR-PROJ-026 / T-V1-PROJ-009）。
 * <p>
 * 状态迁移由 {@code RiskStatusRules} 校验：已识别→处理中→已关闭/已发生；已发生→已关闭。
 * 项目归属校验使用新域项目主档，风险记录保存在 proj_project_risk。
 */
public interface ProjectRiskService {

    /**
     * 创建项目风险。校验项目存在、风险等级合法、写入 identified_at。
     */
    Long createRiskRetired(@Valid ProjectRiskSaveReqVO createReqVO);

    /**
     * 更新项目风险。校验存在、状态迁移合法性。
     */
    void updateRiskRetired(@Valid ProjectRiskSaveReqVO updateReqVO);

    /**
     * 删除项目风险。
     */
    void deleteRiskRetired(Long id);

    /**
     * 批量删除项目风险。
     */
    void deleteRiskListRetired(Collection<Long> ids);

    /**
     * 查询风险详情。
     */
    ProjectRiskDO getRiskRetired(Long id);

    /**
     * 校验风险存在。
     */
    ProjectRiskDO validateRiskExistsRetired(Long id);

    /**
     * 分页查询风险。
     */
    PageResult<ProjectRiskDO> getRiskPageRetired(ProjectRiskPageReqVO pageReqVO);

    /**
     * 查询项目下全部风险。
     */
    List<ProjectRiskDO> getRiskListByProjectIdRetired(Long projectId);

    /**
     * 切换风险状态。校验状态迁移合法性；迁入已关闭时写入 closed_at。
     */
    void transitionStatusRetired(Long riskId, int targetStatus, Long version);
}
