package cn.iocoder.yudao.module.pms.platform.controller.admin.file;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileContextReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileReferenceRespVO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.ExactFileReferenceQuery;
import cn.iocoder.yudao.module.pms.platform.service.file.FileCurrentReferenceQueryService;
import cn.iocoder.yudao.module.pms.platform.service.file.FileQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;

/** Read-only recovery of an already bound file; existing file endpoints keep their contract. */
@RestController
@RequestMapping("/api/v1/pms/file-references")
@Validated
@RequiredArgsConstructor
public class FileCurrentReferenceController {
    private final FileCurrentReferenceQueryService queries;
    private final Environment environment;

    @GetMapping("/current")
    @PreAuthorize("@ss.hasPermission('pms:file:query')")
    public CommonResult<FileReferenceRespVO> current(@Valid @ModelAttribute FileContextReqVO request) {
        return withTrustedTenant(() -> {
            var actor = new FileQueryService.Actor(TenantContextHolder.getRequiredTenantId(),
                    SecurityFrameworkUtils.getLoginUserId());
            var key = new ExactFileReferenceQuery(actor.tenantId(), request.getOwnerContext(), request.getObjectType(),
                    request.getObjectId(), request.getPurposeCode(), request.getReferenceKey());
            return success(queries.resolve(key, actor));
        });
    }

    private <T> T withTrustedTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) throw exception(FILE_SCOPE_FORBIDDEN);
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
