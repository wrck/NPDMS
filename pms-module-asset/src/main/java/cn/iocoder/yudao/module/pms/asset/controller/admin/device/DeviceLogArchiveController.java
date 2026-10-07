package cn.iocoder.yudao.module.pms.asset.controller.admin.device;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.pms.asset.service.configurationlog.DeviceLogArchiveService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/pms/device-configuration-log-archives")
public class DeviceLogArchiveController {
    private final DeviceLogArchiveService service;
    @PostMapping(value="/imports", consumes="application/octet-stream")
    @PreAuthorize("@ss.hasPermission('pms:device-log-archive:import')")
    @ApiAccessLog(requestEnable=false, responseEnable=false, operateModule="设备配置日志", operateName="导入日志")
    public CommonResult<DeviceLogArchiveService.ImportView> upload(@RequestParam Long projectId, @RequestParam String operationId,
                                                                  @RequestParam String fileName, HttpServletRequest request) throws IOException {
        int maximum=20*1024*1024;
        if (request.getContentLengthLong()>maximum) throw new IllegalArgumentException("日志文件须为 1 字节至 20 MiB");
        try (var stream = request.getInputStream()) {
            byte[] bytes=stream.readNBytes(maximum+1);
            if (bytes.length<1 || bytes.length>maximum) throw new IllegalArgumentException("日志文件须为 1 字节至 20 MiB");
            return success(service.upload(projectId, operationId, fileName, bytes));
        }
    }
    @GetMapping("/latest") @PreAuthorize("@ss.hasPermission('pms:device-log-archive:query')")
    public CommonResult<List<DeviceLogArchiveService.DeviceView>> latest(@RequestParam Long projectId) { return success(service.latest(projectId)); }
    @GetMapping("/imports/{id}") @PreAuthorize("@ss.hasPermission('pms:device-log-archive:query')")
    public CommonResult<DeviceLogArchiveService.ImportView> getImport(@PathVariable Long id) { return success(service.getImport(id)); }
    @GetMapping("/history") @PreAuthorize("@ss.hasPermission('pms:device-log-archive:query')")
    public CommonResult<List<DeviceLogArchiveService.FileView>> history(@RequestParam Long projectId, @RequestParam Long deviceId) {
        return success(service.history(projectId,deviceId));
    }
    @GetMapping("/{fileId}/download") @PreAuthorize("@ss.hasPermission('pms:device-log-archive:download')")
    public CommonResult<String> download(@PathVariable Long fileId) { return success(service.download(fileId)); }
}
