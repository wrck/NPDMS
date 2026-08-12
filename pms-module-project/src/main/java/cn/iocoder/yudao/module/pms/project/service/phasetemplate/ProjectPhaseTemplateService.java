package cn.iocoder.yudao.module.pms.project.service.phasetemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.phasetemplate.vo.ProjectPhaseTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.phasetemplate.vo.ProjectPhaseTemplateSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phasetemplate.ProjectPhaseTemplateDO;

import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;

/**
 * PMS 阶段模板 Service 接口
 */
public interface ProjectPhaseTemplateService {

    /**
     * 创建阶段模板
     *
     * @param createReqVO 模板信息
     * @return 模板编号
     */
    Long createProjectPhaseTemplate(@Valid ProjectPhaseTemplateSaveReqVO createReqVO);

    /**
     * 更新阶段模板
     *
     * @param updateReqVO 模板信息
     */
    void updateProjectPhaseTemplate(@Valid ProjectPhaseTemplateSaveReqVO updateReqVO);

    /**
     * 删除阶段模板
     *
     * @param id 模板编号
     */
    void deleteProjectPhaseTemplate(Long id);

    /**
     * 批量删除阶段模板
     *
     * @param ids 模板编号列表
     */
    void deleteProjectPhaseTemplateList(Collection<Long> ids);

    /**
     * 获得阶段模板
     *
     * @param id 模板编号
     * @return 模板信息
     */
    ProjectPhaseTemplateDO getProjectPhaseTemplate(Long id);

    /**
     * 获得阶段模板分页列表
     *
     * @param pageReqVO 分页条件
     * @return 模板分页列表
     */
    PageResult<ProjectPhaseTemplateDO> getProjectPhaseTemplatePage(ProjectPhaseTemplatePageReqVO pageReqVO);

    /**
     * 获取启用的阶段模板列表
     *
     * @return 启用模板列表
     */
    List<ProjectPhaseTemplateDO> getEnabledProjectPhaseTemplateList();

    /**
     * 按项目类型获取启用的阶段模板列表
     *
     * @param projectType 项目类型
     * @return 启用模板列表
     */
    List<ProjectPhaseTemplateDO> getEnabledProjectPhaseTemplateListByType(String projectType);

}
