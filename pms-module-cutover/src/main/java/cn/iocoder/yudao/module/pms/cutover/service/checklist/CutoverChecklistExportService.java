package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistExportResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistView;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistExportException.Code.CHECKLIST_VERSION_STALE;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistExportException.Code.INVALID_EXPORT_REQUEST;
import static cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistExportException.Code.NOT_VISIBLE_OR_NOT_FOUND;

public final class CutoverChecklistExportService {

    private static final Set<String> EXPORTABLE_STATUSES = Set.of("DRAFT", "SUBMITTED");

    private final CutoverChecklistApplicationService checklistService;
    private final CutoverChecklistWorkbookWriter workbookWriter;
    private final OperationAuditApi operationAuditApi;

    public CutoverChecklistExportService(CutoverChecklistApplicationService checklistService,
                                         CutoverChecklistWorkbookWriter workbookWriter,
                                         OperationAuditApi operationAuditApi) {
        this.checklistService = Objects.requireNonNull(checklistService);
        this.workbookWriter = Objects.requireNonNull(workbookWriter);
        this.operationAuditApi = Objects.requireNonNull(operationAuditApi);
    }

    public CutoverChecklistExportResult export(Long tenantId, Long actorId, Long taskId,
                                                Integer checklistVersion, String correlationId) {
        require(positive(tenantId) && positive(actorId) && positive(taskId)
                        && checklistVersion != null && checklistVersion > 0
                        && present(correlationId) && correlationId.equals(correlationId.trim())
                        && correlationId.length() <= 128,
                INVALID_EXPORT_REQUEST, "导出请求非法");
        CutoverChecklistView view;
        try {
            view = checklistService.getView(tenantId, actorId, taskId);
        } catch (CutoverChecklistException exception) {
            throw translate(exception);
        }
        require(Objects.equals(view.checklistVersion(), checklistVersion), CHECKLIST_VERSION_STALE,
                "清单版本已变化，请刷新后重试");
        require(EXPORTABLE_STATUSES.contains(view.status()), NOT_VISIBLE_OR_NOT_FOUND, "当前清单不可导出");
        CutoverChecklistWorkbookWriter.WorkbookContent workbook = workbookWriter.write(view);
        Map<String, Object> safeDetail = new LinkedHashMap<>();
        safeDetail.put("taskId", taskId);
        safeDetail.put("checklistVersion", checklistVersion);
        safeDetail.put("businessSurveyRowCount", workbook.businessSurveyRowCount());
        safeDetail.put("riskRowCount", workbook.riskRowCount());
        operationAuditApi.record(tenantId, actorId, correlationId, "CUTOVER_CHECKLIST_EXPORTED",
                "CutoverChecklist", String.valueOf(view.checklistId()), "SUCCESS", safeDetail);
        String fileName = "cutover-checklist-" + taskId + "-v" + checklistVersion + ".xlsx";
        return new CutoverChecklistExportResult(workbook.bytes(), fileName,
                workbook.businessSurveyRowCount(), workbook.riskRowCount());
    }

    private static CutoverChecklistExportException translate(CutoverChecklistException exception) {
        CutoverChecklistExportException.Code code = switch (exception.getCode()) {
            case NOT_FOUND, DATA_SCOPE_FORBIDDEN -> NOT_VISIBLE_OR_NOT_FOUND;
            case VERSION_CONFLICT -> CHECKLIST_VERSION_STALE;
            case INVALID_REQUEST -> INVALID_EXPORT_REQUEST;
            default -> CutoverChecklistExportException.Code.EXPORT_PROJECTION_INVALID;
        };
        return new CutoverChecklistExportException(code, exception.getMessage(), exception);
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean valid, CutoverChecklistExportException.Code code, String message) {
        if (!valid) {
            throw new CutoverChecklistExportException(code, message);
        }
    }
}
