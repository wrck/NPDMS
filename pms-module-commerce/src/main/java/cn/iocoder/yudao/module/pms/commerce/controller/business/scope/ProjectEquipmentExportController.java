package cn.iocoder.yudao.module.pms.commerce.controller.business.scope;

import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum;
import cn.iocoder.yudao.module.pms.commerce.service.scope.ProjectEquipmentExportRow;
import cn.iocoder.yudao.module.pms.commerce.service.scope.ProjectEquipmentExportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/pms/projects/{projectId}/equipment/export")
@Validated
@RequiredArgsConstructor
public class ProjectEquipmentExportController {
    private final ProjectEquipmentExportService service;
    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:project-device:export') and @ss.hasPermission('pms:device:query')")
    @ApiAccessLog(operateType=OperateTypeEnum.EXPORT)
    public void export(@PathVariable @Positive Long projectId,HttpServletResponse response) throws IOException {
        var rows=service.read(projectId);
        var filename="项目"+projectId+"-设备清单.xlsx";
        response.setHeader("Cache-Control","no-store");
        response.setContentType("application/vnd.ms-excel;charset=UTF-8");
        response.setHeader("Content-Disposition","attachment;filename="+HttpUtils.encodeUtf8(filename));
        ExcelUtils.write(response,filename,"设备清单",ProjectEquipmentExportRow.class,rows);
    }
}
