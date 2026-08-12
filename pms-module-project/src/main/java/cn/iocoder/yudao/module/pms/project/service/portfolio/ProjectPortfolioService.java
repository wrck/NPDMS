package cn.iocoder.yudao.module.pms.project.service.portfolio;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo.ProjectPortfolioPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo.ProjectPortfolioSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio.ProjectPortfolioDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio.ProjectPortfolioMemberDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 项目组合 Service 接口
 */
public interface ProjectPortfolioService {

    /**
     * 创建项目组合
     *
     * @param createReqVO 组合信息
     * @return 组合编号
     */
    Long createPortfolio(@Valid ProjectPortfolioSaveReqVO createReqVO);

    /**
     * 更新项目组合
     *
     * @param updateReqVO 组合信息
     */
    void updatePortfolio(@Valid ProjectPortfolioSaveReqVO updateReqVO);

    /**
     * 删除项目组合
     *
     * @param id 组合编号
     */
    void deletePortfolio(Long id);

    /**
     * 获得项目组合
     *
     * @param id 组合编号
     * @return 组合信息
     */
    ProjectPortfolioDO getPortfolio(Long id);

    /**
     * 获得项目组合分页列表
     *
     * @param pageReqVO 分页条件
     * @return 组合分页列表
     */
    PageResult<ProjectPortfolioDO> getPortfolioPage(ProjectPortfolioPageReqVO pageReqVO);

    /**
     * 发布项目组合（计算成员、生成快照）
     *
     * @param id 组合编号
     */
    void publishPortfolio(Long id);

    /**
     * 重新计算动态成员
     *
     * @param id 组合编号
     */
    void recalculateMembers(Long id);

    /**
     * 获取组合成员列表
     *
     * @param portfolioId 组合编号
     * @return 成员列表
     */
    List<ProjectPortfolioMemberDO> getPortfolioMembers(Long portfolioId);

}
