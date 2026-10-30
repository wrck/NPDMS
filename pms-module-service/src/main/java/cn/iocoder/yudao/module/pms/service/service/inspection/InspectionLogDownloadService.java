package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.pms.platform.api.file.AuthenticatedFileContentApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionExecutionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionTaskDeviceMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.service.inspection.log.InspectionParserDocuments;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.nio.charset.StandardCharsets;

import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

/** A read-only text projection of an existing, device-scoped redacted LOG version. */
@Service
@RequiredArgsConstructor
public class InspectionLogDownloadService {
    private final InspectionAccessService access;
    private final InspectionExecutionMapper executions;
    private final InspectionTaskDeviceMapper devices;
    private final AuthenticatedFileContentApi content;

    public record Query(InspectionRequestContext context, Long taskId, Long executionId) { }
    public record Download(String fileName, byte[] content) {
        public Download { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }

    @Transactional(rollbackFor = Exception.class)
    public Download download(Query query) {
        var task = access.task(new InspectionAccessService.TaskAccess(query.context(), query.taskId(),
                "pms:inspection-task:query", false, false));
        var execution = executions.selectById(query.executionId());
        require(execution != null && !Boolean.TRUE.equals(execution.getDeleted())
                && Objects.equals(execution.getTenantId(), query.context().tenantId())
                && Objects.equals(execution.getTaskId(), task.getId()), "日志执行记录不存在或不属于本任务");
        var device = devices.selectForTask(new InspectionTaskRows(task.getTenantId(), task.getId())).stream()
                .filter(row -> Objects.equals(row.getTenantId(), task.getTenantId()) && Objects.equals(row.getTaskId(), task.getId())
                        && Objects.equals(row.getDeviceId(), execution.getDeviceId())).findFirst().orElse(null);
        require(device != null, "日志设备不属于本任务冻结设备范围");
        require(execution.getArtifactId() != null && execution.getFileVersionNo() != null
                && execution.getFileReferenceKey() != null && execution.getFileSha256() != null, "本次执行没有可下载的已保存日志");
        byte[] bytes = content.read(new AuthenticatedFileContentApi.ReadCommand(new FileArtifactVersionQuery(
                execution.getArtifactId(), execution.getFileVersionNo(), "SRV", "INSPECTION_TASK", task.getId().toString(),
                "LOG", execution.getFileReferenceKey(), FileActionCodes.DOWNLOAD), 52_428_800L));
        require(bytes != null && bytes.length > 0 && execution.getFileSha256().equals(DigestUtil.sha256Hex(bytes)), "日志文件摘要与执行记录不一致");
        var document = InspectionParserDocuments.decode(bytes);
        require(!document.commandBlocks().isEmpty(), "已保存日志没有可生成的命令输出");
        var text = new StringBuilder("# 脱敏日志文本副本：由已保存的巡检日志生成，非上传原文件。\r\n");
        text.append("# task=").append(task.getId()).append("; execution=").append(execution.getId())
                .append("; device=").append(execution.getDeviceId()).append("\r\n");
        for (var block : document.commandBlocks()) {
            require(block != null && block.commandText() != null && block.stdout() != null && block.stderr() != null,
                    "已保存日志命令块不完整，不能生成完整文本副本");
            text.append("[command ").append(block.commandIndex()).append("]\r\n")
                    .append("# status=").append(Objects.toString(block.status(), ""))
                    .append("; truncated=").append(Objects.toString(block.truncated(), ""))
                    .append("; exitCode=").append(Objects.toString(block.exitCode(), "")).append("\r\n")
                    .append("[command-text]\r\n").append(block.commandText()).append("\r\n")
                    .append("[stdout]\r\n").append(block.stdout()).append("\r\n")
                    .append("[stderr]\r\n").append(block.stderr()).append("\r\n");
        }
        byte[] rendered = text.toString().getBytes(StandardCharsets.UTF_8);
        require(rendered.length <= 52_428_800, "脱敏文本副本超过50MB，未截断输出");
        String serial = Objects.toString(device.getSerialNumber(), "device").replaceAll("[^\\p{L}\\p{N}_.-]", "_");
        return new Download("inspection-" + task.getId() + "-" + execution.getId() + "-" + serial + "-redacted.txt", rendered);
    }
}
