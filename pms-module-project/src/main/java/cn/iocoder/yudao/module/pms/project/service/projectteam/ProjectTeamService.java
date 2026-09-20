package cn.iocoder.yudao.module.pms.project.service.projectteam;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo.ProjectTeamMemberPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo.ProjectTeamMemberSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectteam.ProjectTeamMemberRetiredDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * PMS 项目团队 Service 接口
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Deprecated
public interface ProjectTeamService {

    /**
     * 创建项目团队成员
     *
     * @param createReqVO 团队成员信息
     * @return 团队成员编号
     */
    Long createProjectTeamMemberRetired(@Valid ProjectTeamMemberSaveReqVO createReqVO);

    /**
     * 更新项目团队成员
     *
     * @param updateReqVO 团队成员信息
     */
    void updateProjectTeamMemberRetired(@Valid ProjectTeamMemberSaveReqVO updateReqVO);

    /**
     * 删除项目团队成员
     *
     * @param id 团队成员编号
     */
    void deleteProjectTeamMemberRetired(Long id);

    /**
     * 获得项目团队成员
     *
     * @param id 团队成员编号
     * @return 团队成员信息
     */
    ProjectTeamMemberRetiredDO getProjectTeamMemberRetired(Long id);

    /**
     * 获得项目团队成员分页列表
     *
     * @param pageReqVO 分页条件
     * @return 团队成员分页列表
     */
    PageResult<ProjectTeamMemberRetiredDO> getProjectTeamMemberPageRetired(ProjectTeamMemberPageReqVO pageReqVO);

    /**
     * 根据项目编号获取团队成员列表
     *
     * @param projectId 项目编号
     * @return 团队成员列表
     */
    List<ProjectTeamMemberRetiredDO> getTeamListByProjectIdRetired(Long projectId);

}
