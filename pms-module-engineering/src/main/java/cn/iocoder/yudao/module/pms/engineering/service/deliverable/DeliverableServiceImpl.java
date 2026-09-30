package cn.iocoder.yudao.module.pms.engineering.service.deliverable;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableArchiveFactApi;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverablePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverableSummaryItemVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable.DeliverableDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.deliverable.DeliverableMapper;
import cn.iocoder.yudao.module.pms.engineering.enums.EngStatusEnum;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi.DeliveryMaterialView;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 阶段交付件历史查询实现（原 FR-ENG-027 归集入口）。
 * <p>
 * P06R 统一交付件后只读：imp_eng_deliverable 存量为不可变历史（裸 URL 不改文件身份、不重挂），
 * 新写入一律经统一交付件能力；6.4 汇总输出统一材料 + ACC 归档事实 + 历史行（标注"历史归集"）。
 */
@Service
@Validated
@Slf4j
public class DeliverableServiceImpl implements DeliverableService {

    /** 统一材料业务对象类型 → 汇总呈现：实施方案（4.1 审批通过自动归档）。 */
    private static final String BUSINESS_TYPE_SOLUTION = "solution";
    /** 统一材料业务对象类型 → 汇总呈现：现场培训（6.1 客户确认自动归档）。 */
    private static final String BUSINESS_TYPE_TRAINING = "training";

    @Resource
    private DeliverableMapper deliverableMapper;
    @Resource
    private ProjectDeliverableArchiveFactApi projectDeliverableArchiveFactApi;
    @Resource
    private PlatformDeliveryMaterialApi deliveryMaterialApi;

    @Override
    public DeliverableDO getDeliverable(Long id) {
        return deliverableMapper.selectById(id);
    }

    @Override
    public PageResult<DeliverableDO> getDeliverablePage(DeliverablePageReqVO pageReqVO) {
        return deliverableMapper.selectPage(pageReqVO);
    }

    @Override
    public List<DeliverableSummaryItemVO> getProjectSummary(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw exception(DELIVERABLE_NOT_EXISTS);
        }
        List<DeliverableSummaryItemVO> summary = new ArrayList<>();
        // 1. 统一交付件材料（P06R）：批准实施方案/确认培训为业务结果型，其余为项目手工或挂接材料
        for (DeliveryMaterialView material : deliveryMaterialApi.listByProject(projectId)) {
            summary.add(toUnifiedSummaryItem(material));
        }
        // 2. ACC 归档事实：初验/终验/满意度（ACC Owner 分类），未归档的交付件定义同样呈现
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
        // 3. imp_eng_deliverable 历史行：P06R 前存量，不可变，标注"历史归集"
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
            item.setRemark("历史归集（统一交付件前存量）"
                    + (deliverable.getRemark() == null ? "" : "：" + deliverable.getRemark()));
            summary.add(item);
        }
        return summary;
    }

    private DeliverableSummaryItemVO toUnifiedSummaryItem(DeliveryMaterialView material) {
        DeliverableSummaryItemVO item = new DeliverableSummaryItemVO();
        boolean solution = BUSINESS_TYPE_SOLUTION.equals(material.businessObjectType());
        boolean training = BUSINESS_TYPE_TRAINING.equals(material.businessObjectType());
        item.setCategory(solution ? DeliverableSummaryCategories.SCHEME
                : training ? DeliverableSummaryCategories.TRAINING
                : DeliverableSummaryCategories.OTHER);
        // 统一模型不生成材料编码；历史编码仅存量 imp_eng_deliverable 行持有
        item.setCode(null);
        item.setName(material.title() != null ? material.title() : material.fileName());
        item.setSourceLabel(solution ? "4.1 审批通过自动归档（统一交付件）"
                : training ? "6.1 现场培训客户确认自动归档（统一交付件）"
                : "统一交付件登记");
        item.setStatus(PlatformDeliveryMaterialApi.STATUS_WITHDRAWN.equals(material.status())
                ? EngStatusEnum.DELIVERABLE_VOID : EngStatusEnum.DELIVERABLE_ARCHIVED);
        item.setArchivedTime(material.createTime());
        item.setSourceType(solution || training ? material.businessObjectType() : material.ownerModule());
        // businessObjectId 仅实体式登记（solution/training/ACC 报告）为纯数字；
        // 模板冻结链登记的是复合身份串（type|objectId|resultId|formedAt），不可解析为 Long
        item.setSourceId(material.businessObjectId() != null && material.businessObjectId().matches("\\d+")
                ? Long.valueOf(material.businessObjectId()) : material.entityId());
        item.setRemark(PlatformDeliveryMaterialApi.STATUS_WITHDRAWN.equals(material.status())
                ? "材料已撤回" : null);
        return item;
    }

    private String resolveEngCategory(String deliverableType) {
        if ("RECEIPT".equals(deliverableType)) return DeliverableSummaryCategories.RECEIPT;
        if ("TRAINING".equals(deliverableType)) return DeliverableSummaryCategories.TRAINING;
        return DeliverableSummaryCategories.OTHER;
    }

    private String resolveEngSourceLabel(String sourceType) {
        if ("SOLUTION".equals(sourceType)) return "4.1 审批通过自动归档";
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
