package cn.iocoder.yudao.module.pms.project.service.project;

import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectPanoramicRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectProgressRespVO;

/**
 * PMS 项目全景 Service 接口（FR-PROJ-011 / FR-PROJ-021 / T-V1-PROJ-009）。
 * <p>
 * 聚合项目基本信息、客户信息、阶段汇总、任务汇总、风险汇总与团队成员列表；
 * 总体进度按 60% 任务 + 40% 阶段加权计算。
 */
public interface ProjectPanoramicService {

    /**
     * 查询项目全景。
     *
     * @param projectId 项目编号
     * @return 项目全景
     */
    ProjectPanoramicRespVO getProjectPanoramic(Long projectId);

    /**
     * 查询项目进度。
     *
     * @param projectId 项目编号
     * @return 项目进度
     */
    ProjectProgressRespVO getProjectProgress(Long projectId);
}
