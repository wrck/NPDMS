package cn.iocoder.yudao.module.pms.engineering.service.externalprocurement;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.vo.ExternalProcurementApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.vo.ExternalProcurementPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.vo.ExternalProcurementSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.externalprocurement.ExternalProcurementDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.externalprocurement.ExternalProcurementMapper;
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
 * PMS 外采申请 Service 实现（FR-ENG-002）。
 * <p>
 * 状态流转：0 草稿 → 1 已提交 → 2 审批中 → 3 已通过 / 4 已驳回 / 5 已撤回 / 6 已终止。
 * 外采单号全局唯一；草稿/已驳回状态可编辑或删除。
 */
@Service
@Validated
@Slf4j
public class ExternalProcurementServiceImpl implements ExternalProcurementService {

    /**
     * 状态：0 草稿
     */
    public static final int STATUS_DRAFT = 0;
    /**
     * 状态：1 已提交
     */
    public static final int STATUS_SUBMITTED = 1;
    /**
     * 状态：2 审批中
     */
    public static final int STATUS_APPROVING = 2;
    /**
     * 状态：3 已通过
     */
    public static final int STATUS_PASSED = 3;
    /**
     * 状态：4 已驳回
     */
    public static final int STATUS_REJECTED = 4;
    /**
     * 状态：5 已撤回
     */
    public static final int STATUS_WITHDRAWN = 5;
    /**
     * 状态：6 已终止
     */
    public static final int STATUS_TERMINATED = 6;

    /**
     * 审批动作：通过
     */
    public static final String ACTION_PASS = "PASS";
    /**
     * 审批动作：驳回
     */
    public static final String ACTION_REJECT = "REJECT";
    /**
     * 审批动作：退回（退回到草稿）
     */
    public static final String ACTION_RETURN = "RETURN";
    /**
     * 审批动作：转签
     */
    public static final String ACTION_TRANSFER = "TRANSFER";
    /**
     * 审批动作：会签
     */
    public static final String ACTION_COUNTERSIGN = "COUNTERSIGN";

    @Resource
    private ExternalProcurementMapper externalProcurementMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createExternalProcurement(ExternalProcurementSaveReqVO createReqVO) {
        // 1. 校验单号全局唯一
        validateCodeUnique(createReqVO.getCode(), null);
        // 2. 校验项目存在
        // 【待确认】跨模块校验项目存在需通过 pms-module-project 暴露的稳定 API；
        //          当前 engineering 模块未依赖 pms-module-project，遵循 AGENTS.md 模块边界规则暂不直接注入 ProjectMapper。
        validateProjectExists(createReqVO.getProjectId());
        // 3. 转换并写入，初始状态为草稿
        ExternalProcurementDO entity = BeanUtils.toBean(createReqVO, ExternalProcurementDO.class);
        entity.setStatus(STATUS_DRAFT);
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        externalProcurementMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateExternalProcurement(ExternalProcurementSaveReqVO updateReqVO) {
        // 1. 校验存在
        ExternalProcurementDO existing = validateExternalProcurementExists(updateReqVO.getId());
        // 2. 状态校验：仅 0 草稿 / 4 已驳回 可编辑
        validateStatus(existing, STATUS_DRAFT, STATUS_REJECTED);
        // 3. 乐观锁版本校验
        validateVersion(existing, updateReqVO.getVersion());
        // 4. 单号不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(EXT_PROC_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 5. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）
        ExternalProcurementDO update = BeanUtils.toBean(updateReqVO, ExternalProcurementDO.class);
        externalProcurementMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteExternalProcurement(Long id) {
        // 1. 校验存在
        ExternalProcurementDO existing = validateExternalProcurementExists(id);
        // 2. 状态校验：仅 0 草稿 / 4 已驳回 可删除
        validateStatus(existing, STATUS_DRAFT, STATUS_REJECTED);
        // 3. 删除
        externalProcurementMapper.deleteById(id);
    }

    @Override
    public ExternalProcurementDO getExternalProcurement(Long id) {
        return externalProcurementMapper.selectById(id);
    }

    @Override
    public ExternalProcurementDO validateExternalProcurementExists(Long id) {
        ExternalProcurementDO entity = externalProcurementMapper.selectById(id);
        if (entity == null) {
            throw exception(EXT_PROC_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<ExternalProcurementDO> getExternalProcurementPage(ExternalProcurementPageReqVO pageReqVO) {
        return externalProcurementMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitExternalProcurement(Long id) {
        // 1. 校验存在
        ExternalProcurementDO entity = validateExternalProcurementExists(id);
        // 2. 状态校验：0 草稿 / 4 已驳回 → 1 已提交
        validateStatus(entity, STATUS_DRAFT, STATUS_REJECTED);
        // 3. 更新状态
        updateStatus(entity, STATUS_SUBMITTED, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveExternalProcurement(ExternalProcurementApproveReqVO reqVO) {
        // 1. 校验存在
        ExternalProcurementDO entity = validateExternalProcurementExists(reqVO.getId());
        // 2. 状态校验：1 已提交 / 2 审批中 可审批
        validateStatus(entity, STATUS_SUBMITTED, STATUS_APPROVING);
        // 3. 根据审批动作决定目标状态
        int newStatus = resolveApproveStatus(reqVO.getApproveAction());
        // 4. 更新状态、审批人、审批时间、审批意见与审批动作
        updateStatus(entity, newStatus, reqVO.getApproverUserId(), reqVO.getApproveOpinion(), reqVO.getApproveAction());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawExternalProcurement(Long id) {
        // 1. 校验存在
        ExternalProcurementDO entity = validateExternalProcurementExists(id);
        // 2. 状态校验：1 已提交 / 2 审批中 → 5 已撤回
        validateStatus(entity, STATUS_SUBMITTED, STATUS_APPROVING);
        // 3. 更新状态
        updateStatus(entity, STATUS_WITHDRAWN, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminateExternalProcurement(Long id) {
        // 1. 校验存在
        ExternalProcurementDO entity = validateExternalProcurementExists(id);
        // 2. 状态校验：非 3 已通过 / 非 6 已终止 可终止
        if (Objects.equals(entity.getStatus(), STATUS_PASSED)
                || Objects.equals(entity.getStatus(), STATUS_TERMINATED)) {
            throw exception(EXT_PROC_STATUS_INVALID);
        }
        // 3. 更新状态
        updateStatus(entity, STATUS_TERMINATED, null, null, null);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 根据审批动作解析目标状态：
     * PASS → 3 已通过，REJECT → 4 已驳回，RETURN → 0 草稿，TRANSFER / COUNTERSIGN → 2 审批中（保持）
     */
    private int resolveApproveStatus(String action) {
        switch (action) {
            case ACTION_PASS:
                return STATUS_PASSED;
            case ACTION_REJECT:
                return STATUS_REJECTED;
            case ACTION_RETURN:
                return STATUS_DRAFT;
            case ACTION_TRANSFER:
            case ACTION_COUNTERSIGN:
                return STATUS_APPROVING;
            default:
                throw exception(EXT_PROC_STATUS_INVALID);
        }
    }

    /**
     * 更新状态并写入审批信息。version 自增以触发乐观锁。
     */
    private void updateStatus(ExternalProcurementDO entity, int newStatus,
                              Long approverUserId, String approveOpinion, String approveAction) {
        entity.setStatus(newStatus);
        entity.setVersion(entity.getVersion() + 1);
        // 审批类操作（PASS / REJECT / RETURN / TRANSFER / COUNTERSIGN）记录审批信息
        if (approverUserId != null) {
            entity.setApproverUserId(approverUserId);
        }
        if (approveOpinion != null) {
            entity.setApproveOpinion(approveOpinion);
        }
        if (approveAction != null) {
            entity.setApproveAction(approveAction);
        }
        // 审批动作产生终态或退回时，记录审批时间
        if (newStatus == STATUS_PASSED || newStatus == STATUS_REJECTED || newStatus == STATUS_DRAFT) {
            entity.setApproveTime(LocalDateTime.now());
        }
        externalProcurementMapper.updateById(entity);
    }

    private void validateCodeUnique(String code, Long excludeId) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        ExternalProcurementDO existing = externalProcurementMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (excludeId == null || !Objects.equals(existing.getId(), excludeId)) {
            throw exception(EXT_PROC_CODE_DUPLICATE, code);
        }
    }

    /**
     * 校验项目存在。
     * <p>
     * 【待确认】当前 engineering 模块未依赖 pms-module-project，遵循 AGENTS.md 模块边界规则暂不直接注入 ProjectMapper。
     * 待跨模块稳定 API（如 ProjectApi）建立后接入实际校验；现阶段保留扩展点不抛错。
     */
    private void validateProjectExists(Long projectId) {
        // 预留扩展点：稳定跨模块 API 就绪后接入 ProjectMapper.selectById(projectId) 校验
        // 若项目不存在，抛出 exception(EXT_PROC_PROJECT_NOT_EXISTS)
    }

    private void validateVersion(ExternalProcurementDO entity, Integer version) {
        if (version != null && !Objects.equals(entity.getVersion(), version)) {
            throw exception(EXT_PROC_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(ExternalProcurementDO entity, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(entity.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(EXT_PROC_STATUS_INVALID);
    }
}
