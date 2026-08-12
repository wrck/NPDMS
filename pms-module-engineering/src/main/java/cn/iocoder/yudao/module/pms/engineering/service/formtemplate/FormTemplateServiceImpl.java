package cn.iocoder.yudao.module.pms.engineering.service.formtemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.formtemplate.vo.FormTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.formtemplate.vo.FormTemplateSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.formtemplate.FormTemplateDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.formtemplate.FormTemplateMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.forminstance.FormInstanceMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 准备数据表单模板 Service 实现（FR-ENG-007）。
 * <p>
 * 状态流转：0 草稿 → 1 已发布；1 已发布 → 2 已停用；2 已停用 → 1 已发布（重新启用）。
 * 模板编号全局唯一；草稿状态可编辑或删除；存在实例时不可删除。
 */
@Service
@Validated
@Slf4j
public class FormTemplateServiceImpl implements FormTemplateService {

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

    @Resource
    private FormTemplateMapper formTemplateMapper;

    @Resource
    private FormInstanceMapper formInstanceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFormTemplate(FormTemplateSaveReqVO createReqVO) {
        // 1. 校验编号全局唯一
        validateCodeUnique(createReqVO.getCode(), null);
        // 2. 转换并写入，初始状态为草稿
        FormTemplateDO entity = BeanUtils.toBean(createReqVO, FormTemplateDO.class);
        entity.setStatus(STATUS_DRAFT);
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        formTemplateMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFormTemplate(FormTemplateSaveReqVO updateReqVO) {
        // 1. 校验存在
        FormTemplateDO existing = validateFormTemplateExists(updateReqVO.getId());
        // 2. 状态校验：仅 0 草稿 可编辑
        validateStatus(existing, STATUS_DRAFT);
        // 3. 乐观锁版本校验
        validateVersion(existing, updateReqVO.getVersion());
        // 4. 编号不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(FORM_TEMPLATE_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 5. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）
        FormTemplateDO update = BeanUtils.toBean(updateReqVO, FormTemplateDO.class);
        formTemplateMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFormTemplate(Long id) {
        // 1. 校验存在
        FormTemplateDO existing = validateFormTemplateExists(id);
        // 2. 状态校验：仅 0 草稿 可删除
        validateStatus(existing, STATUS_DRAFT);
        // 3. 校验无关联实例
        Long instanceCount = formInstanceMapper.selectCountByTemplateId(id);
        if (instanceCount != null && instanceCount > 0) {
            throw exception(FORM_TEMPLATE_STATUS_INVALID);
        }
        // 4. 删除
        formTemplateMapper.deleteById(id);
    }

    @Override
    public FormTemplateDO getFormTemplate(Long id) {
        return formTemplateMapper.selectById(id);
    }

    @Override
    public FormTemplateDO validateFormTemplateExists(Long id) {
        FormTemplateDO entity = formTemplateMapper.selectById(id);
        if (entity == null) {
            throw exception(FORM_TEMPLATE_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<FormTemplateDO> getFormTemplatePage(FormTemplatePageReqVO pageReqVO) {
        return formTemplateMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishFormTemplate(Long id) {
        // 1. 校验存在
        FormTemplateDO entity = validateFormTemplateExists(id);
        // 2. 状态校验：0 草稿 → 1 已发布
        validateStatus(entity, STATUS_DRAFT);
        // 3. 更新状态
        entity.setStatus(STATUS_PUBLISHED);
        entity.setVersion(entity.getVersion() + 1);
        formTemplateMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableFormTemplate(Long id) {
        // 1. 校验存在
        FormTemplateDO entity = validateFormTemplateExists(id);
        // 2. 状态校验：1 已发布 → 2 已停用
        validateStatus(entity, STATUS_PUBLISHED);
        // 3. 更新状态
        entity.setStatus(STATUS_DISABLED);
        entity.setVersion(entity.getVersion() + 1);
        formTemplateMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableFormTemplate(Long id) {
        // 1. 校验存在
        FormTemplateDO entity = validateFormTemplateExists(id);
        // 2. 状态校验：2 已停用 → 1 已发布
        validateStatus(entity, STATUS_DISABLED);
        // 3. 更新状态
        entity.setStatus(STATUS_PUBLISHED);
        entity.setVersion(entity.getVersion() + 1);
        formTemplateMapper.updateById(entity);
    }

    @Override
    public List<FormTemplateDO> getPublishedFormTemplateList(String productType) {
        if (StringUtils.isBlank(productType)) {
            return formTemplateMapper.selectPublishedList();
        }
        return formTemplateMapper.selectListByProductType(productType);
    }

    @Override
    public List<FormTemplateDO> getAllPublishedFormTemplateList() {
        return formTemplateMapper.selectPublishedList();
    }

    // ==================== 内部工具方法 ====================

    private void validateCodeUnique(String code, Long excludeId) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        FormTemplateDO existing = formTemplateMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (excludeId == null || !Objects.equals(existing.getId(), excludeId)) {
            throw exception(FORM_TEMPLATE_CODE_DUPLICATE, code);
        }
    }

    private void validateVersion(FormTemplateDO entity, Integer version) {
        if (version != null && !Objects.equals(entity.getVersion(), version)) {
            throw exception(FORM_TEMPLATE_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(FormTemplateDO entity, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(entity.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(FORM_TEMPLATE_STATUS_INVALID);
    }
}
