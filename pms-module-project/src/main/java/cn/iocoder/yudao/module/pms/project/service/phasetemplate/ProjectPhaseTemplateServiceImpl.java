package cn.iocoder.yudao.module.pms.project.service.phasetemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.phasetemplate.vo.ProjectPhaseTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.phasetemplate.vo.ProjectPhaseTemplateSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phasetemplate.ProjectPhaseTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.phase.ProjectPhaseMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.phasetemplate.ProjectPhaseTemplateMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PHASE_TEMPLATE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PHASE_TEMPLATE_IN_USE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PHASE_TEMPLATE_NOT_EXISTS;

/**
 * PMS 阶段模板 Service 实现类
 */
@Service
@Validated
public class ProjectPhaseTemplateServiceImpl implements ProjectPhaseTemplateService {

    @Resource
    private ProjectPhaseTemplateMapper projectPhaseTemplateMapper;
    @Resource
    private ProjectPhaseMapper projectPhaseMapper;

    @Override
    public Long createProjectPhaseTemplate(ProjectPhaseTemplateSaveReqVO createReqVO) {
        // 校验编码唯一
        validateCodeUnique(null, createReqVO.getCode());
        // 插入模板
        ProjectPhaseTemplateDO template = BeanUtils.toBean(createReqVO, ProjectPhaseTemplateDO.class);
        projectPhaseTemplateMapper.insert(template);
        return template.getId();
    }

    @Override
    public void updateProjectPhaseTemplate(ProjectPhaseTemplateSaveReqVO updateReqVO) {
        // 校验存在
        validateTemplateExists(updateReqVO.getId());
        // 校验编码唯一
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        // 更新模板
        ProjectPhaseTemplateDO updateObj = BeanUtils.toBean(updateReqVO, ProjectPhaseTemplateDO.class);
        projectPhaseTemplateMapper.updateById(updateObj);
    }

    @Override
    public void deleteProjectPhaseTemplate(Long id) {
        // 校验存在
        validateTemplateExists(id);
        // 校验未被项目阶段引用
        Long refCount = projectPhaseMapper.selectCountByTemplateId(id);
        if (refCount != null && refCount > 0) {
            throw exception(PHASE_TEMPLATE_IN_USE);
        }
        // 删除模板
        projectPhaseTemplateMapper.deleteById(id);
    }

    @Override
    public void deleteProjectPhaseTemplateList(Collection<Long> ids) {
        for (Long id : ids) {
            deleteProjectPhaseTemplate(id);
        }
    }

    @Override
    public ProjectPhaseTemplateDO getProjectPhaseTemplate(Long id) {
        return projectPhaseTemplateMapper.selectById(id);
    }

    @Override
    public PageResult<ProjectPhaseTemplateDO> getProjectPhaseTemplatePage(ProjectPhaseTemplatePageReqVO pageReqVO) {
        return projectPhaseTemplateMapper.selectPage(pageReqVO);
    }

    @Override
    public List<ProjectPhaseTemplateDO> getEnabledProjectPhaseTemplateList() {
        return projectPhaseTemplateMapper.selectEnabledList();
    }

    @Override
    public List<ProjectPhaseTemplateDO> getEnabledProjectPhaseTemplateListByType(String projectType) {
        return projectPhaseTemplateMapper.selectEnabledListByType(projectType);
    }

    private void validateTemplateExists(Long id) {
        if (id == null) {
            return;
        }
        if (projectPhaseTemplateMapper.selectById(id) == null) {
            throw exception(PHASE_TEMPLATE_NOT_EXISTS);
        }
    }

    private void validateCodeUnique(Long id, String code) {
        ProjectPhaseTemplateDO template = projectPhaseTemplateMapper.selectByCode(code);
        if (template == null) {
            return;
        }
        if (id == null || !template.getId().equals(id)) {
            throw exception(PHASE_TEMPLATE_CODE_DUPLICATE);
        }
    }

}
