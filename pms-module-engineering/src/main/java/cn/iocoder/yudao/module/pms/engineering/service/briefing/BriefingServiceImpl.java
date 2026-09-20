package cn.iocoder.yudao.module.pms.engineering.service.briefing;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingGenerateReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.BriefingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.BriefingMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
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
    @Resource
    private FileApi fileApi;
    @Resource
    private EngineeringRecordCodeGenerator recordCodeGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBriefing(BriefingSaveReqVO createReqVO) {
        // 1. 校验项目存在
        validateProjectExists(createReqVO.getProjectId());
        // 2. 转换并写入，初始状态为草稿；编号由系统按项目编码自动生成
        BriefingDO entity = BeanUtils.toBean(createReqVO, BriefingDO.class);
        entity.setCode(recordCodeGenerator.next(createReqVO.getProjectId(),
                EngineeringRecordCodeGenerator.BRIEFING, briefingMapper,
                BriefingDO::getProjectId, BriefingDO::getCode));
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
        // 4. 更新（乐观锁由 MyBatis-Plus @Version 自动处理；编号由系统生成不可改）
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
        // 5. 内容：保留人工填写内容；未填写时由交底书自身真实数据组装，不生成占位文案
        if (StringUtils.isBlank(entity.getContent())) {
            entity.setContent(renderBriefingContent(entity));
        }
        // 6. 生成真实文件写入平台文件服务，取得真实访问地址、大小与校验和；
        //    每次从草稿生成都按当前内容重新出文件，驳回后再次生成反映最新编辑
        byte[] document = renderBriefingDocument(entity);
        String fileUrl = fileApi.createFile(document, entity.getCode() + ".html", "briefing", "text/html");
        entity.setFileUrl(fileUrl);
        entity.setFileName(entity.getCode() + ".html");
        entity.setFileSize((long) document.length);
        entity.setFileChecksum(sha256Hex(document));
        // 7. 更新状态为已生成，记录生成时间
        entity.setStatus(STATUS_GENERATED);
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
        briefingMapper.updateById(entity);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 由交底书自身真实数据组装缺省内容；模板与来源快照按原样呈现，不宣称已完成模板校验。
     */
    private String renderBriefingContent(BriefingDO entity) {
        StringBuilder content = new StringBuilder("工程交底书 ").append(entity.getCode());
        if (StringUtils.isNotBlank(entity.getName())) {
            content.append("：").append(entity.getName());
        }
        content.append("。交底类型：").append(StringUtils.defaultIfBlank(entity.getBriefingType(), "STANDARD"));
        if (entity.getTemplateId() != null) {
            content.append("。模板编号：").append(entity.getTemplateId());
        }
        if (StringUtils.isNotBlank(entity.getSourceSnapshot())) {
            content.append("。来源基线快照：").append(entity.getSourceSnapshot());
        }
        if (StringUtils.isNotBlank(entity.getRemark())) {
            content.append("。备注：").append(entity.getRemark());
        }
        return content.toString();
    }

    private byte[] renderBriefingDocument(BriefingDO entity) {
        String html = "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>"
                + HtmlUtils.htmlEscape(entity.getCode()) + "</title></head><body><h1>工程交底书</h1><pre>"
                + HtmlUtils.htmlEscape(entity.getContent()) + "</pre></body></html>";
        return html.getBytes(StandardCharsets.UTF_8);
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
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
