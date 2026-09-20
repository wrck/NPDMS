package cn.iocoder.yudao.module.pms.project.service.projectteam;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo.ProjectTeamMemberPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo.ProjectTeamMemberSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectteam.ProjectTeamMemberRetiredDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectteam.ProjectTeamMemberMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEAM_MEMBER_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEAM_MEMBER_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEAM_PROJECT_NOT_EXISTS;

/**
 * PMS 项目团队 Service 实现类
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Service
@Validated
@Deprecated
public class ProjectTeamServiceImpl implements ProjectTeamService {

    @Resource
    private ProjectTeamMemberMapper projectTeamMemberMapper;

    @Resource(name = "projectMasterMapper")
    private ProjectMasterMapper projectMapper;

    @Override
    public Long createProjectTeamMemberRetired(ProjectTeamMemberSaveReqVO createReqVO) {
        // 校验项目存在
        validateProjectExists(createReqVO.getProjectId());
        // 校验同项目下 (userId, roleCode) 唯一
        validateTeamMemberUnique(null, createReqVO.getProjectId(),
                createReqVO.getUserId(), createReqVO.getRoleCode());
        // 插入团队成员
        ProjectTeamMemberRetiredDO member = BeanUtils.toBean(createReqVO, ProjectTeamMemberRetiredDO.class);
        projectTeamMemberMapper.insert(member);
        return member.getId();
    }

    @Override
    public void updateProjectTeamMemberRetired(ProjectTeamMemberSaveReqVO updateReqVO) {
        // 校验存在
        validateTeamMemberExists(updateReqVO.getId());
        // 校验项目存在
        validateProjectExists(updateReqVO.getProjectId());
        // 校验同项目下 (userId, roleCode) 唯一
        validateTeamMemberUnique(updateReqVO.getId(), updateReqVO.getProjectId(),
                updateReqVO.getUserId(), updateReqVO.getRoleCode());
        // 更新团队成员
        ProjectTeamMemberRetiredDO updateObj = BeanUtils.toBean(updateReqVO, ProjectTeamMemberRetiredDO.class);
        projectTeamMemberMapper.updateById(updateObj);
    }

    @Override
    public void deleteProjectTeamMemberRetired(Long id) {
        // 校验存在
        validateTeamMemberExists(id);
        // 删除团队成员
        projectTeamMemberMapper.deleteById(id);
    }

    @Override
    public ProjectTeamMemberRetiredDO getProjectTeamMemberRetired(Long id) {
        return projectTeamMemberMapper.selectById(id);
    }

    @Override
    public PageResult<ProjectTeamMemberRetiredDO> getProjectTeamMemberPageRetired(ProjectTeamMemberPageReqVO pageReqVO) {
        return projectTeamMemberMapper.selectPageRetired(pageReqVO);
    }

    @Override
    public List<ProjectTeamMemberRetiredDO> getTeamListByProjectIdRetired(Long projectId) {
        return projectTeamMemberMapper.selectListByProjectIdRetired(projectId);
    }

    private void validateTeamMemberExists(Long id) {
        if (id == null) {
            return;
        }
        if (projectTeamMemberMapper.selectById(id) == null) {
            throw exception(PROJECT_TEAM_MEMBER_NOT_EXISTS);
        }
    }

    private void validateProjectExists(Long projectId) {
        if (projectId == null) {
            return;
        }
        if (projectMapper.selectById(projectId) == null) {
            throw exception(PROJECT_TEAM_PROJECT_NOT_EXISTS);
        }
    }

    private void validateTeamMemberUnique(Long id, Long projectId, Long userId, String roleCode) {
        ProjectTeamMemberRetiredDO existing = projectTeamMemberMapper
                .selectByProjectIdAndUserIdAndRoleCodeRetired(projectId, userId, roleCode);
        if (existing == null) {
            return;
        }
        if (id == null || !existing.getId().equals(id)) {
            throw exception(PROJECT_TEAM_MEMBER_DUPLICATE);
        }
    }

}
