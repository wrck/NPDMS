package cn.iocoder.yudao.module.pms.asset.controller.business.device;

import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.ProjectSerialExportRow;
import cn.iocoder.yudao.module.pms.asset.service.device.ProjectSerialExportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/pms/projects/{projectId}/serials/export")
@Validated
@RequiredArgsConstructor
public class ProjectSerialExportController {
    private final ProjectSerialExportService service;
    public record Filters(@Size(max=128) String sn, @Size(max=255) String name, @Size(max=64) String productCode) { }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:project-device:export') and @ss.hasPermission('pms:device:query')")
    @ApiAccessLog(operateType=OperateTypeEnum.EXPORT)
    public void export(@PathVariable @Positive Long projectId, @jakarta.validation.Valid Filters filters,
                       HttpServletResponse response) throws IOException {
        var rows=service.read(new ProjectSerialExportService.Request(projectId,filters.sn(),filters.name(),filters.productCode()));
        var filename="项目"+projectId+"-序列号详情.xlsx";
        response.setHeader("Cache-Control","no-store");
        response.setContentType("application/vnd.ms-excel;charset=UTF-8");
        response.setHeader("Content-Disposition","attachment;filename="+HttpUtils.encodeUtf8(filename));
        ExcelUtils.write(response,filename,"序列号详情",ProjectSerialExportRow.class,rows);
    }
}
