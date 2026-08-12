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
 */
public interface ProjectRiskService {

    /**
     * 创建项目风险。校验项目存在、风险等级合法、写入 identified_at。
     */
    Long createRisk(@Valid ProjectRiskSaveReqVO createReqVO);

    /**
     * 更新项目风险。校验存在、状态迁移合法性。
     */
    void updateRisk(@Valid ProjectRiskSaveReqVO updateReqVO);

    /**
     * 删除项目风险。
     */
    void deleteRisk(Long id);

    /**
     * 批量删除项目风险。
     */
    void deleteRiskList(Collection<Long> ids);

    /**
     * 查询风险详情。
     */
    ProjectRiskDO getRisk(Long id);

    /**
     * 校验风险存在。
     */
    ProjectRiskDO validateRiskExists(Long id);

    /**
     * 分页查询风险。
     */
    PageResult<ProjectRiskDO> getRiskPage(ProjectRiskPageReqVO pageReqVO);

    /**
     * 查询项目下全部风险。
     */
    List<ProjectRiskDO> getRiskListByProjectId(Long projectId);

    /**
     * 切换风险状态。校验状态迁移合法性；迁入已关闭时写入 closed_at。
     */
    void transitionStatus(Long riskId, int targetStatus, Integer version);
}
