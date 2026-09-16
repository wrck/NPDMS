package com.dp.plat.lowcode.controller;

import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.entity.LowCodeImportTask;
import com.dp.plat.lowcode.service.LowCodeDataImportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 低代码业务数据导入导出 Controller（缺口3）。
 *
 * <p>提供 Excel 模板下载、异步导入、同步导出与导入历史查询接口。
 * 导入/导出需对应权限，并记录操作日志。</p>
 */
@Tag(name = "低代码数据导入导出", description = "LowCode data import/export APIs")
@RestController
@RequestMapping("/api/lowcode/data-import-export")
@RequiredArgsConstructor
public class LowCodeDataImportExportController {

    private final LowCodeDataImportExportService dataImportExportService;

    @Operation(summary = "下载导入模板（Excel，列头为字段 label）")
    @GetMapping("/template")
    @PreAuthorize("@ss.hasPermission('lowcode:data:import')")
    public void downloadTemplate(@RequestParam String entityCode,
                                  HttpServletResponse response) {
        dataImportExportService.downloadImportTemplate(entityCode, response);
    }

    @Operation(summary = "上传 Excel 异步导入")
    @PostMapping("/import")
    @PreAuthorize("@ss.hasPermission('lowcode:data:import')")
    @OperLog(title = "低代码数据导入", businessType = 1)
    public Result<LowCodeImportTask> importExcel(@RequestParam String entityCode,
                                                   @RequestParam("file") MultipartFile file) {
        return Result.ok(dataImportExportService.importExcel(entityCode, file));
    }

    @Operation(summary = "导出当前列表数据为 Excel")
    @GetMapping("/export")
    @PreAuthorize("@ss.hasPermission('lowcode:data:export')")
    @OperLog(title = "低代码数据导出", businessType = 4)
    public void exportExcel(@RequestParam String entityCode,
                            @RequestParam(required = false) Map<String, Object> filters,
                            HttpServletResponse response) {
        dataImportExportService.exportExcel(entityCode, filters, response);
    }

    @Operation(summary = "查询导入任务历史")
    @GetMapping("/tasks")
    @PreAuthorize("@ss.hasPermission('lowcode:data:import')")
    public Result<List<LowCodeImportTask>> listTasks(
            @RequestParam(required = false) String entityCode) {
        return Result.ok(dataImportExportService.listImportTasks(entityCode));
    }

    @Operation(summary = "查询导入任务详情（含失败明细）")
    @GetMapping("/tasks/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:data:import')")
    public Result<LowCodeImportTask> getTask(@PathVariable Long id) {
        return Result.ok(dataImportExportService.getImportTask(id));
    }
}
