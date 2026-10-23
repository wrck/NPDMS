package cn.iocoder.yudao.module.pms.service.service.inspectionrule;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeQuery;
import cn.iocoder.yudao.module.pms.asset.api.producttype.inspection.InspectionAssetProductTypeApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.*;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionRequestContext;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Query views deliberately exclude DAL identity, tenant and audit implementation fields. */
@Service
@RequiredArgsConstructor
public class InspectionRuleQueryService {
    private final InspectionRuleMapper rules;
    private final InspectionRuleRevisionMapper revisions;
    private final InspectionRuleCommandRevisionMapper commands;
    private final InspectionRuleProductTypeRevisionMapper products;
    private final SelectableInspectionRuleMapper selectable;
    private final InspectionAssetProductTypeApi assetTypes;

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PageQuery extends PageParam {
        private Long ruleId;
        private String detectionId;
        private String ruleNameKeyword;
        private String categoryCode;
        private String severityCode;
        private String productTypeCode;
        private String statusCode;
    }

    @Data
    public static class RuleView {
        private Long ruleId;
        private String detectionId;
        private Long revisionId;
        private Integer revisionNo;
        private Integer version;
        private String statusCode;
        private String ruleNameSnapshot;
        private String inspectionItem;
        private String description;
        private String categoryCode;
        private String categoryNameSnapshot;
        private String severityCode;
        private String severityNameSnapshot;
        private Integer sortOrder;
        private String expectedResultRegex;
        private String thresholdDataType;
        private String thresholdOperator;
        private BigDecimal thresholdValue;
        private String thresholdUnit;
        private LocalDateTime publishedAt;
        private LocalDateTime disabledAt;
        private List<InspectionRuleRevisionService.CommandDraft> commands = List.of();
        private List<InspectionRuleRevisionService.ProductTypeDraft> productTypes = List.of();
    }

    public record DeviceSelection(InspectionRequestContext context, List<Long> deviceIds) { }
    public record PublishedSelection(InspectionRequestContext context, List<Long> revisionIds) { }
    public record SelectableRule(Long ruleId, String detectionId, Long revisionId, Integer revisionNo,
                                 String categoryCode, String categoryName, String inspectionItem,
                                 String severityCode, String severityName, Integer sortOrder,
                                 String productTypeCode, String productTypeName) { }

    public PageResult<RuleView> page(PageQuery request) {
        var query = BeanUtils.toBean(request, InspectionRuleRevisionPageQuery.class);
        query.setTenantId(TenantContextHolder.getRequiredTenantId());
        var page = revisions.selectPage(query);
        return new PageResult<>(page.getList().stream().map(this::summary).toList(), page.getTotal());
    }

    public RuleView detail(Long revisionId) {
        var revision = revisions.selectById(revisionId);
        if (revision == null || !Objects.equals(revision.getTenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new InspectionException(InspectionException.Reason.NOT_FOUND, "巡检规则修订不存在或不可见");
        }
        RuleView view = summary(revision);
        var query = new InspectionRuleChildrenQuery(revision.getTenantId(), Set.of(revisionId), null);
        view.setCommands(commands.selectListByRevisionIds(query).stream()
                .map(command -> new InspectionRuleRevisionService.CommandDraft(command.getStableCommandKey(),
                        command.getCommandContent(), command.getExecutionOrder(), command.getTimeoutSeconds(),
                        command.getContinueOnTimeout())).toList());
        view.setProductTypes(products.selectListByRevisionIds(query).stream()
                .map(product -> new InspectionRuleRevisionService.ProductTypeDraft(product.getProductTypeCode(),
                        product.getProductTypeNameSnapshot())).toList());
        return view;
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<RuleView> lockPublished(PublishedSelection query) {
        var views = query.revisionIds().stream().distinct().map(this::detail)
                .sorted(java.util.Comparator.comparing(RuleView::getRuleId).thenComparing(RuleView::getRevisionId)).toList();
        for (var view : views) {
            var locked = revisions.selectPublicationLockForUpdate(new InspectionRulePublicationLockQuery(
                    query.context().tenantId(), view.getRuleId(), view.getRevisionId()));
            InspectionException.require(locked != null && "PUBLISHED".equals(locked.targetRevisionStatusCode())
                    && Objects.equals(locked.currentPublishedRevisionId(), view.getRevisionId()), "巡检规则已换版或停用，请刷新后重新选择");
        }
        return views;
    }

    public List<SelectableRule> selectable(DeviceSelection request) {
        InspectionException.require(request.deviceIds() != null && !request.deviceIds().isEmpty()
                && request.deviceIds().size() <= 100, "请选择1至100台授权设备");
        var resolved = assetTypes.getAuthorizedDeviceProductType(new AuthorizedDeviceProductTypeQuery(
                request.context().actorId(), request.deviceIds()));
        var codes = resolved.stream().filter(item -> item.enabled() && item.productTypeCode() != null)
                .map(item -> item.productTypeCode()).collect(Collectors.toSet());
        return selectable.selectListSelectable(new SelectableInspectionRuleQuery(request.context().tenantId(), codes))
                .stream().map(row -> new SelectableRule(row.ruleId(), row.detectionId(), row.revisionId(), row.revisionNo(),
                        row.categoryCode(), row.categoryName(), row.inspectionItem(), row.severityCode(), row.severityName(),
                        row.sortOrder(), row.productTypeCode(), row.productTypeName())).toList();
    }

    private RuleView summary(InspectionRuleRevisionDO revision) {
        var view = BeanUtils.toBean(revision, RuleView.class);
        view.setRevisionId(revision.getId());
        var rule = rules.selectById(revision.getRuleId());
        if (rule == null || !Objects.equals(rule.getTenantId(), revision.getTenantId())) {
            throw new InspectionException(InspectionException.Reason.NOT_FOUND, "巡检规则身份不存在或不可见");
        }
        view.setDetectionId(rule.getDetectionId());
        return view;
    }
}
