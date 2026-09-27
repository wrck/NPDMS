package cn.iocoder.yudao.module.pms.platform.controller.admin.subscription;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.ResultSelectionPolicy;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.subscription.ResultSubscriptionPort;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.result.PlatformResultSubscriptionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.result.query.ResultSubscriptionPageQuery;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.result.ResultSubscriptionService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 独立结果订阅管理入口：建立订阅、存量补扫、判断结果查询。
 * 订阅、补扫与采纳证据归本能力所有；执行后端只负责调度、等待与恢复。
 */
@RestController
@RequestMapping("/api/v1/pms/result-subscriptions")
@Tag(name = "管理后台 - PMS 结果订阅")
@Validated
@RequiredArgsConstructor
public class ResultSubscriptionController {

    private final ResultSubscriptionService subscriptionService;

    /** 创建请求：选择策略以既有五类语义声明；ALL_EXPECTED 必须携带期望对象集合。 */
    @Data
    public static class SubscriptionCreateReqVO {

        @NotBlank
        private String subscriptionCode;

        @NotBlank
        private String subscriberKind;

        @NotBlank
        private String subscriberNodeKey;

        @NotBlank
        private String resultType;

        @NotBlank
        private String ownerModule;

        @NotBlank
        private String entityType;

        @NotNull
        private String acquisition;

        @NotNull
        private String selection;

        private String validity;

        private String pinnedResultId;

        private List<Long> expectedObjectIds;

        private Long roundNo;
    }

    @Data
    public static class SubscriptionBackfillReqVO {

        @NotNull
        private Long throughSequence;
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:result-subscription:operate')")
    public CommonResult<ResultSubscriptionPort.ResultSubscriptionRecord> create(
            @Valid @RequestBody SubscriptionCreateReqVO reqVO) {
        ResultSelectionPolicy.Validity validity = reqVO.getValidity() == null || reqVO.getValidity().isBlank()
                ? ResultSelectionPolicy.Validity.ANY : ResultSelectionPolicy.Validity.valueOf(reqVO.getValidity());
        var policy = new ResultSelectionPolicy(ResultSelectionPolicy.Acquisition.valueOf(reqVO.getAcquisition()),
                ResultSelectionPolicy.Selection.valueOf(reqVO.getSelection()), validity,
                reqVO.getPinnedResultId(), reqVO.getExpectedObjectIds() == null
                ? List.of() : reqVO.getExpectedObjectIds());
        var command = new ResultSubscriptionPort.CreateCommand(reqVO.getSubscriptionCode(),
                reqVO.getSubscriberKind(), reqVO.getSubscriberNodeKey(), reqVO.getResultType(),
                reqVO.getOwnerModule(), reqVO.getEntityType(), policy,
                reqVO.getRoundNo() == null ? 1L : reqVO.getRoundNo(), null, null, null);
        return success(subscriptionService.create(command, actor()));
    }

    @PostMapping("/{id}/backfill")
    @PreAuthorize("@ss.hasPermission('pms:result-subscription:operate')")
    public CommonResult<ResultSubscriptionPort.ResultSubscriptionDecision> backfill(
            @PathVariable Long id, @Valid @RequestBody SubscriptionBackfillReqVO reqVO) {
        return success(subscriptionService.backfill(id, reqVO.getThroughSequence(), actor()));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('pms:result-subscription:query')")
    public CommonResult<PageResult<PlatformResultSubscriptionDO>> page(
            @RequestParam(required = false) String subscriptionCode,
            @RequestParam(required = false) String resultType,
            @RequestParam(required = false) String ownerModule,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String status,
            ResultSubscriptionPageQuery pageQuery) {
        pageQuery.setSubscriptionCode(subscriptionCode);
        pageQuery.setResultType(resultType);
        pageQuery.setOwnerModule(ownerModule);
        pageQuery.setEntityType(entityType);
        pageQuery.setStatus(status);
        return success(subscriptionService.page(pageQuery));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:result-subscription:query')")
    public CommonResult<ResultSubscriptionPort.ResultSubscriptionRecord> get(@PathVariable Long id) {
        return success(subscriptionService.get(id, actor()));
    }

    @GetMapping("/{id}/last-decision")
    @PreAuthorize("@ss.hasPermission('pms:result-subscription:query')")
    public CommonResult<ResultSubscriptionPort.ResultSubscriptionDecision> lastDecision(@PathVariable Long id) {
        return success(subscriptionService.lastDecision(id, actor()));
    }

    private EntityActor actor() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            throw new BusinessContractException("CALLER_UNRESOLVED", "缺少可信操作者身份");
        }
        return new EntityActor(TenantContextHolder.getRequiredTenantId(), userId, null);
    }
}
