package cn.iocoder.yudao.module.pms.platform.controller.admin.migration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 迁移工具台的契约诊断映射：并发/幂等/计数对不上映射为 409，未找到映射为 404，
 * 租户上下文不一致映射为 403；其余契约诊断返回 400 并保留定位信息。
 */
@RestControllerAdvice(basePackages = "cn.iocoder.yudao.module.pms.platform.controller.admin.migration")
public class MigrationConsoleContractAdvice {

    @ExceptionHandler(PlatformMigrationEvidenceException.class)
    public ResponseEntity<CommonResult<?>> contract(PlatformMigrationEvidenceException exception) {
        HttpStatus status;
        switch (exception.getCode()) {
            case IDEMPOTENCY_CONFLICT, IDEMPOTENCY_IN_PROGRESS, BATCH_STATE_CONFLICT,
                    BATCH_SOURCE_IDENTITY_MISMATCH, SOURCE_RECORD_CONFLICT, SOURCE_ALREADY_CLASSIFIED,
                    MAPPING_CONFLICT, ISSUE_CONFLICT, ISSUE_STATE_CONFLICT, COUNT_MISMATCH ->
                    status = HttpStatus.CONFLICT;
            case BATCH_NOT_FOUND, SOURCE_NOT_FOUND, ISSUE_NOT_FOUND -> status = HttpStatus.NOT_FOUND;
            case TENANT_CONTEXT_MISMATCH -> status = HttpStatus.FORBIDDEN;
            default -> status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status)
                .body(CommonResult.error(status.value(), exception.getCode() + ": " + exception.getMessage()));
    }
}
