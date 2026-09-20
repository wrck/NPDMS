package cn.iocoder.yudao.module.pms.engineering.service.deliverable;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableArchiveFactApi;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverablePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverableSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverableSummaryItemVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable.DeliverableDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.deliverable.DeliverableMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import cn.iocoder.yudao.module.pms.engineering.enums.EngStatusEnum;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.service.solution.SolutionServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 阶段交付件归集 Service 实现（FR-ENG-027）。
 * <p>
 * 归集版本不可覆盖：同一来源业务再次归集时返回已存在记录，不创建新版本。
 * 已归集（status=1）的交付件不可修改/删除，仅可作废。
 */
@Service
@Validated
@Slf4j
public class DeliverableServiceImpl implements DeliverableService {

    @Resource
    private DeliverableMapper deliverableMapper;
    @Resource
    private SolutionMapper solutionMapper;
    @Resource
    private ProjectDeliverableArchiveFactApi projectDeliverableArchiveFactApi;
    @Resource
    private EngineeringRecordCodeGenerator recordCodeGenerator;

    @Override
    public Long createDeliverable(DeliverableSaveReqVO createReqVO) {
        DeliverableDO entity = BeanUtils.toBean(createReqVO, DeliverableDO.class);
        entity.setCode(recordCodeGenerator.next(createReqVO.getProjectId(),
                EngineeringRecordCodeGenerator.DELIVERABLE, deliverableMapper,
                DeliverableDO::getProjectId, DeliverableDO::getCode));
        entity.setStatus(EngStatusEnum.DELIVERABLE_PENDING);
        deliverableMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateDeliverable(DeliverableSaveReqVO updateReqVO) {
        DeliverableDO existing = validateDeliverableExists(updateReqVO.getId());
        // 已归集不可修改（归集版本不可覆盖；编码由系统生成不可改）
        if (Objects.equals(EngStatusEnum.DELIVERABLE_ARCHIVED, existing.getStatus())) {
            throw exception(DELIVERABLE_STATUS_INVALID);
        }
        DeliverableDO update = BeanUtils.toBean(updateReqVO, DeliverableDO.class);
        deliverableMapper.updateById(update);
    }

    @Override
    public void deleteDeliverable(Long id) {
        DeliverableDO existing = validateDeliverableExists(id);
        // 已归集不可删除
        if (Objects.equals(EngStatusEnum.DELIVERABLE_ARCHIVED, existing.getStatus())) {
            throw exception(DELIVERABLE_STATUS_INVALID);
        }
        deliverableMapper.deleteById(id);
    }

    @Override
    public DeliverableDO getDeliverable(Long id) {
        return deliverableMapper.selectById(id);
    }

    @Override
    public DeliverableDO validateDeliverableExists(Long id) {
        DeliverableDO entity = deliverableMapper.selectById(id);
        if (entity == null) {
            throw exception(DELIVERABLE_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<DeliverableDO> getDeliverablePage(DeliverablePageReqVO pageReqVO) {
        return deliverableMapper.selectPage(pageReqVO);
    }

    @Override
    public Long archive(Long id, Long archivedBy) {
        DeliverableDO existing = validateDeliverableExists(id);
        // 归集版本不可覆盖：若已归集，直接返回已存在编号（幂等）
        if (Objects.equals(EngStatusEnum.DELIVERABLE_ARCHIVED, existing.getStatus())) {
            log.info("交付件 {} 已归集，幂等返回，不覆盖版本", id);
            return id;
        }
        // 仅待归集状态可归集
        if (!Objects.equals(EngStatusEnum.DELIVERABLE_PENDING, existing.getStatus())) {
            throw exception(DELIVERABLE_STATUS_INVALID);
        }
        DeliverableDO update = new DeliverableDO();
        update.setId(id);
        update.setStatus(EngStatusEnum.DELIVERABLE_ARCHIVED);
        update.setArchivedBy(archivedBy);
        update.setArchivedTime(LocalDateTime.now());
        update.setVersion(existing.getVersion());
        deliverableMapper.updateById(update);
        return id;
    }

    @Override
    public void voidDeliverable(Long id) {
        DeliverableDO existing = validateDeliverableExists(id);
        // 已作废不可重复作废
        if (Objects.equals(EngStatusEnum.DELIVERABLE_VOID, existing.getStatus())) {
            return;
        }
        DeliverableDO update = new DeliverableDO();
        update.setId(id);
        update.setStatus(EngStatusEnum.DELIVERABLE_VOID);
        update.setVersion(existing.getVersion());
        deliverableMapper.updateById(update);
    }

    @Override
    public List<DeliverableSummaryItemVO> getProjectSummary(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw exception(DELIVERABLE_NOT_EXISTS);
        }
        List<DeliverableSummaryItemVO> summary = new ArrayList<>();
        // 1. ACC 归档事实：初验/终验/满意度（ACC Owner 分类），未归档的交付件定义同样呈现
        projectDeliverableArchiveFactApi.listByProject(TenantContextHolder.getRequiredTenantId(), projectId)
                .forEach(fact -> {
                    DeliverableSummaryItemVO item = new DeliverableSummaryItemVO();
                    item.setCategory(resolveAccCategory(fact.sourceCategory()));
                    item.setCode(fact.deliverableCode());
                    item.setName(fact.name());
                    item.setSourceLabel(resolveAccSourceLabel(fact.sourceCategory()));
                    item.setStatus(null);
                    item.setRemark(fact.archiveStatus() == null ? "验收侧交付件定义尚未归档来源"
                            : (DeliverableSummaryCategories.PENDING_ARCHIVE.equals(fact.archiveStatus())
                            ? "归档补偿处理中" : null));
                    summary.add(item);
                });
        // 2. 批准实施方案（4.1→6.4 自动归档）
        for (SolutionDO solution : solutionMapper.selectListApprovedByProject(projectId)) {
            DeliverableSummaryItemVO item = new DeliverableSummaryItemVO();
            item.setCategory(DeliverableSummaryCategories.SCHEME);
            // 编码展示真实归集记录的系统编码；存量归集件保持其历史编码
            DeliverableDO archivedSolution = deliverableMapper.selectByProjectAndSource(
                    projectId, SolutionServiceImpl.SOURCE_TYPE_SOLUTION, solution.getId());
            item.setCode(archivedSolution != null ? archivedSolution.getCode()
                    : SolutionServiceImpl.SOURCE_TYPE_SOLUTION + "-" + solution.getCode());
            item.setName(StringUtils.defaultIfBlank(solution.getName(), solution.getCode())
                    + "（基线v" + solution.getBaselineVersion() + "）");
            item.setSourceLabel("4.1 审批通过自动归档");
            item.setStatus(EngStatusEnum.DELIVERABLE_ARCHIVED);
            item.setArchivedTime(solution.getApprovedTime());
            item.setSourceType(SolutionServiceImpl.SOURCE_TYPE_SOLUTION);
            item.setSourceId(solution.getId());
            summary.add(item);
        }
        // 3. 工程归集件：签收单进 RECEIPT 类、培训确认进 TRAINING 类，其余进其他归集
        for (DeliverableDO deliverable : deliverableMapper.selectListByProject(projectId)) {
            DeliverableSummaryItemVO item = new DeliverableSummaryItemVO();
            item.setCategory(resolveEngCategory(deliverable.getDeliverableType()));
            item.setCode(deliverable.getCode());
            item.setName(deliverable.getName());
            item.setSourceLabel(resolveEngSourceLabel(deliverable.getSourceType()));
            item.setStatus(deliverable.getStatus());
            item.setArchivedTime(deliverable.getArchivedTime());
            item.setFileUrl(deliverable.getFileUrl());
            item.setSourceType(deliverable.getSourceType());
            item.setSourceId(deliverable.getSourceId());
            item.setRemark(deliverable.getRemark());
            summary.add(item);
        }
        return summary;
    }

    private String resolveEngCategory(String deliverableType) {
        if ("RECEIPT".equals(deliverableType)) return DeliverableSummaryCategories.RECEIPT;
        if ("TRAINING".equals(deliverableType)) return DeliverableSummaryCategories.TRAINING;
        return DeliverableSummaryCategories.OTHER;
    }

    private String resolveEngSourceLabel(String sourceType) {
        if (SolutionServiceImpl.SOURCE_TYPE_SOLUTION.equals(sourceType)) return "4.1 审批通过自动归档";
        if ("TRAINING".equals(sourceType)) return "6.1 现场培训客户确认自动归档";
        return "工程手工归集";
    }

    private String resolveAccCategory(String sourceCategory) {
        if (sourceCategory == null) {
            return DeliverableSummaryCategories.OTHER;
        }
        return switch (sourceCategory) {
            case "PRELIMINARY" -> DeliverableSummaryCategories.PRELIMINARY;
            case "FINAL" -> DeliverableSummaryCategories.FINAL;
            case "SATISFACTION" -> DeliverableSummaryCategories.SATISFACTION;
            default -> DeliverableSummaryCategories.OTHER;
        };
    }

    private String resolveAccSourceLabel(String sourceCategory) {
        if (sourceCategory == null) {
            return "验收侧交付件清单";
        }
        return switch (sourceCategory) {
            case "PRELIMINARY" -> "6.3 初验报告同步";
            case "FINAL" -> "6.3 终验报告同步";
            case "SATISFACTION" -> "6.2 满意度报告同步";
            default -> "验收侧同步";
        };
    }
}
