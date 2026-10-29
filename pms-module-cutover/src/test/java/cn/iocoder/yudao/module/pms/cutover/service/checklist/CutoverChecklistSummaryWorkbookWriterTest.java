package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.CutoverChecklistView;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CutoverChecklistSummaryWorkbookWriterTest {
    @Test void rejectsUnknownSourcesAndDoesNotExposeExternalPayload() throws Exception {
        var writer=new CutoverChecklistSummaryWorkbookWriter();
        assertThrows(CutoverChecklistExportException.class,()->writer.write(view(List.of(
                item("未知","RISK","UNKNOWN","{\"value\":true}",null,null)))));
        var result=writer.write(view(List.of(item("外部","RISK","EXTERNAL",
                "{\"value\":\"private-raw-response\",\"remark\":\"private-identity\"}","获权说明",null))));
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(result.bytes()))) {
            var row=workbook.getSheetAt(0).getRow(1);
            assertEquals("",row.getCell(1).getStringCellValue());
            assertEquals("获权说明",row.getCell(2).getStringCellValue());
        }
    }
    @Test void exportsAllApplicableItemsInThreeColumnsWithoutEvidenceIdsOrFormulaExecution() throws Exception {
        var result=new CutoverChecklistSummaryWorkbookWriter().write(view(List.of(
                item("检查A","RISK","DIRECT","{\"value\":{\"normal\":false,\"remark\":\"需整改\"}}",null,null),
                item("检查B","DUAL_MACHINE_CHECK","DIRECT","{\"value\":{\"executed\":true,\"remark\":\"核对完成\"}}",null,null),
                item("备份","RISK","MANUAL","{\"file\":{\"artifactId\":123,\"referenceKey\":\"private-reference\"}}","备份说明","private-reference"),
                item("规划","BUSINESS_SURVEY","DIRECT","{\"value\":[{\"deviceId\":999,\"location\":\"位置\"}]}",null,null),
                item("文字","BUSINESS_SURVEY","DIRECT","{\"value\":\"=1+1\"}",null,null),
                item("未填写","RISK",null,null,null,null))));
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(result.bytes()))) {
            assertEquals(1,workbook.getNumberOfSheets());
            var sheet=workbook.getSheetAt(0);
            assertEquals("调研清单",sheet.getSheetName());
            assertEquals(7,sheet.getPhysicalNumberOfRows());
            assertEquals(List.of("采集项名称","检查结果","反馈内容"),
                    java.util.stream.IntStream.range(0,3).mapToObj(i->sheet.getRow(0).getCell(i).getStringCellValue()).toList());
            var rows=new java.util.HashMap<String,List<String>>();
            for(int i=1;i<=6;i++) {
                var row=sheet.getRow(i); assertEquals(3,row.getPhysicalNumberOfCells());
                for(var cell:row) assertEquals(CellType.STRING,cell.getCellType());
                rows.put(row.getCell(0).getStringCellValue(),List.of(row.getCell(1).getStringCellValue(),row.getCell(2).getStringCellValue()));
            }
            assertEquals(List.of("否","需整改"),rows.get("检查A"));
            assertEquals(List.of("是","核对完成"),rows.get("检查B"));
            assertEquals(List.of("已上传","备份说明"),rows.get("备份"));
            assertEquals(List.of("已填写",""),rows.get("规划"));
            assertEquals(List.of("已填写","=1+1"),rows.get("文字"));
            assertEquals(List.of("",""),rows.get("未填写"));
            assertFalse(rows.toString().contains("private-reference"));
            assertFalse(rows.toString().contains("999"));
        }
    }
    @Test void collectionReceiptDoesNotImplyCompletedCheckAndFailedManualReferenceDoesNotImplyUpload() throws Exception {
        var result=new CutoverChecklistSummaryWorkbookWriter().write(view(List.of(
                item("采集未反馈","RISK","COLLECTION","{\"technicalStatus\":\"COMPLETED\",\"resultReferenceId\":123}",null,null),
                item("缺少附件","RISK","MANUAL","{\"file\":null}",null,null),
                item("采集已反馈","RISK","COLLECTION","{\"analysis\":{\"value\":{\"normal\":false,\"remark\":\"异常\"}}}",null,null))));
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(result.bytes()))) {
            var sheet=workbook.getSheetAt(0);
            for(int i=1;i<sheet.getPhysicalNumberOfRows();i++) {
                var row=sheet.getRow(i);
                assertEquals(row.getCell(0).getStringCellValue().equals("采集已反馈") ? "否" : "",row.getCell(1).getStringCellValue());
            }
        }
    }
    private CutoverChecklistView.Item item(String name,String type,String source,String answer,String feedback,String file) {
        return new CutoverChecklistView.Item(1L,name,type,name,null,"TEXT",null,"DIRECT",true,"CONFIGURATION",true,1,
                source==null ? null : CutoverChecklistView.CurrentResult.builder().resultVersion(1).resultSourceCode(source)
                        .answerSnapshot(answer).factDescription(feedback).manualEvidenceFileReference(file).build());
    }
    private CutoverChecklistView view(List<CutoverChecklistView.Item> items) {
        return new CutoverChecklistView(31L,"P3",7L,19L,41L,3L,2L,"DRAFT","hash","config","trace",null,items);
    }
}
