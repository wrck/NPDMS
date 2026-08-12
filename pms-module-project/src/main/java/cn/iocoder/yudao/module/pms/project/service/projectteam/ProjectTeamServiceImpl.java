package cn.iocoder.yudao.module.pms.project.service.projectteam;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo.ProjectTeamMemberPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo.ProjectTeamMemberSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectteam.ProjectTeamMemberDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
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
 */
@Service
@Validated
public class ProjectTeamServiceImpl implements ProjectTeamService {

    @Resource
    private ProjectTeamMemberMapper projectTeamMemberMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Override
    public Long createProjectTeamMember(ProjectTeamMemberSaveReqVO createReqVO) {
        // 校验项目存在
        validateProjectExists(createReqVO.getProjectId());
        // 校验同项目下 (userId, roleCode) 唯一
        validateTeamMemberUnique(null, createReqVO.getProjectId(),
                createReqVO.getUserId(), createReqVO.getRoleCode());
        // 插入团队成员
        ProjectTeamMemberDO member = BeanUtils.toBean(createReqVO, ProjectTeamMemberDO.class);
        projectTeamMemberMapper.insert(member);
        return member.getId();
    }

    @Override
    public void updateProjectTeamMember(ProjectTeamMemberSaveReqVO updateReqVO) {
        // 校验存在
        validateTeamMemberExists(updateReqVO.getId());
        // 校验项目存在
        validateProjectExists(updateReqVO.getProjectId());
        // 校验同项目下 (userId, roleCode) 唯一
        validateTeamMemberUnique(updateReqVO.getId(), updateReqVO.getProjectId(),
                updateReqVO.getUserId(), updateReqVO.getRoleCode());
        // 更新团队成员
        ProjectTeamMemberDO updateObj = BeanUtils.toBean(updateReqVO, ProjectTeamMemberDO.class);
        projectTeamMemberMapper.updateById(updateObj);
    }

    @Override
    public void deleteProjectTeamMember(Long id) {
        // 校验存在
        validateTeamMemberExists(id);
        // 删除团队成员
        projectTeamMemberMapper.deleteById(id);
    }

    @Override
    public ProjectTeamMemberDO getProjectTeamMember(Long id) {
        return projectTeamMemberMapper.selectById(id);
    }

    @Override
    public PageResult<ProjectTeamMemberDO> getProjectTeamMemberPage(ProjectTeamMemberPageReqVO pageReqVO) {
        return projectTeamMemberMapper.selectPage(pageReqVO);
    }

    @Override
    public List<ProjectTeamMemberDO> getTeamListByProjectId(Long projectId) {
        return projectTeamMemberMapper.selectListByProjectId(projectId);
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
        ProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw exception(PROJECT_TEAM_PROJECT_NOT_EXISTS);
        }
    }

    private void validateTeamMemberUnique(Long id, Long projectId, Long userId, String roleCode) {
        ProjectTeamMemberDO existing = projectTeamMemberMapper
                .selectByProjectIdAndUserIdAndRoleCode(projectId, userId, roleCode);
        if (existing == null) {
            return;
        }
        if (id == null || !existing.getId().equals(id)) {
            throw exception(PROJECT_TEAM_MEMBER_DUPLICATE);
        }
    }

}
