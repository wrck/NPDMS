package cn.iocoder.yudao.module.pms.platform.controller.admin.export;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
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
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/export-tasks")
@RequiredArgsConstructor
public class ExportTaskController {

    private final ExportTaskApi exportTaskApi;
    private final ExportTaskAccessService accessService;

    @GetMapping("/{id}")
    public CommonResult<ExportTaskRespVO> get(@PathVariable("id") Long id) {
        return success(toResp(exportTaskApi.getFact(new ExportTaskFactQuery(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), id))));
    }

    @PostMapping("/{id}/actions/retry")
    public CommonResult<ExportTaskRespVO> retry(@PathVariable("id") Long id,
                                                @Valid @RequestBody ExportTaskRetryReqVO request) {
        return success(toResp(exportTaskApi.retry(new ExportTaskRetryCommand(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), id,
                request.expectedVersion()))));
    }

    @PostMapping("/{id}/access-ticket")
    public CommonResult<FileAccessTicketRespVO> createAccessTicket(@PathVariable("id") Long id) {
        return success(accessService.createTicket(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), id));
    }

    private ExportTaskRespVO toResp(ExportTaskFact fact) {
        return new ExportTaskRespVO(fact.taskId(), fact.ownerContext(), fact.exportType(), fact.status(),
                fact.failureRetryable(), fact.retryCount(), fact.version(), fact.resultCount(), fact.artifactId(),
                fact.fileVersionNo(), fact.expiresAt());
    }
}
