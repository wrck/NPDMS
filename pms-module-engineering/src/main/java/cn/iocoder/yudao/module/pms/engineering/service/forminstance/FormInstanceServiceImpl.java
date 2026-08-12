package cn.iocoder.yudao.module.pms.engineering.service.forminstance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo.FormInstanceApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo.FormInstancePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo.FormInstanceSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.forminstance.FormInstanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.formtemplate.FormTemplateDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.forminstance.FormInstanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.formtemplate.FormTemplateMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 准备数据表单实例 Service 实现（FR-ENG-007）。
 * <p>
 * 状态流转：0 待填 → 1 已填 → 2 已提交 → 3 已审核 / 4 已驳回（驳回回到 1 已填）。
 * 实例编号全局唯一；待填/已填/已驳回状态可编辑或删除。
 * 创建时自动冻结模板快照，确保实例版本独立于模板后续变更。
 */
@Service
@Validated
@Slf4j
public class FormInstanceServiceImpl implements FormInstanceService {

    /**
     * 状态：0 待填
     */
    public static final int STATUS_PENDING = 0;
    /**
     * 状态：1 已填
     */
    public static final int STATUS_FILLED = 1;
    /**
     * 状态：2 已提交
     */
    public static final int STATUS_SUBMITTED = 2;
    /**
     * 状态：3 已审核
     */
    public static final int STATUS_AUDITED = 3;
    /**
     * 状态：4 已驳回
     */
    public static final int STATUS_REJECTED = 4;

    /**
     * 审核动作：通过
     */
    public static final String ACTION_PASS = "PASS";
    /**
     * 审核动作：驳回
     */
    public static final String ACTION_REJECT = "REJECT";

    @Resource
    private FormInstanceMapper formInstanceMapper;

    @Resource
    private FormTemplateMapper formTemplateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFormInstance(FormInstanceSaveReqVO createReqVO) {
        // 1. 校验编号全局唯一
        validateCodeUnique(createReqVO.getCode(), null);
        // 2. 校验项目存在
        validateProjectExists(createReqVO.getProjectId());
        // 3. 校验模板存在
        FormTemplateDO template = validateTemplateExists(createReqVO.getTemplateId());
        // 4. 转换并写入，初始状态为待填
        FormInstanceDO entity = BeanUtils.toBean(createReqVO, FormInstanceDO.class);
        entity.setStatus(STATUS_PENDING);
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        // 5. 冻结模板快照：若未传入 templateSnapshot，则自动从模板当前版本生成快照
        if (StringUtils.isBlank(entity.getTemplateSnapshot())) {
            entity.setTemplateSnapshot(buildTemplateSnapshot(template));
        }
        formInstanceMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFormInstance(FormInstanceSaveReqVO updateReqVO) {
        // 1. 校验存在
        FormInstanceDO existing = validateFormInstanceExists(updateReqVO.getId());
        // 2. 状态校验：仅 0 待填 / 1 已填 / 4 已驳回 可编辑
        validateStatus(existing, STATUS_PENDING, STATUS_FILLED, STATUS_REJECTED);
        // 3. 乐观锁版本校验
        validateVersion(existing, updateReqVO.getVersion());
        // 4. 编号不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(FORM_INSTANCE_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 5. 模板快照不可变（创建时已冻结）
        // 6. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）
        FormInstanceDO update = BeanUtils.toBean(updateReqVO, FormInstanceDO.class);
        // 保持 templateSnapshot 不变
        update.setTemplateSnapshot(existing.getTemplateSnapshot());
        formInstanceMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFormInstance(Long id) {
        // 1. 校验存在
        FormInstanceDO existing = validateFormInstanceExists(id);
        // 2. 状态校验：仅 0 待填 / 1 已填 / 4 已驳回 可删除
        validateStatus(existing, STATUS_PENDING, STATUS_FILLED, STATUS_REJECTED);
        // 3. 删除
        formInstanceMapper.deleteById(id);
    }

    @Override
    public FormInstanceDO getFormInstance(Long id) {
        return formInstanceMapper.selectById(id);
    }

    @Override
    public FormInstanceDO validateFormInstanceExists(Long id) {
        FormInstanceDO entity = formInstanceMapper.selectById(id);
        if (entity == null) {
            throw exception(FORM_INSTANCE_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<FormInstanceDO> getFormInstancePage(FormInstancePageReqVO pageReqVO) {
        return formInstanceMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveFormInstance(FormInstanceSaveReqVO reqVO) {
        // 1. 校验存在
        FormInstanceDO entity = validateFormInstanceExists(reqVO.getId());
        // 2. 状态校验：0 待填 / 4 已驳回 → 1 已填
        validateStatus(entity, STATUS_PENDING, STATUS_REJECTED);
        // 3. 乐观锁版本校验
        validateVersion(entity, reqVO.getVersion());
        // 4. 更新填报数据与状态
        if (reqVO.getFormData() != null) {
            entity.setFormData(reqVO.getFormData());
        }
        if (reqVO.getName() != null) {
            entity.setName(reqVO.getName());
        }
        if (reqVO.getFillerUserId() != null) {
            entity.setFillerUserId(reqVO.getFillerUserId());
        }
        if (reqVO.getRemark() != null) {
            entity.setRemark(reqVO.getRemark());
        }
        entity.setStatus(STATUS_FILLED);
        entity.setVersion(entity.getVersion() + 1);
        formInstanceMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitFormInstance(Long id) {
        // 1. 校验存在
        FormInstanceDO entity = validateFormInstanceExists(id);
        // 2. 状态校验：0 待填 / 1 已填 / 4 已驳回 → 2 已提交
        validateStatus(entity, STATUS_PENDING, STATUS_FILLED, STATUS_REJECTED);
        // 3. 更新状态与提交时间
        entity.setStatus(STATUS_SUBMITTED);
        entity.setVersion(entity.getVersion() + 1);
        entity.setSubmitTime(LocalDateTime.now());
        formInstanceMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveFormInstance(FormInstanceApproveReqVO reqVO) {
        // 1. 校验存在
        FormInstanceDO entity = validateFormInstanceExists(reqVO.getId());
        // 2. 状态校验：2 已提交 可审核
        validateStatus(entity, STATUS_SUBMITTED);
        // 3. 乐观锁版本校验
        validateVersion(entity, reqVO.getVersion());
        // 4. 根据审核动作决定目标状态
        int newStatus;
        switch (reqVO.getApproveAction()) {
            case ACTION_PASS:
                newStatus = STATUS_AUDITED;
                break;
            case ACTION_REJECT:
                newStatus = STATUS_REJECTED;
                break;
            default:
                throw exception(FORM_INSTANCE_STATUS_INVALID);
        }
        // 5. 更新状态、审核人、审核时间、审核意见
        entity.setStatus(newStatus);
        entity.setVersion(entity.getVersion() + 1);
        if (reqVO.getApproverUserId() != null) {
            entity.setApproverUserId(reqVO.getApproverUserId());
        }
        if (reqVO.getApproveOpinion() != null) {
            entity.setApproveOpinion(reqVO.getApproveOpinion());
        }
        entity.setApproveTime(LocalDateTime.now());
        formInstanceMapper.updateById(entity);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 构建模板快照JSON：将模板当前版本的关键字段聚合为快照。
     * <p>
     * 实际实现可使用 Jackson 序列化；此处简化为拼接 JSON 字符串。
     */
    private String buildTemplateSnapshot(FormTemplateDO template) {
        if (template == null) {
            return "{}";
        }
        return String.format(
                "{\"id\":%d,\"code\":\"%s\",\"name\":\"%s\",\"productType\":\"%s\",\"version\":%d,\"conf\":%s,\"fields\":%s}",
                template.getId(),
                template.getCode() == null ? "" : template.getCode(),
                template.getName() == null ? "" : template.getName(),
                template.getProductType() == null ? "" : template.getProductType(),
                template.getVersion() == null ? 0 : template.getVersion(),
                template.getConf() == null ? "{}" : template.getConf(),
                template.getFields() == null ? "[]" : template.getFields()
        );
    }

    private void validateCodeUnique(String code, Long excludeId) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        FormInstanceDO existing = formInstanceMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (excludeId == null || !Objects.equals(existing.getId(), excludeId)) {
            throw exception(FORM_INSTANCE_CODE_DUPLICATE, code);
        }
    }

    /**
     * 校验项目存在。
     * <p>
     * 【待确认】当前 engineering 模块未依赖 pms-module-project，遵循 AGENTS.md 模块边界规则暂不直接注入 ProjectMapper。
     */
    private void validateProjectExists(Long projectId) {
        // 预留扩展点：稳定跨模块 API 就绪后接入 ProjectMapper.selectById(projectId) 校验
        // 若项目不存在，抛出 exception(FORM_INSTANCE_PROJECT_NOT_EXISTS)
    }

    private FormTemplateDO validateTemplateExists(Long templateId) {
        FormTemplateDO template = formTemplateMapper.selectById(templateId);
        if (template == null) {
            throw exception(FORM_INSTANCE_TEMPLATE_NOT_EXISTS);
        }
        return template;
    }

    private void validateVersion(FormInstanceDO entity, Integer version) {
        if (version != null && !Objects.equals(entity.getVersion(), version)) {
            throw exception(FORM_INSTANCE_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(FormInstanceDO entity, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(entity.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(FORM_INSTANCE_STATUS_INVALID);
    }
}
