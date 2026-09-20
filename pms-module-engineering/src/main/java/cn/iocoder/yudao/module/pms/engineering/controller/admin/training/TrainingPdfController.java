package cn.iocoder.yudao.module.pms.engineering.controller.admin.training;

import cn.iocoder.yudao.module.pms.engineering.service.training.TrainingService;
import cn.iocoder.yudao.module.pms.engineering.service.training.TrainingPdfRenderer;
import jakarta.annotation.Resource;
import cn.iocoder.yudao.module.pms.engineering.enums.TrainingStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.TRAINING_STATUS_INVALID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.TRAINING_NOT_EXISTS;

@RestController
@RequestMapping("/api/v1/pms/training-records")
public class TrainingPdfController {
    @Resource private TrainingService trainingService;

    @GetMapping("/{id}/pdf")
    @PreAuthorize("@ss.hasPermission('pms:imp-training:query')")
    public ResponseEntity<byte[]> download(@PathVariable("id") Long id) throws IOException {
        var record = trainingService.getTraining(id);
        if (record == null) throw exception(TRAINING_NOT_EXISTS);
        if (TrainingStatusEnum.VOID.getStatus().equals(record.getStatus())) {
            throw new ServiceException(TRAINING_STATUS_INVALID.getCode(), "已作废的培训记录不允许下载 PDF");
        }
        if (record.getPrintLayoutSnapshot() != null && cn.iocoder.yudao.framework.common.util.json.JsonUtils
                .parseTree(record.getPrintLayoutSnapshot()).has("engine")) {
            throw new cn.iocoder.yudao.framework.common.exception.ServiceException(
                    cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.TRAINING_ARGUMENT_INVALID.getCode(),
                    "请在培训记录打印预览中生成动态表单 PDF");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(record.getCode() + ".pdf", StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore()).body(TrainingPdfRenderer.render(record));
    }
}
