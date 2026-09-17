package cn.iocoder.yudao.module.pms.engineering.service.briefing.entity;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.entity.vo.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.entity.BriefingEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.BriefingEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.query.BriefingEntityLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.query.BriefingEntityPageQuery;
import cn.iocoder.yudao.module.pms.engineering.domain.briefing.BriefingAggregate;
import cn.iocoder.yudao.module.pms.engineering.domain.briefing.BriefingDocumentArtifact;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.pms.engineering.service.briefing.entity.BriefingEntityAccess.*;

/**
 * SOL 交底应用服务：编排权限、聚合和持久化，不重复拥有领域状态规则。
 * 原交底运行路径保持不动；未接通真实生成适配器时不产生伪造文件或成功事实。
 */
@Service
@Validated
public class BriefingEntityServiceImpl implements BriefingEntityService {
    @Resource
    private BriefingEntityMapper briefingEntityMapper;
    @Resource
    private BriefingEntityAccess access;
    @Resource
    private ObjectProvider<BriefingGenerationPort> generationPorts;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBriefing(BriefingEntitySaveReqVO request) {
        access.lockWrite(request.getProjectId(), CREATE);
        var aggregate = domain(() -> BriefingAggregate.draft(
                TenantContextHolder.getRequiredTenantId(), request.getProjectId(), request.getCode()));
        validateCodeUnique(request.getCode());
        BriefingEntityDO entity = BeanUtils.toBean(request, BriefingEntityDO.class);
        entity.setId(null);
        entity.setTenantId(aggregate.identity().tenantId());
        entity.setStatus(aggregate.state().code());
        entity.setVersion(aggregate.version());
        if (StringUtils.isBlank(entity.getBriefingType())) entity.setBriefingType("STANDARD");
        if (briefingEntityMapper.insert(entity) != 1 || entity.getId() == null)
            throw new IllegalStateException("BRIEFING_CREATE_NOT_PERSISTED");
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBriefing(BriefingEntitySaveReqVO request) {
        BriefingEntityDO current = lock(request.getId(), UPDATE);
        domain(() -> {
            aggregate(current).requireEditable(request.getProjectId(), request.getCode(), request.getVersion());
            return null;
        });
        // 普通编辑不能移动对象身份或改写来源、状态及审核事实。
        BriefingEntityDO update = BeanUtils.toBean(request, BriefingEntityDO.class);
        update.setId(current.getId());
        update.setProjectId(current.getProjectId());
        update.setCode(current.getCode());
        update.setTenantId(current.getTenantId());
        update.setStatus(current.getStatus());
        update.setVersion(current.getVersion());
        updateChecked(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBriefing(Long id) {
        BriefingEntityDO current = lock(id, DELETE);
        domain(() -> { aggregate(current).requireDraft(); return null; });
        if (briefingEntityMapper.deleteById(id) != 1) throw exception(BRIEFING_VERSION_NOT_MATCH);
    }

    @Override
    public BriefingEntityDO getBriefing(Long id) {
        access.requirePermission(QUERY);
        requireId(id);
        var row = briefingEntityMapper.selectByTenantAndId(TenantContextHolder.getRequiredTenantId(), id);
        if (row != null) {
            requireTenant(row);
            access.requireReadable(row.getProjectId());
        }
        return row;
    }

    @Override
    public BriefingEntityDO validateBriefingExists(Long id) {
        BriefingEntityDO entity = getBriefing(id);
        if (entity == null) throw exception(BRIEFING_NOT_EXISTS);
        return entity;
    }

    @Override
    public PageResult<BriefingEntityDO> getBriefingPage(BriefingEntityPageReqVO request) {
        if (request.getPageNo() == null || request.getPageNo() < 1
                || request.getPageSize() == null || request.getPageSize() < 1 || request.getPageSize() > 200)
            throw new IllegalArgumentException("交底分页范围无效");
        var interval = request.getCreateTime();
        if (interval != null && (interval.length != 2
                || (interval[0] != null && interval[1] != null && !interval[0].isBefore(interval[1]))))
            throw new IllegalArgumentException("创建时间必须是有效的左闭右开区间");
        BriefingEntityPageQuery query = BeanUtils.toBean(request, BriefingEntityPageQuery.class);
        query.setTenantId(TenantContextHolder.getRequiredTenantId());
        query.setVisibleProjectIds(access.visibleProjects());
        return briefingEntityMapper.selectPage(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateBriefing(BriefingEntityGenerateReqVO request) {
        BriefingEntityDO current = lock(request.getId(), GENERATE);
        var aggregate = aggregate(current);
        domain(() -> { aggregate.requireGeneratable(request.getVersion()); return null; });
        var generator = generator();
        var artifact = generator.generate(new BriefingGenerationPort.Request(
                aggregate.identity(), aggregate.version(),
                request.getTemplateId() == null ? current.getTemplateId() : request.getTemplateId(),
                request.getSourceSnapshot() == null ? current.getSourceSnapshot() : request.getSourceSnapshot(),
                current.getContent(), access.actorId()));
        var generated = domain(() -> aggregate.generated(request.getVersion(), artifact));
        generator.verify(artifact, access.actorId());
        // 只有身份、版本和实际文件核验均成功后才准备写入；不接纳 URL 拼接占位结果。
        BriefingEntityDO update = copy(current);
        update.setTemplateId(artifact.templateId());
        update.setTemplateSnapshot(artifact.templateSnapshot());
        update.setSourceSnapshot(artifact.sourceSnapshot());
        update.setContent(artifact.content());
        update.setFileUrl(artifact.fileUrl());
        update.setFileName(artifact.fileName());
        update.setFileSize(artifact.fileSize());
        update.setFileChecksum(artifact.fileChecksum());
        update.setStatus(generated.state().code());
        update.setGenerateTime(LocalDateTime.now());
        updateChecked(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveBriefing(BriefingEntityApproveReqVO request) {
        BriefingEntityDO current = lock(request.getId(), AUDIT);
        var reviewed = domain(() -> aggregate(current).reviewed(request.getVersion(), request.getApproveAction()));
        if (reviewed.state() == BriefingAggregate.State.AUDITED) verifyDocument(current);
        BriefingEntityDO update = copy(current);
        update.setStatus(reviewed.state().code());
        // 保留旧请求字段的可反序列化性，但审核身份只能来自服务端登录上下文。
        update.setApproverUserId(access.actorId());
        if (request.getApproveOpinion() != null) update.setApproveOpinion(request.getApproveOpinion());
        update.setApproveTime(LocalDateTime.now());
        updateChecked(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishBriefing(Long id) {
        BriefingEntityDO current = lock(id, PUBLISH);
        var published = domain(() -> aggregate(current).published());
        verifyDocument(current);
        BriefingEntityDO update = copy(current);
        update.setStatus(published.state().code());
        update.setPublishTime(LocalDateTime.now());
        updateChecked(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminateBriefing(Long id) {
        BriefingEntityDO current = lock(id, UPDATE);
        var terminated = domain(() -> aggregate(current).terminated());
        BriefingEntityDO update = copy(current);
        update.setStatus(terminated.state().code());
        updateChecked(update);
    }

    private BriefingEntityDO lock(Long id, String permission) {
        access.requirePermission(permission);
        requireId(id);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var identity = briefingEntityMapper.selectByTenantAndId(tenantId, id);
        if (identity == null) throw exception(BRIEFING_NOT_EXISTS);
        requireTenant(identity);
        access.lockWrite(identity.getProjectId(), permission);
        var row = briefingEntityMapper.selectForUpdate(new BriefingEntityLockQuery(tenantId, id));
        if (row == null) throw exception(BRIEFING_NOT_EXISTS);
        requireTenant(row);
        if (!Objects.equals(row.getId(), id) || !Objects.equals(row.getProjectId(), identity.getProjectId()))
            throw exception(FORBIDDEN);
        return row;
    }

    private BriefingAggregate aggregate(BriefingEntityDO row) {
        return domain(() -> BriefingAggregate.restore(new BriefingAggregate.Identity(
                row.getTenantId(), row.getProjectId(), row.getId(), row.getCode()), row.getVersion(), row.getStatus()));
    }

    private void verifyDocument(BriefingEntityDO row) {
        var aggregate = aggregate(row);
        generator().verify(new BriefingDocumentArtifact(aggregate.identity(), aggregate.version(),
                row.getTemplateId(), row.getTemplateSnapshot(), row.getSourceSnapshot(), row.getContent(),
                row.getFileUrl(), row.getFileName(), row.getFileSize(), row.getFileChecksum()), access.actorId());
    }

    private BriefingGenerationPort generator() {
        var provider = generationPorts.getIfAvailable();
        if (provider == null) throw new IllegalStateException("BRIEFING_DOCUMENT_GENERATION_NOT_CONNECTED");
        return provider;
    }

    private static BriefingEntityDO copy(BriefingEntityDO row) {
        // 写入候选与读到的对象分离，失败路径不提前改变当前对象。
        var result = new BriefingEntityDO();
        BeanUtils.copyProperties(row, result);
        return result;
    }

    private void updateChecked(BriefingEntityDO entity) {
        // @Version 只在真正写入时递增，聚合转换不提前递增或忽略更新失败。
        entity.setUpdater(null);
        entity.setUpdateTime(null);
        if (briefingEntityMapper.updateById(entity) != 1) throw exception(BRIEFING_VERSION_NOT_MATCH);
    }

    private void validateCodeUnique(String code) {
        if (briefingEntityMapper.selectByTenantAndCode(TenantContextHolder.getRequiredTenantId(), code) != null)
            throw exception(BRIEFING_CODE_DUPLICATE, code);
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) throw exception(BRIEFING_NOT_EXISTS);
    }

    private static void requireTenant(BriefingEntityDO row) {
        if (!Objects.equals(row.getTenantId(), TenantContextHolder.getRequiredTenantId())) throw exception(FORBIDDEN);
    }

    private static <T> T domain(Supplier<T> action) {
        try { return action.get(); }
        catch (BriefingAggregate.Rejected rejected) {
            if (rejected.reason() == BriefingAggregate.Reason.VERSION_CONFLICT)
                throw exception(BRIEFING_VERSION_NOT_MATCH);
            if (rejected.reason() == BriefingAggregate.Reason.IDENTITY_CHANGED)
                throw exception(FORBIDDEN);
            throw exception(BRIEFING_STATUS_INVALID);
        }
    }
}
