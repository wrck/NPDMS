package cn.iocoder.yudao.module.pms.commerce.controller;

import cn.iocoder.yudao.module.pms.commerce.controller.business.scope.ProjectEquipmentExportController;
import cn.iocoder.yudao.module.pms.commerce.service.scope.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectEquipmentExportWorkbookTest {
    @Test void worksheetKeepsUnknownQuantityBlankAndZeroNumeric() throws Exception {
        var service=mock(ProjectEquipmentExportService.class);var row=new ProjectEquipmentExportRow();
        row.setNo(1);row.setProductCode("02010005");row.setOrderNo("001234567890123456789");row.setOpenQty(BigDecimal.ZERO);
        when(service.read(10L)).thenReturn(List.of(row));var response=new MockHttpServletResponse() {
            @Override public jakarta.servlet.ServletOutputStream getOutputStream() {
                assertNotNull(getContentType(),"Excel media type must be set before streaming starts");
                assertTrue(getHeader("Content-Disposition").startsWith("attachment;"));
                return super.getOutputStream();
            }
        };
        new ProjectEquipmentExportController(service).export(10L,response);
        try(var book=WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=book.getSheetAt(0);assertEquals(8,sheet.getRow(0).getLastCellNum());var data=sheet.getRow(1);
            assertEquals("02010005",data.getCell(1).getStringCellValue());assertEquals("001234567890123456789",data.getCell(4).getStringCellValue());
            assertEquals(CellType.BLANK,data.getCell(6,Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).getCellType());assertEquals(0,data.getCell(7).getNumericCellValue());
        }
    }
}
