package cn.iocoder.yudao.module.pms.platform.controller.admin.export;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.api.export.ExportTaskApi;
import cn.iocoder.yudao.module.pms.platform.api.export.ExportTaskFact;
import cn.iocoder.yudao.module.pms.platform.api.export.ExportTaskFactQuery;
import cn.iocoder.yudao.module.pms.platform.api.export.ExportTaskRetryCommand;
import cn.iocoder.yudao.module.pms.platform.controller.admin.export.vo.ExportTaskRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.export.vo.ExportTaskRetryReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileAccessTicketRespVO;
import cn.iocoder.yudao.module.pms.platform.service.export.ExportTaskAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;

@RestController
@RequestMapping("/api/v1/pms/export-tasks")
@RequiredArgsConstructor
public class ExportTaskController {

    private final ExportTaskApi exportTaskApi;
    private final ExportTaskAccessService accessService;
    private final Environment environment;

    @GetMapping("/{id}")
    public CommonResult<ExportTaskRespVO> get(@PathVariable("id") Long id) {
        return withTenant(() -> success(toResp(exportTaskApi.getFact(new ExportTaskFactQuery(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), id)))));
    }

    @PostMapping("/{id}/actions/retry")
    public CommonResult<ExportTaskRespVO> retry(@PathVariable("id") Long id,
                                                @Valid @RequestBody ExportTaskRetryReqVO request) {
        return withTenant(() -> success(toResp(exportTaskApi.retry(new ExportTaskRetryCommand(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), id,
                request.expectedVersion())))));
    }

    @PostMapping("/{id}/access-ticket")
    public CommonResult<FileAccessTicketRespVO> createAccessTicket(@PathVariable("id") Long id) {
        return withTenant(() -> success(accessService.createTicket(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), id)));
    }

    private ExportTaskRespVO toResp(ExportTaskFact fact) {
        return new ExportTaskRespVO(fact.taskId(), fact.ownerContext(), fact.exportType(), fact.status(),
                fact.failureRetryable(), fact.retryCount(), fact.version(), fact.resultCount(), fact.artifactId(),
                fact.fileVersionNo(), fact.expiresAt());
    }

    private <T> T withTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
