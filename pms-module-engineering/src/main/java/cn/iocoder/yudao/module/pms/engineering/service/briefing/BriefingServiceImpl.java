package cn.iocoder.yudao.module.pms.engineering.service.briefing;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingGenerateReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.BriefingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.BriefingMapper;
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
 * PMS 工程交底书 Service 实现（FR-ENG-006）。
 * <p>
 * 状态流转：0 草稿 → 1 已生成 → 2 已审核 → 3 已发布；任意非已发布状态可作废为 4 已作废。
 * 交底书编号全局唯一；草稿状态可编辑或删除。
 */
@Service
@Validated
@Slf4j
public class BriefingServiceImpl implements BriefingService {

    /**
     * 状态：0 草稿
     */
    public static final int STATUS_DRAFT = 0;
    /**
     * 状态：1 已生成
     */
    public static final int STATUS_GENERATED = 1;
    /**
     * 状态：2 已审核
     */
    public static final int STATUS_AUDITED = 2;
    /**
     * 状态：3 已发布
     */
    public static final int STATUS_PUBLISHED = 3;
    /**
     * 状态：4 已作废
     */
    public static final int STATUS_TERMINATED = 4;

    /**
     * 审核动作：通过
     */
    public static final String ACTION_PASS = "PASS";
    /**
     * 审核动作：驳回（退回到草稿）
     */
    public static final String ACTION_REJECT = "REJECT";

    @Resource
    private BriefingMapper briefingMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBriefing(BriefingSaveReqVO createReqVO) {
        // 1. 校验编号全局唯一
        validateCodeUnique(createReqVO.getCode(), null);
        // 2. 校验项目存在
        validateProjectExists(createReqVO.getProjectId());
        // 3. 转换并写入，初始状态为草稿
        BriefingDO entity = BeanUtils.toBean(createReqVO, BriefingDO.class);
        entity.setStatus(STATUS_DRAFT);
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        // 默认交底类型
        if (StringUtils.isBlank(entity.getBriefingType())) {
            entity.setBriefingType("STANDARD");
        }
        briefingMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBriefing(BriefingSaveReqVO updateReqVO) {
        // 1. 校验存在
        BriefingDO existing = validateBriefingExists(updateReqVO.getId());
        // 2. 状态校验：仅 0 草稿 可编辑
        validateStatus(existing, STATUS_DRAFT);
        // 3. 乐观锁版本校验
        validateVersion(existing, updateReqVO.getVersion());
        // 4. 编号不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(BRIEFING_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 5. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）
        BriefingDO update = BeanUtils.toBean(updateReqVO, BriefingDO.class);
        briefingMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBriefing(Long id) {
        // 1. 校验存在
        BriefingDO existing = validateBriefingExists(id);
        // 2. 状态校验：仅 0 草稿 可删除
        validateStatus(existing, STATUS_DRAFT);
        // 3. 删除
        briefingMapper.deleteById(id);
    }

    @Override
    public BriefingDO getBriefing(Long id) {
        return briefingMapper.selectById(id);
    }

    @Override
    public BriefingDO validateBriefingExists(Long id) {
        BriefingDO entity = briefingMapper.selectById(id);
        if (entity == null) {
            throw exception(BRIEFING_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<BriefingDO> getBriefingPage(BriefingPageReqVO pageReqVO) {
        return briefingMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateBriefing(BriefingGenerateReqVO reqVO) {
        // 1. 校验存在
        BriefingDO entity = validateBriefingExists(reqVO.getId());
        // 2. 状态校验：0 草稿 → 1 已生成
        validateStatus(entity, STATUS_DRAFT);
        // 3. 乐观锁版本校验
        validateVersion(entity, reqVO.getVersion());
        // 4. 更新模板关联与前序基线快照
        if (reqVO.getTemplateId() != null) {
            entity.setTemplateId(reqVO.getTemplateId());
        }
        if (reqVO.getSourceSnapshot() != null) {
            entity.setSourceSnapshot(reqVO.getSourceSnapshot());
        }
        // 5. 生成内容：按模板快照 + 前序基线数据快照组装内容（此处简化为占位逻辑）
        // 实际生成逻辑可扩展为调用文档生成引擎或模板渲染服务
        if (StringUtils.isBlank(entity.getContent())) {
            entity.setContent("自动生成的交底书内容（基于模板与前序基线数据）。");
        }
        // 6. 生成文件元数据（占位逻辑，实际可调用文件生成服务）
        if (StringUtils.isBlank(entity.getFileUrl())) {
            entity.setFileUrl("/pms/briefing/files/" + entity.getCode() + ".pdf");
            entity.setFileName(entity.getCode() + ".pdf");
            entity.setFileSize(102400L);
            entity.setFileChecksum("auto-" + entity.getCode());
        }
        // 7. 更新状态为已生成，记录生成时间
        entity.setStatus(STATUS_GENERATED);
        entity.setVersion(entity.getVersion() + 1);
        entity.setGenerateTime(LocalDateTime.now());
        briefingMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveBriefing(BriefingApproveReqVO reqVO) {
        // 1. 校验存在
        BriefingDO entity = validateBriefingExists(reqVO.getId());
        // 2. 状态校验：1 已生成 可审核
        validateStatus(entity, STATUS_GENERATED);
        // 3. 乐观锁版本校验
        validateVersion(entity, reqVO.getVersion());
        // 4. 根据审核动作决定目标状态
        int newStatus;
        switch (reqVO.getApproveAction()) {
            case ACTION_PASS:
                newStatus = STATUS_AUDITED;
                break;
            case ACTION_REJECT:
                newStatus = STATUS_DRAFT;
                break;
            default:
                throw exception(BRIEFING_STATUS_INVALID);
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
        briefingMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishBriefing(Long id) {
        // 1. 校验存在
        BriefingDO entity = validateBriefingExists(id);
        // 2. 状态校验：2 已审核 → 3 已发布
        validateStatus(entity, STATUS_AUDITED);
        // 3. 更新状态与发布时间
        entity.setStatus(STATUS_PUBLISHED);
        entity.setVersion(entity.getVersion() + 1);
        entity.setPublishTime(LocalDateTime.now());
        briefingMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminateBriefing(Long id) {
        // 1. 校验存在
        BriefingDO entity = validateBriefingExists(id);
        // 2. 状态校验：非 3 已发布 / 非 4 已作废 可作废
        if (Objects.equals(entity.getStatus(), STATUS_PUBLISHED)
                || Objects.equals(entity.getStatus(), STATUS_TERMINATED)) {
            throw exception(BRIEFING_STATUS_INVALID);
        }
        // 3. 更新状态为已作废
        entity.setStatus(STATUS_TERMINATED);
        entity.setVersion(entity.getVersion() + 1);
        briefingMapper.updateById(entity);
    }

    // ==================== 内部工具方法 ====================

    private void validateCodeUnique(String code, Long excludeId) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        BriefingDO existing = briefingMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (excludeId == null || !Objects.equals(existing.getId(), excludeId)) {
            throw exception(BRIEFING_CODE_DUPLICATE, code);
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
        // 若项目不存在，抛出 exception(BRIEFING_PROJECT_NOT_EXISTS)
    }

    private void validateVersion(BriefingDO entity, Integer version) {
        if (version != null && !Objects.equals(entity.getVersion(), version)) {
            throw exception(BRIEFING_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(BriefingDO entity, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(entity.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(BRIEFING_STATUS_INVALID);
    }
}
