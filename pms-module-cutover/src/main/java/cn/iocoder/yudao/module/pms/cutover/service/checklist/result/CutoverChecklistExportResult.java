package cn.iocoder.yudao.module.pms.cutover.service.checklist.result;

public record CutoverChecklistExportResult(byte[] content, String fileName,
                                           int businessSurveyRowCount, int riskRowCount) {
}
