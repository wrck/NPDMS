package cn.iocoder.yudao.module.pms.engineering.service.doctemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplateSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplateSelectReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplateVersionSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.doctemplate.DocTemplateDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.doctemplate.DocTemplateVersionDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 工程文档模板 Service 接口（V36 结构化文档模板）。
 * <p>
 * 模板状态流转：0 草稿 → 1 已发布；1 已发布 → 2 已停用。
 * 模板编号全局唯一；草稿状态可编辑或删除。
 * 版本管理：版本发布后不可修改；发布版本时同步更新模板 currentVersionId 与 status。
 */
public interface DocTemplateService {

    /**
     * 创建文档模板（校验编号唯一、父模板存在）
     *
     * @param createReqVO 创建信息
     * @return 模板ID
     */
    Long createDocTemplate(@Valid DocTemplateSaveReqVO createReqVO);

    /**
     * 更新文档模板（仅 0 草稿 状态可改）
     *
     * @param updateReqVO 更新信息
     */
    void updateDocTemplate(@Valid DocTemplateSaveReqVO updateReqVO);

    /**
     * 删除文档模板（仅 0 草稿 状态可删）
     *
     * @param id 模板ID
     */
    void deleteDocTemplate(Long id);

    /**
     * 查询文档模板详情
     *
     * @param id 模板ID
     * @return 模板对象
     */
    DocTemplateDO getDocTemplate(Long id);

    /**
     * 分页查询文档模板
     *
     * @param pageReqVO 分页查询条件
     * @return 分页结果
     */
    PageResult<DocTemplateDO> getDocTemplatePage(DocTemplatePageReqVO pageReqVO);

    /**
     * 校验文档模板存在
     *
     * @param id 模板ID
     * @return 模板对象
     */
    DocTemplateDO validateDocTemplateExists(Long id);

    /**
     * 发布文档模板：需存在已发布版本，设置 status=1, currentVersionId 指向已发布版本
     *
     * @param id 模板ID
     */
    void publishDocTemplate(Long id);

    /**
     * 停用文档模板：1 已发布 → 2 已停用
     *
     * @param id 模板ID
     */
    void disableDocTemplate(Long id);

    /**
     * 查询已发布模板列表（供文档创建时下拉选择）
     *
     * @param docCategory 文档类别（可空）
     * @return 已发布模板列表
     */
    List<DocTemplateDO> getPublishedDocTemplateList(String docCategory);

    /**
     * 创建模板新版本（校验版本标签唯一）
     *
     * @param createReqVO 创建信息
     * @return 版本对象
     */
    DocTemplateVersionDO createVersion(@Valid DocTemplateVersionSaveReqVO createReqVO);

    /**
     * 查询版本详情
     *
     * @param versionId 版本ID
     * @return 版本对象
     */
    DocTemplateVersionDO getVersion(Long versionId);

    /**
     * 查询模板的全部版本列表
     *
     * @param templateId 模板ID
     * @return 版本列表
     */
    List<DocTemplateVersionDO> getVersionListByTemplateId(Long templateId);

    /**
     * 查询模板的已发布版本
     *
     * @param templateId 模板ID
     * @return 已发布版本对象（无则返回 null）
     */
    DocTemplateVersionDO getPublishedVersion(Long templateId);

    /**
     * 发布版本：设置 published=1，模板 currentVersionId 指向它，模板 status=1
     *
     * @param versionId 版本ID
     */
    void publishVersion(Long versionId);

    /**
     * 构建模板快照JSON（用于文档创建时锁定模板结构）
     * <p>
     * 包含模板的 code/name/docCategory/applicability + 版本的 sections/sectionOverrides/excludedSections
     *
     * @param versionId 版本ID
     * @return 模板快照JSON字符串
     */
    String buildTemplateSnapshot(Long versionId);

    /**
     * 按条件筛选匹配的模板（三级降级匹配）
     * <p>
     * 1. 精确匹配（projectType+networkType+productType+implementMode 全命中）<br>
     * 2. 降级匹配（依次移除 implementMode → productType → networkType）<br>
     * 3. 返回 isDefault=true 的模板<br>
     * 4. 按 priority 降序排序
     *
     * @param reqVO 选择条件
     * @return 匹配的模板列表
     */
    List<DocTemplateDO> selectTemplates(DocTemplateSelectReqVO reqVO);
}
