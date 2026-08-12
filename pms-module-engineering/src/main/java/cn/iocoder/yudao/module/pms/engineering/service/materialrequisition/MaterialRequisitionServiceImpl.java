package cn.iocoder.yudao.module.pms.engineering.service.materialrequisition;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo.MaterialRequisitionApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo.MaterialRequisitionPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo.MaterialRequisitionSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialrequisition.MaterialRequisitionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialrequisition.MaterialRequisitionMapper;
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
 * PMS OA领料申请 Service 实现（FR-ENG-002）。
 * <p>
 * 状态流转：0 草稿 → 1 已提交 → 2 审批中 → 3 已通过 / 4 已驳回 / 5 已撤回 / 6 已终止。
 * 领料单号全局唯一；草稿/已驳回状态可编辑或删除。
 */
@Service
@Validated
@Slf4j
public class MaterialRequisitionServiceImpl implements MaterialRequisitionService {

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
    private MaterialRequisitionMapper materialRequisitionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMaterialRequisition(MaterialRequisitionSaveReqVO createReqVO) {
        // 1. 校验单号全局唯一
        validateCodeUnique(createReqVO.getCode(), null);
        // 2. 校验项目存在
        // 【待确认】跨模块校验项目存在需通过 pms-module-project 暴露的稳定 API；
        //          当前 engineering 模块未依赖 pms-module-project，遵循 AGENTS.md 模块边界规则暂不直接注入 ProjectMapper。
        validateProjectExists(createReqVO.getProjectId());
        // 3. 转换并写入，初始状态为草稿
        MaterialRequisitionDO entity = BeanUtils.toBean(createReqVO, MaterialRequisitionDO.class);
        entity.setStatus(STATUS_DRAFT);
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        materialRequisitionMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMaterialRequisition(MaterialRequisitionSaveReqVO updateReqVO) {
        // 1. 校验存在
        MaterialRequisitionDO existing = validateMaterialRequisitionExists(updateReqVO.getId());
        // 2. 状态校验：仅 0 草稿 / 4 已驳回 可编辑
        validateStatus(existing, STATUS_DRAFT, STATUS_REJECTED);
        // 3. 乐观锁版本校验
        validateVersion(existing, updateReqVO.getVersion());
        // 4. 单号不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(MATERIAL_REQ_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 5. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）
        MaterialRequisitionDO update = BeanUtils.toBean(updateReqVO, MaterialRequisitionDO.class);
        materialRequisitionMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterialRequisition(Long id) {
        // 1. 校验存在
        MaterialRequisitionDO existing = validateMaterialRequisitionExists(id);
        // 2. 状态校验：仅 0 草稿 / 4 已驳回 可删除
        validateStatus(existing, STATUS_DRAFT, STATUS_REJECTED);
        // 3. 删除
        materialRequisitionMapper.deleteById(id);
    }

    @Override
    public MaterialRequisitionDO getMaterialRequisition(Long id) {
        return materialRequisitionMapper.selectById(id);
    }

    @Override
    public MaterialRequisitionDO validateMaterialRequisitionExists(Long id) {
        MaterialRequisitionDO entity = materialRequisitionMapper.selectById(id);
        if (entity == null) {
            throw exception(MATERIAL_REQ_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<MaterialRequisitionDO> getMaterialRequisitionPage(MaterialRequisitionPageReqVO pageReqVO) {
        return materialRequisitionMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitMaterialRequisition(Long id) {
        // 1. 校验存在
        MaterialRequisitionDO entity = validateMaterialRequisitionExists(id);
        // 2. 状态校验：0 草稿 / 4 已驳回 → 1 已提交
        validateStatus(entity, STATUS_DRAFT, STATUS_REJECTED);
        // 3. 更新状态
        updateStatus(entity, STATUS_SUBMITTED, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveMaterialRequisition(MaterialRequisitionApproveReqVO reqVO) {
        // 1. 校验存在
        MaterialRequisitionDO entity = validateMaterialRequisitionExists(reqVO.getId());
        // 2. 状态校验：1 已提交 / 2 审批中 可审批
        validateStatus(entity, STATUS_SUBMITTED, STATUS_APPROVING);
        // 3. 根据审批动作决定目标状态
        int newStatus = resolveApproveStatus(reqVO.getApproveAction());
        // 4. 更新状态、审批人、审批时间、审批意见与审批动作
        updateStatus(entity, newStatus, reqVO.getApproverUserId(), reqVO.getApproveOpinion(), reqVO.getApproveAction());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawMaterialRequisition(Long id) {
        // 1. 校验存在
        MaterialRequisitionDO entity = validateMaterialRequisitionExists(id);
        // 2. 状态校验：1 已提交 / 2 审批中 → 5 已撤回
        validateStatus(entity, STATUS_SUBMITTED, STATUS_APPROVING);
        // 3. 更新状态
        updateStatus(entity, STATUS_WITHDRAWN, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminateMaterialRequisition(Long id) {
        // 1. 校验存在
        MaterialRequisitionDO entity = validateMaterialRequisitionExists(id);
        // 2. 状态校验：非 3 已通过 / 非 6 已终止 可终止
        if (Objects.equals(entity.getStatus(), STATUS_PASSED)
                || Objects.equals(entity.getStatus(), STATUS_TERMINATED)) {
            throw exception(MATERIAL_REQ_STATUS_INVALID);
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
                throw exception(MATERIAL_REQ_STATUS_INVALID);
        }
    }

    /**
     * 更新状态并写入审批信息。version 自增以触发乐观锁。
     */
    private void updateStatus(MaterialRequisitionDO entity, int newStatus,
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
        materialRequisitionMapper.updateById(entity);
    }

    private void validateCodeUnique(String code, Long excludeId) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        MaterialRequisitionDO existing = materialRequisitionMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (excludeId == null || !Objects.equals(existing.getId(), excludeId)) {
            throw exception(MATERIAL_REQ_CODE_DUPLICATE, code);
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
        // 若项目不存在，抛出 exception(MATERIAL_REQ_PROJECT_NOT_EXISTS)
    }

    private void validateVersion(MaterialRequisitionDO entity, Integer version) {
        if (version != null && !Objects.equals(entity.getVersion(), version)) {
            throw exception(MATERIAL_REQ_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(MaterialRequisitionDO entity, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(entity.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(MATERIAL_REQ_STATUS_INVALID);
    }
}
