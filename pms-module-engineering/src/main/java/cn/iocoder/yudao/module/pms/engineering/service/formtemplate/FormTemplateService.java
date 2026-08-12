package cn.iocoder.yudao.module.pms.engineering.service.formtemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.formtemplate.vo.FormTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.formtemplate.vo.FormTemplateSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.formtemplate.FormTemplateDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 准备数据表单模板 Service 接口（FR-ENG-007）。
 * <p>
 * 状态流转：0 草稿 → 1 已发布；1 已发布 → 2 已停用；2 已停用 → 1 已发布（重新启用）。
 * 模板编号全局唯一；草稿状态可编辑或删除；已发布状态可停用；存在实例时不可删除。
 */
public interface FormTemplateService {

    /**
     * 创建表单模板（校验编号唯一）
     *
     * @param createReqVO 创建信息
     * @return 模板编号
     */
    Long createFormTemplate(@Valid FormTemplateSaveReqVO createReqVO);

    /**
     * 更新表单模板（仅 0 草稿 状态可改）
     *
     * @param updateReqVO 更新信息
     */
    void updateFormTemplate(@Valid FormTemplateSaveReqVO updateReqVO);

    /**
     * 删除表单模板（仅 0 草稿 状态且无实例可删）
     *
     * @param id 模板编号
     */
    void deleteFormTemplate(Long id);

    /**
     * 查询表单模板详情
     *
     * @param id 模板编号
     * @return 模板对象
     */
    FormTemplateDO getFormTemplate(Long id);

    /**
     * 校验表单模板存在
     *
     * @param id 模板编号
     * @return 模板对象
     */
    FormTemplateDO validateFormTemplateExists(Long id);

    /**
     * 分页查询表单模板
     *
     * @param pageReqVO 分页查询条件
     * @return 分页结果
     */
    PageResult<FormTemplateDO> getFormTemplatePage(FormTemplatePageReqVO pageReqVO);

    /**
     * 发布表单模板：0 草稿 → 1 已发布
     *
     * @param id 模板编号
     */
    void publishFormTemplate(Long id);

    /**
     * 停用表单模板：1 已发布 → 2 已停用
     *
     * @param id 模板编号
     */
    void disableFormTemplate(Long id);

    /**
     * 重新启用表单模板：2 已停用 → 1 已发布
     *
     * @param id 模板编号
     */
    void enableFormTemplate(Long id);

    /**
     * 按产品类型查询已发布模板列表（供实例创建时下拉选择）
     *
     * @param productType 产品类型（可空，空则查询所有已发布模板）
     * @return 已发布模板列表
     */
    List<FormTemplateDO> getPublishedFormTemplateList(String productType);

    /**
     * 查询所有已发布模板列表
     *
     * @return 已发布模板列表
     */
    List<FormTemplateDO> getAllPublishedFormTemplateList();
}
