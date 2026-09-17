package cn.iocoder.yudao.module.pms.engineering.service.briefing.entity;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.entity.vo.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.entity.BriefingEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.BriefingEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.query.BriefingEntityLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.query.BriefingEntityPageQuery;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.LocalDateTime;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.pms.engineering.service.briefing.entity.BriefingEntityStatePolicy.*;

/**
 * 从 BriefingServiceImpl 复制的独立承接实现；不调用旧 Service、Mapper 或实体。
 * 保留原状态和生成占位语义。锁查询、受保护元数据及更新计数仅在本副本修正。
 */
@Service
@Validated
public class BriefingEntityServiceImpl implements BriefingEntityService {
    @Resource
    private BriefingEntityMapper briefingEntityMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBriefing(BriefingEntitySaveReqVO request) {
        validateCodeUnique(request.getCode());
        // 原项目存在性校验是空扩展点，本次不凭空引入新的项目准入规则。
        BriefingEntityDO entity = BeanUtils.toBean(request, BriefingEntityDO.class);
        entity.setId(null);
        entity.setTenantId(TenantContextHolder.getRequiredTenantId());
        entity.setStatus(DRAFT);
        entity.setVersion(0);
        if (StringUtils.isBlank(entity.getBriefingType())) entity.setBriefingType("STANDARD");
        briefingEntityMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBriefing(BriefingEntitySaveReqVO request) {
        BriefingEntityDO current = lock(request.getId());
        require(isDraft(current.getStatus()));
        validateVersion(current, request.getVersion());
        if (!Objects.equals(current.getCode(), request.getCode()))
            throw exception(BRIEFING_CODE_DUPLICATE, request.getCode());
        // 保存对象不携带状态、来源或审核元数据；不把响应对象直接写回数据库。
        BriefingEntityDO update = BeanUtils.toBean(request, BriefingEntityDO.class);
        update.setTenantId(current.getTenantId());
        update.setVersion(current.getVersion());
        updateChecked(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBriefing(Long id) {
        BriefingEntityDO current = lock(id);
        require(isDraft(current.getStatus()));
        if (briefingEntityMapper.deleteById(id) != 1) throw exception(BRIEFING_VERSION_NOT_MATCH);
    }

    @Override
    public BriefingEntityDO getBriefing(Long id) { return briefingEntityMapper.selectById(id); }

    @Override
    public BriefingEntityDO validateBriefingExists(Long id) {
        BriefingEntityDO entity = getBriefing(id);
        if (entity == null) throw exception(BRIEFING_NOT_EXISTS);
        return entity;
    }

    @Override
    public PageResult<BriefingEntityDO> getBriefingPage(BriefingEntityPageReqVO request) {
        if (request.getCreateTime() != null && request.getCreateTime().length != 2)
            throw new IllegalArgumentException("创建时间必须是起止区间");
        BriefingEntityPageQuery query = BeanUtils.toBean(request, BriefingEntityPageQuery.class);
        query.setTenantId(TenantContextHolder.getRequiredTenantId());
        return briefingEntityMapper.selectPage(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateBriefing(BriefingEntityGenerateReqVO request) {
        BriefingEntityDO entity = lock(request.getId());
        require(canGenerate(entity.getStatus()));
        validateVersion(entity, request.getVersion());
        if (request.getTemplateId() != null) entity.setTemplateId(request.getTemplateId());
        if (request.getSourceSnapshot() != null) entity.setSourceSnapshot(request.getSourceSnapshot());
        // 原实现尚未接入文档生成引擎。这里只保留其占位结果，不宣称生成了真实文件。
        if (StringUtils.isBlank(entity.getContent()))
            entity.setContent("自动生成的交底书内容（基于模板与前序基线数据）。");
        if (StringUtils.isBlank(entity.getFileUrl())) {
            entity.setFileUrl("/pms/briefing/files/" + entity.getCode() + ".pdf");
            entity.setFileName(entity.getCode() + ".pdf");
            entity.setFileSize(102400L);
            entity.setFileChecksum("auto-" + entity.getCode());
        }
        entity.setStatus(GENERATED);
        entity.setGenerateTime(LocalDateTime.now());
        updateChecked(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveBriefing(BriefingEntityApproveReqVO request) {
        BriefingEntityDO entity = lock(request.getId());
        require(canApprove(entity.getStatus()));
        validateVersion(entity, request.getVersion());
        try { entity.setStatus(approvalTarget(request.getApproveAction())); }
        catch (IllegalArgumentException invalid) { throw exception(BRIEFING_STATUS_INVALID); }
        if (request.getApproverUserId() != null) entity.setApproverUserId(request.getApproverUserId());
        if (request.getApproveOpinion() != null) entity.setApproveOpinion(request.getApproveOpinion());
        entity.setApproveTime(LocalDateTime.now());
        updateChecked(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishBriefing(Long id) {
        BriefingEntityDO entity = lock(id);
        require(canPublish(entity.getStatus()));
        entity.setStatus(PUBLISHED);
        entity.setPublishTime(LocalDateTime.now());
        updateChecked(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminateBriefing(Long id) {
        BriefingEntityDO entity = lock(id);
        require(canTerminate(entity.getStatus()));
        entity.setStatus(TERMINATED);
        updateChecked(entity);
    }

    private BriefingEntityDO lock(Long id) {
        if (id == null) throw exception(BRIEFING_NOT_EXISTS);
        BriefingEntityDO entity = briefingEntityMapper.selectForUpdate(
                new BriefingEntityLockQuery(TenantContextHolder.getRequiredTenantId(), id));
        if (entity == null) throw exception(BRIEFING_NOT_EXISTS);
        return entity;
    }
    private void updateChecked(BriefingEntityDO entity) {
        // @Version 负责增加一次版本；提前手工递增会导致更新条件匹配错误。
        entity.setUpdater(null);
        entity.setUpdateTime(null);
        if (briefingEntityMapper.updateById(entity) != 1) throw exception(BRIEFING_VERSION_NOT_MATCH);
    }
    private void validateCodeUnique(String code) {
        if (StringUtils.isNotBlank(code) && briefingEntityMapper.selectByCode(code) != null)
            throw exception(BRIEFING_CODE_DUPLICATE, code);
    }
    private void validateVersion(BriefingEntityDO entity, Integer version) {
        if (version != null && !Objects.equals(entity.getVersion(), version))
            throw exception(BRIEFING_VERSION_NOT_MATCH);
    }
    private void require(boolean allowed) {
        if (!allowed) throw exception(BRIEFING_STATUS_INVALID);
    }
}
