package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Generates the small immutable ASCII PDF persisted as the satisfaction decision document. */
final class SatisfactionResultDocumentRenderer {

    private SatisfactionResultDocumentRenderer() {
    }

    static byte[] render(Long resultId, Long taskId, Long questionnaireId, Long responseId,
                         String score, String threshold, boolean passed, String ruleVersion) {
        String line = "Result " + resultId + "  Task " + taskId + "  Questionnaire " + questionnaireId
                + "  Response " + responseId + "  Score " + score + "  Threshold " + threshold
                + "  Passed " + passed + "  Rule " + safe(ruleVersion);
        String stream = "BT /F1 10 Tf 40 760 Td (" + escape(line) + ") Tj ET";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                "<< /Length " + stream.getBytes(StandardCharsets.US_ASCII).length + " >>\nstream\n" + stream + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.US_ASCII).length);
            pdf.append(index + 1).append(" 0 obj\n").append(objects.get(index)).append("\nendobj\n");
        }
        int xref = pdf.toString().getBytes(StandardCharsets.US_ASCII).length;
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        offsets.forEach(offset -> pdf.append(String.format("%010d 00000 n \n", offset)));
        pdf.append("trailer << /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
