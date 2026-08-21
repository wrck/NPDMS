package cn.iocoder.yudao.module.pms.project.service.project;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * PMS 项目 Service 实现类（旧链只读过渡，F-PM01 存量冻结）
 * <p>
 * 写实现（含编码/来源键/客户校验）已随写端点一并退役，由新链
 * {@code ProjectManualCreationServiceImpl} 按目标模型承接。
 */
@Service
@Validated
public class ProjectServiceImpl implements ProjectService {

    @Resource
    private ProjectMapper projectMapper;

    @Override
    public ProjectDO getProject(Long id) {
        return projectMapper.selectById(id);
    }

    @Override
    public PageResult<ProjectDO> getProjectPage(ProjectPageReqVO pageReqVO) {
        return projectMapper.selectPage(pageReqVO);
    }

}
