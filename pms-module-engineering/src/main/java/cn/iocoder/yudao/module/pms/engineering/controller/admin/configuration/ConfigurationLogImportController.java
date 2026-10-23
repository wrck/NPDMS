package cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.engineering.service.configuration.ConfigurationLogImportService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/configuration-log-imports")
public class ConfigurationLogImportController {
    private final ConfigurationLogImportService imports;

    @ExceptionHandler({BusinessContractException.class, IllegalArgumentException.class})
    public CommonResult<?> rejected(RuntimeException rejection) {
        return CommonResult.error(400, rejection.getMessage());
    }

    @PostMapping(consumes = "application/octet-stream")
    @PreAuthorize("@ss.hasPermission('pms:imp-configuration:create')")
    @ApiAccessLog(requestEnable = false, responseEnable = false, operateModule = "配置日志", operateName = "导入日志")
    public CommonResult<ConfigurationLogImportService.Imported> upload(
            @RequestParam Long projectId, @RequestParam String operationId,
            @RequestParam String fileName, HttpServletRequest request) throws IOException {
        int maximum = 20 * 1024 * 1024;
        if (request.getContentLengthLong() > maximum) throw new IllegalArgumentException("日志文件须为 1 字节至 20 MiB");
        try (var stream = request.getInputStream()) {
            byte[] content = stream.readNBytes(maximum + 1);
            if (content.length < 1 || content.length > maximum) throw new IllegalArgumentException("日志文件须为 1 字节至 20 MiB");
            return CommonResult.success(imports.importLog(projectId, operationId, fileName, content));
        }
    }
}
