package cn.iocoder.yudao.module.pms.platform.controller.admin.result;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultFormationPort;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 统一业务结果失效入口：失效走结果端口（同事务记录证据历史影响并追加 INVALIDATED 事件），
 * 不直接写存储、不回滚历史判断。
 */
@RestController
@RequestMapping("/api/v1/pms/business-results")
@Tag(name = "管理后台 - PMS 统一业务结果")
@Validated
@RequiredArgsConstructor
public class BusinessResultController {

    private final BusinessResultFormationPort formationPort;

    @Data
    public static class ResultInvalidateReqVO {

        @NotBlank
        private String resultType;

        @NotBlank
        private String ownerModule;

        @NotBlank
        private String entityType;

        @NotNull
        private Long entityId;

        @NotBlank
        private String formationBasis;
    }

    @PostMapping("/invalidate")
    @PreAuthorize("@ss.hasPermission('pms:business-model:operate')")
    public CommonResult<Boolean> invalidate(@Valid @RequestBody ResultInvalidateReqVO reqVO) {
        formationPort.invalidate(reqVO.getResultType(),
                new EntityRef(TenantContextHolder.getRequiredTenantId(), reqVO.getOwnerModule(),
                        reqVO.getEntityType(), reqVO.getEntityId()),
                reqVO.getFormationBasis());
        return success(true);
    }
}
