package cn.iocoder.yudao.module.pms.cutover.controller.admin.dashboard;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.CutoverDashboardQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.view.CutoverDashboardKpiView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** F-CUT-007 REST candidate; production registration waits for all Owner providers. */
@RequestMapping("/api/v1/pms/cutover-dashboard")
@ResponseBody
public class CutoverDashboardController {
    private static final int OWNER_PROVIDER_ERROR = 1_011_007_100;
    private static final int OWNER_DATA_ERROR = 1_011_007_101;
    private static final int PERMISSION_ERROR = 1_011_007_102;

    private final CutoverDashboardQueryService queryService;
    private final CutoverDashboardRequestContext requestContext;

    public CutoverDashboardController(CutoverDashboardQueryService queryService,
                                      CutoverDashboardRequestContext requestContext) {
        this.queryService = Objects.requireNonNull(queryService);
        this.requestContext = Objects.requireNonNull(requestContext);
    }

    @GetMapping("/kpis")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<CutoverDashboardKpiData> kpis() {
        var trusted = requestContext.current();
        CutoverDashboardKpiView view = queryService.inspect(
                trusted.tenantId(), trusted.actorId(), trusted.permissions());
        return success(new CutoverDashboardKpiData(view.todoCount(), view.archivedCount(), view.approvingCount(),
                view.rejectedPendingModificationCount(), view.generatedAt()));
    }

    @ExceptionHandler(CutoverDashboardOwnerFactException.class)
    public ResponseEntity<CommonResult<CutoverDashboardErrorData>> handleOwner(
            CutoverDashboardOwnerFactException exception) {
        if ("OWNER_PROVIDER_UNAVAILABLE".equals(exception.category())) {
            return error(503, OWNER_PROVIDER_ERROR, exception,
                    "RETRY_AFTER_OWNER_RECOVERY");
        }
        return error(500, OWNER_DATA_ERROR, exception, "CONTACT_SUPPORT");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResult<CutoverDashboardErrorData>> handlePermission(
            AccessDeniedException exception) {
        CommonResult<CutoverDashboardErrorData> body = CommonResult.error(PERMISSION_ERROR, exception.getMessage());
        body.setData(new CutoverDashboardErrorData("FUNCTION_PERMISSION_DENIED",
                "CUTOVER_DASHBOARD_QUERY_FORBIDDEN", "REQUEST_PERMISSION", null));
        return ResponseEntity.status(403).body(body);
    }

    private static ResponseEntity<CommonResult<CutoverDashboardErrorData>> error(
            int status, int errorCode, CutoverDashboardOwnerFactException exception, String recoveryAction) {
        CommonResult<CutoverDashboardErrorData> body = CommonResult.error(errorCode, exception.getMessage());
        body.setData(new CutoverDashboardErrorData(exception.category(), exception.reasonCode(),
                recoveryAction, exception.ownerContext()));
        return ResponseEntity.status(status).body(body);
    }
}
