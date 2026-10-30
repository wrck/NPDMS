package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistView;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/** TF-059已确认的三列清单；旧十列Writer保留，不改历史文件。 */
public final class CutoverChecklistSummaryWorkbookWriter {
    public CutoverChecklistWorkbookWriter.WorkbookContent write(CutoverChecklistView view) {
        if (view == null || view.items() == null) throw invalid("授权清单投影为空");
        var items = view.items().stream().filter(item -> item != null && item.applicable())
                .sorted(Comparator.comparingInt((CutoverChecklistView.Item item) -> item.sortOrder() == null ? 0 : item.sortOrder())
                        .thenComparing(CutoverChecklistView.Item::stableItemKey)).toList();
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("调研清单");
            var headers = List.of("采集项名称", "检查结果", "反馈内容");
            var header = sheet.createRow(0);
            var headerStyle = workbook.createCellStyle();
            var font = workbook.createFont(); font.setBold(true); headerStyle.setFont(font);
            var bodyStyle = workbook.createCellStyle(); bodyStyle.setWrapText(true);
            bodyStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);
            for (int i=0;i<headers.size();i++) {
                var cell=header.createCell(i,CellType.STRING);cell.setCellValue(headers.get(i));cell.setCellStyle(headerStyle);
            }
            sheet.setColumnWidth(0, 34 * 256); sheet.setColumnWidth(1, 16 * 256); sheet.setColumnWidth(2, 80 * 256);
            sheet.createFreezePane(0, 1);
            for (int i=0;i<items.size();i++) {
                var item=items.get(i);
                if (item.itemName()==null || item.itemName().isBlank()) throw invalid("授权清单项名称缺失");
                var display=display(item.currentResult());
                var row=sheet.createRow(i+1);
                var values=List.of(item.itemName(),display.result(),display.feedback());
                for(int column=0;column<values.size();column++) {
                    var cell=row.createCell(column,CellType.STRING);cell.setCellValue(values.get(column));cell.setCellStyle(bodyStyle);
                }
            }
            workbook.write(output);
            return new CutoverChecklistWorkbookWriter.WorkbookContent(output.toByteArray(),
                    (int)items.stream().filter(item->"BUSINESS_SURVEY".equals(item.itemTypeCode())).count(),
                    (int)items.stream().filter(item->"RISK".equals(item.itemTypeCode())).count());
        } catch(IOException ex) { throw invalid("授权清单工作簿生成失败"); }
    }

    private Display display(CutoverChecklistView.CurrentResult result) {
        if(result==null) return new Display("", "");
        if(result.resultSourceCode()==null || !List.of("DIRECT", "MANUAL", "COLLECTION", "EXTERNAL").contains(result.resultSourceCode()))
            throw invalid("授权清单结果来源无效");
        var feedback=new LinkedHashSet<String>();
        add(feedback,result.factDescription());
        if("EXTERNAL".equals(result.resultSourceCode())) return new Display("",String.join("\n",feedback));
        JsonNode snapshot;
        try { snapshot=result.answerSnapshot()==null ? null : JsonUtils.getObjectMapper().readTree(result.answerSnapshot()); }
        catch(RuntimeException ex) { snapshot=null; }
        var value=snapshot;
        if(value!=null && value.has("analysis")) value=value.get("analysis");
        if(value!=null && value.has("value")) value=value.get("value");
        if(value!=null && value.isObject()) {
            for(String field:List.of("remark","feedback","notes","description")) {
                var text=value.get(field); if(text!=null && text.isTextual()) add(feedback,text.asText());
            }
        }
        String check=booleanLabel(value);
        if(check.isEmpty() && value!=null && value.isObject()) {
            var checks=new LinkedHashSet<String>();
            for(String field:List.of("normal","executed","passed")) {
                var label=booleanLabel(value.get(field));if(!label.isEmpty()) checks.add(label);
            }
            if(checks.size()==1) check=checks.iterator().next();
            else if(checks.size()>1) {
                // Do not invent an overall pass/fail when distinct checks disagree.
                for(String field:List.of("normal","executed","passed")) {
                    var label=booleanLabel(value.get(field));if(!label.isEmpty()) add(feedback,
                            ("passed".equals(field) ? "是否通过" : "检查是否正常")+"："+label);
                }
                check="已填写";
            }
        }
        if(!check.isEmpty()) return new Display(check,String.join("\n",feedback));
        if("MANUAL".equals(result.resultSourceCode())) {
            return new Display(result.manualEvidenceFileReference()==null ? "" : "已上传",String.join("\n",feedback));
        }
        if("COLLECTION".equals(result.resultSourceCode()) && (snapshot==null || !snapshot.has("analysis")))
            return new Display("",String.join("\n",feedback));
        if(value!=null && value.isTextual()) add(feedback,value.asText());
        else if(snapshot==null && "DIRECT".equals(result.resultSourceCode())) add(feedback,result.answerSnapshot());
        boolean filled=value!=null && !value.isNull() && (!(value.isObject() || value.isArray()) || !value.isEmpty())
                && (!value.isTextual() || !value.asText().isBlank());
        if(snapshot==null && result.answerSnapshot()!=null && !result.answerSnapshot().isBlank()) filled="DIRECT".equals(result.resultSourceCode());
        return new Display(filled ? "已填写" : "",String.join("\n",feedback));
    }

    private String booleanLabel(JsonNode value) {
        if(value==null) return "";
        if(value.isBoolean()) return value.asBoolean() ? "是" : "否";
        if(value.isTextual()) return switch(value.asText()) { case "YES", "是" -> "是"; case "NO", "否" -> "否"; default -> ""; };
        return "";
    }
    private void add(LinkedHashSet<String> values,String text) { if(text!=null && !text.isBlank()) values.add(text); }
    private record Display(String result,String feedback) { }
    private CutoverChecklistExportException invalid(String message) {
        return new CutoverChecklistExportException(CutoverChecklistExportException.Code.EXPORT_PROJECTION_INVALID,message);
    }
}
