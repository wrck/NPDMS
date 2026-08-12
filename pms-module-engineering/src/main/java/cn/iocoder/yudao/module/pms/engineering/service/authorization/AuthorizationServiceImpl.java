package cn.iocoder.yudao.module.pms.engineering.service.authorization;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo.AuthorizationApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo.AuthorizationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo.AuthorizationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.authorization.AuthorizationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.authorization.AuthorizationMapper;
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
 * PMS 授权与借货 Service 实现（FR-ENG-010）。
 * <p>
 * 状态流转：0 草稿 → 1 已提交 → 2 审批中 → 3 已通过 / 4 已驳回 / 5 已撤回 / 6 已终止。
 * 授权编号全局唯一；草稿、已驳回、已撤回状态可编辑；草稿状态可删除。
 */
@Service
@Validated
@Slf4j
public class AuthorizationServiceImpl implements AuthorizationService {

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
    public static final int STATUS_APPROVED = 3;
    /**
     * 状态：4 已驳回
     */
    public static final int STATUS_REJECTED = 4;
    /**
     * 状态：5 已撤回
     */
    public static final int STATUS_RECALLED = 5;
    /**
     * 状态：6 已终止
     */
    public static final int STATUS_TERMINATED = 6;

    /**
     * 审批动作：通过
     */
    public static final String ACTION_PASS = "PASS";
    /**
     * 审批动作：驳回（恢复为可修订状态）
     */
    public static final String ACTION_REJECT = "REJECT";
    /**
     * 审批动作：终止
     */
    public static final String ACTION_TERMINATE = "TERMINATE";

    @Resource
    private AuthorizationMapper authorizationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAuthorization(AuthorizationSaveReqVO createReqVO) {
        // 1. 校验编号全局唯一
        validateCodeUnique(createReqVO.getCode(), null);
        // 2. 校验项目存在
        validateProjectExists(createReqVO.getProjectId());
        // 3. 转换并写入，初始状态为草稿
        AuthorizationDO entity = BeanUtils.toBean(createReqVO, AuthorizationDO.class);
        entity.setStatus(STATUS_DRAFT);
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        if (entity.getUsedCount() == null) {
            entity.setUsedCount(0);
        }
        // 默认授权类型
        if (StringUtils.isBlank(entity.getAuthorizationType())) {
            entity.setAuthorizationType("TEMPORARY");
        }
        authorizationMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAuthorization(AuthorizationSaveReqVO updateReqVO) {
        // 1. 校验存在
        AuthorizationDO existing = validateAuthorizationExists(updateReqVO.getId());
        // 2. 状态校验：0 草稿 / 4 已驳回 / 5 已撤回 可编辑
        validateStatus(existing, STATUS_DRAFT, STATUS_REJECTED, STATUS_RECALLED);
        // 3. 乐观锁版本校验
        validateVersion(existing, updateReqVO.getVersion());
        // 4. 编号不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(AUTHORIZATION_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 5. 更新（已驳回/已撤回状态编辑后恢复为草稿）
        AuthorizationDO update = BeanUtils.toBean(updateReqVO, AuthorizationDO.class);
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            update.setStatus(STATUS_DRAFT);
        }
        authorizationMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAuthorization(Long id) {
        // 1. 校验存在
        AuthorizationDO existing = validateAuthorizationExists(id);
        // 2. 状态校验：仅 0 草稿 可删除
        validateStatus(existing, STATUS_DRAFT);
        // 3. 删除
        authorizationMapper.deleteById(id);
    }

    @Override
    public AuthorizationDO getAuthorization(Long id) {
        return authorizationMapper.selectById(id);
    }

    @Override
    public AuthorizationDO validateAuthorizationExists(Long id) {
        AuthorizationDO entity = authorizationMapper.selectById(id);
        if (entity == null) {
            throw exception(AUTHORIZATION_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<AuthorizationDO> getAuthorizationPage(AuthorizationPageReqVO pageReqVO) {
        return authorizationMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitAuthorization(Long id) {
        // 1. 校验存在
        AuthorizationDO entity = validateAuthorizationExists(id);
        // 2. 状态校验：0 草稿 / 4 已驳回 / 5 已撤回 → 1 已提交 → 2 审批中
        validateStatus(entity, STATUS_DRAFT, STATUS_REJECTED, STATUS_RECALLED);
        // 3. 更新状态为已提交，记录提交人与提交时间
        entity.setStatus(STATUS_SUBMITTED);
        entity.setVersion(entity.getVersion() + 1);
        entity.setSubmitTime(LocalDateTime.now());
        // 提交人预留扩展点：实际可从 SecurityFrameworkUtils 获取登录用户
        authorizationMapper.updateById(entity);
        // 4. 模拟审批流入口：实际可调用 BPM 启动流程实例并回写 processInstanceId
        log.info("[submitAuthorization][授权编号({}) 已提交，进入审批中]", entity.getCode());
        entity.setStatus(STATUS_APPROVING);
        authorizationMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveAuthorization(AuthorizationApproveReqVO reqVO) {
        // 1. 校验存在
        AuthorizationDO entity = validateAuthorizationExists(reqVO.getId());
        // 2. 状态校验：2 审批中 可审批
        validateStatus(entity, STATUS_APPROVING);
        // 3. 乐观锁版本校验
        validateVersion(entity, reqVO.getVersion());
        // 4. 根据审批动作决定目标状态
        int newStatus;
        switch (reqVO.getApproveAction()) {
            case ACTION_PASS:
                newStatus = STATUS_APPROVED;
                break;
            case ACTION_REJECT:
                newStatus = STATUS_REJECTED;
                break;
            case ACTION_TERMINATE:
                newStatus = STATUS_TERMINATED;
                break;
            default:
                throw exception(AUTHORIZATION_STATUS_INVALID);
        }
        // 5. 更新状态、审批人、审批时间、审批意见
        entity.setStatus(newStatus);
        entity.setVersion(entity.getVersion() + 1);
        if (reqVO.getApproverUserId() != null) {
            entity.setApproverUserId(reqVO.getApproverUserId());
        }
        if (reqVO.getApproveOpinion() != null) {
            entity.setApproveOpinion(reqVO.getApproveOpinion());
        }
        entity.setApproveTime(LocalDateTime.now());
        authorizationMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recallAuthorization(Long id) {
        // 1. 校验存在
        AuthorizationDO entity = validateAuthorizationExists(id);
        // 2. 状态校验：1 已提交 / 2 审批中 → 5 已撤回
        validateStatus(entity, STATUS_SUBMITTED, STATUS_APPROVING);
        // 3. 更新状态为已撤回，记录撤回时间
        entity.setStatus(STATUS_RECALLED);
        entity.setVersion(entity.getVersion() + 1);
        entity.setRecallTime(LocalDateTime.now());
        authorizationMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminateAuthorization(Long id) {
        // 1. 校验存在
        AuthorizationDO entity = validateAuthorizationExists(id);
        // 2. 状态校验：3 已通过 → 6 已终止
        validateStatus(entity, STATUS_APPROVED);
        // 3. 更新状态为已终止
        entity.setStatus(STATUS_TERMINATED);
        entity.setVersion(entity.getVersion() + 1);
        authorizationMapper.updateById(entity);
    }

    // ==================== 内部工具方法 ====================

    private void validateCodeUnique(String code, Long excludeId) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        AuthorizationDO existing = authorizationMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (excludeId == null || !Objects.equals(existing.getId(), excludeId)) {
            throw exception(AUTHORIZATION_CODE_DUPLICATE, code);
        }
    }

    /**
     * 校验项目存在。
     * <p>
     * 【待确认】遵循 AGENTS.md 模块边界规则暂不直接注入 ProjectMapper。
     */
    private void validateProjectExists(Long projectId) {
        // 预留扩展点
    }

    private void validateVersion(AuthorizationDO entity, Integer version) {
        if (version != null && !Objects.equals(entity.getVersion(), version)) {
            throw exception(AUTHORIZATION_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(AuthorizationDO entity, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(entity.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(AUTHORIZATION_STATUS_INVALID);
    }
}
