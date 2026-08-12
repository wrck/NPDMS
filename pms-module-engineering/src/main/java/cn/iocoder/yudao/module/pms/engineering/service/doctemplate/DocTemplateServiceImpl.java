package cn.iocoder.yudao.module.pms.engineering.service.doctemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplateSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplateSelectReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo.DocTemplateVersionSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.doctemplate.DocTemplateDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.doctemplate.DocTemplateVersionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.doctemplate.DocTemplateMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.doctemplate.DocTemplateVersionMapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 工程文档模板 Service 实现（V36 结构化文档模板）。
 * <p>
 * 模板状态流转：0 草稿 → 1 已发布；1 已发布 → 2 已停用；2 已停用 → 1 已发布（重新发布）。
 * 模板编号全局唯一；草稿状态可编辑或删除。
 * 版本管理：版本标签在同一模板内唯一；已发布版本不可修改；发布版本时同步更新模板 currentVersionId 与 status。
 * 模板选择：三级降级匹配（精确匹配 → 依次移除 implementMode/productType/networkType → 默认模板），按 priority 降序排序。
 */
@Service
@Validated
@Slf4j
public class DocTemplateServiceImpl implements DocTemplateService {

    /**
     * 状态：0 草稿
     */
    public static final int STATUS_DRAFT = 0;
    /**
     * 状态：1 已发布
     */
    public static final int STATUS_PUBLISHED = 1;
    /**
     * 状态：2 已停用
     */
    public static final int STATUS_DISABLED = 2;

    /**
     * 版本未发布
     */
    public static final int VERSION_UNPUBLISHED = 0;
    /**
     * 版本已发布
     */
    public static final int VERSION_PUBLISHED = 1;

    /**
     * 维度匹配权重（保证 projectType > networkType > productType > implementMode）。
     * 降级顺序为依次移除 implementMode → productType → networkType，故 implementMode 权重最低。
     */
    private static final int SCORE_PROJECT_TYPE = 8000;
    private static final int SCORE_NETWORK_TYPE = 4000;
    private static final int SCORE_PRODUCT_TYPE = 2000;
    private static final int SCORE_IMPLEMENT_MODE = 1000;

    @Resource
    private DocTemplateMapper docTemplateMapper;

    @Resource
    private DocTemplateVersionMapper docTemplateVersionMapper;

    // ==================== 模板主表 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDocTemplate(DocTemplateSaveReqVO createReqVO) {
        // 1. 校验编号全局唯一
        validateCodeUnique(createReqVO.getCode(), null);
        // 2. 校验父模板存在（若指定）
        validateParentTemplate(createReqVO.getParentTemplateId());
        // 3. 转换并写入，初始状态为草稿
        DocTemplateDO entity = BeanUtils.toBean(createReqVO, DocTemplateDO.class);
        entity.setStatus(STATUS_DRAFT);
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        docTemplateMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDocTemplate(DocTemplateSaveReqVO updateReqVO) {
        // 1. 校验存在
        DocTemplateDO existing = validateDocTemplateExists(updateReqVO.getId());
        // 2. 状态校验：仅 0 草稿 可编辑
        validateStatus(existing, STATUS_DRAFT);
        // 3. 乐观锁版本校验
        validateVersion(existing, updateReqVO.getVersion());
        // 4. 编号不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(DOC_TEMPLATE_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 5. 校验父模板存在（若指定）
        validateParentTemplate(updateReqVO.getParentTemplateId());
        // 6. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）
        DocTemplateDO update = BeanUtils.toBean(updateReqVO, DocTemplateDO.class);
        docTemplateMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocTemplate(Long id) {
        // 1. 校验存在
        DocTemplateDO existing = validateDocTemplateExists(id);
        // 2. 状态校验：仅 0 草稿 可删除
        validateStatus(existing, STATUS_DRAFT);
        // 3. 删除模板及其全部版本
        List<DocTemplateVersionDO> versions = docTemplateVersionMapper.selectListByTemplateId(id);
        for (DocTemplateVersionDO version : versions) {
            docTemplateVersionMapper.deleteById(version.getId());
        }
        docTemplateMapper.deleteById(id);
    }

    @Override
    public DocTemplateDO getDocTemplate(Long id) {
        return docTemplateMapper.selectById(id);
    }

    @Override
    public DocTemplateDO validateDocTemplateExists(Long id) {
        DocTemplateDO entity = docTemplateMapper.selectById(id);
        if (entity == null) {
            throw exception(DOC_TEMPLATE_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<DocTemplateDO> getDocTemplatePage(DocTemplatePageReqVO pageReqVO) {
        return docTemplateMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishDocTemplate(Long id) {
        // 1. 校验存在
        DocTemplateDO entity = validateDocTemplateExists(id);
        // 2. 状态校验：0 草稿 或 2 已停用 → 1 已发布
        validateStatus(entity, STATUS_DRAFT, STATUS_DISABLED);
        // 3. 校验存在已发布版本
        DocTemplateVersionDO publishedVersion = docTemplateVersionMapper.selectPublishedVersion(id);
        if (publishedVersion == null) {
            throw exception(DOC_TEMPLATE_NO_PUBLISHED_VERSION);
        }
        // 4. 更新模板状态与当前版本
        entity.setStatus(STATUS_PUBLISHED);
        entity.setCurrentVersionId(publishedVersion.getId());
        entity.setVersion(entity.getVersion() + 1);
        docTemplateMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableDocTemplate(Long id) {
        // 1. 校验存在
        DocTemplateDO entity = validateDocTemplateExists(id);
        // 2. 状态校验：1 已发布 → 2 已停用
        validateStatus(entity, STATUS_PUBLISHED);
        // 3. 更新状态
        entity.setStatus(STATUS_DISABLED);
        entity.setVersion(entity.getVersion() + 1);
        docTemplateMapper.updateById(entity);
    }

    @Override
    public List<DocTemplateDO> getPublishedDocTemplateList(String docCategory) {
        return docTemplateMapper.selectPublishedList(docCategory);
    }

    // ==================== 模板版本 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocTemplateVersionDO createVersion(DocTemplateVersionSaveReqVO createReqVO) {
        // 1. 校验模板存在
        validateDocTemplateExists(createReqVO.getTemplateId());
        // 2. 校验版本标签唯一
        DocTemplateVersionDO existing = docTemplateVersionMapper.selectByTemplateIdAndVersionLabel(
                createReqVO.getTemplateId(), createReqVO.getVersionLabel());
        if (existing != null) {
            throw exception(DOC_TEMPLATE_VERSION_LABEL_DUPLICATE, createReqVO.getVersionLabel());
        }
        // 3. 转换并写入，初始为未发布
        DocTemplateVersionDO entity = BeanUtils.toBean(createReqVO, DocTemplateVersionDO.class);
        entity.setPublished(VERSION_UNPUBLISHED);
        docTemplateVersionMapper.insert(entity);
        return entity;
    }

    @Override
    public DocTemplateVersionDO getVersion(Long versionId) {
        return docTemplateVersionMapper.selectById(versionId);
    }

    @Override
    public List<DocTemplateVersionDO> getVersionListByTemplateId(Long templateId) {
        return docTemplateVersionMapper.selectListByTemplateId(templateId);
    }

    @Override
    public DocTemplateVersionDO getPublishedVersion(Long templateId) {
        return docTemplateVersionMapper.selectPublishedVersion(templateId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishVersion(Long versionId) {
        // 1. 校验版本存在
        DocTemplateVersionDO version = docTemplateVersionMapper.selectById(versionId);
        if (version == null) {
            throw exception(DOC_TEMPLATE_VERSION_NOT_EXISTS);
        }
        // 2. 已发布版本不可重复发布（幂等：若已发布则直接返回）
        if (Objects.equals(version.getPublished(), VERSION_PUBLISHED)) {
            return;
        }
        // 3. 发布版本
        version.setPublished(VERSION_PUBLISHED);
        docTemplateVersionMapper.updateById(version);
        // 4. 同步更新模板 currentVersionId 与 status
        DocTemplateDO template = validateDocTemplateExists(version.getTemplateId());
        template.setCurrentVersionId(version.getId());
        template.setStatus(STATUS_PUBLISHED);
        template.setVersion(template.getVersion() + 1);
        docTemplateMapper.updateById(template);
    }

    @Override
    public String buildTemplateSnapshot(Long versionId) {
        // 1. 校验版本存在
        DocTemplateVersionDO version = docTemplateVersionMapper.selectById(versionId);
        if (version == null) {
            throw exception(DOC_TEMPLATE_VERSION_NOT_EXISTS);
        }
        // 2. 校验模板存在
        DocTemplateDO template = validateDocTemplateExists(version.getTemplateId());
        // 3. 组装快照（模板基础信息 + 版本章节定义）
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("templateId", template.getId());
        snapshot.put("code", template.getCode());
        snapshot.put("name", template.getName());
        snapshot.put("docCategory", template.getDocCategory());
        snapshot.put("applicability", parseApplicability(template.getApplicability()));
        snapshot.put("versionId", version.getId());
        snapshot.put("versionLabel", version.getVersionLabel());
        // sections/sectionOverrides/excludedSections 为 JSON 字符串，解析为对象后放入快照
        snapshot.put("sections", parseJsonToObj(version.getSections()));
        snapshot.put("sectionOverrides", parseJsonToObj(version.getSectionOverrides()));
        snapshot.put("excludedSections", parseJsonToObj(version.getExcludedSections()));
        return JsonUtils.toJsonString(snapshot);
    }

    // ==================== 模板选择（三级降级匹配） ====================

    @Override
    public List<DocTemplateDO> selectTemplates(DocTemplateSelectReqVO reqVO) {
        // 1. 查询该类别下全部已发布模板
        List<DocTemplateDO> published = docTemplateMapper.selectPublishedList(reqVO.getDocCategory());
        if (published.isEmpty()) {
            return Collections.emptyList();
        }
        // 2. 解析每个模板的 applicability 进行匹配打分
        List<ScoredTemplate> scored = new ArrayList<>();
        for (DocTemplateDO t : published) {
            Applicability app = parseApplicability(t.getApplicability());
            int score = calculateScore(app, reqVO);
            if (score >= 0) {
                scored.add(new ScoredTemplate(t, score, app.getPriority()));
            }
        }
        // 3. 无匹配时降级：返回 isDefault=true 的模板
        if (scored.isEmpty()) {
            return published.stream().filter(t -> {
                Applicability app = parseApplicability(t.getApplicability());
                return app.isDefault != null && app.isDefault;
            }).collect(Collectors.toList());
        }
        // 4. 按分数降序，分数相同则按 priority 降序
        scored.sort((a, b) -> {
            int cmp = Integer.compare(b.score, a.score);
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(b.priority, a.priority);
        });
        return scored.stream().map(s -> s.template).collect(Collectors.toList());
    }

    // ==================== 内部工具方法 ====================

    private void validateCodeUnique(String code, Long excludeId) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        DocTemplateDO existing = docTemplateMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (excludeId == null || !Objects.equals(existing.getId(), excludeId)) {
            throw exception(DOC_TEMPLATE_CODE_DUPLICATE, code);
        }
    }

    private void validateParentTemplate(Long parentTemplateId) {
        if (parentTemplateId == null) {
            return;
        }
        DocTemplateDO parent = docTemplateMapper.selectById(parentTemplateId);
        if (parent == null) {
            throw exception(DOC_TEMPLATE_PARENT_NOT_EXISTS);
        }
    }

    private void validateVersion(DocTemplateDO entity, Integer version) {
        if (version != null && !Objects.equals(entity.getVersion(), version)) {
            throw exception(DOC_TEMPLATE_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(DocTemplateDO entity, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(entity.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(DOC_TEMPLATE_STATUS_INVALID);
    }

    /**
     * 解析 applicability JSON 字符串为 Applicability 对象。
     * 解析失败时返回空对象（不阻断流程）。
     */
    private Applicability parseApplicability(String json) {
        if (StringUtils.isBlank(json)) {
            return new Applicability();
        }
        Applicability app = JsonUtils.parseObject(json, Applicability.class);
        return app != null ? app : new Applicability();
    }

    /**
     * 计算模板与选择条件的匹配分数。
     * <p>
     * 维度权重：projectType(8000) > networkType(4000) > productType(2000) > implementMode(1000)。
     * 精确匹配加分，通配（模板未指定该维度）加0分，不匹配返回 -1（排除）。
     * isDefault 加 1 分。
     */
    private int calculateScore(Applicability app, DocTemplateSelectReqVO reqVO) {
        int score = 0;
        // projectType
        if (StringUtils.isNotBlank(reqVO.getProjectType())) {
            if (app.getProjectType() != null && !app.getProjectType().isEmpty()) {
                if (!app.getProjectType().contains(reqVO.getProjectType())) {
                    return -1;
                }
                score += SCORE_PROJECT_TYPE;
            }
        }
        // networkType
        if (StringUtils.isNotBlank(reqVO.getNetworkType())) {
            if (app.getNetworkType() != null && !app.getNetworkType().isEmpty()) {
                if (!app.getNetworkType().contains(reqVO.getNetworkType())) {
                    return -1;
                }
                score += SCORE_NETWORK_TYPE;
            }
        }
        // productType
        if (StringUtils.isNotBlank(reqVO.getProductType())) {
            if (app.getProductType() != null && !app.getProductType().isEmpty()) {
                if (!app.getProductType().contains(reqVO.getProductType())) {
                    return -1;
                }
                score += SCORE_PRODUCT_TYPE;
            }
        }
        // implementMode
        if (StringUtils.isNotBlank(reqVO.getImplementMode())) {
            if (app.getImplementMode() != null && !app.getImplementMode().isEmpty()) {
                if (!app.getImplementMode().contains(reqVO.getImplementMode())) {
                    return -1;
                }
                score += SCORE_IMPLEMENT_MODE;
            }
        }
        // isDefault 加 1 分
        if (app.getIsDefault() != null && app.getIsDefault()) {
            score += 1;
        }
        return score;
    }

    /**
     * 将 JSON 字符串解析为 Object（用于快照中嵌套 JSON 字段的解包）。
     * 解析失败时返回原始字符串。
     */
    private Object parseJsonToObj(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(json, Object.class);
        } catch (Exception e) {
            // 解析失败返回原始字符串，保证快照可生成
            return json;
        }
    }

    /**
     * 模板适用条件（对应 applicability JSON）。
     * 各维度字段为数组，模板支持匹配多个值；空数组或 null 表示该维度通配。
     */
    @Data
    public static class Applicability {
        /** 项目类型列表 */
        private List<String> projectType;
        /** 网络类型列表 */
        private List<String> networkType;
        /** 产品类型列表 */
        private List<String> productType;
        /** 实施模式列表 */
        private List<String> implementMode;
        /** 优先级（数值越大越优先） */
        private Integer priority;
        /** 是否默认模板 */
        private Boolean isDefault;
    }

    /**
     * 带分数的模板（用于排序）。
     */
    private static class ScoredTemplate {
        final DocTemplateDO template;
        final int score;
        final int priority;

        ScoredTemplate(DocTemplateDO template, int score, Integer priority) {
            this.template = template;
            this.score = score;
            this.priority = priority != null ? priority : 0;
        }
    }
}
