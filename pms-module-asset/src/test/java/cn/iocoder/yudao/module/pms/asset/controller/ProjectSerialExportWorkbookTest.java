package cn.iocoder.yudao.module.pms.asset.controller;

import cn.iocoder.yudao.module.pms.asset.controller.business.device.ProjectSerialExportController;
import cn.iocoder.yudao.module.pms.asset.service.device.ProjectSerialExportService;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.ProjectSerialExportRow;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import java.io.ByteArrayInputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectSerialExportWorkbookTest {
    @Test void workbookPreservesIdentifiersAndOnlyTheVisibleColumns() throws Exception {
        var service=mock(ProjectSerialExportService.class);var row=new ProjectSerialExportRow();
        row.setDeviceId(2115000000000000001L);row.setSn("001234567890123456789");row.setProductCode("02010005");row.setProductName("=1+1");
        row.setLocation("地址 / 站点");row.setLocationSnapshot("{\"notExported\":true}");row.setExtendedCount(0L);
        when(service.read(any())).thenReturn(List.of(row));var response=new MockHttpServletResponse() {
            @Override public jakarta.servlet.ServletOutputStream getOutputStream() {
                assertNotNull(getContentType(),"Excel media type must be set before streaming starts");
                assertTrue(getHeader("Content-Disposition").startsWith("attachment;"));
                return super.getOutputStream();
            }
        };
        new ProjectSerialExportController(service).export(10L,new ProjectSerialExportController.Filters(null,null,null),response);
        try(var book=WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=book.getSheetAt(0);assertEquals(17,sheet.getRow(0).getLastCellNum());var data=sheet.getRow(1);
            assertEquals(CellType.STRING,data.getCell(0).getCellType());assertEquals("001234567890123456789",data.getCell(0).getStringCellValue());
            assertEquals("02010005",data.getCell(1).getStringCellValue());assertEquals(CellType.STRING,data.getCell(2).getCellType());
            assertEquals("地址 / 站点",data.getCell(3).getStringCellValue());assertEquals(1,sheet.getLastRowNum());
        }
    }
    @Test void sourceFailureLeavesNoPartialWorkbook() {
        var service=mock(ProjectSerialExportService.class);when(service.read(any())).thenThrow(new IllegalStateException("source failed"));
        var response=new MockHttpServletResponse();
        assertThrows(IllegalStateException.class,()->new ProjectSerialExportController(service).export(10L,new ProjectSerialExportController.Filters(null,null,null),response));
        assertEquals(0,response.getContentAsByteArray().length);
    }
}
