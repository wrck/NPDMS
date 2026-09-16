package com.dp.plat.lowcode.controller;

import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.dto.AppSourceManifest;
import com.dp.plat.lowcode.service.LowCodeAppSourceExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 应用源码导出控制器（批次5-T10）。
 *
 * <p>借鉴网易轻舟源码导出 — 无黑盒引擎。将低代码应用配置打包为
 * 可独立部署的源码 ZIP（JSON + DDL + POM + README）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/lowcode/app-source")
@RequiredArgsConstructor
public class LowCodeAppSourceExportController {

    private final LowCodeAppSourceExportService exportService;

    @Operation(summary = "预览导出清单（不生成 ZIP）")
    @GetMapping("/manifest")
    @PreAuthorize("@ss.hasPermission('lowcode:app-source:export')")
    public Result<AppSourceManifest> previewManifest(
            @Parameter(description = "业务类型（应用分组键），为空时导出全部")
            @RequestParam(required = false) String bizType) {
        return Result.ok(exportService.previewManifest(bizType));
    }

    @Operation(summary = "导出应用源码（ZIP）")
    @GetMapping("/export")
    @PreAuthorize("@ss.hasPermission('lowcode:app-source:export')")
    @OperLog(title = "低代码应用源码导出", businessType = 4)
    public ResponseEntity<byte[]> exportAsZip(
            @Parameter(description = "业务类型（应用分组键），为空时导出全部")
            @RequestParam(required = false) String bizType) {
        byte[] zip = exportService.exportAsZip(bizType);
        String fileName = "lowcode-app" + (bizType == null ? "-all" : "-" + bizType) + ".zip";
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encodedName)
                .header("Content-Type", "application/zip")
                .body(zip);
    }

    @Operation(summary = "查询可导出的应用列表（按 bizType 去重）")
    @GetMapping("/apps")
    @PreAuthorize("@ss.hasPermission('lowcode:app-source:export')")
    public Result<List<String>> listExportableApps() {
        return Result.ok(exportService.listExportableApps());
    }
}
