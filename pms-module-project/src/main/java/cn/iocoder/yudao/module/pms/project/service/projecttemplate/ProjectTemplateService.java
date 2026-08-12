package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectCreateFromTemplateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;

import java.util.List;

/**
 * PMS 项目模板 Service 接口
 */
public interface ProjectTemplateService {

    /**
     * 创建项目模板
     */
    Long createProjectTemplate(ProjectTemplateSaveReqVO reqVO);

    /**
     * 更新项目模板
     */
    void updateProjectTemplate(ProjectTemplateSaveReqVO reqVO);

    /**
     * 删除项目模板
     */
    void deleteProjectTemplate(Long id);

    /**
     * 查询项目模板详情
     */
    ProjectTemplateDO getProjectTemplate(Long id);

    /**
     * 分页查询项目模板
     */
    PageResult<ProjectTemplateDO> getProjectTemplatePage(ProjectTemplatePageReqVO reqVO);

    /**
     * 查询全部启用的项目模板
     */
    List<ProjectTemplateDO> getEnabledProjectTemplateList();

    /**
     * 按项目类型查询启用项目模板
     */
    List<ProjectTemplateDO> getEnabledProjectTemplateListByType(String projectType);

    /**
     * 从模板创建项目（实例化阶段+任务+团队角色）
     */
    Long createProjectFromTemplate(ProjectCreateFromTemplateReqVO reqVO);
}
