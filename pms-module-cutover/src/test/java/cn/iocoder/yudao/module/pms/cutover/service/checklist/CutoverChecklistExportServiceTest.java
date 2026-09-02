package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistExportResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistView;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CutoverChecklistExportServiceTest {

    @Test
    void exportsAuthorizedViewAndAuditsOnlySafeCounts() {
        CutoverChecklistApplicationService checklistService = mock(CutoverChecklistApplicationService.class);
        OperationAuditApi auditApi = mock(OperationAuditApi.class);
        when(checklistService.getView(9L, 12L, 31L)).thenReturn(new CutoverChecklistView(
                31L, "P3", 7, 19L, 41L, 3, 2, "SUBMITTED", "hash", "config", "trace", null,
                List.of(new CutoverChecklistView.Item(51L, "survey-1", "BUSINESS_SURVEY", "survey",
                        null, "TEXT", null, "DIRECT", true, "CONFIGURATION", true, 1,
                        new CutoverChecklistView.CurrentResult(1, "DIRECT", "answer", null,
                                null, null, null, null, null)))));
        CutoverChecklistExportService service = new CutoverChecklistExportService(checklistService,
                new CutoverChecklistWorkbookWriter(), auditApi);

        CutoverChecklistExportResult result = service.export(9L, 12L, 31L, 3, "corr-export-1");

        assertThat(result.fileName()).isEqualTo("cutover-checklist-31-v3.xlsx");
        assertThat(result.content()).isNotEmpty();
        assertThat(result.businessSurveyRowCount()).isOne();
        assertThat(result.riskRowCount()).isZero();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, ?>> detail = ArgumentCaptor.forClass(Map.class);
        verify(auditApi).record(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq("corr-export-1"),
                org.mockito.ArgumentMatchers.eq("CUTOVER_CHECKLIST_EXPORTED"),
                org.mockito.ArgumentMatchers.eq("CutoverChecklist"), org.mockito.ArgumentMatchers.eq("41"),
                org.mockito.ArgumentMatchers.eq("SUCCESS"), detail.capture());
        assertThat(detail.getValue().keySet()).containsExactlyInAnyOrder(
                "taskId", "checklistVersion", "businessSurveyRowCount", "riskRowCount");
        assertThat(detail.getValue().get("taskId")).isEqualTo(31L);
        assertThat(detail.getValue().get("checklistVersion")).isEqualTo(3);
        assertThat(detail.getValue().get("businessSurveyRowCount")).isEqualTo(1);
        assertThat(detail.getValue().get("riskRowCount")).isEqualTo(0);
    }
}
