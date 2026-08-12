package cn.iocoder.yudao.module.pms.engineering.service.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo.RiskHandleReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo.RiskPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo.RiskSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.risk.RiskDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.risk.RiskMapper;
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
 * PMS 单机风险 Service 实现（FR-ENG-008）。
 * <p>
 * 状态流转：0 草稿 → 1 已识别 → 2 已确认 → 3 已同步CRM → 4 已关闭。
 * 风险编号全局唯一；草稿状态可编辑或删除。
 */
@Service
@Validated
@Slf4j
public class RiskServiceImpl implements RiskService {

    /**
     * 状态：0 草稿
     */
    public static final int STATUS_DRAFT = 0;
    /**
     * 状态：1 已识别
     */
    public static final int STATUS_IDENTIFIED = 1;
    /**
     * 状态：2 已确认
     */
    public static final int STATUS_CONFIRMED = 2;
    /**
     * 状态：3 已同步CRM
     */
    public static final int STATUS_CRM_SYNCED = 3;
    /**
     * 状态：4 已关闭
     */
    public static final int STATUS_CLOSED = 4;

    @Resource
    private RiskMapper riskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRisk(RiskSaveReqVO createReqVO) {
        // 1. 校验编号全局唯一
        validateCodeUnique(createReqVO.getCode(), null);
        // 2. 校验项目存在
        validateProjectExists(createReqVO.getProjectId());
        // 3. 转换并写入，初始状态为草稿
        RiskDO entity = BeanUtils.toBean(createReqVO, RiskDO.class);
        entity.setStatus(STATUS_DRAFT);
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        if (entity.getCrmSynced() == null) {
            entity.setCrmSynced(false);
        }
        // 默认风险类型与等级
        if (StringUtils.isBlank(entity.getRiskType())) {
            entity.setRiskType("SINGLE_DEVICE");
        }
        if (StringUtils.isBlank(entity.getRiskLevel())) {
            entity.setRiskLevel("MEDIUM");
        }
        riskMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRisk(RiskSaveReqVO updateReqVO) {
        // 1. 校验存在
        RiskDO existing = validateRiskExists(updateReqVO.getId());
        // 2. 状态校验：仅 0 草稿 / 1 已识别 可编辑
        validateStatus(existing, STATUS_DRAFT, STATUS_IDENTIFIED);
        // 3. 乐观锁版本校验
        validateVersion(existing, updateReqVO.getVersion());
        // 4. 编号不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(RISK_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 5. 更新
        RiskDO update = BeanUtils.toBean(updateReqVO, RiskDO.class);
        riskMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRisk(Long id) {
        // 1. 校验存在
        RiskDO existing = validateRiskExists(id);
        // 2. 状态校验：仅 0 草稿 可删除
        validateStatus(existing, STATUS_DRAFT);
        // 3. 删除
        riskMapper.deleteById(id);
    }

    @Override
    public RiskDO getRisk(Long id) {
        return riskMapper.selectById(id);
    }

    @Override
    public RiskDO validateRiskExists(Long id) {
        RiskDO entity = riskMapper.selectById(id);
        if (entity == null) {
            throw exception(RISK_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<RiskDO> getRiskPage(RiskPageReqVO pageReqVO) {
        return riskMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmRisk(RiskHandleReqVO reqVO) {
        // 1. 校验存在
        RiskDO entity = validateRiskExists(reqVO.getId());
        // 2. 状态校验：0 草稿 / 1 已识别 → 2 已确认
        validateStatus(entity, STATUS_DRAFT, STATUS_IDENTIFIED);
        // 3. 乐观锁版本校验
        validateVersion(entity, reqVO.getVersion());
        // 4. 更新状态、处理人、处理意见、处理时间
        entity.setStatus(STATUS_CONFIRMED);
        entity.setVersion(entity.getVersion() + 1);
        if (reqVO.getHandlerUserId() != null) {
            entity.setHandlerUserId(reqVO.getHandlerUserId());
        }
        if (reqVO.getHandleOpinion() != null) {
            entity.setHandleOpinion(reqVO.getHandleOpinion());
        }
        entity.setHandleTime(LocalDateTime.now());
        riskMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncCrm(Long id) {
        // 1. 校验存在
        RiskDO entity = validateRiskExists(id);
        // 2. 状态校验：2 已确认 → 3 已同步CRM
        validateStatus(entity, STATUS_CONFIRMED);
        // 3. 幂等校验：避免重复同步
        if (Boolean.TRUE.equals(entity.getCrmSynced())) {
            throw exception(RISK_CRM_ALREADY_SYNCED);
        }
        // 4. 模拟同步CRM（实际可调用 CRM Integration API）
        log.info("[syncCrm][风险编号({}) 同步至 CRM]", entity.getCode());
        // 5. 更新状态、CRM同步标记与时间
        entity.setStatus(STATUS_CRM_SYNCED);
        entity.setVersion(entity.getVersion() + 1);
        entity.setCrmSynced(true);
        entity.setCrmSyncTime(LocalDateTime.now());
        riskMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeRisk(RiskHandleReqVO reqVO) {
        // 1. 校验存在
        RiskDO entity = validateRiskExists(reqVO.getId());
        // 2. 状态校验：3 已同步CRM → 4 已关闭
        validateStatus(entity, STATUS_CRM_SYNCED);
        // 3. 乐观锁版本校验
        validateVersion(entity, reqVO.getVersion());
        // 4. 更新状态、处理人、处理意见、处理时间
        entity.setStatus(STATUS_CLOSED);
        entity.setVersion(entity.getVersion() + 1);
        if (reqVO.getHandlerUserId() != null) {
            entity.setHandlerUserId(reqVO.getHandlerUserId());
        }
        if (reqVO.getHandleOpinion() != null) {
            entity.setHandleOpinion(reqVO.getHandleOpinion());
        }
        entity.setHandleTime(LocalDateTime.now());
        riskMapper.updateById(entity);
    }

    // ==================== 内部工具方法 ====================

    private void validateCodeUnique(String code, Long excludeId) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        RiskDO existing = riskMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (excludeId == null || !Objects.equals(existing.getId(), excludeId)) {
            throw exception(RISK_CODE_DUPLICATE, code);
        }
    }

    /**
     * 校验项目存在。
     * <p>
     * 【待确认】遵循 AGENTS.md 模块边界规则暂不直接注入 ProjectMapper，
     * 待跨模块稳定 API 建立后接入实际校验；现阶段保留扩展点不抛错。
     */
    private void validateProjectExists(Long projectId) {
        // 预留扩展点：稳定跨模块 API 就绪后接入 ProjectMapper.selectById(projectId) 校验
    }

    private void validateVersion(RiskDO entity, Integer version) {
        if (version != null && !Objects.equals(entity.getVersion(), version)) {
            throw exception(RISK_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(RiskDO entity, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(entity.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(RISK_STATUS_INVALID);
    }
}
